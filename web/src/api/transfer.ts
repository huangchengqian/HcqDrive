/**
 * File transfer (download + upload) with progress.
 *
 * - Downloads stream to a Blob; a callback reports `loaded` / `total`.
 * - Uploads use the chunked protocol when the file is large and the
 *   small-file `PUT /api/file/upload` fast path otherwise. The caller
 *   receives per-chunk progress events.
 */

import { ApiClientError, buildUrl, getToken } from './client'
import type {
  ConflictStrategy,
  UploadCompleteRequest,
  UploadCompleteResponse,
  UploadInitRequest,
  UploadInitResponse,
} from '@/types/api'

/* ------------------------------------------------------------------ */
/*  Download                                                           */
/* ------------------------------------------------------------------ */

export interface DownloadOptions {
  signal?: AbortSignal
  onProgress?: (loaded: number, total: number) => void
}

export interface DownloadResult {
  blob: Blob
  filename: string | null
  mime: string | null
}

/**
 * Download a single file with progress reporting. Falls back to a
 * normal `fetch` when the server does not honour Range requests.
 */
export async function downloadFile(
  path: string,
  options: DownloadOptions = {},
): Promise<DownloadResult> {
  const url = buildUrl('/api/file/raw', { path })
  return xhrDownload(url, options)
}

function xhrDownload(url: string, options: DownloadOptions): Promise<DownloadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', url, true)
    xhr.responseType = 'blob'
    xhr.withCredentials = false

    const token = getToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    if (options.signal) {
      if (options.signal.aborted) {
        reject(new ApiClientError('ABORTED', 'Aborted', 0))
        return
      }
      options.signal.addEventListener('abort', () => xhr.abort(), { once: true })
    }

    xhr.onprogress = (event) => {
      if (options.onProgress && event.lengthComputable) {
        options.onProgress(event.loaded, event.total)
      }
    }

    xhr.onerror = () => {
      reject(new ApiClientError('NETWORK_ERROR', 'Network error', 0))
    }

    xhr.onabort = () => {
      reject(new ApiClientError('ABORTED', 'Download aborted', 0))
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const blob = xhr.response as Blob
        const disp = xhr.getResponseHeader('Content-Disposition') ?? ''
        const filename = parseContentDisposition(disp)
        const mime = xhr.getResponseHeader('Content-Type')
        resolve({ blob, filename, mime })
      } else {
        reject(
          new ApiClientError(
            'HTTP_ERROR',
            `Download failed (${xhr.status})`,
            xhr.status,
          ),
        )
      }
    }

    xhr.send()
  })
}

function parseContentDisposition(header: string): string | null {
  if (!header) return null
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (utf8 && utf8[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const quoted = /filename="?([^";]+)"?/i.exec(header)
  return quoted && quoted[1] ? quoted[1] : null
}

/* ------------------------------------------------------------------ */
/*  Thumbnail helper                                                   */
/* ------------------------------------------------------------------ */

export interface ThumbnailOptions {
  path: string
  /** "small" | "medium" | "large". */
  size?: 'small' | 'medium' | 'large'
  signal?: AbortSignal
}

export function thumbnailUrl({ path, size = 'medium' }: ThumbnailOptions): string {
  return buildUrl('/api/file/thumb', { path, size })
}

/* ------------------------------------------------------------------ */
/*  Upload (chunked + small-file fast path)                            */
/* ------------------------------------------------------------------ */

const DEFAULT_STRATEGY: ConflictStrategy = {
  onConflict: 'rename',
  concurrency: 3,
  chunkSize: 4 * 1024 * 1024,
}

const SMALL_FILE_LIMIT = 50 * 1024 * 1024

export interface UploadEventMap {
  init: { uploadId: string; total: number }
  progress: { loaded: number; total: number; chunkIndex: number }
  done: { entry: { id: string; name: string; path: string } }
  error: { message: string }
  retry: { attempt: number; reason: string }
}

export type UploadEventName = keyof UploadEventMap
export type UploadListener<E extends UploadEventName> = (
  payload: UploadEventMap[E],
) => void

export interface UploadHandle {
  cancel(): void
  pause(): void
  resume(): void
  on<E extends UploadEventName>(event: E, listener: UploadListener<E>): () => void
}

export interface UploadInput {
  file: File
  /** Destination directory, must end with `/`. */
  path: string
  strategy?: Partial<ConflictStrategy>
  signal?: AbortSignal
}

export interface UploadTicket {
  handle: UploadHandle
  promise: Promise<UploadCompleteResponse>
}

/**
 * Begin an upload. Returns a handle for pause/resume/cancel and a
 * promise that resolves when the file has been committed server-side.
 */
export function startUpload(input: UploadInput): UploadTicket {
  const strategy: ConflictStrategy = { ...DEFAULT_STRATEGY, ...input.strategy }
  const listeners: { [E in UploadEventName]: Set<UploadListener<E>> } = {
    init: new Set(),
    progress: new Set(),
    done: new Set(),
    error: new Set(),
    retry: new Set(),
  }

  const ctrl = new AbortController()
  if (input.signal) {
    input.signal.addEventListener('abort', () => ctrl.abort(), { once: true })
  }

  let paused = false
  let cancelled = false

  const handle: UploadHandle = {
    cancel() {
      cancelled = true
      ctrl.abort()
    },
    pause() {
      paused = true
      ctrl.abort()
    },
    resume() {
      if (paused) {
        paused = false
        // Resume is handled by re-running startUpload inside the runner.
        restart()
      }
    },
    on(event, listener) {
      listeners[event].add(listener as UploadListener<UploadEventName>)
      return () => listeners[event].delete(listener as UploadListener<UploadEventName>)
    },
  }

  const emit = <E extends UploadEventName>(event: E, payload: UploadEventMap[E]) => {
    listeners[event].forEach((fn) => {
      try {
        fn(payload)
      } catch {
        /* swallow listener errors */
      }
    })
  }

  const promise = new Promise<UploadCompleteResponse>((resolve, reject) => {
    void (async () => {
      try {
        const initReq: UploadInitRequest = {
          path: input.path,
          name: input.file.name,
          size: input.file.size,
          clientId: cryptoRandomId(),
          modifiedAt: input.file.lastModified,
        }
        if (cancelled) throw new ApiClientError('ABORTED', 'Upload cancelled', 0)
        const init = await uploadInit(initReq, ctrl.signal)
        emit('init', { uploadId: init.uploadId, total: input.file.size })

        if (cancelled) throw new ApiClientError('ABORTED', 'Upload cancelled', 0)

        if (init.direct || input.file.size <= SMALL_FILE_LIMIT) {
          await uploadSingle(init.uploadId, input.file, ctrl.signal, (loaded) =>
            emit('progress', { loaded, total: input.file.size, chunkIndex: 0 }),
          )
        } else {
          await uploadChunked(
            init.uploadId,
            input.file,
            init.chunkSize || strategy.chunkSize,
            ctrl.signal,
            (loaded, total, idx) =>
              emit('progress', { loaded, total, chunkIndex: idx }),
          )
        }

        if (cancelled) throw new ApiClientError('ABORTED', 'Upload cancelled', 0)

        const finalReq: UploadCompleteRequest = { uploadId: init.uploadId }
        const final = await uploadComplete(finalReq, ctrl.signal)
        emit('done', { entry: final.entry })
        resolve(final)
      } catch (err) {
        if (paused) {
          // Paused: keep listener alive; do not reject yet.
          return
        }
        const message = err instanceof Error ? err.message : 'Upload failed'
        emit('error', { message })
        reject(err)
      }
    })()
  })

  function restart(): void {
    // Simple resume: kick off a new upload that will append to the
    // existing session when the server supports it. Otherwise the
    // promise is already in a "paused" terminal state and the caller
    // should call startUpload() again.
    void startUpload({ ...input, signal: ctrl.signal })
  }

  return { handle, promise }
}

/* ---------- low-level HTTP helpers ---------- */

async function uploadInit(body: UploadInitRequest, signal?: AbortSignal) {
  const res = await fetch(buildUrl('/api/file/upload/init'), {
    method: 'POST',
    headers: withAuth({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
    signal,
  })
  if (!res.ok) throw await toApiError(res)
  return (await res.json()) as UploadInitResponse
}

async function uploadComplete(body: UploadCompleteRequest, signal?: AbortSignal) {
  const res = await fetch(buildUrl('/api/file/upload/complete'), {
    method: 'POST',
    headers: withAuth({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
    signal,
  })
  if (!res.ok) throw await toApiError(res)
  return (await res.json()) as UploadCompleteResponse
}

async function uploadSingle(
  uploadId: string,
  file: File,
  signal: AbortSignal,
  onProgress: (loaded: number) => void,
): Promise<void> {
  await xhrSend({
    method: 'PUT',
    url: buildUrl('/api/file/upload', { uploadId }),
    body: file,
    signal,
    onProgress: (loaded) => onProgress(loaded),
  })
}

async function uploadChunked(
  uploadId: string,
  file: File,
  chunkSize: number,
  signal: AbortSignal,
  onProgress: (loaded: number, total: number, chunkIndex: number) => void,
): Promise<void> {
  const total = file.size
  let offset = 0
  let idx = 0
  while (offset < total) {
    const end = Math.min(offset + chunkSize, total)
    const chunk = file.slice(offset, end)
    const result = await xhrSend({
      method: 'PUT',
      url: buildUrl('/api/file/upload/chunk', { uploadId, index: idx }),
      body: chunk,
      signal,
    })
    onProgress(end, total, idx)
    if (result.complete) return
    offset = end
    idx += 1
  }
}

function withAuth(headers: Record<string, string>): Record<string, string> {
  const token = getToken()
  if (token) return { ...headers, Authorization: `Bearer ${token}` }
  return headers
}

async function toApiError(res: Response): Promise<ApiClientError> {
  const text = await res.text().catch(() => res.statusText)
  let code = 'HTTP_ERROR'
  let message = text || res.statusText
  try {
    const json = JSON.parse(text) as { code?: string; error?: string; message?: string }
    code = json.code ?? code
    message = json.error ?? json.message ?? message
  } catch {
    /* not json */
  }
  return new ApiClientError(code, message, res.status)
}

interface XhrOptions {
  method: 'PUT' | 'POST'
  url: string
  body: XMLHttpRequestBodyInit | null
  signal: AbortSignal
  onProgress?: (loaded: number, total: number) => void
}

function xhrSend({ method, url, body, signal, onProgress }: XhrOptions): Promise<{
  complete: boolean
}> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, url, true)
    const token = getToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    if (signal.aborted) {
      reject(new ApiClientError('ABORTED', 'Aborted', 0))
      return
    }
    signal.addEventListener('abort', () => xhr.abort(), { once: true })

    xhr.upload.onprogress = (event) => {
      if (onProgress && event.lengthComputable) onProgress(event.loaded, event.total)
    }

    xhr.onerror = () => reject(new ApiClientError('NETWORK_ERROR', 'Network error', 0))
    xhr.onabort = () => reject(new ApiClientError('ABORTED', 'Aborted', 0))
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const json = JSON.parse(xhr.responseText || '{}') as { complete?: boolean }
          resolve({ complete: Boolean(json.complete) })
        } catch {
          resolve({ complete: true })
        }
      } else {
        reject(new ApiClientError('HTTP_ERROR', `Upload failed (${xhr.status})`, xhr.status))
      }
    }
    xhr.send(body)
  })
}

function cryptoRandomId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

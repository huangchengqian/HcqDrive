/**
 * API client core. Wraps `fetch`, attaches auth headers, normalises
 * errors, and exposes a single `ApiClientError` to callers.
 *
 * Transport-level concerns only. Endpoint helpers live in sibling files
 * (`./auth.ts`, `./files.ts`, `./transfer.ts`, `./media.ts`, `./share.ts`).
 */

import type { ApiErrorPayload } from '@/types/api'

const TOKEN_KEY = 'hcqdrive:token'
const DEVICE_KEY = 'hcqdrive:device'
const DISCOVERY_KEY = 'hcqdrive:discovery'

const API_BASE: string = (import.meta.env.VITE_API_BASE ?? '/').replace(/\/+$/, '/')

/* ------------------------------------------------------------------ */
/*  Token / device storage                                            */
/* ------------------------------------------------------------------ */

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* storage disabled */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* storage disabled */
  }
}

export function getStoredDevice(): string | null {
  try {
    return localStorage.getItem(DEVICE_KEY)
  } catch {
    return null
  }
}

export function setStoredDevice(name: string): void {
  try {
    localStorage.setItem(DEVICE_KEY, name)
  } catch {
    /* storage disabled */
  }
}

export function clearStoredDevice(): void {
  try {
    localStorage.removeItem(DEVICE_KEY)
  } catch {
    /* storage disabled */
  }
}

export function getApiBase(): string {
  return API_BASE
}

/* ------------------------------------------------------------------ */
/*  Error class                                                       */
/* ------------------------------------------------------------------ */

/** Local representation of an error returned from the server. */
export class ApiClientError extends Error {
  readonly code: string
  readonly status: number
  readonly payload: ApiErrorPayload | null

  constructor(
    code: string,
    message: string,
    status: number,
    payload: ApiErrorPayload | null = null,
  ) {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.status = status
    this.payload = payload
  }

  /** True when the server told us the caller is not authenticated. */
  isAuth(): boolean {
    return this.status === 401 || this.code === 'UNAUTHORIZED'
  }

  /** True when the server says we are forbidden. */
  isForbidden(): boolean {
    return this.status === 403 || this.code === 'FORBIDDEN'
  }

  /** True when the resource is missing. */
  isNotFound(): boolean {
    return this.status === 404 || this.code === 'NOT_FOUND'
  }

  /** True when the request failed before a response (offline, CORS, abort). */
  isNetwork(): boolean {
    return this.status === 0 || this.code === 'NETWORK_ERROR'
  }
}

/* ------------------------------------------------------------------ */
/*  Request helpers                                                   */
/* ------------------------------------------------------------------ */

export type RequestBody = unknown
export type RequestOptions = Omit<RequestInit, 'body' | 'method'> & {
  /** Optional AbortSignal; aborts the request when triggered. */
  signal?: AbortSignal
  /** Bypass auto 401 -> redirect (use for `/api/auth/...`). */
  skipAuthRedirect?: boolean
}

function buildHeaders(init: RequestOptions): Headers {
  const headers = new Headers(init.headers)
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  const token = getToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

function buildBody(body: RequestBody, headers: Headers): BodyInit | undefined {
  if (body === undefined || body === null) return undefined
  if (body instanceof FormData) {
    return body
  }
  if (body instanceof Blob || body instanceof ArrayBuffer) {
    return body
  }
  if (typeof body === 'string') {
    if (!headers.has('Content-Type')) headers.set('Content-Type', 'text/plain;charset=UTF-8')
    return body
  }
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return JSON.stringify(body)
}

async function parsePayload(response: Response): Promise<unknown> {
  const ct = response.headers.get('content-type') ?? ''
  if (ct.includes('application/json')) {
    try {
      return await response.json()
    } catch {
      return null
    }
  }
  // Fallback: text (server might be down or proxy returns HTML).
  try {
    return await response.text()
  } catch {
    return null
  }
}

function normaliseUrl(path: string, query?: Record<string, unknown>): string {
  const base = resolveBase().endsWith('/') ? resolveBase() : `${resolveBase()}/`
  const cleanPath = path.startsWith('/') ? path.slice(1) : path
  const url = `${base}${cleanPath}`
  if (!query) return url
  const params = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null) continue
    params.set(k, String(v))
  }
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

function resolveBase(): string {
  const discovered = readDiscoveredBase()
  if (discovered) return discovered
  return API_BASE
}

function readDiscoveredBase(): string | null {
  try {
    const raw = localStorage.getItem(DISCOVERY_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { baseUrl?: unknown }
    if (typeof parsed?.baseUrl !== 'string' || !parsed.baseUrl) return null
    return parsed.baseUrl
  } catch {
    return null
  }
}

async function request<T>(
  method: string,
  path: string,
  body: RequestBody,
  query: Record<string, unknown> | undefined,
  options: RequestOptions,
): Promise<T> {
  const url = normaliseUrl(path, query)
  const headers = buildHeaders(options)
  const initBody = buildBody(body, headers)

  let response: Response
  try {
    response = await fetch(url, {
      ...options,
      method,
      headers,
      body: initBody,
    })
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Network error'
    throw new ApiClientError('NETWORK_ERROR', message, 0)
  }

  const payload = await parsePayload(response)

  if (response.status === 401 && !options.skipAuthRedirect) {
    clearToken()
    if (
      typeof window !== 'undefined' &&
      window.location.hash !== '#/pair' &&
      !window.location.pathname.includes('/pair')
    ) {
      // Defer so the caller can `await` and observe the error first.
      window.setTimeout(() => {
        window.location.hash = '#/pair'
      }, 0)
    }
  }

  if (!response.ok) {
    const obj = (typeof payload === 'object' && payload !== null ? payload : {}) as Partial<ApiErrorPayload> & {
      message?: string
    }
    const code = obj.code ?? mapStatusToCode(response.status)
    const message = obj.error ?? obj.message ?? response.statusText ?? 'Request failed'
    throw new ApiClientError(code, message, response.status, {
      error: message,
      code,
    })
  }

  return payload as T
}

function mapStatusToCode(status: number): string {
  if (status === 400) return 'BAD_REQUEST'
  if (status === 401) return 'UNAUTHORIZED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  if (status === 409) return 'CONFLICT'
  if (status === 413) return 'PAYLOAD_TOO_LARGE'
  if (status === 415) return 'UNSUPPORTED_MEDIA_TYPE'
  if (status === 422) return 'UNPROCESSABLE_ENTITY'
  if (status === 429) return 'RATE_LIMITED'
  if (status >= 500) return 'SERVER_ERROR'
  return 'HTTP_ERROR'
}

/* ------------------------------------------------------------------ */
/*  Public surface                                                    */
/* ------------------------------------------------------------------ */

export const api = {
  get: <T>(path: string, query?: Record<string, unknown>, options: RequestOptions = {}) =>
    request<T>('GET', path, undefined, query, options),

  post: <T>(
    path: string,
    body?: RequestBody,
    options: RequestOptions = {},
  ) => request<T>('POST', path, body, undefined, options),

  put: <T>(path: string, body?: RequestBody, options: RequestOptions = {}) =>
    request<T>('PUT', path, body, undefined, options),

  patch: <T>(path: string, body?: RequestBody, options: RequestOptions = {}) =>
    request<T>('PATCH', path, body, undefined, options),

  delete: <T>(path: string, query?: Record<string, unknown>, options: RequestOptions = {}) =>
    request<T>('DELETE', path, undefined, query, options),
}

/**
 * Returns the base URL the client is currently targeting. Prefer the cached
 * mDNS-discovered base (e.g. `http://hcqdrive.local:8080`) when available;
 * otherwise falls back to the build-time `VITE_API_BASE` or `/`.
 */
export function getEffectiveApiBase(): string {
  return resolveBase()
}

/** Pure URL builder; useful for <a href> and XMLHttpRequest. */
export function buildUrl(path: string, query?: Record<string, unknown>): string {
  return normaliseUrl(path, query)
}

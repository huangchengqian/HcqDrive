import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiClientError } from '@/api/client'
import { filesApi } from '@/api/files'
import { startUpload, type UploadHandle, type UploadTicket } from '@/api/transfer'
import { fileTypeFor } from '@/lib/fileType'
import type { ConflictStrategy, FileEntry, StatResponse } from '@/types/api'
import { useFilesStore } from './files'
import { useToastStore } from './toast'

export type UploadStatus = 'queued' | 'uploading' | 'paused' | 'done' | 'error' | 'cancelled'

export interface UploadItem {
  id: string
  file: File
  path: string
  name: string
  size: number
  uploaded: number
  status: UploadStatus
  startedAt: number | null
  finishedAt: number | null
  error: string | null
  speed: number
  eta: number
  handle: UploadHandle | null
  result: FileEntry | null
}

const DEFAULT_STRATEGY: ConflictStrategy = {
  onConflict: 'rename',
  concurrency: 3,
  chunkSize: 4 * 1024 * 1024,
}

export const useUploadStore = defineStore('upload', () => {
  const items = ref<UploadItem[]>([])
  const isOpen = ref(false)
  const isCollapsed = ref(false)
  let speedSamples: Array<{ at: number; bytes: number }> = []
  const activeCount = ref(0)

  const totalBytes = computed(() => items.value.reduce((sum, i) => sum + i.size, 0))
  const uploadedBytes = computed(() => items.value.reduce((sum, i) => sum + i.uploaded, 0))
  const overallProgress = computed(() => {
    if (totalBytes.value === 0) return 0
    return Math.min(1, uploadedBytes.value / totalBytes.value)
  })
  const inProgress = computed(() => items.value.filter((i) => i.status === 'uploading' || i.status === 'queued').length)
  const hasFailures = computed(() => items.value.some((i) => i.status === 'error'))
  const isEmpty = computed(() => items.value.length === 0)

  function open(): void {
    isOpen.value = true
  }

  function close(): void {
    if (inProgress.value === 0) {
      items.value = []
    }
    isOpen.value = false
  }

  function toggleCollapsed(): void {
    isCollapsed.value = !isCollapsed.value
  }

  function clearFinished(): void {
    items.value = items.value.filter((i) => i.status === 'uploading' || i.status === 'paused' || i.status === 'queued')
  }

  function retry(itemId: string): void {
    const item = items.value.find((i) => i.id === itemId)
    if (!item) return
    if (item.status !== 'error' && item.status !== 'cancelled') return
    startItem(item)
  }

  function pause(itemId: string): void {
    const item = items.value.find((i) => i.id === itemId)
    if (!item) return
    if (item.status !== 'uploading' && item.status !== 'queued') return
    item.handle?.pause()
    item.status = 'paused'
  }

  function resume(itemId: string): void {
    const item = items.value.find((i) => i.id === itemId)
    if (!item) return
    if (item.status !== 'paused') return
    startItem(item)
  }

  function cancel(itemId: string): void {
    const item = items.value.find((i) => i.id === itemId)
    if (!item) return
    item.handle?.cancel()
    item.status = 'cancelled'
    item.finishedAt = Date.now()
    activeCount.value = Math.max(0, activeCount.value - 1)
  }

  function addFiles(files: FileList | File[], targetPath?: string): void {
    const filesStore = useFilesStore()
    const path = targetPath ?? ensureTrailingSlash(filesStore.currentPath)
    const arr = Array.from(files)
    if (arr.length === 0) return
    const newItems: UploadItem[] = arr.map((file) => ({
      id: `up-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      file,
      path,
      name: file.name,
      size: file.size,
      uploaded: 0,
      status: 'queued' as UploadStatus,
      startedAt: null,
      finishedAt: null,
      error: null,
      speed: 0,
      eta: 0,
      handle: null,
      result: null,
    }))
    items.value = [...items.value, ...newItems]
    open()
    newItems.forEach(startItem)
  }

  function startItem(item: UploadItem): void {
    item.status = 'uploading'
    item.error = null
    item.startedAt = Date.now()
    item.finishedAt = null
    item.uploaded = 0
    speedSamples = []
    activeCount.value += 1

    let ticket: UploadTicket
    try {
      ticket = startUpload({
        file: item.file,
        path: item.path,
        strategy: { ...DEFAULT_STRATEGY },
      })
    } catch (err) {
      failItem(item, err instanceof Error ? err.message : '上传启动失败')
      return
    }

    item.handle = ticket.handle

    const offProgress = ticket.handle.on('progress', (p) => {
      item.uploaded = p.loaded
      const now = Date.now()
      speedSamples.push({ at: now, bytes: p.loaded })
      speedSamples = speedSamples.filter((s) => now - s.at < 5000)
      const oldest = speedSamples[0]
      if (oldest) {
        const dt = (now - oldest.at) / 1000
        const db = p.loaded - oldest.bytes
        item.speed = dt > 0 ? db / dt : 0
        const remaining = item.size - p.loaded
        item.eta = item.speed > 0 ? Math.ceil(remaining / item.speed) : 0
      }
    })

    const offDone = ticket.handle.on('done', (payload) => {
      item.status = 'done'
      item.finishedAt = Date.now()
      item.uploaded = item.size
      item.speed = 0
      item.eta = 0
      item.result = {
        id: payload.entry.id,
        name: payload.entry.name,
        path: payload.entry.path,
        kind: 'file',
        size: item.size,
        mime: item.file.type || null,
        modifiedAt: Date.now(),
        createdAt: Date.now(),
        type: fileTypeFor(item.name, item.file.type),
        hidden: false,
        thumbnailUrl: null,
      }
      activeCount.value = Math.max(0, activeCount.value - 1)
      offProgress()
      offDone()
      const filesStore = useFilesStore()
      filesStore.upsertEntry(item.result)
      const toast = useToastStore()
      toast.push({ tone: 'success', message: `已上传 ${item.name}` })
    })

    void ticket.promise
      .then((final) => {
        if (item.status !== 'done') {
          item.status = 'done'
          item.finishedAt = Date.now()
          item.uploaded = item.size
          const resultEntry: FileEntry = {
            ...final.entry,
            type: final.entry.type ?? fileTypeFor(final.entry.name, final.entry.mime),
          }
          item.result = resultEntry
          const filesStore = useFilesStore()
          filesStore.upsertEntry(resultEntry)
        }
      })
      .catch((err: unknown) => {
        if (item.status === 'paused' || item.status === 'cancelled') return
        const message = err instanceof ApiClientError ? err.message : err instanceof Error ? err.message : '上传失败'
        failItem(item, message)
      })
  }

  function failItem(item: UploadItem, message: string): void {
    item.status = 'error'
    item.error = message
    item.finishedAt = Date.now()
    item.speed = 0
    item.eta = 0
    activeCount.value = Math.max(0, activeCount.value - 1)
    const toast = useToastStore()
    toast.push({ tone: 'error', message: `${item.name} 上传失败:${message}` })
  }

  async function mkdir(targetPath: string, name: string): Promise<StatResponse | null> {
    try {
      const result = await filesApi.mkdir({ path: ensureTrailingSlash(targetPath), name })
      const filesStore = useFilesStore()
      filesStore.upsertEntry({
        ...result.entry,
        type: result.entry.type ?? fileTypeFor(result.entry.name, result.entry.mime),
      })
      return result
    } catch (err) {
      const message = err instanceof ApiClientError ? err.message : '新建文件夹失败'
      const toast = useToastStore()
      toast.push({ tone: 'error', message })
      return null
    }
  }

  return {
    items,
    isOpen,
    isCollapsed,
    totalBytes,
    uploadedBytes,
    overallProgress,
    inProgress,
    hasFailures,
    isEmpty,
    activeCount,
    open,
    close,
    toggleCollapsed,
    clearFinished,
    retry,
    pause,
    resume,
    cancel,
    addFiles,
    mkdir,
  }
})

function ensureTrailingSlash(path: string): string {
  if (!path) return '/'
  return path.endsWith('/') ? path : `${path}/`
}

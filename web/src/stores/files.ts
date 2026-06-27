import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { filesApi } from '@/api/files'
import { ApiClientError } from '@/api/client'
import { fileTypeFor } from '@/lib/fileType'
import type { FileEntry, ListResponse } from '@/types/api'

export type FilesView = 'list' | 'grid'
export type SortKey = 'name' | 'size' | 'modifiedAt' | 'type'
export type SortOrder = 'asc' | 'desc'

export interface FilesError {
  message: string
  code: string
  status: number
}

interface PathStackEntry {
  path: string
  entries: FileEntry[]
}

const ROOT_PATH = '/'
const MAX_HISTORY = 32

function normaliseEntries(payload: ListResponse): FileEntry[] {
  return payload.entries.map((e) => ({
    ...e,
    type: e.type ?? fileTypeFor(e.name, e.mime),
  }))
}

export const useFilesStore = defineStore('files', () => {
  const currentPath = ref(ROOT_PATH)
  const entries = ref<FileEntry[]>([])
  const parent = ref<string | null>(null)
  const total = ref(0)
  const isLoading = ref(false)
  const view = ref<FilesView>('list')
  const sort = ref<SortKey>('name')
  const order = ref<SortOrder>('asc')
  const error = ref<FilesError | null>(null)
  const search = ref('')
  const typeFilter = ref<string | null>(null)
  const historyBack = ref<string[]>([])
  const historyForward = ref<string[]>([])

  const files = computed(() => entries.value.filter((e) => e.kind === 'file'))
  const directories = computed(() => entries.value.filter((e) => e.kind === 'directory' && !e.hidden))
  const hiddenCount = computed(() => entries.value.filter((e) => e.hidden).length)

  const sortedEntries = computed(() => {
    const list = [...entries.value]
    const dirsFirst = (a: FileEntry, b: FileEntry) => {
      if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1
      return 0
    }
    list.sort(dirsFirst)
    const key = sort.value
    const asc = order.value === 'asc'
    list.sort((a, b) => {
      const cmp = key === 'name' ? a.name.localeCompare(b.name)
        : key === 'size' ? a.size - b.size
        : key === 'modifiedAt' ? a.modifiedAt - b.modifiedAt
        : (a.type ?? '').localeCompare(b.type ?? '')
      return asc ? cmp : -cmp
    })
    return list
  })

  const filteredEntries = computed(() => {
    let result = sortedEntries.value
    const q = search.value.trim().toLowerCase()
    if (q) {
      result = result.filter(
        (e) => e.name.toLowerCase().includes(q) || (e.mime ? e.mime.toLowerCase().includes(q) : false),
      )
    }
    if (typeFilter.value) {
      result = result.filter((e) => {
        if (e.kind === 'directory') return typeFilter.value === 'folder'
        if (typeFilter.value === 'image') return e.type === 'image' || e.mime?.startsWith('image/')
        if (typeFilter.value === 'video') return e.type === 'video' || e.mime?.startsWith('video/')
        if (typeFilter.value === 'audio') return e.type === 'audio' || e.mime?.startsWith('audio/')
        if (typeFilter.value === 'document') return e.type === 'document'
        if (typeFilter.value === 'archive') return e.type === 'archive'
        if (typeFilter.value === 'apk') return e.name.endsWith('.apk')
        if (typeFilter.value === 'file') return e.kind === 'file'
        return true
      })
    }
    return result
  })

  const canGoBack = computed(() => historyBack.value.length > 0)
  const canGoForward = computed(() => historyForward.value.length > 0)

  async function load(path: string = currentPath.value, opts: { pushHistory?: boolean } = {}): Promise<void> {
    const { pushHistory = true } = opts
    const cleanPath = path.startsWith('/') ? path : `/${path}`
    isLoading.value = true
    error.value = null
    try {
      const result = await filesApi.list({ path: cleanPath, sort: sort.value, order: order.value })
      if (pushHistory && cleanPath !== currentPath.value) {
        if (currentPath.value !== ROOT_PATH) {
          historyBack.value = [...historyBack.value, currentPath.value].slice(-MAX_HISTORY)
        }
        historyForward.value = []
      }
      currentPath.value = result.path
      parent.value = result.parent
      entries.value = normaliseEntries(result)
      total.value = result.total
    } catch (err) {
      error.value = toError(err)
    } finally {
      isLoading.value = false
    }
  }

  async function refresh(): Promise<void> {
    await load(currentPath.value, { pushHistory: false })
  }

  async function goBack(): Promise<void> {
    if (historyBack.value.length === 0) return
    const next = historyBack.value[historyBack.value.length - 1]
    if (!next) return
    historyBack.value = historyBack.value.slice(0, -1)
    historyForward.value = [...historyForward.value, currentPath.value]
    await load(next, { pushHistory: false })
  }

  async function goForward(): Promise<void> {
    if (historyForward.value.length === 0) return
    const next = historyForward.value[historyForward.value.length - 1]
    if (!next) return
    historyForward.value = historyForward.value.slice(0, -1)
    historyBack.value = [...historyBack.value, currentPath.value]
    await load(next, { pushHistory: false })
  }

  async function goUp(): Promise<void> {
    if (parent.value && parent.value !== currentPath.value) {
      await load(parent.value)
    }
  }

  function setView(v: FilesView): void {
    view.value = v
  }

  function setSort(key: SortKey): void {
    if (sort.value === key) {
      order.value = order.value === 'asc' ? 'desc' : 'asc'
    } else {
      sort.value = key
      order.value = 'asc'
    }
  }

  function setOrder(o: SortOrder): void {
    order.value = o
  }

  function setSearch(q: string): void {
    search.value = q
  }

  function pushEntries(extra: FileEntry[]): void {
    const seen = new Set(entries.value.map((e) => e.path))
    for (const e of extra) {
      if (!seen.has(e.path)) {
        entries.value = [...entries.value, e]
        total.value += 1
      }
    }
  }

  function removeEntriesByIds(ids: string[]): void {
    const set = new Set(ids)
    entries.value = entries.value.filter((e) => !set.has(e.id))
    total.value = Math.max(0, total.value - ids.length)
  }

  function upsertEntry(entry: FileEntry): void {
    const next = [...entries.value]
    const idx = next.findIndex((e) => e.id === entry.id || e.path === entry.path)
    if (idx >= 0) next[idx] = entry
    else next.push(entry)
    entries.value = next
  }

  function clearError(): void {
    error.value = null
  }

  function reset(): void {
    currentPath.value = ROOT_PATH
    entries.value = []
    parent.value = null
    total.value = 0
    error.value = null
    historyBack.value = []
    historyForward.value = []
    search.value = ''
  }

  return {
    currentPath,
    parent,
    entries,
    total,
    isLoading,
    view,
    sort,
    order,
    error,
    search,
    typeFilter,
    directories,
    files,
    hiddenCount,
    sortedEntries,
    filteredEntries,
    canGoBack,
    canGoForward,
    load,
    refresh,
    goBack,
    goForward,
    goUp,
    setView,
    setSort,
    setOrder,
    setSearch,
    pushEntries,
    removeEntriesByIds,
    upsertEntry,
    clearError,
    reset,
  }
})

function toError(err: unknown): FilesError {
  if (err instanceof ApiClientError) {
    return {
      message: err.message,
      code: err.code,
      status: err.status,
    }
  }
  const message = err instanceof Error ? err.message : '未知错误'
  return { message, code: 'UNKNOWN', status: 0 }
}

export type FilesPathStack = PathStackEntry

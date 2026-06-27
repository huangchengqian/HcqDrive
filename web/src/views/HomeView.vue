<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  ChevronUp,
  Download,
  FileText as FileTextIcon,
  Folder as FolderIcon,
  FolderPlus,
  Image as ImageIcon,
  LayoutGrid,
  List as ListIcon,
  Music as MusicIcon,
  Package as PackageIcon,
  RefreshCw,
  Search,
  Trash2,
  Upload,
  Video as VideoIcon,
  X,
} from 'lucide-vue-next'

import AppHeader from '@/components/layout/AppHeader.vue'
import Breadcrumb from '@/components/file/Breadcrumb.vue'
import SortMenu from '@/components/file/SortMenu.vue'
import FileListItem from '@/components/file/FileListItem.vue'
import FileGridItem from '@/components/file/FileGridItem.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import IconButton from '@/components/ui/IconButton.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import ThemeSwitcher from '@/components/ui/ThemeSwitcher.vue'

import RenameDialog from '@/components/dialogs/RenameDialog.vue'
import MoveDialog from '@/components/dialogs/MoveDialog.vue'
import PropertiesDialog from '@/components/dialogs/PropertiesDialog.vue'
import DeleteConfirmDialog from '@/components/dialogs/DeleteConfirmDialog.vue'
import NewFolderDialog from '@/components/dialogs/NewFolderDialog.vue'
import ShareLinkDialog from '@/components/dialogs/ShareLinkDialog.vue'

import UploadDropzone from '@/components/upload/UploadDropzone.vue'
import UploadQueue from '@/components/upload/UploadQueue.vue'

import { useFilesStore } from '@/stores/files'
import { useUploadStore } from '@/stores/upload'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useShortcuts } from '@/composables/useShortcuts'
import { debounce } from '@/composables/debounce'
import { openContextMenu } from '@/composables/useContextMenu'
import { ApiClientError, buildUrl, getToken } from '@/api/client'
import { filesApi } from '@/api/files'
import { shareApi } from '@/api/share'
import { downloadFile } from '@/api/transfer'
import type { FileEntry, ShareCreateRequest } from '@/types/api'
import type { MenuEntry } from '@/components/ui/MenuList.vue'

const FILTERS = [
  { key: 'all', label: '全部', icon: ListIcon },
  { key: 'image', label: '照片', icon: ImageIcon },
  { key: 'video', label: '视频', icon: VideoIcon },
  { key: 'audio', label: '音乐', icon: MusicIcon },
  { key: 'document', label: '文档', icon: FileTextIcon },
  { key: 'apk', label: 'APK', icon: PackageIcon },
  { key: 'folder', label: '文件夹', icon: FolderIcon },
] as const

const router = useRouter()
const files = useFilesStore()
const upload = useUploadStore()
const auth = useAuthStore()
const toast = useToastStore()

const search = ref(files.search)
const activeEntry = ref<FileEntry | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const focusedId = ref<string | null>(null)

const contextMenu = ref({ open: false, x: 0, y: 0, payload: null as { entry: FileEntry; items: FileEntry[] } | null })

const renameState = ref({ open: false, entry: null as FileEntry | null, loading: false })
const moveState = ref({ open: false, sources: [] as FileEntry[], loading: false })
const propertiesState = ref({ open: false, entry: null as FileEntry | null })
const deleteState = ref({ open: false, items: [] as FileEntry[], loading: false })
const newFolderState = ref({ open: false, loading: false })
const shareState = ref({ open: false, entry: null as FileEntry | null, loading: false, resultToken: null as string | null })

const debouncedSearch = debounce((value: string) => {
  files.setSearch(value)
}, 300)

watch(search, (value) => debouncedSearch(value))

const contextItems = computed<MenuEntry[]>(() => {
  const payload = contextMenu.value.payload
  if (!payload) return []
  const single = payload.items.length === 1
  const target = single ? payload.items[0] : null
  const items: MenuEntry[] = []
  if (single && target && target.kind === 'file') {
    items.push({ key: 'open', label: '打开 / 预览', icon: ArrowRight, shortcut: 'Enter' })
  }
  items.push({ key: 'download', label: single ? '下载' : `下载 ${payload.items.length} 项 (ZIP)`, icon: Download })
  items.push({ key: 'share', label: '生成分享链接', icon: RefreshCw })
  if (single && target) {
    items.push({ key: 'rename', label: '重命名', icon: ChevronUp, shortcut: 'F2' })
    items.push({ key: 'move', label: '移动到…', icon: ArrowRight })
  }
  items.push({ key: 'properties', label: '查看属性', icon: ChevronUp })
  const sep: MenuEntry = { key: 'separator-1', separator: true }
  items.push(sep)
  items.push({ key: 'delete', label: '删除', icon: Trash2, tone: 'danger', shortcut: 'Del' })
  return items
})

useShortcuts(() => [
  { key: 'arrowdown', handler: () => moveFocus(1) },
  { key: 'arrowup', handler: () => moveFocus(-1) },
  { key: 'enter', handler: () => activeEntry.value && openEntry(activeEntry.value) },
  { key: ' ', handler: () => activeEntry.value && toggleSelect(activeEntry.value) },
  { key: 'delete', handler: () => bulkDelete() },
  { key: 'f2', handler: () => activeEntry.value && startRename(activeEntry.value) },
  {
    key: 'a',
    meta: true,
    handler: () => {
      const next = new Set(filteredEntries.value.map((e) => e.id))
      selectedIds.value = next
    },
  },
  { key: 'r', meta: true, handler: () => files.refresh() },
  { key: 'escape', handler: () => clearSelection() },
])

onMounted(async () => {
  void auth.fetchStatus()
  if (files.entries.length === 0) {
    await files.load('/')
  }
  await nextTick()
  focusFirst()
})

onBeforeUnmount(() => {
  debouncedSearch.cancel()
})

const selectedIds = ref<Set<string>>(new Set())
const selectedEntries = computed(() =>
  files.filteredEntries.filter((e) => selectedIds.value.has(e.id)),
)
const filteredEntries = computed(() => files.filteredEntries)

function isAllSelected(): boolean {
  if (filteredEntries.value.length === 0) return false
  return filteredEntries.value.every((e) => selectedIds.value.has(e.id))
}

function toggleSelectAll(): void {
  if (isAllSelected()) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(filteredEntries.value.map((e) => e.id))
  }
}

function toggleSelect(entry: FileEntry): void {
  const next = new Set(selectedIds.value)
  if (next.has(entry.id)) next.delete(entry.id)
  else next.add(entry.id)
  selectedIds.value = next
}

function clearSelection(): void {
  selectedIds.value = new Set()
}

function selectOnly(entry: FileEntry): void {
  selectedIds.value = new Set([entry.id])
}

function focusFirst(): void {
  const first = filteredEntries.value[0]
  if (first) focusedId.value = first.id
}

function moveFocus(delta: number): void {
  if (filteredEntries.value.length === 0) return
  const list = filteredEntries.value
  const idx = focusedId.value ? list.findIndex((e) => e.id === focusedId.value) : -1
  const nextIdx = Math.max(0, Math.min(list.length - 1, idx + delta))
  const next = list[nextIdx]
  if (next) {
    focusedId.value = next.id
    activeEntry.value = next
  }
}

function openEntry(entry: FileEntry): void {
  if (entry.kind === 'directory') {
    void files.load(entry.path)
    clearSelection()
    return
  }
  void router.push({ name: 'preview', query: { path: entry.path, from: files.currentPath } })
}

function openMenu(entry: FileEntry, event: MouseEvent): void {
  const items = selectedIds.value.has(entry.id) && selectedIds.value.size > 1
    ? selectedEntries.value
    : [entry]
  if (!selectedIds.value.has(entry.id)) {
    selectOnly(entry)
  }
  openContextMenu(contextMenu.value, { clientX: event.clientX, clientY: event.clientY }, { entry, items })
}

function onContextSelect(key: string): void {
  const payload = contextMenu.value.payload
  if (!payload) return
  const items = payload.items
  const first = items[0]
  switch (key) {
    case 'open':
      if (first) openEntry(first)
      break
    case 'download':
      void bulkDownload(items)
      break
    case 'share':
      if (first) openShare(first)
      break
    case 'rename':
      if (first) startRename(first)
      break
    case 'move':
      openMove(items)
      break
    case 'properties':
      if (first) openProperties(first)
      break
    case 'delete':
      startDelete(items)
      break
  }
}

async function bulkDownload(items: FileEntry[]): Promise<void> {
  const dirs = items.filter((i) => i.kind === 'directory')
  const files = items.filter((i) => i.kind === 'file')
  if (dirs.length > 0 || files.length > 1) {
    const params = new URLSearchParams()
    items.forEach((i) => params.append('paths', i.path))
    const url = buildUrl('/api/file/zip', { paths: items.map((i) => i.path) })
    triggerBrowserDownload(url, suggestedArchiveName(items))
    return
  }
  const target = items[0]
  if (!target) return
  try {
    const result = await downloadFile(target.path)
    saveBlob(result.blob, result.filename ?? target.name)
    toast.push({ tone: 'success', message: `已下载 ${target.name}` })
  } catch (err) {
    handleError(err, '下载失败')
  }
}

function suggestedArchiveName(items: FileEntry[]): string {
  if (items.length === 1) {
    const first = items[0]
    if (first) {
      const base = first.name.replace(/\.[^.]+$/, '')
      return `${base}.zip`
    }
  }
  const ts = new Date().toISOString().slice(0, 16).replace(/[T:]/g, '-')
  return `hcqdrive-${ts}.zip`
}

function triggerBrowserDownload(url: string, filename: string): void {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  if (getToken()) {
    a.dataset.token = '1'
  }
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  toast.push({ tone: 'info', message: '已加入下载队列' })
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function startRename(entry: FileEntry): void {
  if (entry.kind === 'directory' || entry.kind === 'file') {
    renameState.value = { open: true, entry, loading: false }
  }
}

async function submitRename(newName: string): Promise<void> {
  const entry = renameState.value.entry
  if (!entry) return
  renameState.value.loading = true
  try {
    const result = await filesApi.rename({ path: entry.path, newName })
    const next = { ...result.entry, type: result.entry.type ?? entry.type }
    files.upsertEntry(next)
    toast.push({ tone: 'success', message: '已重命名' })
    renameState.value = { open: false, entry: null, loading: false }
  } catch (err) {
    handleError(err, '重命名失败')
    renameState.value.loading = false
  }
}

function openMove(items: FileEntry[]): void {
  moveState.value = { open: true, sources: items, loading: false }
}

async function submitMove(destination: string): Promise<void> {
  moveState.value.loading = true
  try {
    await filesApi.move({ src: moveState.value.sources.map((s) => s.path), dst: destination })
    const removed = moveState.value.sources.map((s) => s.id)
    files.removeEntriesByIds(removed)
    toast.push({ tone: 'success', message: `已移动 ${moveState.value.sources.length} 项` })
    moveState.value = { open: false, sources: [], loading: false }
    clearSelection()
  } catch (err) {
    handleError(err, '移动失败')
    moveState.value.loading = false
  }
}

function openProperties(entry: FileEntry): void {
  propertiesState.value = { open: true, entry }
}

function startDelete(items: FileEntry[]): void {
  if (items.length === 0) return
  deleteState.value = {
    open: true,
    items: items.map((i) => ({ ...i })),
    loading: false,
  }
}

async function submitDelete(): Promise<void> {
  const items = deleteState.value.items
  deleteState.value.loading = true
  try {
    await filesApi.delete({ paths: items.map((i) => i.path), recursive: true })
    const ids = items.map((i) => i.id)
    files.removeEntriesByIds(ids)
    toast.push({ tone: 'success', message: `已删除 ${items.length} 项` })
    deleteState.value = { open: false, items: [], loading: false }
    clearSelection()
  } catch (err) {
    handleError(err, '删除失败')
    deleteState.value.loading = false
  }
}

function bulkDelete(): void {
  const targets = selectedEntries.value
  if (targets.length === 0) return
  startDelete(targets)
}

function openNewFolder(): void {
  newFolderState.value = { open: true, loading: false }
}

async function submitNewFolder(name: string): Promise<void> {
  newFolderState.value.loading = true
  try {
    const result = await filesApi.mkdir({ path: files.currentPath, name })
    if (result.entry) {
      files.upsertEntry({
        ...result.entry,
        type: result.entry.type ?? 'folder',
      })
    } else {
      await files.refresh()
    }
    toast.push({ tone: 'success', message: '已新建文件夹' })
    newFolderState.value = { open: false, loading: false }
  } catch (err) {
    handleError(err, '新建文件夹失败')
    newFolderState.value.loading = false
  }
}

function openShare(entry: FileEntry): void {
  if (entry.kind !== 'file') {
    toast.push({ tone: 'info', message: '文件夹分享将在 M4 提供' })
    return
  }
  shareState.value = { open: true, entry, loading: false, resultToken: null }
}

async function submitShare(payload: ShareCreateRequest): Promise<void> {
  const entry = shareState.value.entry
  if (!entry) return
  shareState.value.loading = true
  try {
    const result = await shareApi.create(payload)
    shareState.value = {
      open: true,
      entry,
      loading: false,
      resultToken: result.token,
    }
  } catch (err) {
    handleError(err, '生成分享链接失败')
    shareState.value.loading = false
  }
}

async function revokeShare(token: string): Promise<void> {
  try {
    await shareApi.revoke({ token })
    toast.push({ tone: 'success', message: '已撤销分享' })
  } catch (err) {
    handleError(err, '撤销失败')
  }
}

function pickFiles(): void {
  fileInput.value?.click()
}

function onFilePicked(event: Event): void {
  const target = event.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  upload.addFiles(target.files)
  target.value = ''
}

function handleError(err: unknown, fallback: string): void {
  const message = err instanceof ApiClientError ? err.message : err instanceof Error ? err.message : fallback
  toast.push({ tone: 'error', message })
}
</script>

<template>
  <div class="relative min-h-screen bg-surface-50 dark:bg-surface-950">
    <AppHeader />

    <main class="mx-auto max-w-7xl px-3 pb-40 pt-3 sm:px-6 sm:pt-4">
      <div class="mb-3 flex items-center gap-2 sm:hidden">
        <Breadcrumb class="min-w-0 flex-1" />
      </div>

      <div class="mb-3 hidden items-center gap-3 sm:flex">
        <Breadcrumb class="min-w-0 flex-1" />
        <div class="flex shrink-0 items-center gap-1">
          <IconButton
            size="sm"
            label="后退"
            :disabled="!files.canGoBack"
            @click="files.goBack()"
          >
            <template #default="{ iconSize }">
              <ArrowLeft :class="iconSize" />
            </template>
          </IconButton>
          <IconButton
            size="sm"
            label="前进"
            :disabled="!files.canGoForward"
            @click="files.goForward()"
          >
            <template #default="{ iconSize }">
              <ArrowRight :class="iconSize" />
            </template>
          </IconButton>
          <IconButton
            v-if="files.parent && files.parent !== files.currentPath"
            size="sm"
            label="上一级"
            @click="files.goUp()"
          >
            <template #default="{ iconSize }">
              <ChevronUp :class="iconSize" />
            </template>
          </IconButton>
        </div>
      </div>

      <div
        class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <div class="flex items-center gap-2">
          <div
            class="flex h-9 w-9 items-center justify-center rounded-md bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300"
            aria-hidden="true"
          >
            <LayoutGrid :size="16" />
          </div>
          <div>
            <h1 class="text-sm font-semibold text-surface-900 sm:text-base dark:text-surface-50">
              {{ files.currentPath && files.currentPath !== '/' ? files.currentPath.split('/').pop() : '所有文件' }}
            </h1>
            <p class="text-[11px] text-surface-500 dark:text-surface-400">
              共 {{ files.total }} 项
              <span v-if="selectedIds.size > 0" class="ml-1 text-primary-600 dark:text-primary-400">
                · 已选 {{ selectedIds.size }}
              </span>
            </p>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <label class="relative flex-1 sm:flex-none">
            <span class="sr-only">搜索文件</span>
            <Search
              :size="14"
              class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-surface-400"
              aria-hidden="true"
            />
            <input
              v-model="search"
              type="search"
              inputmode="search"
              placeholder="搜索当前目录…"
              class="h-9 w-full rounded-md border border-border-light bg-surface-0 pl-8 pr-3 text-sm text-surface-900 placeholder:text-surface-400 transition focus:border-primary-500 focus:outline-none focus:ring-4 focus:ring-primary-500/15 sm:w-60 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100"
            />
          </label>
          <SortMenu />
          <div
            class="flex items-center rounded-md border border-border-light bg-surface-0 p-0.5 dark:border-border-dark dark:bg-surface-900"
            role="group"
            aria-label="视图切换"
          >
            <button
              type="button"
              :class="[
                'flex h-8 w-8 items-center justify-center rounded transition',
                files.view === 'list'
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300'
                  : 'text-surface-500 hover:text-surface-700 dark:hover:text-surface-300',
              ]"
              :aria-pressed="files.view === 'list'"
              aria-label="列表视图"
              @click="files.setView('list')"
            >
              <ListIcon :size="14" />
            </button>
            <button
              type="button"
              :class="[
                'flex h-8 w-8 items-center justify-center rounded transition',
                files.view === 'grid'
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300'
                  : 'text-surface-500 hover:text-surface-700 dark:hover:text-surface-300',
              ]"
              :aria-pressed="files.view === 'grid'"
              aria-label="网格视图"
              @click="files.setView('grid')"
            >
              <LayoutGrid :size="14" />
            </button>
          </div>
          <IconButton size="sm" label="刷新" :disabled="files.isLoading" @click="files.refresh()">
            <template #default="{ iconSize }">
              <RefreshCw :class="[iconSize, files.isLoading ? 'animate-spin' : '']" />
            </template>
          </IconButton>
          <ThemeSwitcher />
          <BaseButton
            variant="secondary"
            size="md"
            class="hidden lg:inline-flex"
            @click="openNewFolder"
          >
            <template #icon>
              <FolderPlus :size="14" />
            </template>
            新建文件夹
          </BaseButton>
          <BaseButton variant="primary" size="md" @click="pickFiles">
            <template #icon>
              <Upload :size="14" />
            </template>
            上传
          </BaseButton>
          <input
            ref="fileInput"
            type="file"
            class="hidden"
            multiple
            @change="onFilePicked"
          />
        </div>
      </div>

      <div class="-mx-3 mb-2 flex flex-wrap gap-1 px-3">
        <button v-for="f in FILTERS" :key="f.key" type="button"
          :class="['rounded-lg px-3 py-1.5 text-xs font-medium transition',
            (files.typeFilter ?? 'all') === f.key
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300'
              : 'text-surface-500 hover:bg-surface-100 hover:text-surface-700 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-200']"
          @click="files.typeFilter = f.key === 'all' ? null : f.key">
          <component :is="f.icon" :size="13" class="inline-block -mt-px mr-1 align-middle" />
          {{ f.label }}
        </button>
      </div>

      <BaseCard padding="none" class="overflow-hidden">
        <div
          v-if="files.error"
          class="px-3 py-3"
        >
          <EmptyState
            :title="files.error.status === 0 ? '网络连接失败' : '加载失败'"
            :description="files.error.message"
            variant="error"
            :loading="files.isLoading"
            @retry="files.refresh()"
          />
        </div>

        <template v-else-if="files.isLoading && files.entries.length === 0">
          <div v-if="files.view === 'list'" class="space-y-1 p-3">
            <div v-for="i in 8" :key="i" class="flex items-center gap-3 rounded-lg p-2.5">
              <Skeleton :size="2.5" shape="circle" />
              <div class="flex-1 space-y-1.5">
                <Skeleton :size="0.85" shape="rect" />
                <Skeleton :size="0.65" shape="line" />
              </div>
            </div>
          </div>
          <div v-else class="grid grid-cols-2 gap-3 p-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            <Skeleton v-for="i in 8" :key="i" :size="6" shape="rect" />
          </div>
        </template>

        <template v-else-if="filteredEntries.length === 0">
          <EmptyState
            v-if="search"
            title="没有匹配的文件"
            description="试试调整搜索关键词,或清除筛选条件"
            variant="empty"
          />
          <EmptyState
            v-else
            title="这个目录还没有内容"
            description="拖拽文件到此处,或点击下方按钮上传第一个文件"
            variant="empty"
          >
            <template #action>
              <div class="mt-1 flex items-center gap-2">
                <BaseButton variant="primary" size="md" @click="pickFiles">
                  <template #icon>
                    <Upload :size="14" />
                  </template>
                  上传文件
                </BaseButton>
                <BaseButton variant="secondary" size="md" @click="openNewFolder">
                  <template #icon>
                    <FolderPlus :size="14" />
                  </template>
                  新建文件夹
                </BaseButton>
              </div>
            </template>
          </EmptyState>
        </template>

        <template v-else>
          <div
            v-if="files.view === 'list'"
            class="divide-y divide-border-light dark:divide-border-dark"
            role="list"
          >
            <div
              class="hidden items-center gap-3 border-b border-border-light bg-surface-50/60 px-3 py-2 text-[11px] font-medium uppercase tracking-wide text-surface-500 lg:flex dark:border-border-dark dark:bg-surface-900/60 dark:text-surface-400"
            >
              <button
                type="button"
                class="flex h-4 w-4 shrink-0 items-center justify-center rounded-[4px] border transition"
                :class="
                  isAllSelected()
                    ? 'border-primary-600 bg-primary-600 text-white'
                    : 'border-surface-300 dark:border-surface-600'
                "
                :aria-checked="isAllSelected()"
                role="checkbox"
                aria-label="全选"
                @click="toggleSelectAll"
              >
                <svg v-if="isAllSelected()" class="h-3 w-3" viewBox="0 0 12 12" fill="none" aria-hidden="true">
                  <path
                    d="M2 6.5L4.5 9L10 3"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
              <span class="flex-1">名称</span>
              <span class="w-20 text-right">大小</span>
              <span class="w-28 text-right">修改时间</span>
              <span class="w-9" />
            </div>

            <FileListItem
              v-for="entry in filteredEntries"
              :key="entry.id"
              :entry="entry"
              selectable
              :selected="selectedIds.has(entry.id)"
              :active="focusedId === entry.id"
              @open="openEntry"
              @select="toggleSelect"
              @menu="openMenu"
              @focus="(e) => (focusedId = e.id)"
            />
          </div>

          <div
            v-else
            class="grid grid-cols-2 gap-3 p-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6"
          >
            <FileGridItem
              v-for="entry in filteredEntries"
              :key="entry.id"
              :entry="entry"
              selectable
              :selected="selectedIds.has(entry.id)"
              :active="focusedId === entry.id"
              @open="openEntry"
              @select="toggleSelect"
              @menu="openMenu"
              @focus="(e) => (focusedId = e.id)"
            />
          </div>
        </template>
      </BaseCard>
    </main>

    <transition
      enter-active-class="transition duration-200 ease-out-soft"
      enter-from-class="translate-y-4 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="selectedIds.size > 0"
        class="fixed inset-x-0 bottom-0 z-20 border-t border-border-light bg-surface-0/95 px-3 py-2.5 backdrop-blur-md safe-bottom dark:border-border-dark dark:bg-surface-900/95"
      >
        <div class="mx-auto flex max-w-7xl items-center gap-2 sm:gap-3">
          <span class="text-sm font-medium text-surface-700 dark:text-surface-200">
            <span class="tabular-nums">{{ selectedIds.size }}</span> 项已选
          </span>
          <button
            type="button"
            class="ml-1 text-xs text-primary-600 hover:underline dark:text-primary-400"
            @click="toggleSelectAll"
          >
            {{ isAllSelected() ? '取消全选' : '全选' }}
          </button>
          <div class="ml-auto flex items-center gap-1.5 sm:gap-2">
            <BaseButton variant="secondary" size="sm" @click="bulkDownload(selectedEntries)">
              <template #icon>
                <Download :size="13" />
              </template>
              <span class="hidden sm:inline">下载</span>
            </BaseButton>
            <BaseButton variant="secondary" size="sm" @click="openMove(selectedEntries)">
              <template #icon>
                <ArrowRight :size="13" />
              </template>
              <span class="hidden sm:inline">移动</span>
            </BaseButton>
            <BaseButton
              v-if="selectedIds.size === 1 && selectedEntries[0]"
              variant="secondary"
              size="sm"
              @click="startRename(selectedEntries[0]!)"
            >
              <template #icon>
                <ChevronUp :size="13" />
              </template>
              <span class="hidden sm:inline">重命名</span>
            </BaseButton>
            <BaseButton variant="danger" size="sm" @click="bulkDelete">
              <template #icon>
                <Trash2 :size="13" />
              </template>
              <span class="hidden sm:inline">删除</span>
            </BaseButton>
            <IconButton size="sm" label="取消选择" @click="clearSelection">
              <template #default="{ iconSize }">
                <X :class="iconSize" />
              </template>
            </IconButton>
          </div>
        </div>
      </div>
    </transition>

    <ContextMenu
      :open="contextMenu.open"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :items="contextItems"
      @select="onContextSelect"
      @close="contextMenu.open = false"
    />

    <RenameDialog
      :open="renameState.open"
      :entry="renameState.entry"
      :loading="renameState.loading"
      @close="renameState = { open: false, entry: null, loading: false }"
      @submit="submitRename"
    />
    <MoveDialog
      :open="moveState.open"
      :sources="moveState.sources"
      :initial-path="files.currentPath"
      :loading="moveState.loading"
      @close="moveState = { open: false, sources: [], loading: false }"
      @submit="submitMove"
    />
    <PropertiesDialog
      :open="propertiesState.open"
      :entry="propertiesState.entry"
      @close="propertiesState = { open: false, entry: null }"
    />
    <DeleteConfirmDialog
      :open="deleteState.open"
      :items="deleteState.items"
      :loading="deleteState.loading"
      @close="deleteState = { open: false, items: [], loading: false }"
      @confirm="submitDelete"
    />
    <NewFolderDialog
      :open="newFolderState.open"
      :parent-path="files.currentPath"
      :loading="newFolderState.loading"
      @close="newFolderState = { open: false, loading: false }"
      @submit="submitNewFolder"
    />
    <ShareLinkDialog
      :open="shareState.open"
      :entry="shareState.entry"
      :loading="shareState.loading"
      :result-token="shareState.resultToken"
      @close="shareState = { open: false, entry: null, loading: false, resultToken: null }"
      @create="submitShare"
      @revoke="revokeShare"
    />

    <UploadDropzone />
    <UploadQueue />
  </div>
</template>

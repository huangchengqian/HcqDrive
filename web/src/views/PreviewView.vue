<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Captions,
  CaptionsOff,
  ChevronLeft,
  ChevronRight,
  Download,
  FileText,
  Maximize2,
  Minimize2,
  Music,
  Pause,
  PictureInPicture,
  Play,
  RotateCcw,
  RotateCw,
  Volume,
  Volume1,
  Volume2,
  VolumeX,
  ZoomIn,
  ZoomOut,
} from 'lucide-vue-next'

import BaseButton from '@/components/ui/BaseButton.vue'
import IconButton from '@/components/ui/IconButton.vue'
import Spinner from '@/components/ui/Spinner.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

import { filesApi } from '@/api/files'
import { ApiClientError, buildUrl } from '@/api/client'
import { fileTypeFor } from '@/lib/fileType'
import { formatAbsoluteTime, formatBytes } from '@/lib/format'
import { highlightCode } from '@/lib/highlight'
import { pickSubtitleCandidates, srtToVtt, type SubtitleCandidate } from '@/lib/subtitle'
import type { FileEntry, FileType } from '@/types/api'

const route = useRoute()
const router = useRouter()

const RATE_KEY = 'hcqdrive:player:rate'
const RATE_OPTIONS = [0.5, 1, 1.5, 2] as const
type Rate = (typeof RATE_OPTIONS)[number]

const filePath = computed(() => {
  const raw = route.query.path
  if (typeof raw === 'string' && raw.length > 0) return raw
  if (Array.isArray(raw) && typeof raw[0] === 'string') return raw[0]
  return null
})

const fromPath = computed(() => {
  const raw = route.query.from
  if (typeof raw === 'string' && raw.length > 0) return raw
  return '/'
})

const entry = ref<FileEntry | null>(null)
const siblingEntries = ref<FileEntry[]>([])
const isLoading = ref(true)
const error = ref<string | null>(null)

const zoom = ref(1)
const rotation = ref(0)
const isFullscreen = ref(false)

async function loadEntry(): Promise<void> {
  const path = filePath.value
  if (!path) {
    error.value = '缺少文件路径'
    isLoading.value = false
    return
  }
  isLoading.value = true
  error.value = null
  try {
    const stat = await filesApi.stat({ path })
    entry.value = {
      ...stat.entry,
      type: stat.entry.type ?? fileTypeFor(stat.entry.name, stat.entry.mime),
    }
    await loadSiblings()
  } catch (err) {
    error.value = err instanceof ApiClientError ? err.message : '加载失败'
  } finally {
    isLoading.value = false
  }
}

async function loadSiblings(): Promise<void> {
  try {
    const list = await filesApi.list({ path: fromPath.value })
    siblingEntries.value = list.entries.filter((e) => e.kind === 'file')
  } catch {
    siblingEntries.value = []
  }
}

function reload(): void {
  void loadEntry()
}

const fileType = computed<FileType>(() => {
  if (!entry.value) return 'unknown'
  return entry.value.type ?? fileTypeFor(entry.value.name, entry.value.mime)
})

const isImage = computed(() => fileType.value === 'image')
const isVideo = computed(() => fileType.value === 'video')
const isAudio = computed(() => fileType.value === 'audio')
const isPdf = computed(() => fileType.value === 'document' && entry.value?.mime === 'application/pdf')
const isText = computed(() => {
  if (!entry.value) return false
  if (fileType.value !== 'document') return false
  const mime = entry.value.mime ?? ''
  return mime.startsWith('text/') || mime === 'application/json' || /\.(txt|md|markdown|log|json|xml|csv|ya?ml|tsv|js|jsx|ts|tsx|py|java|kt|css|scss|html|sh|sql|toml|yaml|yml)$/i.test(entry.value.name)
})

const rawUrl = computed(() => {
  if (!entry.value) return null
  return buildUrl('/api/file/raw', { path: entry.value.path })
})

const downloadUrl = computed(() => {
  if (!entry.value) return null
  return buildUrl('/api/file/raw', { path: entry.value.path, download: 1 })
})

const coverUrl = computed(() => {
  if (!entry.value) return null
  const dir = entry.value.path.split('/').slice(0, -1).join('/')
  if (!dir) return null
  return buildUrl('/api/file/raw', { path: `${dir}/cover.jpg` })
})

const subtitleCandidates = computed<SubtitleCandidate[]>(() => {
  if (!entry.value) return []
  const siblings = siblingEntries.value.map((s) => ({ name: s.name, path: s.path }))
  return pickSubtitleCandidates(entry.value.path, siblings)
})

const activeSubtitleIndex = ref<number>(-1)
const subtitleText = ref<string | null>(null)
const subtitleBlobUrl = ref<string | null>(null)

watch(activeSubtitleIndex, (idx) => {
  void loadSubtitleText(idx)
})

watch(subtitleBlobUrl, (_url, oldUrl) => {
  if (oldUrl) URL.revokeObjectURL(oldUrl)
})

async function loadSubtitleText(idx: number): Promise<void> {
  subtitleText.value = null
  subtitleBlobUrl.value = null
  if (idx < 0) return
  if (idx >= subtitleCandidates.value.length) return
  const candidate = subtitleCandidates.value[idx]
  if (!candidate) return
  try {
    const url = buildUrl('/api/file/raw', { path: candidate.path })
    const res = await fetch(url, { headers: { Accept: 'text/*' } })
    if (!res.ok) return
    const body = await res.text()
    subtitleText.value = candidate.ext === 'srt' ? srtToVtt(body) : body
    const blob = new Blob([subtitleText.value], { type: 'text/vtt;charset=utf-8' })
    subtitleBlobUrl.value = URL.createObjectURL(blob)
  } catch {
    subtitleText.value = null
    subtitleBlobUrl.value = null
  }
}

const textContent = ref<string | null>(null)
const textError = ref<string | null>(null)

async function loadTextContent(): Promise<void> {
  if (!isText.value || !rawUrl.value) return
  textError.value = null
  try {
    const res = await fetch(rawUrl.value, { headers: { Accept: 'text/*' } })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const limit = 256 * 1024
    const reader = res.body?.getReader()
    if (!reader) {
      textContent.value = await res.text()
      return
    }
    const decoder = new TextDecoder('utf-8')
    let received = 0
    const chunks: string[] = []
    while (received < limit) {
      const { value, done } = await reader.read()
      if (done) break
      if (!value) continue
      received += value.byteLength
      chunks.push(decoder.decode(value, { stream: true }))
      if (received >= limit) {
        chunks.push('\n\n…(文件过大,仅显示前 256KB)…')
        break
      }
    }
    textContent.value = chunks.join('')
  } catch (err) {
    textError.value = err instanceof Error ? err.message : '无法读取文件内容'
  }
}

const highlightedContent = computed(() => {
  if (!isText.value || !entry.value || textContent.value == null) return null
  return highlightCode(textContent.value, entry.value.name)
})

watch(
  () => [filePath.value, isText.value],
  () => {
    zoom.value = 1
    rotation.value = 0
    if (isText.value) {
      void loadTextContent()
    } else {
      textContent.value = null
    }
  },
  { immediate: true },
)

watch(
  () => [filePath.value, subtitleCandidates.value.length],
  () => {
    if (subtitleCandidates.value.length === 0) {
      activeSubtitleIndex.value = -1
      return
    }
    activeSubtitleIndex.value = 0
  },
  { immediate: true },
)

const currentIndex = computed(() => {
  if (!entry.value) return -1
  return siblingEntries.value.findIndex((e) => e.path === entry.value?.path)
})
const prevEntry = computed(() => {
  if (currentIndex.value <= 0) return null
  return siblingEntries.value[currentIndex.value - 1] ?? null
})
const nextEntry = computed(() => {
  if (currentIndex.value < 0) return null
  return siblingEntries.value[currentIndex.value + 1] ?? null
})

function goPrev(): void {
  const prev = prevEntry.value
  if (prev) {
    void router.replace({ name: 'preview', query: { path: prev.path, from: fromPath.value } })
  }
}
function goNext(): void {
  const next = nextEntry.value
  if (next) {
    void router.replace({ name: 'preview', query: { path: next.path, from: fromPath.value } })
  }
}

function zoomIn(): void { zoom.value = Math.min(8, +(zoom.value + 0.25).toFixed(2)) }
function zoomOut(): void { zoom.value = Math.max(0.25, +(zoom.value - 0.25).toFixed(2)) }
function rotate(delta: number): void { rotation.value = (rotation.value + delta + 360) % 360 }

async function toggleFullscreen(): Promise<void> {
  if (typeof document === 'undefined') return
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen?.()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen?.()
    isFullscreen.value = false
  }
}

function goBack(): void {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
  } else {
    void router.replace({ name: 'home' })
  }
}

/* ---------- video state ---------- */
const videoRef = ref<HTMLVideoElement | null>(null)
const isVideoPlaying = ref(false)
const isVideoLoading = ref(true)
const videoError = ref<string | null>(null)
const isBuffering = ref(false)
const videoDuration = ref(0)
const videoCurrentTime = ref(0)
const videoVolume = ref(1)
const isMuted = ref(false)
const playbackRate = ref<Rate>(loadStoredRate())
const subtitleMenuOpen = ref(false)
const isPipSupported = ref(false)
const isPipActive = ref(false)

function loadStoredRate(): Rate {
  try {
    const raw = localStorage.getItem(RATE_KEY)
    const n = raw ? Number(raw) : 1
    if (RATE_OPTIONS.includes(n as Rate)) return n as Rate
  } catch {
    /* storage disabled */
  }
  return 1
}

function storeRate(r: Rate): void {
  try {
    localStorage.setItem(RATE_KEY, String(r))
  } catch {
    /* storage disabled */
  }
}

function setRate(r: Rate): void {
  playbackRate.value = r
  storeRate(r)
  if (videoRef.value) videoRef.value.playbackRate = r
  if (audioRef.value) audioRef.value.playbackRate = r
}

function toggleVideo(): void {
  const el = videoRef.value
  if (!el) return
  if (el.paused || el.ended) {
    void el.play().catch(() => undefined)
  } else {
    el.pause()
  }
}

function seekBy(delta: number): void {
  const el = videoRef.value
  if (!el) return
  el.currentTime = Math.max(0, Math.min(el.duration || 0, el.currentTime + delta))
}

function setVolume(v: number): void {
  const clamped = Math.max(0, Math.min(1, v))
  videoVolume.value = clamped
  if (clamped > 0) isMuted.value = false
  if (videoRef.value) {
    videoRef.value.volume = clamped
    videoRef.value.muted = isMuted.value
  }
}

function nudgeVolume(delta: number): void {
  setVolume(videoVolume.value + delta)
}

function toggleMute(): void {
  isMuted.value = !isMuted.value
  if (videoRef.value) videoRef.value.muted = isMuted.value
}

const volumeIcon = computed(() => {
  if (isMuted.value || videoVolume.value === 0) return VolumeX
  if (videoVolume.value < 0.34) return Volume
  if (videoVolume.value < 0.67) return Volume1
  return Volume2
})

async function togglePip(): Promise<void> {
  const el = videoRef.value
  if (!el || !isPipSupported.value) return
  try {
    if (document.pictureInPictureElement === el) {
      await document.exitPictureInPicture()
      isPipActive.value = false
    } else {
      await el.requestPictureInPicture()
      isPipActive.value = true
    }
  } catch {
    isPipActive.value = false
  }
}

function onVideoLoadStart(): void {
  isVideoLoading.value = true
  videoError.value = null
}
function onVideoCanPlay(): void {
  isVideoLoading.value = false
  videoError.value = null
}
function onVideoError(): void {
  isVideoLoading.value = false
  videoError.value = '视频加载失败,可能是格式不受支持或文件已损坏'
}
function onVideoWaiting(): void { isBuffering.value = true }
function onVideoPlaying(): void {
  isBuffering.value = false
  isVideoPlaying.value = true
}
function onVideoPause(): void { isVideoPlaying.value = false }
function onVideoProgress(): void {
  if (videoRef.value) isBuffering.value = videoRef.value.readyState < 3
}
function onVideoTimeUpdate(): void {
  if (videoRef.value) videoCurrentTime.value = videoRef.value.currentTime
}
function onVideoLoadedMeta(): void {
  if (videoRef.value) {
    videoDuration.value = videoRef.value.duration || 0
    videoRef.value.volume = videoVolume.value
    videoRef.value.muted = isMuted.value
    videoRef.value.playbackRate = playbackRate.value
  }
}
function onVideoEnterPiP(): void { isPipActive.value = true }
function onVideoLeavePiP(): void { isPipActive.value = false }
function onVideoVolumeChange(): void {
  if (videoRef.value) {
    videoVolume.value = videoRef.value.volume
    isMuted.value = videoRef.value.muted
  }
}

function retryVideo(): void {
  const el = videoRef.value
  if (!el) return
  videoError.value = null
  isVideoLoading.value = true
  el.load()
}

/* ---------- audio state ---------- */
const audioRef = ref<HTMLAudioElement | null>(null)
const isAudioPlaying = ref(false)
const isAudioLoading = ref(true)
const audioError = ref<string | null>(null)
const audioDuration = ref(0)
const audioCurrentTime = ref(0)
const audioVolume = ref(1)
const isAudioMuted = ref(false)
const audioCoverBroken = ref(false)

function toggleAudio(): void {
  const el = audioRef.value
  if (!el) return
  if (el.paused || el.ended) {
    void el.play().catch(() => undefined)
  } else {
    el.pause()
  }
}

function seekAudio(t: number): void {
  const el = audioRef.value
  if (!el) return
  el.currentTime = Math.max(0, Math.min(el.duration || 0, t))
}

function setAudioVolume(v: number): void {
  const clamped = Math.max(0, Math.min(1, v))
  audioVolume.value = clamped
  if (audioRef.value) audioRef.value.volume = clamped
}

function toggleAudioMute(): void {
  isAudioMuted.value = !isAudioMuted.value
  if (audioRef.value) audioRef.value.muted = isAudioMuted.value
}

function onAudioLoadStart(): void { isAudioLoading.value = true; audioError.value = null }
function onAudioCanPlay(): void { isAudioLoading.value = false }
function onAudioError(): void {
  isAudioLoading.value = false
  audioError.value = '音频加载失败'
}
function onAudioPlay(): void { isAudioPlaying.value = true }
function onAudioPause(): void { isAudioPlaying.value = false }
function onAudioTimeUpdate(): void {
  if (audioRef.value) audioCurrentTime.value = audioRef.value.currentTime
}
function onAudioLoadedMeta(): void {
  if (audioRef.value) {
    audioDuration.value = audioRef.value.duration || 0
    audioRef.value.volume = audioVolume.value
    audioRef.value.muted = isAudioMuted.value
    audioRef.value.playbackRate = playbackRate.value
  }
}

function retryAudio(): void {
  const el = audioRef.value
  if (!el) return
  audioError.value = null
  isAudioLoading.value = true
  el.load()
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const total = Math.floor(seconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = m.toString().padStart(h > 0 ? 2 : 1, '0')
  const ss = s.toString().padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

function onCoverError(): void {
  audioCoverBroken.value = true
}

/* ---------- keyboard ---------- */
function isEditableTarget(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

function onKeydown(event: KeyboardEvent): void {
  if (isEditableTarget(event.target)) return

  if (event.key === 'Escape') {
    event.preventDefault()
    if (subtitleMenuOpen.value) {
      subtitleMenuOpen.value = false
      return
    }
    goBack()
    return
  }

  if (isImage.value) {
    if (event.key === 'ArrowLeft') { event.preventDefault(); zoomOut() }
    else if (event.key === 'ArrowRight') { event.preventDefault(); zoomIn() }
  }

  if (event.key === 'ArrowLeft' && prevEntry.value && !(isVideo.value || isAudio.value)) {
    event.preventDefault()
    goPrev()
    return
  }
  if (event.key === 'ArrowRight' && nextEntry.value && !(isVideo.value || isAudio.value)) {
    event.preventDefault()
    goNext()
    return
  }

  if (isVideo.value && videoRef.value) {
    switch (event.key) {
      case ' ':
      case 'Spacebar':
        event.preventDefault()
        toggleVideo()
        return
      case 'ArrowLeft':
        event.preventDefault()
        seekBy(-10)
        return
      case 'ArrowRight':
        event.preventDefault()
        seekBy(10)
        return
      case 'ArrowUp':
        event.preventDefault()
        nudgeVolume(0.1)
        return
      case 'ArrowDown':
        event.preventDefault()
        nudgeVolume(-0.1)
        return
      case 'f':
      case 'F':
        event.preventDefault()
        void toggleFullscreen()
        return
      case 'm':
      case 'M':
        event.preventDefault()
        toggleMute()
        return
      case 'p':
      case 'P':
        if (isPipSupported.value) {
          event.preventDefault()
          void togglePip()
        }
        return
    }
  }

  if (isAudio.value && audioRef.value) {
    switch (event.key) {
      case ' ':
      case 'Spacebar':
        event.preventDefault()
        toggleAudio()
        return
      case 'ArrowLeft':
        event.preventDefault()
        seekAudio(audioRef.value.currentTime - 10)
        return
      case 'ArrowRight':
        event.preventDefault()
        seekAudio(audioRef.value.currentTime + 10)
        return
      case 'm':
      case 'M':
        event.preventDefault()
        toggleAudioMute()
        return
    }
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = Boolean(document.fullscreenElement)
  })
  if (typeof document !== 'undefined' && 'pictureInPictureEnabled' in document) {
    isPipSupported.value = document.pictureInPictureEnabled === true
  }
  await loadEntry()
  await nextTick()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (subtitleBlobUrl.value) {
    URL.revokeObjectURL(subtitleBlobUrl.value)
  }
})

const subtitleMenuItems = computed(() => {
  const items: Array<{ key: string; label: string; ext?: string }> = [
    { key: '__off__', label: '关闭字幕' },
  ]
  subtitleCandidates.value.forEach((c, i) => {
    items.push({ key: String(i), label: c.label, ext: c.ext })
  })
  return items
})

function onSubtitleSelect(key: string): void {
  subtitleMenuOpen.value = false
  if (key === '__off__') {
    activeSubtitleIndex.value = -1
    return
  }
  const idx = Number(key)
  if (Number.isFinite(idx)) activeSubtitleIndex.value = idx
}
</script>

<template>
  <div class="flex min-h-screen flex-col bg-surface-950 text-surface-100">
    <header
      class="sticky top-0 z-30 flex items-center gap-2 border-b border-white/5 bg-surface-950/90 px-3 py-2.5 backdrop-blur-md safe-top"
    >
      <IconButton size="sm" tone="default" label="返回" class="text-surface-200 hover:bg-white/5 hover:text-white" @click="goBack">
        <template #default="{ iconSize }">
          <ArrowLeft :class="iconSize" />
        </template>
      </IconButton>
      <div class="min-w-0 flex-1">
        <h1 class="truncate text-sm font-semibold text-white">{{ entry?.name ?? '预览' }}</h1>
        <p class="truncate text-[11px] text-surface-400">{{ entry?.path ?? '—' }}</p>
      </div>

      <div v-if="isImage" class="flex items-center gap-1">
        <IconButton size="sm" tone="default" label="缩小" class="text-surface-200 hover:bg-white/5" @click="zoomOut">
          <template #default="{ iconSize }">
            <ZoomOut :class="iconSize" />
          </template>
        </IconButton>
        <span class="min-w-[3rem] text-center text-[11px] tabular-nums text-surface-300">{{ Math.round(zoom * 100) }}%</span>
        <IconButton size="sm" tone="default" label="放大" class="text-surface-200 hover:bg-white/5" @click="zoomIn">
          <template #default="{ iconSize }">
            <ZoomIn :class="iconSize" />
          </template>
        </IconButton>
        <IconButton size="sm" tone="default" label="逆时针旋转" class="text-surface-200 hover:bg-white/5" @click="rotate(-90)">
          <template #default="{ iconSize }">
            <RotateCcw :class="iconSize" />
          </template>
        </IconButton>
        <IconButton size="sm" tone="default" label="顺时针旋转" class="text-surface-200 hover:bg-white/5" @click="rotate(90)">
          <template #default="{ iconSize }">
            <RotateCcw :class="iconSize" />
          </template>
        </IconButton>
        <IconButton size="sm" tone="default" :label="isFullscreen ? '退出全屏' : '全屏'" class="text-surface-200 hover:bg-white/5" @click="toggleFullscreen">
          <template #default="{ iconSize }">
            <component :is="isFullscreen ? Minimize2 : Maximize2" :class="iconSize" />
          </template>
        </IconButton>
      </div>

      <a
        v-if="downloadUrl"
        :href="downloadUrl"
        :download="entry?.name"
        class="ml-1 flex h-8 items-center gap-1.5 rounded-md bg-primary-600 px-3 text-xs font-medium text-white shadow-sm transition hover:bg-primary-700"
      >
        <Download :size="13" />
        <span>下载</span>
      </a>
    </header>

    <main class="relative flex flex-1 flex-col overflow-hidden">
      <div
        v-if="isLoading"
        class="flex flex-1 items-center justify-center"
      >
        <Spinner size="lg" />
      </div>

      <EmptyState
        v-else-if="error && !entry"
        :title="'无法预览'"
        :description="error"
        variant="error"
        @retry="reload"
      />

      <template v-else-if="entry">
        <div class="relative flex-1 overflow-auto bg-black/60">
          <div
            v-if="isImage && rawUrl"
            class="flex min-h-full items-center justify-center p-4"
          >
            <img
              :src="rawUrl"
              :alt="entry.name"
              :style="{ transform: `scale(${zoom}) rotate(${rotation}deg)`, transition: 'transform 200ms ease', imageOrientation: 'from-image' }"
              class="max-h-[80vh] max-w-full select-none rounded shadow-floating"
              draggable="false"
            />
          </div>

          <div
            v-else-if="isVideo && rawUrl"
            class="flex min-h-full flex-col items-center justify-center gap-3 p-4"
          >
            <div class="relative w-full max-w-5xl">
              <video
                ref="videoRef"
                :src="rawUrl"
                playsinline
                preload="metadata"
                class="mx-auto block max-h-[78vh] w-full rounded bg-black shadow-floating"
                :poster="coverUrl ?? undefined"
                crossorigin="anonymous"
                :style="{ colorScheme: 'dark' }"
                @loadstart="onVideoLoadStart"
                @canplay="onVideoCanPlay"
                @error="onVideoError"
                @waiting="onVideoWaiting"
                @playing="onVideoPlaying"
                @play="onVideoPlaying"
                @pause="onVideoPause"
                @progress="onVideoProgress"
                @timeupdate="onVideoTimeUpdate"
                @loadedmetadata="onVideoLoadedMeta"
                @volumechange="onVideoVolumeChange"
                @enterpictureinpicture="onVideoEnterPiP"
                @leavepictureinpicture="onVideoLeavePiP"
              >
                <track
                  v-if="subtitleBlobUrl"
                  :key="subtitleBlobUrl"
                  :src="subtitleBlobUrl"
                  kind="subtitles"
                  srclang="zh"
                  :label="activeSubtitleIndex >= 0 && subtitleCandidates[activeSubtitleIndex] ? subtitleCandidates[activeSubtitleIndex]!.label : '字幕'"
                  default
                />
              </video>

              <div
                v-if="isVideoLoading && !videoError"
                class="pointer-events-none absolute inset-0 flex items-center justify-center"
                aria-hidden="true"
              >
                <div class="flex flex-col items-center gap-2 rounded-md bg-black/50 px-4 py-3">
                  <Spinner size="md" inline />
                  <span class="text-xs text-white/80">正在加载…</span>
                </div>
              </div>

              <div
                v-if="isBuffering && !isVideoLoading"
                class="pointer-events-none absolute inset-x-0 top-0 h-0.5 overflow-hidden"
                aria-hidden="true"
              >
                <div class="h-full w-1/3 animate-[indeterminate_1.2s_linear_infinite] rounded-full bg-primary-400" />
              </div>

              <div
                v-if="videoError"
                class="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/70 text-center"
                role="alert"
              >
                <p class="text-sm font-medium text-white">{{ videoError }}</p>
                <BaseButton variant="secondary" size="sm" @click="retryVideo">
                  <template #icon>
                    <RotateCw :size="13" />
                  </template>
                  重试
                </BaseButton>
              </div>

              <div
                v-if="isPipActive"
                class="pointer-events-none absolute right-2 top-2 rounded-md bg-primary-500/90 px-2 py-1 text-[10px] font-medium text-white shadow-sm"
              >
                画中画
              </div>
            </div>

            <div class="flex w-full max-w-5xl flex-wrap items-center justify-center gap-2 text-xs text-surface-300">
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 hover:bg-white/15"
                :aria-label="isVideoPlaying ? '暂停' : '播放'"
                @click="toggleVideo"
              >
                <component :is="isVideoPlaying ? Pause : Play" :size="14" />
              </button>

              <div class="flex items-center gap-1 rounded-full bg-white/5 px-2 py-1">
                <button
                  type="button"
                  class="flex h-6 w-6 items-center justify-center rounded-full text-surface-300 hover:bg-white/10 hover:text-white"
                  :aria-label="isMuted ? '取消静音' : '静音'"
                  @click="toggleMute"
                >
                  <component :is="volumeIcon" :size="13" />
                </button>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.01"
                  :value="isMuted ? 0 : videoVolume"
                  class="h-1 w-20 accent-primary-500"
                  aria-label="音量"
                  @input="setVolume(Number(($event.target as HTMLInputElement).value))"
                />
              </div>

              <span class="tabular-nums text-[11px] text-surface-400">
                {{ formatTime(videoCurrentTime) }} / {{ formatTime(videoDuration) }}
              </span>

              <div class="flex items-center gap-1 rounded-full bg-white/5 px-1 py-0.5">
                <span class="px-1 text-[10px] uppercase text-surface-400">速率</span>
                <button
                  v-for="r in RATE_OPTIONS"
                  :key="r"
                  type="button"
                  :class="[
                    'rounded-full px-2 py-0.5 text-[11px] tabular-nums transition',
                    playbackRate === r
                      ? 'bg-primary-600 text-white shadow-sm'
                      : 'text-surface-300 hover:bg-white/10 hover:text-white',
                  ]"
                  :aria-label="`播放速率 ${r}x`"
                  @click="setRate(r)"
                >
                  {{ r }}x
                </button>
              </div>

              <div class="relative">
                <button
                  type="button"
                  :class="[
                    'flex h-7 items-center gap-1.5 rounded-full px-2.5 text-[11px] transition',
                    activeSubtitleIndex >= 0
                      ? 'bg-primary-600 text-white'
                      : 'bg-white/5 text-surface-300 hover:bg-white/10 hover:text-white',
                  ]"
                  :aria-label="'字幕'"
                  :aria-expanded="subtitleMenuOpen"
                  @click="subtitleMenuOpen = !subtitleMenuOpen"
                >
                  <component :is="activeSubtitleIndex >= 0 ? Captions : CaptionsOff" :size="13" />
                  <span>{{ activeSubtitleIndex >= 0 ? subtitleCandidates[activeSubtitleIndex]?.label : '字幕' }}</span>
                  <ChevronRight :size="11" class="-rotate-90" />
                </button>
                <div
                  v-if="subtitleMenuOpen"
                  class="absolute bottom-full left-1/2 z-40 mb-2 w-56 -translate-x-1/2 overflow-hidden rounded-lg border border-white/10 bg-surface-900/95 p-1 text-surface-100 shadow-floating backdrop-blur-md"
                  role="menu"
                >
                  <button
                    v-for="item in subtitleMenuItems"
                    :key="item.key"
                    type="button"
                    :class="[
                      'flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-xs transition',
                      (item.key === '__off__' && activeSubtitleIndex === -1) ||
                      item.key === String(activeSubtitleIndex)
                        ? 'bg-primary-600/20 text-primary-200'
                        : 'hover:bg-white/10',
                    ]"
                    @click="onSubtitleSelect(item.key)"
                  >
                    <component :is="item.key === '__off__' ? CaptionsOff : Captions" :size="12" />
                    <span class="flex-1 truncate">{{ item.label }}</span>
                    <span v-if="item.ext" class="text-[10px] uppercase text-surface-400">{{ item.ext }}</span>
                  </button>
                  <p
                    v-if="subtitleCandidates.length === 0"
                    class="px-2 py-2 text-[11px] text-surface-400"
                  >
                    同目录未发现字幕文件
                  </p>
                </div>
              </div>

              <button
                v-if="isPipSupported"
                type="button"
                class="flex h-7 items-center gap-1.5 rounded-full bg-white/5 px-2.5 text-[11px] text-surface-300 hover:bg-white/10 hover:text-white"
                :aria-label="isPipActive ? '退出画中画' : '画中画'"
                @click="togglePip"
              >
                <PictureInPicture :size="13" />
                <span>画中画</span>
              </button>

              <button
                type="button"
                class="flex h-7 items-center gap-1.5 rounded-full bg-white/5 px-2.5 text-[11px] text-surface-300 hover:bg-white/10 hover:text-white"
                :aria-label="isFullscreen ? '退出全屏' : '全屏'"
                @click="toggleFullscreen"
              >
                <component :is="isFullscreen ? Minimize2 : Maximize2" :size="12" />
                <span>{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
              </button>
            </div>

            <p class="text-[10px] text-surface-500">
              快捷键: Space 播放/暂停 · ← → 进度 · ↑ ↓ 音量 · M 静音 · F 全屏 · P 画中画
            </p>
          </div>

          <div
            v-else-if="isAudio && rawUrl"
            class="flex min-h-full flex-col items-center justify-center gap-4 p-6"
          >
            <div class="flex w-full max-w-md flex-col items-center gap-4 rounded-2xl bg-white/5 p-6 shadow-floating">
              <div
                class="relative flex h-44 w-44 items-center justify-center overflow-hidden rounded-2xl bg-gradient-to-br from-primary-500/40 to-primary-700/40 text-primary-100"
              >
                <template v-if="coverUrl && !audioCoverBroken">
                  <img
                    :src="coverUrl"
                    alt="封面"
                    class="h-full w-full object-cover"
                    @error="onCoverError"
                  />
                </template>
                <Music v-else :size="56" :stroke-width="1.4" />
              </div>

              <div class="w-full text-center">
                <p class="truncate text-base font-medium text-white">{{ entry.name }}</p>
                <p class="mt-1 text-[11px] uppercase tracking-wide text-surface-400">音频</p>
              </div>

              <audio
                ref="audioRef"
                :src="rawUrl"
                preload="metadata"
                class="hidden"
                @loadstart="onAudioLoadStart"
                @canplay="onAudioCanPlay"
                @error="onAudioError"
                @play="onAudioPlay"
                @pause="onAudioPause"
                @timeupdate="onAudioTimeUpdate"
                @loadedmetadata="onAudioLoadedMeta"
              />

              <div v-if="isAudioLoading && !audioError" class="flex items-center gap-2 text-xs text-surface-400">
                <Spinner size="sm" inline />
                正在加载…
              </div>

              <div v-else-if="audioError" class="flex flex-col items-center gap-2 text-center">
                <p class="text-xs text-danger-400">{{ audioError }}</p>
                <BaseButton variant="secondary" size="sm" @click="retryAudio">
                  <template #icon>
                    <RotateCw :size="13" />
                  </template>
                  重试
                </BaseButton>
              </div>

              <div v-else class="flex w-full flex-col gap-3">
                <div class="flex items-center gap-3">
                  <button
                    type="button"
                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-600 text-white shadow-sm transition hover:bg-primary-700"
                    :aria-label="isAudioPlaying ? '暂停' : '播放'"
                    @click="toggleAudio"
                  >
                    <component :is="isAudioPlaying ? Pause : Play" :size="18" />
                  </button>
                  <div class="flex-1">
                    <div class="flex items-center justify-between text-[11px] tabular-nums text-surface-400">
                      <span>{{ formatTime(audioCurrentTime) }}</span>
                      <span>{{ formatTime(audioDuration) }}</span>
                    </div>
                    <input
                      type="range"
                      min="0"
                      :max="audioDuration || 0"
                      step="0.1"
                      :value="audioCurrentTime"
                      class="mt-1 h-1 w-full accent-primary-500"
                      aria-label="播放进度"
                      @input="seekAudio(Number(($event.target as HTMLInputElement).value))"
                    />
                  </div>
                </div>

                <div class="flex flex-wrap items-center justify-center gap-2">
                  <div class="flex items-center gap-1 rounded-full bg-white/5 px-2 py-1">
                    <button
                      type="button"
                      class="flex h-6 w-6 items-center justify-center rounded-full text-surface-300 hover:bg-white/10 hover:text-white"
                      :aria-label="isAudioMuted ? '取消静音' : '静音'"
                      @click="toggleAudioMute"
                    >
                      <component :is="isAudioMuted ? VolumeX : Volume2" :size="13" />
                    </button>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.01"
                      :value="audioVolume"
                      class="h-1 w-16 accent-primary-500"
                      aria-label="音量"
                      @input="setAudioVolume(Number(($event.target as HTMLInputElement).value))"
                    />
                  </div>
                  <div class="flex items-center gap-1 rounded-full bg-white/5 px-1 py-0.5">
                    <button
                      v-for="r in RATE_OPTIONS"
                      :key="r"
                      type="button"
                      :class="[
                        'rounded-full px-2 py-0.5 text-[11px] tabular-nums transition',
                        playbackRate === r
                          ? 'bg-primary-600 text-white'
                          : 'text-surface-300 hover:bg-white/10 hover:text-white',
                      ]"
                      @click="setRate(r)"
                    >
                      {{ r }}x
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <p class="text-[10px] text-surface-500">
              快捷键: Space 播放/暂停 · ← → ±10s · M 静音
            </p>
          </div>

          <div
            v-else-if="isPdf && rawUrl"
            class="h-[calc(100vh-9rem)] w-full"
          >
            <iframe
              :src="rawUrl"
              class="h-full w-full border-0 bg-white"
              :title="entry.name"
            />
          </div>

          <div
            v-else-if="isText"
            class="mx-auto max-w-4xl px-4 py-4"
          >
            <div
              v-if="textContent"
              class="overflow-hidden rounded-md bg-surface-900/80 shadow-floating"
            >
              <pre
                class="scrollbar-thin max-h-[78vh] overflow-auto p-4 font-mono text-[12px] leading-relaxed text-surface-100"
              ><code v-html="highlightedContent ?? ''" /></pre>
            </div>
            <p v-else-if="textError" class="text-sm text-danger-500">{{ textError }}</p>
            <p v-else class="text-sm text-surface-400">读取中…</p>
          </div>

          <div
            v-else
            class="flex min-h-full flex-col items-center justify-center gap-4 p-6 text-center text-surface-300"
          >
            <div class="flex h-20 w-20 items-center justify-center rounded-full bg-white/5">
              <FileText :size="36" :stroke-width="1.4" />
            </div>
            <div>
              <p class="text-base font-medium text-white">该类型暂不支持在线预览</p>
              <p class="mt-1 text-xs text-surface-400">点击右上角下载到本地查看</p>
            </div>
            <a
              v-if="downloadUrl"
              :href="downloadUrl"
              :download="entry.name"
              class="flex h-9 items-center gap-1.5 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
            >
              <Download :size="14" />
              <span>下载文件</span>
            </a>
          </div>
        </div>

        <footer
          v-if="prevEntry || nextEntry"
          class="flex items-center justify-between gap-2 border-t border-white/5 bg-surface-950/90 px-3 py-2 text-xs text-surface-300 backdrop-blur-md safe-bottom"
        >
          <BaseButton
            v-if="prevEntry"
            variant="secondary"
            size="sm"
            @click="goPrev"
          >
            <template #icon>
              <ChevronLeft :size="13" />
            </template>
            上一张
          </BaseButton>
          <span v-else class="text-surface-500">已是第一张</span>
          <span class="text-surface-500">
            {{ entry.kind === 'file' ? formatBytes(entry.size) : '' }}
            <span v-if="entry.modifiedAt" class="ml-2">{{ formatAbsoluteTime(entry.modifiedAt) }}</span>
          </span>
          <BaseButton
            v-if="nextEntry"
            variant="secondary"
            size="sm"
            @click="goNext"
          >
            下一张
            <template #icon>
              <ChevronRight :size="13" />
            </template>
          </BaseButton>
          <span v-else class="text-surface-500">已是最后一张</span>
        </footer>
      </template>
    </main>
  </div>
</template>

<style scoped>
.hl-keyword {
  color: #c084fc;
  font-weight: 500;
}
.hl-string {
  color: #86efac;
}
.hl-number {
  color: #fbbf24;
}
.hl-comment {
  color: #71717a;
  font-style: italic;
}
.hl-type {
  color: #60a5fa;
}
.hl-attr,
.hl-tag,
.hl-key {
  color: #60a5fa;
  font-weight: 500;
}
.hl-title {
  color: #f472b6;
  font-weight: 600;
}

@keyframes indeterminate {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(400%); }
}
</style>
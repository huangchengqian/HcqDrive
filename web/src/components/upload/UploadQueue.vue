<script setup lang="ts">
import { computed } from 'vue'
import { Check, ChevronDown, ChevronUp, File as FileIcon, Pause, Play, RefreshCw, Upload, X } from 'lucide-vue-next'
import { useUploadStore, type UploadItem } from '@/stores/upload'
import { formatBytes } from '@/lib/format'

const upload = useUploadStore()

const overallPercent = computed(() => Math.round(upload.overallProgress * 100))

function progressPct(item: UploadItem): number {
  if (item.size === 0) return item.status === 'done' ? 100 : 0
  return Math.min(100, Math.round((item.uploaded / item.size) * 100))
}

function statusLabel(item: UploadItem): string {
  switch (item.status) {
    case 'queued':
      return '等待中'
    case 'uploading':
      return '上传中'
    case 'paused':
      return '已暂停'
    case 'done':
      return '已完成'
    case 'error':
      return item.error || '失败'
    case 'cancelled':
      return '已取消'
  }
}

function speedLabel(item: UploadItem): string {
  if (item.status !== 'uploading' || item.speed <= 0) return ''
  return `${formatBytes(item.speed)}/s`
}

function etaLabel(item: UploadItem): string {
  if (item.status !== 'uploading' || item.eta <= 0) return ''
  if (item.eta < 60) return `${item.eta} 秒`
  const minutes = Math.floor(item.eta / 60)
  const seconds = item.eta % 60
  return `${minutes} 分 ${seconds} 秒`
}
</script>

<template>
  <teleport to="body">
    <transition
      enter-active-class="transition duration-200 ease-out-soft"
      enter-from-class="translate-y-4 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-out"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-4 opacity-0"
    >
      <div
        v-if="upload.isOpen && !upload.isEmpty"
        class="fixed inset-x-0 bottom-0 z-30 px-3 pb-3 sm:right-4 sm:left-auto sm:bottom-4 sm:w-[360px] sm:px-0 sm:pb-0 safe-bottom"
      >
        <div class="overflow-hidden rounded-xl border border-border-light bg-surface-0/95 shadow-floating backdrop-blur-md dark:border-border-dark dark:bg-surface-900/95">
          <header class="flex items-center gap-2 border-b border-border-light px-3 py-2.5 dark:border-border-dark">
            <span class="flex h-7 w-7 items-center justify-center rounded-md bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
              <Upload :size="14" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="text-sm font-medium text-surface-900 dark:text-surface-50">
                上传队列
                <span class="ml-1 text-xs text-surface-500">({{ upload.items.length }})</span>
              </p>
              <p class="text-[11px] tabular-nums text-surface-500 dark:text-surface-400">
                {{ formatBytes(upload.uploadedBytes) }} / {{ formatBytes(upload.totalBytes) }} · {{ overallPercent }}%
              </p>
            </div>
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded text-surface-500 hover:bg-surface-100 hover:text-surface-700 dark:hover:bg-surface-800 dark:hover:text-surface-200"
              :aria-label="upload.isCollapsed ? '展开' : '折叠'"
              @click="upload.toggleCollapsed()"
            >
              <component :is="upload.isCollapsed ? ChevronUp : ChevronDown" :size="14" />
            </button>
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded text-surface-500 hover:bg-surface-100 hover:text-surface-700 dark:hover:bg-surface-800 dark:hover:text-surface-200"
              aria-label="关闭队列"
              @click="upload.close()"
            >
              <X :size="14" />
            </button>
          </header>

          <div v-if="!upload.isCollapsed" class="max-h-72 overflow-y-auto">
            <div class="px-3 py-2">
              <div class="h-1.5 w-full overflow-hidden rounded-full bg-surface-200 dark:bg-surface-800">
                <div
                  class="h-full rounded-full bg-primary-500 transition-all duration-300 ease-out-soft"
                  :style="{ width: `${overallPercent}%` }"
                />
              </div>
            </div>
            <ul class="divide-y divide-border-light dark:divide-border-dark">
              <li
                v-for="item in upload.items"
                :key="item.id"
                class="flex items-center gap-2.5 px-3 py-2.5"
              >
                <span
                  :class="[
                    'flex h-8 w-8 shrink-0 items-center justify-center rounded-md',
                    item.status === 'error'
                      ? 'bg-danger-50 text-danger-500 dark:bg-danger-500/10 dark:text-danger-400'
                      : item.status === 'done'
                        ? 'bg-success-50 text-success-600 dark:bg-success-500/10 dark:text-success-500'
                        : 'bg-surface-100 text-surface-500 dark:bg-surface-800 dark:text-surface-300',
                  ]"
                >
                  <Check v-if="item.status === 'done'" :size="14" />
                  <X v-else-if="item.status === 'error'" :size="14" />
                  <FileIcon v-else :size="14" />
                </span>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-xs font-medium text-surface-900 dark:text-surface-50" :title="item.name">
                    {{ item.name }}
                  </p>
                  <div class="mt-1 flex items-center gap-2 text-[10px] tabular-nums text-surface-500 dark:text-surface-400">
                    <span>{{ statusLabel(item) }}</span>
                    <span v-if="speedLabel(item)" class="text-primary-600 dark:text-primary-400">{{ speedLabel(item) }}</span>
                    <span v-if="etaLabel(item)">· 剩余 {{ etaLabel(item) }}</span>
                  </div>
                  <div class="mt-1 h-1 w-full overflow-hidden rounded-full bg-surface-200 dark:bg-surface-800">
                    <div
                      :class="[
                        'h-full transition-all duration-200 ease-out-soft',
                        item.status === 'error'
                          ? 'bg-danger-500'
                          : item.status === 'done'
                            ? 'bg-success-500'
                            : 'bg-primary-500',
                      ]"
                      :style="{ width: `${progressPct(item)}%` }"
                    />
                  </div>
                </div>
                <div class="flex shrink-0 items-center gap-0.5">
                  <button
                    v-if="item.status === 'uploading' || item.status === 'queued'"
                    type="button"
                    class="flex h-7 w-7 items-center justify-center rounded text-surface-500 hover:bg-surface-100 hover:text-surface-700 dark:hover:bg-surface-800 dark:hover:text-surface-200"
                    aria-label="暂停"
                    @click="upload.pause(item.id)"
                  >
                    <Pause :size="12" />
                  </button>
                  <button
                    v-else-if="item.status === 'paused'"
                    type="button"
                    class="flex h-7 w-7 items-center justify-center rounded text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
                    aria-label="继续"
                    @click="upload.resume(item.id)"
                  >
                    <Play :size="12" />
                  </button>
                  <button
                    v-else-if="item.status === 'error' || item.status === 'cancelled'"
                    type="button"
                    class="flex h-7 w-7 items-center justify-center rounded text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
                    aria-label="重试"
                    @click="upload.retry(item.id)"
                  >
                    <RefreshCw :size="12" />
                  </button>
                  <button
                    v-if="item.status !== 'done'"
                    type="button"
                    class="flex h-7 w-7 items-center justify-center rounded text-surface-500 hover:bg-danger-50 hover:text-danger-600 dark:hover:bg-danger-500/10 dark:hover:text-danger-500"
                    aria-label="取消"
                    @click="upload.cancel(item.id)"
                  >
                    <X :size="12" />
                  </button>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

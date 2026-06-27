<script setup lang="ts">
import { computed } from 'vue'
import { FolderOpen, MoreHorizontal } from 'lucide-vue-next'
import { formatBytes, formatRelativeTime } from '@/lib/format'
import { fileTypeFor } from '@/lib/fileType'
import type { FileEntry } from '@/types/api'
import FileThumbnail from './FileThumbnail.vue'
import IconButton from '@/components/ui/IconButton.vue'
import { useLongPress } from '@/composables/useLongPress'

const props = withDefaults(
  defineProps<{
    entry: FileEntry
    selectable?: boolean
    selected?: boolean
    active?: boolean
    showThumbnail?: boolean
  }>(),
  { selectable: true, selected: false, active: false, showThumbnail: true },
)

const emit = defineEmits<{
  (e: 'open', entry: FileEntry): void
  (e: 'select', entry: FileEntry): void
  (e: 'menu', entry: FileEntry, event: MouseEvent): void
  (e: 'focus', entry: FileEntry): void
}>()

const type = computed(() => props.entry.type ?? fileTypeFor(props.entry.name, props.entry.mime))
const isDir = computed(() => props.entry.kind === 'directory')
const colorClass = computed(() => {
  if (isDir.value) return 'text-primary-500 dark:text-primary-400'
  switch (type.value) {
    case 'image':
      return 'text-rose-500 dark:text-rose-400'
    case 'video':
      return 'text-violet-500 dark:text-violet-400'
    case 'audio':
      return 'text-amber-500 dark:text-amber-400'
    case 'archive':
      return 'text-yellow-600 dark:text-yellow-400'
    case 'document':
      return 'text-sky-500 dark:text-sky-400'
    default:
      return 'text-surface-400 dark:text-surface-500'
  }
})

const containerClass = computed(() => {
  if (props.selected) {
    return 'border-primary-200 bg-primary-50/70 dark:border-primary-700 dark:bg-primary-500/10'
  }
  if (props.active) {
    return 'border-primary-300 bg-primary-50/30 dark:border-primary-700 dark:bg-primary-500/5'
  }
  return 'border-transparent hover:border-border-light hover:bg-surface-100/60 dark:hover:border-border-dark dark:hover:bg-surface-800/40'
})

const longPress = useLongPress((event) => {
  emit('menu', props.entry, event as unknown as MouseEvent)
}, { duration: 500 })
</script>

<template>
  <div
    :class="[
      'group relative flex items-center gap-3 rounded-lg border px-3 py-2.5 transition duration-200 ease-out-soft',
      containerClass,
    ]"
    role="listitem"
    v-bind="longPress.pressHandlers"
    @click.self="selectable ? emit('select', entry) : undefined"
  >
    <button
      v-if="selectable"
      type="button"
      class="flex h-4 w-4 shrink-0 items-center justify-center rounded-[4px] border transition"
      :class="
        selected
          ? 'border-primary-600 bg-primary-600 text-white'
          : 'border-surface-300 group-hover:border-primary-500 dark:border-surface-600'
      "
      :aria-checked="selected"
      role="checkbox"
      :aria-label="`选择 ${entry.name}`"
      @click.stop="emit('select', entry)"
    >
      <svg v-if="selected" class="h-3 w-3" viewBox="0 0 12 12" fill="none" aria-hidden="true">
        <path d="M2 6.5L4.5 9L10 3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>

    <button
      type="button"
      class="flex min-w-0 flex-1 items-center gap-3 text-left focus-visible:outline-none"
      :aria-label="`打开 ${entry.name}`"
      @click="emit('open', entry)"
      @focus="emit('focus', entry)"
      @contextmenu.prevent="(e) => emit('menu', entry, e as MouseEvent)"
    >
      <div class="h-10 w-10 shrink-0">
        <FileThumbnail v-if="showThumbnail && !isDir" :entry="entry" rounded="md" />
        <span
          v-else
          :class="[
            'flex h-10 w-10 items-center justify-center rounded-md bg-surface-100 transition group-hover:bg-surface-200 dark:bg-surface-800 dark:group-hover:bg-surface-700',
            colorClass,
          ]"
          aria-hidden="true"
        >
          <FolderOpen v-if="isDir" :size="20" :stroke-width="1.8" />
          <span v-else class="text-[10px] font-semibold uppercase text-surface-500">
            {{ entry.name.split('.').pop()?.slice(0, 4) }}
          </span>
        </span>
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <span class="truncate text-sm font-medium text-surface-900 dark:text-surface-50" :title="entry.name">
            {{ entry.name }}
          </span>
          <span
            v-if="entry.hidden"
            class="rounded-full bg-surface-200 px-1.5 py-0.5 text-[9px] font-medium uppercase text-surface-500 dark:bg-surface-700 dark:text-surface-400"
          >隐藏</span>
        </div>
        <div class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-surface-500 dark:text-surface-400">
          <span v-if="isDir" class="rounded-full bg-primary-100 px-2 py-px text-[10px] font-medium uppercase tracking-wide text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
            文件夹
          </span>
          <span v-else class="tabular-nums">{{ formatBytes(entry.size) }}</span>
          <span aria-hidden="true">·</span>
          <span class="lg:hidden">{{ formatRelativeTime(entry.modifiedAt) }}</span>
          <span class="hidden lg:inline">{{ type }}</span>
        </div>
      </div>
      <div class="hidden shrink-0 items-center gap-6 text-xs tabular-nums text-surface-500 lg:flex dark:text-surface-400">
        <span class="w-20 text-right">{{ isDir ? '—' : formatBytes(entry.size) }}</span>
        <span class="w-28 text-right">{{ formatRelativeTime(entry.modifiedAt) }}</span>
      </div>
    </button>

    <IconButton
      size="sm"
      label="更多操作"
      class="opacity-0 transition group-hover:opacity-100 focus-visible:opacity-100"
      @click.stop="(e) => emit('menu', entry, e as unknown as MouseEvent)"
    >
      <template #default="{ iconSize }">
        <MoreHorizontal :class="iconSize" />
      </template>
    </IconButton>
  </div>
</template>

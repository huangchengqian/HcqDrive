<script setup lang="ts">
import { computed } from 'vue'
import { MoreHorizontal } from 'lucide-vue-next'
import { formatBytes, formatRelativeTime } from '@/lib/format'
import type { FileEntry } from '@/types/api'
import FileThumbnail from './FileThumbnail.vue'
import IconButton from '@/components/ui/IconButton.vue'

const props = withDefaults(
  defineProps<{
    entry: FileEntry
    selectable?: boolean
    selected?: boolean
    active?: boolean
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { selectable: true, selected: false, active: false, size: 'md' },
)

const emit = defineEmits<{
  (e: 'open', entry: FileEntry): void
  (e: 'select', entry: FileEntry): void
  (e: 'menu', entry: FileEntry, event: MouseEvent): void
  (e: 'focus', entry: FileEntry): void
}>()
const isDir = computed(() => props.entry.kind === 'directory')

const sizeClass = computed(() => {
  if (props.size === 'sm') return 'h-20'
  if (props.size === 'lg') return 'h-32'
  return 'h-24'
})

const ringClass = computed(() => {
  if (props.selected) {
    return 'border-primary-500 bg-primary-50/80 dark:border-primary-400 dark:bg-primary-500/10'
  }
  if (props.active) {
    return 'border-primary-300 bg-primary-50/40 dark:border-primary-700 dark:bg-primary-500/5'
  }
  return 'border-border-light hover:border-surface-300 hover:bg-surface-50 dark:border-border-dark dark:hover:border-surface-700 dark:hover:bg-surface-800/40'
})
</script>

<template>
  <div
    :class="[
      'group relative flex flex-col items-stretch gap-2 rounded-lg border p-2 text-left transition',
      ringClass,
    ]"
    role="listitem"
  >
    <button
      v-if="selectable"
      type="button"
      class="absolute left-2.5 top-2.5 z-10 flex h-4 w-4 items-center justify-center rounded-[4px] border bg-surface-0 transition dark:bg-surface-900"
      :class="
        selected
          ? 'border-primary-600 bg-primary-600 text-white'
          : 'border-surface-300 opacity-0 group-hover:opacity-100 dark:border-surface-600'
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
      class="flex flex-1 flex-col items-stretch gap-2 text-left focus-visible:outline-none"
      :aria-label="`打开 ${entry.name}`"
      @click="emit('open', entry)"
      @focus="emit('focus', entry)"
    >
      <div :class="['relative w-full overflow-hidden rounded-md', sizeClass]">
        <FileThumbnail v-if="!isDir" :entry="entry" />
        <div
          v-else
          class="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100 text-primary-600 dark:from-primary-500/15 dark:to-primary-500/5 dark:text-primary-300"
          aria-hidden="true"
        >
          <Folder :size="32" :stroke-width="1.6" />
        </div>
      </div>
      <div class="min-w-0 px-1 pb-1">
        <p
          class="truncate text-xs font-medium text-surface-800 dark:text-surface-100"
          :title="entry.name"
        >
          {{ entry.name }}
        </p>
        <p class="mt-0.5 truncate text-[10px] tabular-nums text-surface-500 dark:text-surface-400">
          <span v-if="isDir">{{ entry.childCount !== undefined && entry.childCount !== null ? `${entry.childCount} 项` : '文件夹' }}</span>
          <span v-else>{{ formatBytes(entry.size) }} · {{ formatRelativeTime(entry.modifiedAt) }}</span>
        </p>
      </div>
    </button>

    <IconButton
      size="sm"
      label="更多操作"
      class="absolute right-1.5 top-1.5 opacity-0 transition group-hover:opacity-100 focus-visible:opacity-100"
      @click.stop="(e) => emit('menu', entry, e as unknown as MouseEvent)"
    >
      <template #default="{ iconSize }">
        <MoreHorizontal :class="iconSize" />
      </template>
    </IconButton>
  </div>
</template>

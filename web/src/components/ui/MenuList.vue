<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from 'lucide-vue-next'

export interface MenuItem {
  key: string
  label: string
  icon?: unknown
  tone?: 'default' | 'danger'
  disabled?: boolean
  separator?: false
  shortcut?: string
}

export interface MenuSeparator {
  separator: true
  key: string
}

export type MenuEntry = MenuItem | MenuSeparator

const props = withDefaults(
  defineProps<{
    items: MenuEntry[]
    minWidth?: number
  }>(),
  { minWidth: 220 },
)

const emit = defineEmits<{ (e: 'select', key: string): void }>()

function isItem(entry: MenuEntry): entry is MenuItem {
  return !(entry as MenuSeparator).separator
}

const minWidthStyle = computed(() => ({ minWidth: `${props.minWidth}px` }))
</script>

<template>
  <div
    data-context-menu-root
    :style="minWidthStyle"
    class="overflow-hidden rounded-lg border border-border-light bg-surface-0/95 p-1 shadow-floating backdrop-blur-md dark:border-border-dark dark:bg-surface-900/95"
    role="menu"
  >
    <template v-for="entry in items" :key="entry.key">
      <div
        v-if="!isItem(entry)"
        class="my-1 h-px bg-border-light dark:bg-border-dark"
        role="separator"
      />
      <button
        v-else
        type="button"
        role="menuitem"
        :disabled="entry.disabled"
        :class="[
          'flex w-full items-center gap-2.5 rounded-md px-2.5 py-1.5 text-left text-sm transition',
          entry.tone === 'danger'
            ? 'text-danger-600 hover:bg-danger-50 dark:text-danger-500 dark:hover:bg-danger-500/10'
            : 'text-surface-700 hover:bg-surface-100 dark:text-surface-200 dark:hover:bg-surface-800',
          entry.disabled ? 'cursor-not-allowed opacity-50 hover:bg-transparent dark:hover:bg-transparent' : '',
        ]"
        @click="() => !entry.disabled && emit('select', entry.key)"
      >
        <span
          v-if="entry.icon"
          aria-hidden="true"
          class="flex h-4 w-4 shrink-0 items-center justify-center"
        >
          <component :is="entry.icon" :size="14" :stroke-width="1.8" />
        </span>
        <span class="flex-1 truncate">{{ entry.label }}</span>
        <span
          v-if="entry.shortcut"
          class="ml-2 text-[11px] font-medium text-surface-400 dark:text-surface-500"
        >
          {{ entry.shortcut }}
        </span>
        <ChevronRight v-else :size="12" class="text-surface-300 dark:text-surface-600" />
      </button>
    </template>
  </div>
</template>

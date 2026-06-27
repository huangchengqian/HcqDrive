<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDownAZ, ArrowUpAZ, ChevronDown } from 'lucide-vue-next'
import { useFilesStore, type SortKey } from '@/stores/files'
import { useClickOutside } from '@/composables/useClickOutside'

interface Option {
  key: SortKey
  label: string
}

const files = useFilesStore()
const root = ref<HTMLElement | null>(null)
const open = ref(false)

const labels: Record<SortKey, string> = {
  name: '名称',
  size: '大小',
  modifiedAt: '修改时间',
  type: '类型',
}

const options = computed<Option[]>(() => [
  { key: 'name', label: labels.name },
  { key: 'size', label: labels.size },
  { key: 'modifiedAt', label: labels.modifiedAt },
  { key: 'type', label: labels.type },
])

const activeLabel = computed(() => labels[files.sort])

useClickOutside(root, () => {
  open.value = false
})

function toggle(): void {
  open.value = !open.value
}

function pick(key: SortKey): void {
  if (files.sort !== key) {
    files.setSort(key)
  } else {
    files.setOrder(files.order === 'asc' ? 'desc' : 'asc')
  }
  open.value = false
  void files.refresh()
}

function toggleOrder(): void {
  files.setOrder(files.order === 'asc' ? 'desc' : 'asc')
  void files.refresh()
}
</script>

<template>
  <div ref="root" class="relative flex items-center">
    <button
      type="button"
      class="flex h-9 items-center gap-1.5 rounded-md border border-border-light bg-surface-0 px-2.5 text-sm font-medium text-surface-700 transition hover:border-surface-300 hover:bg-surface-50 dark:border-border-dark dark:bg-surface-900 dark:text-surface-200 dark:hover:border-surface-700 dark:hover:bg-surface-800"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="toggle"
    >
      <ArrowDownAZ v-if="files.order === 'asc'" :size="14" :stroke-width="2" class="text-surface-400" />
      <ArrowUpAZ v-else :size="14" :stroke-width="2" class="text-surface-400" />
      <span>{{ activeLabel }}</span>
      <ChevronDown :size="14" :stroke-width="2" class="text-surface-400" />
    </button>
    <button
      type="button"
      class="ml-1 flex h-9 w-9 items-center justify-center rounded-md border border-border-light bg-surface-0 text-surface-500 transition hover:border-surface-300 hover:text-surface-700 dark:border-border-dark dark:bg-surface-900 dark:text-surface-400 dark:hover:border-surface-700 dark:hover:text-surface-200"
      :aria-label="`切换排序方向,当前 ${files.order === 'asc' ? '升序' : '降序'}`"
      @click="toggleOrder"
    >
      <ArrowDownAZ v-if="files.order === 'asc'" :size="14" :stroke-width="2" />
      <ArrowUpAZ v-else :size="14" :stroke-width="2" />
    </button>
    <transition
      enter-active-class="transition duration-100 ease-out-soft"
      enter-from-class="opacity-0 -translate-y-1 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition duration-75 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0 -translate-y-1"
    >
      <div
        v-if="open"
        class="absolute right-0 top-11 z-30 min-w-[180px] overflow-hidden rounded-lg border border-border-light bg-surface-0/95 p-1 shadow-floating backdrop-blur-md dark:border-border-dark dark:bg-surface-900/95"
        role="menu"
      >
        <button
          v-for="opt in options"
          :key="opt.key"
          type="button"
          :class="[
            'flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-sm transition',
            files.sort === opt.key
              ? 'bg-primary-50 text-primary-700 dark:bg-primary-500/10 dark:text-primary-300'
              : 'text-surface-700 hover:bg-surface-100 dark:text-surface-200 dark:hover:bg-surface-800',
          ]"
          @click="pick(opt.key)"
        >
          <span class="flex h-4 w-4 items-center justify-center">
            <Check v-if="files.sort === opt.key" :size="12" />
          </span>
          <span class="flex-1">{{ opt.label }}</span>
        </button>
      </div>
    </transition>
  </div>
</template>

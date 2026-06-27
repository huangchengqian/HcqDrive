<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, Folder, Home } from 'lucide-vue-next'
import { useFilesStore } from '@/stores/files'

const files = useFilesStore()

interface Crumb {
  label: string
  path: string
  isRoot: boolean
}

const crumbs = computed<Crumb[]>(() => {
  const path = files.currentPath
  if (!path || path === '/') {
    return [{ label: '根目录', path: '/', isRoot: true }]
  }
  const parts = path.split('/').filter(Boolean)
  const out: Crumb[] = [{ label: '根目录', path: '/', isRoot: true }]
  let acc = ''
  for (const part of parts) {
    acc += `/${part}`
    out.push({ label: part, path: acc, isRoot: false })
  }
  return out
})

async function goTo(crumb: Crumb): Promise<void> {
  if (crumb.path === files.currentPath) return
  await files.load(crumb.path)
}
</script>

<template>
  <nav
    class="flex min-w-0 items-center gap-1 text-sm"
    aria-label="路径"
  >
    <ol class="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto scrollbar-thin">
      <li
        v-for="(crumb, i) in crumbs"
        :key="crumb.path"
        class="flex items-center gap-1"
      >
        <button
          type="button"
          :class="[
            'flex shrink-0 items-center gap-1.5 rounded-md px-2 py-1 font-medium transition',
            i === crumbs.length - 1
              ? 'text-surface-900 dark:text-surface-50'
              : 'text-surface-500 hover:bg-surface-100 hover:text-surface-700 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-200',
          ]"
          :aria-current="i === crumbs.length - 1 ? 'page' : undefined"
          @click="goTo(crumb)"
        >
          <Home v-if="crumb.isRoot" :size="13" :stroke-width="2" />
          <Folder v-else :size="13" :stroke-width="2" />
          <span class="max-w-[12rem] truncate">{{ crumb.label }}</span>
        </button>
        <ChevronRight
          v-if="i < crumbs.length - 1"
          :size="13"
          class="shrink-0 text-surface-300 dark:text-surface-600"
          aria-hidden="true"
        />
      </li>
    </ol>
  </nav>
</template>

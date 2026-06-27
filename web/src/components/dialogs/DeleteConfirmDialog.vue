<script setup lang="ts">
import { computed } from 'vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { formatBytes } from '@/lib/format'

interface DeleteItem {
  id: string
  name: string
  path: string
  kind: 'file' | 'directory'
  size: number
}

const props = defineProps<{
  open: boolean
  items: DeleteItem[]
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'close'): void; (e: 'confirm'): void }>()

const summary = computed(() => {
  if (props.items.length === 0) return ''
  const total = props.items.reduce((sum, i) => sum + i.size, 0)
  const files = props.items.filter((i) => i.kind === 'file').length
  const dirs = props.items.filter((i) => i.kind === 'directory').length
  const parts: string[] = []
  if (files > 0) parts.push(`${files} 个文件`)
  if (dirs > 0) parts.push(`${dirs} 个文件夹`)
  const head = parts.length > 0 ? parts.join(' · ') : `${props.items.length} 项`
  return total > 0 ? `${head} · 共 ${formatBytes(total)}` : head
})

const sample = computed(() => props.items.slice(0, 5))
const extraCount = computed(() => Math.max(0, props.items.length - sample.value.length))
</script>

<template>
  <ConfirmDialog
    :open="open"
    title="删除所选项?"
    :message="`此操作不可撤销。${summary}`"
    confirm-label="删除"
    cancel-label="取消"
    :loading="loading"
    tone="danger"
    @cancel="emit('close')"
    @confirm="emit('confirm')"
  >
    <template v-if="open" #default>
      <ul class="mt-2 max-h-32 space-y-1 overflow-y-auto rounded-md bg-surface-100/70 p-2 text-xs dark:bg-surface-800/60">
        <li
          v-for="item in sample"
          :key="item.id"
          class="flex items-center gap-2 truncate text-surface-700 dark:text-surface-200"
        >
          <span class="truncate">{{ item.name }}</span>
          <span v-if="item.kind === 'file'" class="ml-auto text-[10px] tabular-nums text-surface-500">
            {{ formatBytes(item.size) }}
          </span>
        </li>
        <li v-if="extraCount > 0" class="text-surface-500">还有 {{ extraCount }} 项…</li>
      </ul>
    </template>
  </ConfirmDialog>
</template>

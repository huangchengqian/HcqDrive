<script setup lang="ts">
import { computed } from 'vue'
import { Calendar, FileType2, Hash, HardDrive, MapPin, Ruler, Shield } from 'lucide-vue-next'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { formatAbsoluteTime, formatBytes } from '@/lib/format'
import type { FileEntry } from '@/types/api'

const props = defineProps<{
  open: boolean
  entry: FileEntry | null
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const isDir = computed(() => props.entry?.kind === 'directory')

const rows = computed(() => {
  const e = props.entry
  if (!e) return []
  return [
    { icon: FileType2, label: '名称', value: e.name },
    { icon: MapPin, label: '路径', value: e.path },
    { icon: HardDrive, label: '类型', value: isDir.value ? '文件夹' : e.mime ?? '未知' },
    {
      icon: Ruler,
      label: '大小',
      value: isDir.value
        ? e.childCount !== undefined && e.childCount !== null
          ? `${e.childCount} 个项目`
          : '—'
        : formatBytes(e.size),
    },
    { icon: Calendar, label: '修改时间', value: formatAbsoluteTime(e.modifiedAt) },
    {
      icon: Calendar,
      label: '创建时间',
      value: e.createdAt ? formatAbsoluteTime(e.createdAt) : '—',
    },
    { icon: Hash, label: 'ID', value: e.id },
    { icon: Shield, label: '权限', value: isDir.value ? 'rwx' : 'rw-' },
  ]
})
</script>

<template>
  <BaseDialog :open="open" title="属性" size="sm" @close="emit('close')">
    <dl v-if="entry" class="divide-y divide-border-light text-sm dark:divide-border-dark">
      <div
        v-for="row in rows"
        :key="row.label"
        class="flex items-center gap-2.5 py-2 first:pt-0 last:pb-0"
      >
        <span class="flex h-6 w-6 shrink-0 items-center justify-center text-surface-400">
          <component :is="row.icon" :size="13" :stroke-width="2" />
        </span>
        <dt class="w-20 shrink-0 text-xs text-surface-500 dark:text-surface-400">{{ row.label }}</dt>
        <dd class="min-w-0 flex-1 truncate text-surface-800 dark:text-surface-200" :title="String(row.value)">
          {{ row.value }}
        </dd>
      </div>
    </dl>
    <template #footer>
      <BaseButton variant="primary" size="md" @click="emit('close')">关闭</BaseButton>
    </template>
  </BaseDialog>
</template>

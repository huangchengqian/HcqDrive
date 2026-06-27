<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

const props = defineProps<{
  open: boolean
  entry: { id: string; name: string; path: string; kind: 'file' | 'directory' } | null
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', newName: string): void
}>()

const name = ref('')
const error = ref<string | null>(null)

const fullName = computed(() => {
  if (!props.entry) return ''
  const base = props.entry.path.replace(/\/$/, '').split('/').pop() ?? ''
  if (!props.entry.kind || props.entry.kind === 'directory') return base
  const idx = base.lastIndexOf('.')
  return idx > 0 ? base.slice(0, idx) : base
})

const extension = computed(() => {
  if (!props.entry || props.entry.kind !== 'file') return ''
  const base = props.entry.path.split('/').pop() ?? ''
  const idx = base.lastIndexOf('.')
  return idx > 0 ? base.slice(idx) : ''
})

watch(
  () => [props.open, props.entry?.id],
  ([open]) => {
    if (open) {
      name.value = fullName.value
      error.value = null
      void nextTick()
    }
  },
  { immediate: true },
)

function validate(value: string): string | null {
  if (!value.trim()) return '名称不能为空'
  if (value.includes('/') || value.includes('\\')) return '名称不能包含 / 或 \\'
  if (value === '.' || value === '..') return '名称不能是 . 或 ..'
  if (value.length > 200) return '名称过长(最多 200 字符)'
  return null
}

function onSubmit(): void {
  const msg = validate(name.value)
  if (msg) {
    error.value = msg
    return
  }
  const final = extension.value ? `${name.value.trim()}${extension.value}` : name.value.trim()
  if (final === props.entry?.name) {
    emit('close')
    return
  }
  emit('submit', final)
}
</script>

<template>
  <BaseDialog
    :open="open"
    title="重命名"
    description="为文件或文件夹设置新名称"
    size="sm"
    @close="emit('close')"
  >
    <form id="rename-form" class="space-y-3" @submit.prevent="onSubmit">
      <BaseInput
        v-model="name"
        label="新名称"
        placeholder="请输入新名称"
        :error="error ?? undefined"
        :maxlength="200"
        show-count
        autofocus
        @update:model-value="error = null"
      />
      <p v-if="extension" class="text-xs text-surface-500 dark:text-surface-400">
        扩展名将保留为 <span class="font-medium text-surface-700 dark:text-surface-300">{{ extension }}</span>
      </p>
    </form>
    <template #footer>
      <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('close')">取消</BaseButton>
      <BaseButton
        variant="primary"
        size="md"
        :loading="loading"
        @click="onSubmit"
      >
        重命名
      </BaseButton>
    </template>
  </BaseDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { FolderPlus } from 'lucide-vue-next'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

const props = defineProps<{
  open: boolean
  parentPath: string
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'close'): void; (e: 'submit', name: string): void }>()

const name = ref('')
const error = ref<string | null>(null)

watch(
  () => props.open,
  (open) => {
    if (open) {
      name.value = ''
      error.value = null
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

function submit(): void {
  const msg = validate(name.value)
  if (msg) {
    error.value = msg
    return
  }
  emit('submit', name.value.trim())
}
</script>

<template>
  <BaseDialog :open="open" title="新建文件夹" size="sm" @close="emit('close')">
    <div class="space-y-3">
      <div class="flex items-center gap-2 rounded-md bg-primary-50/60 px-3 py-2 text-xs text-primary-700 dark:bg-primary-500/10 dark:text-primary-300">
        <FolderPlus :size="14" />
        <span>将在 <span class="font-medium">{{ parentPath === '/' ? '根目录' : parentPath }}</span> 内创建</span>
      </div>
      <BaseInput
        v-model="name"
        label="文件夹名称"
        placeholder="例如:工作报告"
        :error="error ?? undefined"
        :maxlength="200"
        show-count
        autofocus
        @update:model-value="error = null"
        @enter="submit"
      />
    </div>
    <template #footer>
      <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('close')">取消</BaseButton>
      <BaseButton variant="primary" size="md" :loading="loading" @click="submit">创建</BaseButton>
    </template>
  </BaseDialog>
</template>

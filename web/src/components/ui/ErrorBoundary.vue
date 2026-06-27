<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import { AlertOctagon, RotateCw } from 'lucide-vue-next'
import BaseButton from './BaseButton.vue'

const error = ref<Error | null>(null)
const stack = ref<string | null>(null)

onErrorCaptured((err) => {
  error.value = err instanceof Error ? err : new Error(String(err))
  stack.value = err instanceof Error && err.stack ? err.stack : null
  return false
})

function reset(): void {
  error.value = null
  stack.value = null
}
</script>

<template>
  <div
    v-if="error"
    class="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center"
    role="alert"
  >
    <div
      class="flex h-14 w-14 items-center justify-center rounded-full bg-danger-50 text-danger-500 dark:bg-danger-500/10 dark:text-danger-400"
      aria-hidden="true"
    >
      <AlertOctagon :size="26" />
    </div>
    <div>
      <p class="text-sm font-medium text-surface-700 dark:text-surface-200">
        页面出错了
      </p>
      <p class="mt-1 text-xs text-surface-500 dark:text-surface-400">
        {{ error.message || '发生了一个未处理的错误' }}
      </p>
      <pre
        v-if="stack"
        class="mx-auto mt-3 max-h-40 max-w-lg overflow-auto rounded-md bg-surface-100 p-2 text-left text-[10px] leading-snug text-surface-500 dark:bg-surface-800 dark:text-surface-400"
      >{{ stack }}</pre>
    </div>
    <BaseButton variant="primary" size="md" @click="reset">
      <template #icon>
        <RotateCw :size="14" />
      </template>
      重试
    </BaseButton>
  </div>
  <slot v-else />
</template>

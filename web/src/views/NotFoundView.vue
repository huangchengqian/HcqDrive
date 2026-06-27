<script setup lang="ts">
import { useRouter } from 'vue-router'
import { AlertTriangle, ArrowLeft } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'

const router = useRouter()
function back(): void {
  if (router.options.history.state.back) {
    router.back()
  } else {
    void router.replace({ name: 'home' })
  }
}
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center bg-surface-50 px-4 py-10 dark:bg-surface-950">
    <div
      class="flex h-14 w-14 items-center justify-center rounded-full bg-warning-50 text-warning-500 dark:bg-warning-500/15 dark:text-warning-400"
      aria-hidden="true"
    >
      <AlertTriangle :size="26" />
    </div>
    <h1 class="mt-4 text-lg font-semibold text-surface-900 dark:text-surface-50">页面走丢了</h1>
    <p class="mt-1.5 text-sm text-surface-500 dark:text-surface-400">
      请检查链接,或返回主页继续操作。
    </p>
    <BaseButton variant="primary" size="md" class="mt-5" @click="back">
      <template #icon>
        <ArrowLeft :size="14" />
      </template>
      返回
    </BaseButton>
  </main>
</template>

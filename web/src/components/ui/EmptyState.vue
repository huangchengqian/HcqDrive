<script setup lang="ts">
import { ref, watch } from 'vue'
import { AlertCircle, Inbox, RefreshCw } from 'lucide-vue-next'
import BaseButton from './BaseButton.vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    variant?: 'empty' | 'error' | 'notFound'
    retryLabel?: string
    loading?: boolean
  }>(),
  {
    description: undefined,
    variant: 'empty',
    retryLabel: '重试',
    loading: false,
  },
)

const emit = defineEmits<{ (e: 'retry'): void }>()
const isShaking = ref(false)

watch(
  () => props.title,
  () => {
    isShaking.value = false
    requestAnimationFrame(() => {
      isShaking.value = true
      window.setTimeout(() => (isShaking.value = false), 350)
    })
  },
)
</script>

<template>
  <div
    :class="[
      'flex flex-col items-center justify-center gap-3 px-6 py-16 text-center animate-fade-in',
      isShaking ? 'animate-shake' : '',
    ]"
    role="status"
    :aria-label="title"
  >
    <div
      :class="[
        'flex h-14 w-14 items-center justify-center rounded-full',
        variant === 'error'
          ? 'bg-danger-50 text-danger-500 dark:bg-danger-500/10 dark:text-danger-400'
          : 'bg-surface-100 text-surface-400 dark:bg-surface-800 dark:text-surface-500',
      ]"
      aria-hidden="true"
    >
      <AlertCircle v-if="variant === 'error'" :size="26" />
      <Inbox v-else :size="26" />
    </div>
    <div>
      <p class="text-sm font-medium text-surface-700 dark:text-surface-200">{{ title }}</p>
      <p v-if="description" class="mt-1 text-xs text-surface-500 dark:text-surface-400">
        {{ description }}
      </p>
    </div>
    <slot name="action">
      <BaseButton
        v-if="variant === 'error'"
        variant="secondary"
        size="sm"
        :loading="loading"
        @click="emit('retry')"
      >
        <template #icon>
          <RefreshCw :size="14" />
        </template>
        {{ retryLabel }}
      </BaseButton>
    </slot>
  </div>
</template>

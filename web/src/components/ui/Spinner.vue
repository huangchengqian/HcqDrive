<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
    label?: string
    inline?: boolean
  }>(),
  { size: 'md', label: '加载中', inline: false },
)

const sizeClass = computed(() => {
  if (props.size === 'sm') return 'h-4 w-4'
  if (props.size === 'lg') return 'h-7 w-7'
  return 'h-5 w-5'
})
</script>

<template>
  <span
    :class="[
      'inline-flex items-center gap-2 text-surface-500 dark:text-surface-400',
      inline ? '' : 'justify-center',
    ]"
    role="status"
    :aria-label="label"
  >
    <Loader2 :class="[sizeClass, 'animate-spin text-primary-500']" aria-hidden="true" />
    <span v-if="!inline" class="text-xs">{{ label }}</span>
  </span>
</template>

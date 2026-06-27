<script setup lang="ts">
import { computed } from 'vue'

type Size = 'sm' | 'md' | 'lg'
type Tone = 'default' | 'primary' | 'danger'

const props = withDefaults(
  defineProps<{
    size?: Size
    tone?: Tone
    label: string
    disabled?: boolean
  }>(),
  {
    size: 'md',
    tone: 'default',
    disabled: false,
  },
)

const sizeClasses: Record<Size, string> = {
  sm: 'h-8 w-8',
  md: 'h-10 w-10',
  lg: 'h-12 w-12',
}

const toneClasses: Record<Tone, string> = {
  default:
    'text-surface-600 hover:bg-surface-100 hover:text-surface-900 active:bg-surface-200 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-50',
  primary:
    'text-primary-600 hover:bg-primary-50 hover:text-primary-700 active:bg-primary-100 dark:text-primary-400 dark:hover:bg-primary-500/10',
  danger:
    'text-danger-600 hover:bg-danger-50 hover:text-danger-700 active:bg-danger-100 dark:text-danger-500 dark:hover:bg-danger-500/10',
}

const classes = computed(() => {
  const base =
    'inline-flex items-center justify-center rounded-full transition duration-200 ease-out-soft focus-visible:ring-2 focus-visible:ring-primary-500/60 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-50 dark:focus-visible:ring-offset-surface-950 disabled:cursor-not-allowed disabled:opacity-50'
  return [base, sizeClasses[props.size], toneClasses[props.tone]].join(' ')
})

const iconSize = computed(() => {
  if (props.size === 'sm') return 'h-4 w-4'
  if (props.size === 'lg') return 'h-6 w-6'
  return 'h-5 w-5'
})
</script>

<template>
  <button
    type="button"
    :class="classes"
    :aria-label="label"
    :title="label"
    :disabled="disabled"
  >
    <slot :iconSize="iconSize" />
  </button>
</template>

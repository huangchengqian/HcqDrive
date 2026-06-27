<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
    block?: boolean
    iconOnly?: boolean
    ariaLabel?: string
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
    iconOnly: false,
    ariaLabel: undefined,
  },
)

const variantClasses: Record<Variant, string> = {
  primary:
    'bg-primary-600 text-white shadow-sm hover:bg-primary-700 active:bg-primary-800 disabled:bg-primary-300 dark:disabled:bg-primary-800/40',
  secondary:
    'bg-surface-100 text-surface-900 hover:bg-surface-200 active:bg-surface-300 disabled:bg-surface-100/60 disabled:text-surface-400 dark:bg-surface-800 dark:text-surface-100 dark:hover:bg-surface-700 dark:active:bg-surface-800',
  ghost:
    'bg-transparent text-surface-700 hover:bg-surface-100 active:bg-surface-200 disabled:text-surface-400 dark:text-surface-300 dark:hover:bg-surface-800 dark:active:bg-surface-700',
  danger:
    'bg-danger-600 text-white shadow-sm hover:bg-danger-700 active:bg-danger-700 disabled:bg-danger-500/60',
}

const sizeClasses: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-12 px-5 text-base gap-2',
}

const iconSizeClasses: Record<Size, string> = {
  sm: 'h-3.5 w-3.5',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
}

const classes = computed(() => {
  const base =
    'inline-flex items-center justify-center rounded-md font-medium select-none whitespace-nowrap transition duration-200 ease-out-soft focus-visible:ring-2 focus-visible:ring-primary-500/60 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-50 dark:focus-visible:ring-offset-surface-950 disabled:cursor-not-allowed disabled:shadow-none'
  const variant = variantClasses[props.variant]
  const size = props.iconOnly ? `${sizeClasses[props.size].split(' ')[0]} w-auto aspect-square px-0` : sizeClasses[props.size]
  return [base, variant, size, props.block ? 'w-full' : ''].join(' ')
})

const loaderSize = computed(() => iconSizeClasses[props.size])
</script>

<template>
  <button
    :type="type"
    :class="classes"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    :aria-label="ariaLabel"
  >
    <Loader2 v-if="loading" :class="['animate-spin', loaderSize]" aria-hidden="true" />
    <slot v-else name="icon" />
    <span v-if="!iconOnly" class="flex items-center gap-2">
      <slot />
    </span>
  </button>
</template>

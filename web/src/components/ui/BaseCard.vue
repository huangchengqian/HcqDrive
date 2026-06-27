<script setup lang="ts">
import { computed } from 'vue'

type Padding = 'none' | 'sm' | 'md' | 'lg' | 'xl' | '2xl'

const props = withDefaults(
  defineProps<{
    padding?: Padding
    elevated?: boolean
    interactive?: boolean
    as?: 'div' | 'article' | 'section' | 'li'
  }>(),
  {
    padding: 'lg',
    elevated: false,
    interactive: false,
    as: 'div',
  },
)

const paddingClasses: Record<Padding, string> = {
  none: 'p-0',
  sm: 'p-3',
  md: 'p-4',
  lg: 'p-6',
  xl: 'p-8',
  '2xl': 'p-10',
}

const classes = computed(() => {
  const base =
    'rounded-lg border border-border-light bg-surface-0 text-surface-900 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100'
  const shadow = props.elevated
    ? 'shadow-floating'
    : 'shadow-card'
  const interactive = props.interactive
    ? 'transition duration-200 ease-out-soft hover:border-primary-300 hover:shadow-floating dark:hover:border-primary-700 cursor-pointer'
    : ''
  return [base, shadow, paddingClasses[props.padding], interactive].join(' ')
})
</script>

<template>
  <component :is="as" :class="classes">
    <slot />
  </component>
</template>

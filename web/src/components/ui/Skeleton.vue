<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** Width / height in rem units. */
    size?: number
    /** Shape variant. */
    shape?: 'rect' | 'circle' | 'line'
    /** Animation style. */
    variant?: 'shimmer' | 'pulse'
  }>(),
  { size: 1, shape: 'rect', variant: 'shimmer' },
)

const classes = computed(() => {
  const base =
    props.variant === 'pulse'
      ? 'animate-pulse bg-surface-200 dark:bg-surface-800'
      : 'skeleton-shimmer bg-surface-200 dark:bg-surface-800'
  const shape =
    props.shape === 'circle'
      ? 'rounded-full'
      : props.shape === 'line'
        ? 'h-2 w-full rounded-full'
        : 'rounded-md'
  return [base, shape]
})
</script>

<template>
  <span
    aria-hidden="true"
    :class="classes"
    :style="{
      width: shape === 'line' ? '100%' : `${size}rem`,
      height: shape === 'line' ? undefined : `${size}rem`,
    }"
  />
</template>

<style scoped>
.skeleton-shimmer {
  background-image: linear-gradient(
    90deg,
    transparent 0%,
    rgb(255 255 255 / 0.5) 50%,
    transparent 100%
  );
  background-size: 200% 100%;
  background-repeat: no-repeat;
  animation: skeleton-shimmer 1.6s linear infinite;
}
:global(.dark) .skeleton-shimmer {
  background-image: linear-gradient(
    90deg,
    transparent 0%,
    rgb(63 63 70 / 0.4) 50%,
    transparent 100%
  );
}
@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>

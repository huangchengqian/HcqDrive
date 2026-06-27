<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X, Info, CheckCircle2, AlertTriangle, XCircle } from 'lucide-vue-next'
import { useToastStore, type ToastTone } from '@/stores/toast'
import { storeToRefs } from 'pinia'

const toast = useToastStore()
const { toasts } = storeToRefs(toast)

const toneStyles: Record<ToastTone, { ring: string; icon: string; bar: string }> = {
  info: {
    ring: 'border-primary-200 dark:border-primary-500/30',
    icon: 'text-primary-500 dark:text-primary-400',
    bar: 'bg-primary-500',
  },
  success: {
    ring: 'border-success-500/40 dark:border-success-500/30',
    icon: 'text-success-600 dark:text-success-500',
    bar: 'bg-success-500',
  },
  warning: {
    ring: 'border-warning-500/40 dark:border-warning-500/30',
    icon: 'text-warning-600 dark:text-warning-500',
    bar: 'bg-warning-500',
  },
  error: {
    ring: 'border-danger-500/40 dark:border-danger-500/30',
    icon: 'text-danger-600 dark:text-danger-500',
    bar: 'bg-danger-500',
  },
}

const containerRef = ref<HTMLElement | null>(null)
const liveRegion = ref<HTMLDivElement | null>(null)

function getIcon(tone: ToastTone) {
  if (tone === 'success') return CheckCircle2
  if (tone === 'warning') return AlertTriangle
  if (tone === 'error') return XCircle
  return Info
}

watch(toasts, (list) => {
  if (!liveRegion.value) return
  const last = list[list.length - 1]
  if (last) liveRegion.value.textContent = last.message
})

onMounted(() => {
  /* live region handles announcements */
})
onBeforeUnmount(() => {
  /* nothing to tear down */
})
const visible = computed(() => toasts.value.slice(-4))
</script>

<template>
  <div
    ref="containerRef"
    aria-live="polite"
    aria-atomic="true"
    class="pointer-events-none fixed inset-x-0 top-3 z-[60] flex flex-col items-center gap-2 px-3 sm:top-4 sm:items-end sm:pr-4"
  >
    <transition-group
      tag="div"
      class="flex w-full flex-col items-center gap-2 sm:items-end"
      enter-active-class="transition duration-200 ease-out-soft"
      enter-from-class="opacity-0 -translate-y-2 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition duration-150 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0 translate-x-4"
    >
      <div
        v-for="t in visible"
        :key="t.id"
        :class="[
          'pointer-events-auto flex w-full max-w-sm items-start gap-3 overflow-hidden rounded-lg border bg-surface-0/95 px-3 py-2.5 shadow-floating backdrop-blur-md dark:bg-surface-900/95',
          toneStyles[t.tone].ring,
        ]"
        role="alert"
      >
        <span
          aria-hidden="true"
          :class="['mt-0.5 shrink-0', toneStyles[t.tone].icon]"
        >
          <component :is="getIcon(t.tone)" :size="16" :stroke-width="2" />
        </span>
        <div class="min-w-0 flex-1">
          <p class="text-sm leading-snug text-surface-700 dark:text-surface-200">
            {{ t.message }}
          </p>
          <button
            v-if="t.actionLabel && t.action"
            type="button"
            class="mt-1 text-xs font-medium text-primary-600 hover:underline dark:text-primary-400"
            @click="
              () => {
                t.action?.()
                toast.dismiss(t.id)
              }
            "
          >
            {{ t.actionLabel }}
          </button>
        </div>
        <button
          type="button"
          class="-mr-1 -mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded text-surface-400 hover:bg-surface-100 hover:text-surface-700 dark:hover:bg-surface-800 dark:hover:text-surface-200"
          aria-label="关闭"
          @click="toast.dismiss(t.id)"
        >
          <X :size="14" />
        </button>
        <span
          aria-hidden="true"
          :class="['absolute bottom-0 left-0 h-0.5 w-full origin-left', toneStyles[t.tone].bar, t.duration > 0 ? 'animate-toast-progress' : '']"
          :style="{
            animationDuration: `${t.duration}ms`,
          }"
        />
      </div>
    </transition-group>
    <div ref="liveRegion" class="sr-only" />
  </div>
</template>

<style scoped>
.animate-toast-progress {
  animation-name: toast-progress;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}
@keyframes toast-progress {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}
</style>

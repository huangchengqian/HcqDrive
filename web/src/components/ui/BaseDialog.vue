<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import IconButton from './IconButton.vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
    closeOnBackdrop?: boolean
    closeOnEsc?: boolean
  }>(),
  {
    title: undefined,
    description: undefined,
    size: 'md',
    closeOnBackdrop: true,
    closeOnEsc: true,
  },
)

const emit = defineEmits<{ (e: 'close'): void }>()

const dialogRef = ref<HTMLDivElement | null>(null)
const previousActive = ref<HTMLElement | null>(null)

const maxWidth = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'max-w-sm'
    case 'lg':
      return 'max-w-2xl'
    case 'xl':
      return 'max-w-4xl'
    default:
      return 'max-w-md'
  }
})

function focusFirst(): void {
  if (!dialogRef.value) return
  const focusables = dialogRef.value.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
  )
  const first = focusables[0]
  if (first) first.focus()
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open) return
  if (event.key === 'Escape' && props.closeOnEsc) {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key === 'Tab' && dialogRef.value) {
    const focusables = dialogRef.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )
    if (focusables.length === 0) return
    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    if (!first || !last) return
    const active = document.activeElement
    if (event.shiftKey && active === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousActive.value = (document.activeElement as HTMLElement | null) ?? null
      document.body.style.overflow = 'hidden'
      await nextTick()
      focusFirst()
    } else {
      document.body.style.overflow = ''
      previousActive.value?.focus?.()
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeydown)
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
  })
}

function onBackdrop(): void {
  if (props.closeOnBackdrop) emit('close')
}
</script>

<template>
  <teleport to="body">
    <transition
      enter-active-class="transition duration-200 ease-out-soft"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-end justify-center bg-surface-950/40 px-3 py-4 backdrop-blur-sm sm:items-center sm:p-6"
        role="presentation"
        @mousedown.self="onBackdrop"
      >
        <transition
          appear
          enter-active-class="transition duration-250 ease-out-soft"
          enter-from-class="opacity-0 translate-y-4 scale-[0.98] sm:translate-y-0 sm:scale-95"
          enter-to-class="opacity-100 translate-y-0 scale-100"
          leave-active-class="transition duration-150 ease-out"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="open"
            ref="dialogRef"
            :class="[
              'relative w-full overflow-hidden rounded-xl border border-border-light bg-surface-0 shadow-floating dark:border-border-dark dark:bg-surface-900',
              maxWidth,
            ]"
            role="dialog"
            aria-modal="true"
            :aria-label="title"
            @mousedown.stop
          >
            <div
              v-if="title || $slots.header"
              class="flex items-start gap-3 border-b border-border-light px-5 py-4 dark:border-border-dark"
            >
              <div class="min-w-0 flex-1">
                <h2 v-if="title" class="text-base font-semibold text-surface-900 dark:text-surface-50">
                  {{ title }}
                </h2>
                <p
                  v-if="description"
                  class="mt-0.5 text-xs text-surface-500 dark:text-surface-400"
                >
                  {{ description }}
                </p>
              </div>
              <slot name="header" />
              <IconButton size="sm" label="关闭" @click="emit('close')">
                <template #default="{ iconSize }">
                  <X :class="iconSize" />
                </template>
              </IconButton>
            </div>
            <div class="max-h-[calc(100vh-12rem)] overflow-y-auto px-5 py-4 sm:max-h-[70vh]">
              <slot />
            </div>
            <div
              v-if="$slots.footer"
              class="flex flex-col-reverse items-stretch gap-2 border-t border-border-light bg-surface-50/60 px-5 py-3 sm:flex-row sm:items-center sm:justify-end sm:gap-2 dark:border-border-dark dark:bg-surface-950/40"
            >
              <slot name="footer" />
            </div>
          </div>
        </transition>
      </div>
    </transition>
  </teleport>
</template>

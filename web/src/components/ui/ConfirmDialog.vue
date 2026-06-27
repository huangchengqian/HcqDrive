<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle } from 'lucide-vue-next'
import BaseButton from './BaseButton.vue'
import IconButton from './IconButton.vue'
import { X } from 'lucide-vue-next'

const props = defineProps<{
  open: boolean
  title?: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  loading?: boolean
  tone?: 'danger' | 'warning'
}>()

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()

const toneClass = computed(() =>
  props.tone === 'warning'
    ? 'bg-warning-50 text-warning-600 dark:bg-warning-500/10 dark:text-warning-500'
    : 'bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-500',
)
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
        @mousedown.self="emit('cancel')"
      >
        <div
          class="relative w-full max-w-sm overflow-hidden rounded-xl border border-border-light bg-surface-0 p-5 shadow-floating dark:border-border-dark dark:bg-surface-900"
          role="alertdialog"
          aria-modal="true"
          :aria-label="title ?? '确认操作'"
        >
          <IconButton
            size="sm"
            label="关闭"
            class="absolute right-2 top-2"
            @click="emit('cancel')"
          >
            <template #default="{ iconSize }">
              <X :class="iconSize" />
            </template>
          </IconButton>
          <div :class="['mb-3 flex h-10 w-10 items-center justify-center rounded-full', toneClass]">
            <AlertTriangle :size="20" :stroke-width="2" />
          </div>
          <h2 class="text-base font-semibold text-surface-900 dark:text-surface-50">
            {{ title ?? '确认操作' }}
          </h2>
          <p class="mt-1.5 text-sm text-surface-500 dark:text-surface-400">
            {{ message }}
          </p>
          <div class="mt-5 flex flex-col-reverse items-stretch gap-2 sm:flex-row sm:items-center sm:justify-end">
            <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('cancel')">
              {{ cancelLabel ?? '取消' }}
            </BaseButton>
            <BaseButton
              :variant="tone === 'warning' ? 'primary' : 'danger'"
              size="md"
              :loading="loading"
              @click="emit('confirm')"
            >
              {{ confirmLabel ?? '确认' }}
            </BaseButton>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

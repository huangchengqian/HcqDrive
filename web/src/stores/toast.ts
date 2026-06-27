import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastTone = 'info' | 'success' | 'warning' | 'error'

export interface ToastInput {
  tone?: ToastTone
  message: string
  /** Auto-dismiss in milliseconds. `0` to keep open. */
  duration?: number
  /** Optional CTA label, e.g. "重试". */
  actionLabel?: string
  action?: () => void
}

export interface ToastEntry extends Required<Pick<ToastInput, 'tone' | 'message' | 'duration'>> {
  id: string
  actionLabel?: string
  action?: () => void
}

let counter = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastEntry[]>([])

  function push(input: ToastInput): string {
    const id = `t-${Date.now()}-${counter++}`
    const entry: ToastEntry = {
      id,
      tone: input.tone ?? 'info',
      message: input.message,
      duration: input.duration ?? defaultDuration(input.tone ?? 'info'),
    }
    if (input.actionLabel !== undefined) entry.actionLabel = input.actionLabel
    if (input.action !== undefined) entry.action = input.action
    toasts.value = [...toasts.value, entry]
    if (entry.duration > 0) {
      window.setTimeout(() => dismiss(id), entry.duration)
    }
    return id
  }

  function dismiss(id: string): void {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function clear(): void {
    toasts.value = []
  }

  return { toasts, push, dismiss, clear }
})

function defaultDuration(tone: ToastTone): number {
  switch (tone) {
    case 'error':
      return 6000
    case 'success':
      return 2400
    case 'warning':
      return 4000
    default:
      return 3000
  }
}

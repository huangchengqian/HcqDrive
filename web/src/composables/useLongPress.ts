import { computed, onBeforeUnmount, ref, type ComputedRef } from 'vue'

export interface LongPressOptions {
  /** Press duration in ms. */
  duration?: number
  /** Optional movement tolerance in px (cancels the press if exceeded). */
  moveTolerance?: number
}

export function useLongPress(handler: (event: PointerEvent) => void, options: LongPressOptions = {}) {
  const duration = options.duration ?? 500
  const moveTolerance = options.moveTolerance ?? 12
  const isPressed = ref(false)
  let timer: number | null = null
  let startX = 0
  let startY = 0

  function clear(): void {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  function onDown(event: PointerEvent): void {
    isPressed.value = true
    startX = event.clientX
    startY = event.clientY
    clear()
    timer = window.setTimeout(() => {
      timer = null
      if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
        navigator.vibrate(20)
      }
      handler(event)
    }, duration)
  }

  function onMove(event: PointerEvent): void {
    if (!isPressed.value) return
    const dx = event.clientX - startX
    const dy = event.clientY - startY
    if (Math.hypot(dx, dy) > moveTolerance) {
      cancel()
    }
  }

  function cancel(): void {
    isPressed.value = false
    clear()
  }

  function onUp(): void {
    if (timer !== null) {
      clear()
    } else {
      isPressed.value = false
    }
  }

  onBeforeUnmount(() => {
    clear()
  })

  return {
    isPressed: computed(() => isPressed.value) as ComputedRef<boolean>,
    pressHandlers: {
      onPointerdown: onDown,
      onPointermove: onMove,
      onPointerup: onUp,
      onPointercancel: cancel,
      onPointerleave: cancel,
    },
  } as const
}

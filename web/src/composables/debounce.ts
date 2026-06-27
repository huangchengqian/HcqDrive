/**
 * Small helpers to debounce / throttle.
 */

export function debounce<TArgs extends unknown[]>(fn: (...args: TArgs) => void, wait: number): {
  (...args: TArgs): void
  cancel(): void
  flush(): void
} {
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastArgs: TArgs | null = null

  function invoke(): void {
    if (lastArgs) {
      fn(...lastArgs)
      lastArgs = null
    }
    timer = null
  }

  function debounced(...args: TArgs): void {
    lastArgs = args
    if (timer !== null) clearTimeout(timer)
    timer = setTimeout(invoke, wait)
  }

  debounced.cancel = () => {
    if (timer !== null) clearTimeout(timer)
    timer = null
    lastArgs = null
  }
  debounced.flush = () => {
    if (timer !== null) {
      clearTimeout(timer)
      invoke()
    }
  }

  return debounced
}

export function throttle<TArgs extends unknown[]>(fn: (...args: TArgs) => void, wait: number): (...args: TArgs) => void {
  let last = 0
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastArgs: TArgs | null = null

  return function throttled(...args: TArgs): void {
    const now = Date.now()
    const remaining = wait - (now - last)
    lastArgs = args
    if (remaining <= 0) {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
      last = now
      fn(...args)
      lastArgs = null
    } else if (timer === null) {
      timer = setTimeout(() => {
        last = Date.now()
        timer = null
        if (lastArgs) {
          fn(...lastArgs)
          lastArgs = null
        }
      }, remaining)
    }
  }
}

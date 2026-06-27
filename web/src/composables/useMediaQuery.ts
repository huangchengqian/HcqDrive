import { onBeforeUnmount, onMounted } from 'vue'

const mql =
  typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null

export function useReducedMotion(): { prefersReducedMotion: () => boolean } {
  function isReduced(): boolean {
    if (typeof window === 'undefined') return false
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  }
  return { prefersReducedMotion: isReduced }
}

export function useSystemColorScheme(onChange: (isDark: boolean) => void): void {
  if (typeof window === 'undefined' || !mql) return
  const handler = (e: MediaQueryListEvent): void => onChange(e.matches)
  onMounted(() => {
    mql.addEventListener('change', handler)
  })
  onBeforeUnmount(() => {
    mql.removeEventListener('change', handler)
  })
}

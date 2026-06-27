import { ref, watch, type Ref } from 'vue'

const STORAGE_KEY = 'hcqdrive:theme'

export type ThemeMode = 'light' | 'dark' | 'system'

const isBrowser = typeof window !== 'undefined'

function readStoredMode(): ThemeMode {
  if (!isBrowser) return 'system'
  const v = localStorage.getItem(STORAGE_KEY)
  if (v === 'light' || v === 'dark' || v === 'system') return v
  return 'system'
}

function prefersDark(): boolean {
  if (!isBrowser) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyMode(mode: ThemeMode): void {
  if (!isBrowser) return
  const root = document.documentElement
  const dark = mode === 'dark' || (mode === 'system' && prefersDark())
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
}

let mediaQuery: MediaQueryList | null = null
let systemListener: ((e: MediaQueryListEvent) => void) | null = null
const mode: Ref<ThemeMode> = ref(readStoredMode())
let mediaBound = false

watch(
  mode,
  (next) => {
    if (isBrowser) localStorage.setItem(STORAGE_KEY, next)
    applyMode(next)
  },
  { immediate: true },
)

if (isBrowser && !mediaBound) {
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  systemListener = () => {
    if (mode.value === 'system') applyMode('system')
  }
  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', systemListener)
  } else {
    mediaQuery.addListener(systemListener)
  }
  mediaBound = true
}

export function useTheme() {
  function setMode(next: ThemeMode): void {
    mode.value = next
  }
  function cycleMode(): void {
    mode.value = mode.value === 'light' ? 'dark' : mode.value === 'dark' ? 'system' : 'light'
  }
  function isDark(): boolean {
    if (mode.value === 'system') return prefersDark()
    return mode.value === 'dark'
  }
  return { mode, setMode, cycleMode, isDark }
}

export function teardownTheme(): void {
  if (isBrowser && mediaQuery && systemListener) {
    if (mediaQuery.removeEventListener) {
      mediaQuery.removeEventListener('change', systemListener)
    } else {
      mediaQuery.removeListener(systemListener)
    }
  }
  mediaBound = false
  mediaQuery = null
  systemListener = null
}

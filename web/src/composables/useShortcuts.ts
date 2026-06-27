import { onBeforeUnmount, onMounted } from 'vue'

export type ShortcutHandler = (event: KeyboardEvent) => void

export interface ShortcutBinding {
  key: string
  meta?: boolean
  shift?: boolean
  alt?: boolean
  handler: ShortcutHandler
  /** When false the binding is disabled (e.g. inside an input). */
  enabled?: () => boolean
}

const isMac =
  typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent)

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  return false
}

function normalizeKey(key: string): string {
  if (key === ' ') return 'space'
  if (key === 'Escape') return 'escape'
  if (key === 'ArrowUp') return 'arrowup'
  if (key === 'ArrowDown') return 'arrowdown'
  if (key === 'ArrowLeft') return 'arrowleft'
  if (key === 'ArrowRight') return 'arrowright'
  return key.toLowerCase()
}

export function useShortcuts(bindings: () => ShortcutBinding[]): void {
  function onKeydown(event: KeyboardEvent): void {
    if (isTypingTarget(event.target)) return
    const key = normalizeKey(event.key)
    for (const binding of bindings()) {
      if (normalizeKey(binding.key) !== key) continue
      if (binding.meta) {
        const want = isMac ? event.metaKey : event.ctrlKey
        if (!want) continue
      }
      if (binding.shift !== undefined && binding.shift !== event.shiftKey) continue
      if (binding.alt !== undefined && binding.alt !== event.altKey) continue
      if (binding.enabled && !binding.enabled()) continue
      event.preventDefault()
      binding.handler(event)
      return
    }
  }
  onMounted(() => {
    window.addEventListener('keydown', onKeydown)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
  })
}

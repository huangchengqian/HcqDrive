import { onBeforeUnmount } from 'vue'

export interface ContextMenuState {
  open: boolean
  x: number
  y: number
  payload: unknown
}

const OFFSET = 4

export function openContextMenu(
  state: { x: number; y: number; open: boolean; payload: unknown },
  event: { clientX: number; clientY: number; preventDefault?: () => void },
  payload: unknown,
  bounds: { width: number; height: number } = { width: 240, height: 320 },
): void {
  event.preventDefault?.()
  const vw = typeof window !== 'undefined' ? window.innerWidth : bounds.width
  const vh = typeof window !== 'undefined' ? window.innerHeight : bounds.height
  const x = Math.min(event.clientX, vw - bounds.width - OFFSET)
  const y = Math.min(event.clientY, vh - bounds.height - OFFSET)
  state.x = Math.max(OFFSET, x)
  state.y = Math.max(OFFSET, y)
  state.payload = payload
  state.open = true
}

export function closeContextMenu(state: { open: boolean; payload: unknown }): void {
  state.open = false
  state.payload = null
}

export function useContextMenuDismiss(
  state: { open: boolean },
  onDismiss: () => void,
): void {
  function onPointer(event: MouseEvent): void {
    if (!state.open) return
    const target = event.target as HTMLElement | null
    if (target?.closest('[data-context-menu-root]')) return
    onDismiss()
  }
  function onKey(event: KeyboardEvent): void {
    if (state.open && event.key === 'Escape') onDismiss()
  }
  function onScroll(): void {
    if (state.open) onDismiss()
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('mousedown', onPointer, true)
    window.addEventListener('contextmenu', onPointer, true)
    window.addEventListener('keydown', onKey)
    window.addEventListener('scroll', onScroll, true)
  }
  onBeforeUnmount(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('mousedown', onPointer, true)
      window.removeEventListener('contextmenu', onPointer, true)
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('scroll', onScroll, true)
    }
  })
}

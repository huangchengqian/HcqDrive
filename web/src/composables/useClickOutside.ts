import { onBeforeUnmount, onMounted, type Ref } from 'vue'

export function useClickOutside(target: Ref<HTMLElement | null>, handler: () => void): void {
  function onPointer(event: MouseEvent | TouchEvent): void {
    const t = event.target
    if (!(t instanceof Node)) return
    if (target.value && !target.value.contains(t)) {
      handler()
    }
  }
  function onKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') handler()
  }
  onMounted(() => {
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('touchstart', onPointer, { passive: true })
    window.addEventListener('keydown', onKey)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('mousedown', onPointer)
    window.removeEventListener('touchstart', onPointer)
    window.removeEventListener('keydown', onKey)
  })
}

import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useElementSize(target: Ref<HTMLElement | null>): { width: Ref<number>; height: Ref<number> } {
  const width = ref(0)
  const height = ref(0)
  let ro: ResizeObserver | null = null

  onMounted(() => {
    if (!target.value || typeof ResizeObserver === 'undefined') return
    ro = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      const rect = entry.contentRect
      width.value = rect.width
      height.value = rect.height
    })
    ro.observe(target.value)
  })

  onBeforeUnmount(() => {
    ro?.disconnect()
  })

  return { width, height }
}

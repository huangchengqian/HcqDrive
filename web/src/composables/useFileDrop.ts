import { onBeforeUnmount, onMounted, type Ref } from 'vue'

export interface DragHandlers {
  onDragEnter?: (event: DragEvent) => void
  onDragOver?: (event: DragEvent) => void
  onDragLeave?: (event: DragEvent) => void
  onDrop?: (event: DragEvent) => void
}

export function useFileDrop(
  onFiles: (files: File[]) => void,
  target: Ref<HTMLElement | null>,
  options: { disabled?: Ref<boolean> } = {},
) {
  let depth = 0

  function preventDefault(event: DragEvent): void {
    event.preventDefault()
    event.stopPropagation()
  }

  function hasFiles(event: DragEvent): boolean {
    if (!event.dataTransfer) return false
    return Array.from(event.dataTransfer.types).includes('Files')
  }

  function onEnter(event: DragEvent): void {
    if (!hasFiles(event)) return
    if (options.disabled?.value) return
    preventDefault(event)
    depth += 1
    if (depth === 1) target.value?.classList.add('is-drag-over')
  }

  function onOver(event: DragEvent): void {
    if (!hasFiles(event)) return
    preventDefault(event)
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
  }

  function onLeave(event: DragEvent): void {
    if (!hasFiles(event)) return
    preventDefault(event)
    depth = Math.max(0, depth - 1)
    if (depth === 0) target.value?.classList.remove('is-drag-over')
  }

  function onDrop(event: DragEvent): void {
    preventDefault(event)
    depth = 0
    target.value?.classList.remove('is-drag-over')
    if (options.disabled?.value) return
    const files = event.dataTransfer?.files
    if (!files || files.length === 0) return
    onFiles(Array.from(files))
  }

  onMounted(() => {
    window.addEventListener('dragenter', onEnter)
    window.addEventListener('dragover', onOver)
    window.addEventListener('dragleave', onLeave)
    window.addEventListener('drop', onDrop)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('dragenter', onEnter)
    window.removeEventListener('dragover', onOver)
    window.removeEventListener('dragleave', onLeave)
    window.removeEventListener('drop', onDrop)
  })
}

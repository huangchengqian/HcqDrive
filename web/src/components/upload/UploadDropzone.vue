<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { UploadCloud } from 'lucide-vue-next'
import { useUploadStore } from '@/stores/upload'

const upload = useUploadStore()
const overlay = ref<HTMLElement | null>(null)
const visible = ref(false)
let depth = 0

function hasFiles(event: DragEvent): boolean {
  if (!event.dataTransfer) return false
  return Array.from(event.dataTransfer.types).includes('Files')
}

function prevent(event: DragEvent): void {
  event.preventDefault()
  event.stopPropagation()
}

function onEnter(event: DragEvent): void {
  if (!hasFiles(event)) return
  prevent(event)
  depth += 1
  visible.value = true
}
function onOver(event: DragEvent): void {
  if (!hasFiles(event)) return
  prevent(event)
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}
function onLeave(event: DragEvent): void {
  if (!hasFiles(event)) return
  prevent(event)
  depth = Math.max(0, depth - 1)
  if (depth === 0) visible.value = false
}
function onDrop(event: DragEvent): void {
  prevent(event)
  depth = 0
  visible.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  upload.addFiles(files)
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

const transitionClass = computed(() => 'transition duration-200 ease-out-soft')
</script>

<template>
  <teleport to="body">
    <transition :enter-active-class="transitionClass" enter-from-class="opacity-0" enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-out" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div
        v-if="visible"
        ref="overlay"
        class="pointer-events-none fixed inset-0 z-[45] flex items-center justify-center bg-primary-500/10 backdrop-blur-sm"
        aria-hidden="true"
      >
        <div class="pointer-events-none flex flex-col items-center gap-3 rounded-2xl border-2 border-dashed border-primary-500 bg-surface-0/90 px-10 py-12 shadow-floating dark:bg-surface-900/90">
          <div class="flex h-14 w-14 items-center justify-center rounded-full bg-primary-100 text-primary-600 dark:bg-primary-500/20 dark:text-primary-300 animate-breathe">
            <UploadCloud :size="28" :stroke-width="1.6" />
          </div>
          <p class="text-base font-medium text-surface-900 dark:text-surface-50">
            松手以上传到当前目录
          </p>
          <p class="text-xs text-surface-500 dark:text-surface-400">
            支持多文件,大于 50MB 自动分片
          </p>
        </div>
      </div>
    </transition>
  </teleport>
</template>

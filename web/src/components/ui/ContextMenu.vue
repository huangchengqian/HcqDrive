<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import MenuList, { type MenuEntry } from './MenuList.vue'
import { closeContextMenu, useContextMenuDismiss } from '@/composables/useContextMenu'

const props = defineProps<{
  open: boolean
  x: number
  y: number
  items: MenuEntry[]
}>()

const emit = defineEmits<{ (e: 'select', key: string): void; (e: 'close'): void }>()

const root = ref<HTMLDivElement | null>(null)

function onSelect(key: string): void {
  emit('select', key)
  close()
}

function close(): void {
  closeContextMenu({ open: props.open, payload: null })
  emit('close')
}

useContextMenuDismiss({ open: props.open }, close)

onMounted(() => {
  /* listener is registered inside composable */
})

watch(
  () => [props.x, props.y, props.open],
  ([x, y, open]) => {
    if (!open || typeof window === 'undefined') return
    if (!root.value) return
    const el = root.value
    const rect = el.getBoundingClientRect()
    const vw = window.innerWidth
    const vh = window.innerHeight
    let nx = Number(x)
    let ny = Number(y)
    if (nx + rect.width > vw - 4) nx = Math.max(4, vw - rect.width - 4)
    if (ny + rect.height > vh - 4) ny = Math.max(4, vh - rect.height - 4)
    el.style.left = `${nx}px`
    el.style.top = `${ny}px`
  },
  { immediate: true },
)
</script>

<template>
  <teleport to="body">
    <transition
      enter-active-class="transition duration-100 ease-out-soft"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-75 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="open"
        ref="root"
        class="fixed z-[55] origin-top-left"
        style="left: 0; top: 0"
        @contextmenu.prevent
      >
        <MenuList :items="items" @select="onSelect" />
      </div>
    </transition>
  </teleport>
</template>

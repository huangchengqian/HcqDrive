<script setup lang="ts">
import { computed } from 'vue'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import { useClickOutside } from '@/composables/useClickOutside'
import { ref } from 'vue'
import IconButton from './IconButton.vue'

const { mode, setMode } = useTheme()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

useClickOutside(root, () => {
  open.value = false
})

const order: ThemeMode[] = ['light', 'dark', 'system']

const currentIcon = computed(() => {
  if (mode.value === 'dark') return Moon
  if (mode.value === 'light') return Sun
  return Monitor
})

const currentLabel = computed(() => {
  if (mode.value === 'dark') return '深色'
  if (mode.value === 'light') return '浅色'
  return '跟随系统'
})

function pick(next: ThemeMode): void {
  setMode(next)
  open.value = false
}
</script>

<template>
  <div ref="root" class="relative">
    <IconButton size="sm" :label="`主题:${currentLabel}`" @click="open = !open">
      <template #default="{ iconSize }">
        <component :is="currentIcon" :class="iconSize" />
      </template>
    </IconButton>
    <transition
      enter-active-class="transition duration-100 ease-out-soft"
      enter-from-class="opacity-0 -translate-y-1 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition duration-75 ease-out"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0 -translate-y-1"
    >
      <div
        v-if="open"
        class="absolute right-0 top-10 z-40 min-w-[140px] overflow-hidden rounded-lg border border-border-light bg-surface-0/95 p-1 shadow-floating backdrop-blur-md dark:border-border-dark dark:bg-surface-900/95"
        role="menu"
      >
        <button
          v-for="m in order"
          :key="m"
          type="button"
          :class="[
            'flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-sm transition',
            mode === m
              ? 'bg-primary-50 text-primary-700 dark:bg-primary-500/10 dark:text-primary-300'
              : 'text-surface-700 hover:bg-surface-100 dark:text-surface-200 dark:hover:bg-surface-800',
          ]"
          @click="pick(m)"
        >
          <Sun v-if="m === 'light'" :size="14" />
          <Moon v-else-if="m === 'dark'" :size="14" />
          <Monitor v-else :size="14" />
          <span class="flex-1">{{ m === 'light' ? '浅色' : m === 'dark' ? '深色' : '跟随系统' }}</span>
        </button>
      </div>
    </transition>
  </div>
</template>

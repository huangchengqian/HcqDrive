<script setup lang="ts">
import { Cloud, RefreshCw, LogOut } from 'lucide-vue-next'
import IconButton from '@/components/ui/IconButton.vue'
import { useAuthStore } from '@/stores/auth'
import { useFilesStore } from '@/stores/files'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useTheme } from '@/composables/useTheme'
import { computed } from 'vue'
import { Sun, Moon, Monitor } from 'lucide-vue-next'

const auth = useAuthStore()
const files = useFilesStore()
const router = useRouter()
const toast = useToastStore()
const { mode, cycleMode } = useTheme()

async function refresh(): Promise<void> {
  await files.refresh()
  toast.push({ tone: 'success', message: '已刷新', duration: 1500 })
}

function signOut(): void {
  auth.signOut()
  void router.replace({ name: 'pair' })
}

const themeIcon = computed(() => {
  if (mode.value === 'dark') return Moon
  if (mode.value === 'light') return Sun
  return Monitor
})
const themeLabel = computed(() => {
  if (mode.value === 'dark') return '切换到浅色'
  if (mode.value === 'light') return '切换到跟随系统'
  return '切换到深色'
})
</script>

<template>
  <header
    class="sticky top-0 z-30 border-b border-border-light bg-surface-0/80 backdrop-blur-md dark:border-border-dark dark:bg-surface-900/80"
  >
    <div class="mx-auto flex h-15 max-w-7xl items-center gap-3 px-3 sm:px-6">
      <RouterLink
        :to="{ name: 'home' }"
        class="flex items-center gap-2.5 rounded-md py-1 pr-2 transition hover:opacity-80"
        aria-label="返回主页"
        @click="() => files.reset()"
      >
        <span
          class="flex h-9 w-9 items-center justify-center rounded-md bg-gradient-to-br from-primary-500 to-primary-700 text-white shadow-sm"
        >
          <Cloud :size="18" :stroke-width="2.2" />
        </span>
        <span class="hidden text-sm font-semibold tracking-tight text-surface-900 sm:inline dark:text-surface-50">
          HcqDrive
        </span>
      </RouterLink>

      <div class="ml-auto flex items-center gap-1">
        <span
          v-if="auth.deviceName"
          class="hidden text-xs text-surface-500 sm:inline dark:text-surface-400"
        >
          {{ auth.deviceName }}
        </span>
        <IconButton
          size="sm"
          :label="files.isLoading ? '刷新中' : '刷新'"
          :disabled="files.isLoading"
          @click="refresh"
        >
          <template #default="{ iconSize }">
            <RefreshCw :class="[iconSize, files.isLoading ? 'animate-spin' : '']" />
          </template>
        </IconButton>
        <IconButton size="sm" :label="themeLabel" @click="cycleMode()">
          <template #default="{ iconSize }">
            <component :is="themeIcon" :class="iconSize" />
          </template>
        </IconButton>
        <IconButton
          size="sm"
          tone="danger"
          label="退出登录"
          @click="signOut"
        >
          <template #default="{ iconSize }">
            <LogOut :class="iconSize" />
          </template>
        </IconButton>
      </div>
    </div>
  </header>
</template>

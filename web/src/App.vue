<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ToastStack from '@/components/ui/ToastStack.vue'
import OfflineBanner from '@/components/ui/OfflineBanner.vue'
import ErrorBoundary from '@/components/ui/ErrorBoundary.vue'
import { useNetworkStore } from '@/stores/network'

const network = useNetworkStore()

onMounted(() => {
  network.init()
})
</script>

<template>
  <ErrorBoundary>
    <div
      class="min-h-full bg-surface-50 text-surface-900 dark:bg-surface-950 dark:text-surface-100"
    >
      <RouterView v-slot="{ Component, route }">
        <transition
          enter-active-class="transition duration-250 ease-out-soft"
          enter-from-class="opacity-0 translate-y-1"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-150 ease-out"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
          mode="out-in"
        >
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </RouterView>
      <ToastStack />
      <OfflineBanner />
    </div>
  </ErrorBoundary>
</template>

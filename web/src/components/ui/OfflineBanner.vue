<script setup lang="ts">
import { computed } from 'vue'
import { WifiOff } from 'lucide-vue-next'
import { useNetworkStore } from '@/stores/network'
import { storeToRefs } from 'pinia'

const network = useNetworkStore()
const { state } = storeToRefs(network)
const visible = computed(() => state.value === 'offline')
</script>

<template>
  <transition
    enter-active-class="transition duration-200 ease-out-soft"
    enter-from-class="-translate-y-full opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transition duration-150 ease-out"
    leave-from-class="translate-y-0"
    leave-to-class="-translate-y-full opacity-0"
  >
    <div
      v-if="visible"
      class="fixed inset-x-0 top-0 z-40 flex items-center justify-center gap-2 border-b border-warning-500/30 bg-warning-500/95 px-3 py-1.5 text-xs font-medium text-white backdrop-blur-md safe-top"
      role="status"
    >
      <WifiOff :size="13" :stroke-width="2" />
      <span>网络已断开,部分功能暂不可用</span>
    </div>
  </transition>
</template>

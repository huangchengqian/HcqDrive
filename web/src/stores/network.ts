import { defineStore } from 'pinia'
import { ref } from 'vue'

export type NetworkState = 'online' | 'offline' | 'unknown'

export const useNetworkStore = defineStore('network', () => {
  const state = ref<NetworkState>(
    typeof navigator === 'undefined' ? 'unknown' : navigator.onLine ? 'online' : 'offline',
  )

  function setOnline(): void {
    state.value = 'online'
  }
  function setOffline(): void {
    state.value = 'offline'
  }

  function init(): void {
    if (typeof window === 'undefined') return
    window.addEventListener('online', setOnline)
    window.addEventListener('offline', setOffline)
  }

  function destroy(): void {
    if (typeof window === 'undefined') return
    window.removeEventListener('online', setOnline)
    window.removeEventListener('offline', setOffline)
  }

  return { state, init, destroy }
})

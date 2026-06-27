import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { ApiClientError, clearStoredDevice, clearToken, getStoredDevice, getToken, setStoredDevice, setToken } from '@/api/client'
import { discoverBase } from '@/api/discover'
import type { DiscoverResponse, StatusResponse } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const deviceName = ref<string | null>(null)
  const status = ref<StatusResponse | null>(null)
  const isPairing = ref(false)
  const pairError = ref<string | null>(null)
  const isHydrated = ref(false)
  const statusLoading = ref(false)
  const discovery = ref<DiscoverResponse | null>(null)
  const discoveryLoading = ref(false)

  const isAuthenticated = computed(() => token.value !== null)

  function hydrate(): void {
    if (isHydrated.value) return
    token.value = getToken()
    deviceName.value = getStoredDevice()
    isHydrated.value = true
  }

  /**
   * Probe candidate base URLs (`hcqdrive.local`, env-supplied host, loopback)
   * and pin the API client to the first one whose `/api/discover` succeeds.
   * Safe to call repeatedly; cached hits return instantly.
   */
  async function discover(signal?: AbortSignal): Promise<DiscoverResponse | null> {
    discoveryLoading.value = true
    try {
      const found = await discoverBase(signal)
      discovery.value = found?.info ?? null
      return discovery.value
    } finally {
      discoveryLoading.value = false
    }
  }

  async function fetchStatus(signal?: AbortSignal): Promise<StatusResponse | null> {
    statusLoading.value = true
    try {
      const result = await authApi.status(signal)
      status.value = result
      return result
    } catch (err) {
      if (err instanceof ApiClientError && err.isAuth()) {
        clearToken()
        clearStoredDevice()
        token.value = null
        deviceName.value = null
      }
      return null
    } finally {
      statusLoading.value = false
    }
  }

  async function pair(code: string): Promise<boolean> {
    const clean = code.replace(/\D/g, '').slice(0, 6)
    if (clean.length !== 6) {
      pairError.value = '请输入 6 位配对码'
      return false
    }
    isPairing.value = true
    pairError.value = null
    try {
      const result = await authApi.pair({
        code: clean,
        deviceName: clientDeviceLabel(),
      })
      setToken(result.token)
      setStoredDevice(result.deviceName)
      token.value = result.token
      deviceName.value = result.deviceName
      return true
    } catch (err) {
      pairError.value = errorMessage(err, '配对失败,请重试')
      return false
    } finally {
      isPairing.value = false
    }
  }

  function signOut(): void {
    clearToken()
    clearStoredDevice()
    token.value = null
    deviceName.value = null
    status.value = null
  }

  function clearError(): void {
    pairError.value = null
  }

  return {
    token,
    deviceName,
    status,
    isPairing,
    pairError,
    isAuthenticated,
    statusLoading,
    discovery,
    discoveryLoading,
    hydrate,
    discover,
    fetchStatus,
    pair,
    signOut,
    clearError,
  }
})

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiClientError) {
    if (err.status === 401 || err.status === 403) return '配对码无效或已过期'
    if (err.status === 429) return '尝试次数过多,请稍后再试'
    if (err.status === 0) return '无法连接到手机,请检查 WiFi'
    if (err.status >= 500) return '手机端服务异常,请稍后重试'
    return err.message || fallback
  }
  if (err instanceof Error) return err.message || fallback
  return fallback
}

function clientDeviceLabel(): string {
  if (typeof navigator === 'undefined') return 'Web Browser'
  const ua = navigator.userAgent
  if (/Edg\//.test(ua)) return 'Edge'
  if (/Chrome\//.test(ua) && !/Chromium\//.test(ua)) return 'Chrome'
  if (/Firefox\//.test(ua)) return 'Firefox'
  if (/Safari\//.test(ua) && /Version\//.test(ua)) return 'Safari'
  if (/Mobile/.test(ua)) return 'Mobile Browser'
  return 'Web Browser'
}

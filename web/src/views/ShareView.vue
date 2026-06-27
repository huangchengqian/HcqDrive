<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  Cloud,
  Download,
  Eye,
  EyeOff,
  FileText,
  KeyRound,
  Loader2,
  ShieldCheck,
  ShieldOff,
  X,
} from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import { ApiClientError, buildUrl } from '@/api/client'
import { shareApi } from '@/api/share'
import { useToastStore } from '@/stores/toast'
import type { ShareDto } from '@/types/api'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const token = computed(() => {
  const raw = route.params.token
  if (Array.isArray(raw)) return raw[0] ?? ''
  return (raw ?? '').toString()
})

type Status = 'loading' | 'password' | 'ready' | 'downloading' | 'done' | 'error'

const status = ref<Status>('loading')
const errorCode = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const info = ref<ShareDto | null>(null)
const password = ref<string>('')
const showPassword = ref(false)
const passwordInput = ref<HTMLInputElement | null>(null)

const filename = computed(() => {
  if (!info.value) return ''
  const path = info.value.path
  const trimmed = path.endsWith('/') ? path.slice(0, -1) : path
  return trimmed.substring(trimmed.lastIndexOf('/') + 1) || trimmed || 'download'
})

const isExpired = computed(() => errorCode.value === 'SHARE_EXPIRED')
const isLimitReached = computed(() => errorCode.value === 'SHARE_LIMIT_REACHED')
const isNotFound = computed(() => errorCode.value === 'SHARE_NOT_FOUND')

watch(
  () => token.value,
  () => {
    password.value = ''
    errorCode.value = null
    errorMessage.value = null
    void probe()
  },
  { immediate: true },
)

onMounted(() => {
  void probe()
})

onBeforeUnmount(() => {
  password.value = ''
})

async function probe(): Promise<void> {
  if (!token.value) {
    status.value = 'error'
    errorCode.value = 'SHARE_NOT_FOUND'
    errorMessage.value = '链接无效'
    return
  }
  status.value = 'loading'
  errorCode.value = null
  errorMessage.value = null
  try {
    const dto = await shareApi.info(token.value)
    if (dto.isExpired) {
      errorCode.value = 'SHARE_EXPIRED'
      errorMessage.value = '分享链接已过期'
      status.value = 'error'
      return
    }
    if (dto.maxDownloads != null && dto.downloadCount >= dto.maxDownloads) {
      errorCode.value = 'SHARE_LIMIT_REACHED'
      errorMessage.value = '已达到下载次数上限'
      status.value = 'error'
      return
    }
    info.value = dto
    if (dto.hasPassword && !password.value) {
      status.value = 'password'
    } else {
      status.value = 'ready'
    }
  } catch (err) {
    if (err instanceof ApiClientError) {
      errorCode.value = err.code
      errorMessage.value = err.message
    } else {
      errorMessage.value = '无法访问分享,请检查网络'
    }
    status.value = 'error'
  }
}

function goHome(): void {
  void router.replace({ name: 'home' })
}

async function startDownload(): Promise<void> {
  if (!info.value) return
  if (info.value.hasPassword && !password.value) {
    status.value = 'password'
    return
  }
  status.value = 'downloading'
  errorCode.value = null
  errorMessage.value = null
  const url = buildUrl(
    `/api/share/${token.value}`,
    password.value ? { password: password.value } : undefined,
  )
  try {
    const res = await fetch(url, { credentials: 'omit' })
    if (!res.ok) {
      const body = await safeReadJson(res)
      const errMsg = (fallback: string) => body?.error ?? fallback
      if (res.status === 401) {
        errorCode.value = 'SHARE_INVALID_PASSWORD'
        errorMessage.value = errMsg('密码错误')
        status.value = 'password'
        return
      }
      if (res.status === 403) {
        const code = body?.code
        if (code === 'SHARE_LIMIT_REACHED') {
          errorCode.value = 'SHARE_LIMIT_REACHED'
          errorMessage.value = errMsg('已达到下载次数上限')
          status.value = 'error'
          return
        }
        if (code === 'SHARE_PASSWORD_REQUIRED') {
          errorCode.value = 'SHARE_PASSWORD_REQUIRED'
          errorMessage.value = errMsg('需要密码')
          status.value = 'password'
          return
        }
      }
      if (res.status === 404) {
        errorCode.value = 'SHARE_NOT_FOUND'
        errorMessage.value = errMsg('分享不存在')
        status.value = 'error'
        return
      }
      if (res.status === 410) {
        errorCode.value = 'SHARE_EXPIRED'
        errorMessage.value = errMsg('分享已过期')
        status.value = 'error'
        return
      }
      throw new ApiClientError(
        body?.code ?? 'HTTP_ERROR',
        body?.error ?? `HTTP ${res.status}`,
        res.status,
        body ? { error: body.error ?? `HTTP ${res.status}`, code: body.code ?? 'HTTP_ERROR' } : null,
      )
    }
    const blob = await res.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename.value
    a.rel = 'noopener'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(blobUrl), 30_000)
    status.value = 'done'
    if (info.value) {
      info.value = { ...info.value, downloadCount: info.value.downloadCount + 1 }
    }
    toast.push({ tone: 'success', message: '已开始下载' })
  } catch (err) {
    if (err instanceof ApiClientError) {
      errorCode.value = err.code
      errorMessage.value = err.message
      if (err.code === 'SHARE_INVALID_PASSWORD') {
        status.value = 'password'
        return
      }
    }
    errorMessage.value = '下载失败,请重试'
    status.value = 'error'
  }
}

async function safeReadJson(res: Response): Promise<{ code?: string; error?: string } | null> {
  try {
    return (await res.json()) as { code?: string; error?: string }
  } catch {
    return null
  }
}
</script>

<template>
  <main
    class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-gradient-to-br from-surface-50 via-surface-50 to-primary-50/40 px-4 py-10 sm:px-6 dark:from-surface-950 dark:via-surface-950 dark:to-primary-950/30"
  >
    <div
      aria-hidden="true"
      class="pointer-events-none absolute -top-32 left-1/2 h-72 w-72 -translate-x-1/2 rounded-full bg-primary-500/20 blur-3xl animate-breathe"
    />
    <div
      aria-hidden="true"
      class="pointer-events-none absolute -bottom-32 right-1/4 h-72 w-72 rounded-full bg-primary-300/20 blur-3xl dark:bg-primary-700/15"
    />

    <section class="relative z-10 w-full max-w-md animate-fade-up">
      <div class="mb-6 flex flex-col items-center text-center">
        <div
          class="mb-4 flex h-14 w-14 items-center justify-center rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 text-white shadow-floating"
        >
          <Cloud :size="28" :stroke-width="2" />
        </div>
        <h1 class="text-2xl font-semibold tracking-tight text-surface-900 dark:text-surface-50">HcqDrive 分享</h1>
        <p class="mt-1.5 text-sm text-surface-500 dark:text-surface-400">
          {{ info ? `「${filename}」` : '正在解析分享链接…' }}
        </p>
      </div>

      <div
        class="rounded-xl border border-border-light bg-surface-0 p-6 shadow-floating sm:p-8 dark:border-border-dark dark:bg-surface-900"
      >
        <div v-if="status === 'loading'" class="flex flex-col items-center gap-3 py-4 text-sm text-surface-500">
          <Loader2 :size="28" class="animate-spin text-primary-500" />
          正在加载分享信息…
        </div>

        <form
          v-else-if="status === 'password'"
          class="space-y-5"
          @submit.prevent="startDownload"
        >
          <div class="flex flex-col items-center gap-2 text-center">
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-primary-50 text-primary-600 dark:bg-primary-500/15 dark:text-primary-300">
              <KeyRound :size="20" />
            </div>
            <p class="text-sm text-surface-700 dark:text-surface-200">需要密码</p>
            <p class="text-xs text-surface-500 dark:text-surface-400">请输入创建时设置的访问密码</p>
          </div>
          <div class="relative">
            <input
              ref="passwordInput"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="访问密码"
              maxlength="32"
              autocomplete="off"
              class="h-11 w-full rounded-md border border-border-light bg-surface-0 px-3 pr-10 text-sm text-surface-900 placeholder:text-surface-400 focus:border-primary-500 focus:outline-none focus:ring-4 focus:ring-primary-500/15 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100"
              autofocus
            />
            <button
              type="button"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-surface-400 hover:text-surface-600"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <component :is="showPassword ? EyeOff : Eye" :size="16" />
            </button>
          </div>
          <p
            v-if="errorMessage"
            class="flex items-center justify-center gap-1.5 text-xs text-danger-600 dark:text-danger-500"
            role="alert"
          >
            <X :size="12" /> {{ errorMessage }}
          </p>
          <BaseButton type="submit" variant="primary" size="lg" block>
            验证并下载
          </BaseButton>
        </form>

        <div v-else-if="status === 'ready' || status === 'downloading' || status === 'done'" class="space-y-5">
          <div class="flex items-start gap-3 rounded-lg bg-primary-50/60 p-3 dark:bg-primary-500/10">
            <span class="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
              <FileText :size="18" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-surface-900 dark:text-surface-50" :title="filename">{{ filename }}</p>
              <p class="mt-0.5 truncate text-[11px] text-surface-500 dark:text-surface-400" :title="info?.path">{{ info?.path }}</p>
              <p class="mt-1 text-[11px] text-surface-400 dark:text-surface-500">
                <span v-if="info?.maxDownloads != null">已下载 {{ info?.downloadCount }} / {{ info?.maxDownloads }} 次</span>
                <span v-else>已下载 {{ info?.downloadCount }} 次</span>
                <span v-if="info?.hasPassword" class="ml-2 inline-flex items-center gap-0.5">
                  <ShieldCheck :size="10" /> 密码保护
                </span>
              </p>
            </div>
          </div>
          <BaseButton
            variant="primary"
            size="lg"
            block
            :loading="status === 'downloading'"
            :disabled="status === 'downloading'"
            @click="startDownload"
          >
            <template #icon><Download :size="16" /></template>
            {{ status === 'done' ? '再次下载' : '下载文件' }}
          </BaseButton>
        </div>

        <div v-else class="space-y-4 text-center">
          <div
            class="mx-auto flex h-12 w-12 items-center justify-center rounded-full"
            :class="isExpired || isLimitReached
              ? 'bg-warning-50 text-warning-500 dark:bg-warning-500/15 dark:text-warning-400'
              : 'bg-danger-50 text-danger-500 dark:bg-danger-500/15 dark:text-danger-400'"
          >
            <AlertTriangle :size="22" />
          </div>
          <p class="text-sm font-medium text-surface-900 dark:text-surface-50">
            <template v-if="isExpired">分享链接已过期</template>
            <template v-else-if="isLimitReached">下载次数已达上限</template>
            <template v-else-if="isNotFound">分享链接无效或已撤销</template>
            <template v-else>{{ errorMessage ?? '无法打开此分享' }}</template>
          </p>
          <div class="flex justify-center gap-2">
            <BaseButton variant="primary" size="md" @click="goHome">返回首页</BaseButton>
          </div>
        </div>
      </div>

      <p class="mt-6 flex items-center justify-center gap-1 text-center text-[11px] text-surface-400 dark:text-surface-500">
        <ShieldOff :size="11" /> 局域网直连 · 数据不出 WiFi
      </p>
    </section>
  </main>
</template>

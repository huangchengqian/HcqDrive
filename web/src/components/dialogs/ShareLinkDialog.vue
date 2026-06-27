<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Clock,
  Copy,
  Download,
  KeyRound,
  Link2,
  Loader2,
  QrCode,
  ShieldOff,
  Trash2,
  X,
} from 'lucide-vue-next'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { shareApi } from '@/api/share'
import { ApiClientError } from '@/api/client'
import { useToastStore } from '@/stores/toast'
import type { ShareCreateRequest, ShareDto, FileEntry } from '@/types/api'

const props = withDefaults(
  defineProps<{
    open: boolean
    entry: Pick<FileEntry, 'id' | 'name' | 'path' | 'kind'> | null
    loading?: boolean
    resultToken?: string | null
  }>(),
  {
    loading: false,
    resultToken: null,
  },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'create', payload: ShareCreateRequest): void
  (e: 'revoke', token: string): void
}>()

const toast = useToastStore()

const expiry = ref<number | null>(60 * 60 * 24)
const maxDownloads = ref<number | null>(null)
const password = ref<string>('')
const showPassword = ref(false)
const activeShares = ref<ShareDto[]>([])
const isLoadingList = ref(false)
const isRevoking = ref<string | null>(null)

const expiryOptions: { label: string; seconds: number | null }[] = [
  { label: '1 小时', seconds: 60 * 60 },
  { label: '24 小时', seconds: 60 * 60 * 24 },
  { label: '7 天', seconds: 60 * 60 * 24 * 7 },
  { label: '30 天', seconds: 60 * 60 * 24 * 30 },
  { label: '永不过期', seconds: null },
]
const downloadOptions: (number | null)[] = [1, 5, 10, 50, null]

const activeShareUrl = computed(() =>
  props.resultToken ? shareApi.buildConsumeUrl(props.resultToken) : '',
)

const activeShareDownloadUrl = computed(() =>
  props.resultToken ? shareApi.buildDownloadUrl(props.resultToken) : '',
)

const activeShareQrUrl = computed(() => {
  if (!activeShareUrl.value) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&margin=10&data=${encodeURIComponent(
    activeShareUrl.value,
  )}`
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      expiry.value = 60 * 60 * 24
      maxDownloads.value = null
      password.value = ''
      showPassword.value = false
      void loadActiveShares()
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (props.open) void loadActiveShares()
})

onBeforeUnmount(() => {
  activeShares.value = []
})

async function loadActiveShares(signal?: AbortSignal): Promise<void> {
  isLoadingList.value = true
  try {
    const result = await shareApi.list(signal)
    activeShares.value = result.shares
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 401) {
      // not paired; fall back to local-only
      activeShares.value = []
    } else {
      toast.push({ tone: 'warning', message: '无法加载分享列表' })
    }
  } finally {
    isLoadingList.value = false
  }
}

function submit(): void {
  if (!props.entry) return
  emit('create', {
    path: props.entry.path,
    ttlSeconds: expiry.value,
    maxDownloads: maxDownloads.value,
    ...(password.value ? { password: password.value } : {}),
  })
}

async function copyLink(url: string, label: string): Promise<void> {
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    toast.push({ tone: 'success', message: `${label}已复制` })
  } catch {
    toast.push({ tone: 'error', message: '复制失败,请手动选择' })
  }
}

function downloadShare(): void {
  if (!activeShareDownloadUrl.value) return
  const a = document.createElement('a')
  a.href = activeShareDownloadUrl.value
  a.target = '_self'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

async function revoke(token: string): Promise<void> {
  isRevoking.value = token
  try {
    emit('revoke', token)
    await shareApi.revoke({ token })
    activeShares.value = activeShares.value.filter((s) => s.token !== token)
    toast.push({ tone: 'success', message: '分享已撤销' })
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 404) {
      activeShares.value = activeShares.value.filter((s) => s.token !== token)
      toast.push({ tone: 'info', message: '分享已不存在' })
    } else {
      toast.push({ tone: 'error', message: '撤销失败' })
    }
  } finally {
    isRevoking.value = null
  }
}

function formatExpiry(expiresAt: number | null): string {
  if (!expiresAt) return '永不过期'
  const diff = expiresAt - Date.now()
  if (diff <= 0) return '已过期'
  const minutes = Math.round(diff / 60_000)
  if (minutes < 60) return `${minutes} 分钟后`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} 小时后`
  const days = Math.round(hours / 24)
  return `${days} 天后`
}

function formatDownloads(share: ShareDto): string {
  if (share.maxDownloads == null) return `${share.downloadCount} / 不限`
  return `${share.downloadCount} / ${share.maxDownloads}`
}
</script>

<template>
  <BaseDialog
    :open="open"
    title="分享管理"
    :description="entry ? `为「${entry.name}」创建链接` : '所有活跃分享'"
    size="lg"
    @close="emit('close')"
  >
    <div class="space-y-5">
      <section v-if="!resultToken" class="space-y-4">
        <div v-if="entry" class="rounded-md bg-primary-50/60 px-3 py-2 text-xs text-primary-700 dark:bg-primary-500/10 dark:text-primary-300">
          将为 <span class="font-medium">{{ entry.name }}</span> 创建分享链接
        </div>
        <div>
          <p class="mb-1.5 flex items-center gap-1 text-xs font-medium text-surface-700 dark:text-surface-300">
            <Clock :size="12" /> 过期时间
          </p>
          <div class="grid grid-cols-2 gap-1.5 sm:grid-cols-5">
            <button
              v-for="opt in expiryOptions"
              :key="String(opt.seconds)"
              type="button"
              :class="[
                'rounded-md border px-2 py-1.5 text-xs transition',
                expiry === opt.seconds
                  ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-500/15 dark:text-primary-200'
                  : 'border-border-light text-surface-600 hover:border-surface-300 dark:border-border-dark dark:text-surface-300',
              ]"
              @click="expiry = opt.seconds"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
        <div>
          <p class="mb-1.5 flex items-center gap-1 text-xs font-medium text-surface-700 dark:text-surface-300">
            <Download :size="12" /> 最大下载次数
          </p>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="n in downloadOptions"
              :key="String(n)"
              type="button"
              :class="[
                'rounded-md border px-2 py-1.5 text-xs transition',
                maxDownloads === n
                  ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-500/15 dark:text-primary-200'
                  : 'border-border-light text-surface-600 hover:border-surface-300 dark:border-border-dark dark:text-surface-300',
              ]"
              @click="maxDownloads = n"
            >
              <X v-if="n === null" :size="11" class="mr-0.5 inline-block align-text-bottom" />
              {{ n === null ? '不限' : `${n} 次` }}
            </button>
          </div>
        </div>
        <div>
          <p class="mb-1.5 flex items-center gap-1 text-xs font-medium text-surface-700 dark:text-surface-300">
            <KeyRound :size="12" /> 访问密码 <span class="text-surface-400">(可选)</span>
          </p>
          <div class="relative">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="留空则不设密码"
              maxlength="32"
              class="h-9 w-full rounded-md border border-border-light bg-surface-0 px-3 pr-9 text-sm text-surface-900 placeholder:text-surface-400 focus:border-primary-500 focus:outline-none focus:ring-4 focus:ring-primary-500/15 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100"
            />
            <button
              type="button"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-surface-400 hover:text-surface-600"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <component :is="showPassword ? ShieldOff : KeyRound" :size="14" />
            </button>
          </div>
        </div>
      </section>

      <section v-else class="space-y-3">
        <div class="flex flex-col items-center gap-3 rounded-md border border-border-light bg-surface-50 px-4 py-4 dark:border-border-dark dark:bg-surface-950 sm:flex-row">
          <div class="flex h-32 w-32 shrink-0 items-center justify-center overflow-hidden rounded-md border border-border-light bg-surface-0 p-1.5 dark:border-border-dark dark:bg-surface-900">
            <img
              v-if="activeShareQrUrl"
              :src="activeShareQrUrl"
              :alt="`分享二维码 ${resultToken}`"
              class="h-full w-full object-contain"
              referrerpolicy="no-referrer"
              loading="lazy"
            />
            <QrCode v-else :size="32" class="text-surface-400" />
          </div>
          <div class="min-w-0 flex-1 space-y-2">
            <div class="flex items-center gap-2 rounded-md border border-border-light bg-surface-0 px-3 py-2 dark:border-border-dark dark:bg-surface-900">
              <Link2 :size="14" class="shrink-0 text-primary-500" />
              <code class="min-w-0 flex-1 truncate text-xs text-surface-800 dark:text-surface-200">{{ activeShareUrl }}</code>
              <button
                type="button"
                class="ml-auto flex h-7 w-7 items-center justify-center rounded text-surface-500 hover:bg-surface-100 dark:hover:bg-surface-800"
                aria-label="复制链接"
                @click="copyLink(activeShareUrl, '分享链接')"
              >
                <Copy :size="14" />
              </button>
            </div>
            <div class="flex flex-wrap gap-1.5">
              <BaseButton variant="secondary" size="sm" @click="downloadShare">
                <template #icon><Download :size="13" /></template>
                直接下载
              </BaseButton>
              <BaseButton variant="ghost" size="sm" @click="copyLink(activeShareDownloadUrl, '直链')">
                <template #icon><Copy :size="13" /></template>
                复制 API 链接
              </BaseButton>
            </div>
          </div>
        </div>
        <p class="flex items-center gap-1.5 text-[11px] text-surface-500">
          <ShieldOff :size="11" /> 链接已生成,关闭后可在下方列表中再次访问
        </p>
      </section>

      <section>
        <div class="mb-2 flex items-center justify-between">
          <p class="text-xs font-medium text-surface-700 dark:text-surface-300">
            活跃分享 <span class="text-surface-400">({{ activeShares.length }})</span>
          </p>
          <button
            type="button"
            class="text-[11px] text-primary-600 hover:underline dark:text-primary-400"
            :disabled="isLoadingList"
            @click="() => loadActiveShares()"
          >
            {{ isLoadingList ? '刷新中…' : '刷新' }}
          </button>
        </div>
        <div class="overflow-hidden rounded-md border border-border-light dark:border-border-dark">
          <div v-if="isLoadingList && activeShares.length === 0" class="flex items-center justify-center gap-2 px-3 py-6 text-xs text-surface-500">
            <Loader2 :size="14" class="animate-spin" /> 加载中…
          </div>
          <div v-else-if="activeShares.length === 0" class="px-3 py-6 text-center text-xs text-surface-500">
            暂无活跃分享
          </div>
          <div v-else class="max-h-72 overflow-y-auto">
            <table class="w-full text-xs">
              <thead class="sticky top-0 bg-surface-50 text-left text-surface-500 dark:bg-surface-950">
                <tr>
                  <th class="px-3 py-2 font-medium">Token</th>
                  <th class="px-3 py-2 font-medium">路径</th>
                  <th class="px-3 py-2 font-medium">过期</th>
                  <th class="px-3 py-2 font-medium">下载</th>
                  <th class="px-3 py-2 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-light dark:divide-border-dark">
                <tr
                  v-for="share in activeShares"
                  :key="share.token"
                  class="text-surface-700 hover:bg-surface-50/60 dark:text-surface-300 dark:hover:bg-surface-950/40"
                >
                  <td class="px-3 py-2 font-mono text-[11px] text-surface-500">{{ share.token }}</td>
                  <td class="max-w-[12rem] truncate px-3 py-2" :title="share.path">{{ share.path }}</td>
                  <td class="px-3 py-2 text-surface-500">{{ formatExpiry(share.expiresAt) }}</td>
                  <td class="px-3 py-2 text-surface-500">{{ formatDownloads(share) }}</td>
                  <td class="px-3 py-2 text-right">
                    <button
                      type="button"
                      :disabled="isRevoking === share.token"
                      class="inline-flex items-center gap-1 rounded text-danger-600 hover:bg-danger-50 disabled:opacity-50 dark:text-danger-400 dark:hover:bg-danger-500/10"
                      @click="revoke(share.token)"
                    >
                      <Loader2 v-if="isRevoking === share.token" :size="12" class="animate-spin" />
                      <Trash2 v-else :size="12" />
                      <span>撤销</span>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
    <template #footer>
      <template v-if="!resultToken">
        <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('close')">取消</BaseButton>
        <BaseButton variant="primary" size="md" :loading="loading" :disabled="!entry" @click="submit">
          生成链接
        </BaseButton>
      </template>
      <template v-else>
        <BaseButton variant="primary" size="md" @click="emit('close')">完成</BaseButton>
      </template>
    </template>
  </BaseDialog>
</template>

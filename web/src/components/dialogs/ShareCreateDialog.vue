<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Clock, Download, KeyRound, Link2, ShieldOff, X } from 'lucide-vue-next'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { ShareCreateRequest } from '@/types/api'

export interface ExpiryOption {
  label: string
  /** Seconds until expiry. `null` = never expires. */
  seconds: number | null
}

const props = withDefaults(
  defineProps<{
    open: boolean
    entryName: string
    entryPath: string
    loading?: boolean
    /** Selectable expiry options. Defaults to 1h/24h/7d/30d/never. */
    options?: ExpiryOption[]
  }>(),
  {
    loading: false,
    options: () => [
      { label: '1 小时', seconds: 60 * 60 },
      { label: '24 小时', seconds: 60 * 60 * 24 },
      { label: '7 天', seconds: 60 * 60 * 24 * 7 },
      { label: '30 天', seconds: 60 * 60 * 24 * 30 },
      { label: '永不过期', seconds: null },
    ],
  },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', payload: ShareCreateRequest): void
}>()

const expiry = ref<number | null>(60 * 60 * 24)
const maxDownloads = ref<number | null>(null)
const password = ref<string>('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      expiry.value = 60 * 60 * 24
      maxDownloads.value = null
      password.value = ''
    }
  },
  { immediate: true },
)

const expiryLabel = computed(() => {
  return props.options.find((o) => o.seconds === expiry.value)?.label ?? ''
})

const downloadsLabel = computed(() => {
  if (maxDownloads.value === null) return '不限'
  return `${maxDownloads.value} 次`
})

function submit(): void {
  emit('submit', {
    path: props.entryPath,
    ttlSeconds: expiry.value,
    maxDownloads: maxDownloads.value,
    ...(password.value ? { password: password.value } : {}),
  })
}
</script>

<template>
  <BaseDialog
    :open="open"
    title="生成分享链接"
    :description="`「${entryName}」`"
    size="md"
    @close="emit('close')"
  >
    <div class="space-y-5">
      <div>
        <p class="mb-2 flex items-center gap-1.5 text-xs font-medium text-surface-700 dark:text-surface-300">
          <Clock :size="13" /> 过期时间
        </p>
        <div class="grid grid-cols-2 gap-1.5 sm:grid-cols-5">
          <button
            v-for="opt in options"
            :key="String(opt.seconds)"
            type="button"
            :class="[
              'rounded-md border px-2 py-2 text-xs transition',
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
        <p class="mb-2 flex items-center gap-1.5 text-xs font-medium text-surface-700 dark:text-surface-300">
          <Download :size="13" /> 最大下载次数
        </p>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="n in [1, 5, 10, 50]"
            :key="n"
            type="button"
            :class="[
              'rounded-md border px-3 py-2 text-xs transition',
              maxDownloads === n
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-500/15 dark:text-primary-200'
                : 'border-border-light text-surface-600 hover:border-surface-300 dark:border-border-dark dark:text-surface-300',
            ]"
            @click="maxDownloads = n"
          >
            {{ n }} 次
          </button>
          <button
            type="button"
            :class="[
              'rounded-md border px-3 py-2 text-xs transition',
              maxDownloads === null
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-500/15 dark:text-primary-200'
                : 'border-border-light text-surface-600 hover:border-surface-300 dark:border-border-dark dark:text-surface-300',
            ]"
            @click="maxDownloads = null"
          >
            <X :size="11" class="mr-1 inline-block align-text-bottom" />
            不限
          </button>
          <input
            v-model.number="maxDownloads"
            type="number"
            min="1"
            max="9999"
            placeholder="自定义"
            class="h-8 w-24 rounded-md border border-border-light bg-surface-0 px-2 text-xs text-surface-900 placeholder:text-surface-400 focus:border-primary-500 focus:outline-none focus:ring-4 focus:ring-primary-500/15 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100"
            @input="(e) => {
              const v = (e.target as HTMLInputElement).valueAsNumber
              maxDownloads = Number.isFinite(v) && v > 0 ? v : null
            }"
          />
        </div>
      </div>

      <div>
        <p class="mb-2 flex items-center gap-1.5 text-xs font-medium text-surface-700 dark:text-surface-300">
          <KeyRound :size="13" /> 访问密码 <span class="text-surface-400">(可选)</span>
        </p>
        <input
          v-model="password"
          type="password"
          placeholder="留空则不设密码,最多 32 字符"
          maxlength="32"
          class="h-9 w-full rounded-md border border-border-light bg-surface-0 px-3 text-sm text-surface-900 placeholder:text-surface-400 focus:border-primary-500 focus:outline-none focus:ring-4 focus:ring-primary-500/15 dark:border-border-dark dark:bg-surface-900 dark:text-surface-100"
        />
      </div>

      <div class="rounded-md bg-primary-50/60 px-3 py-2 text-xs text-primary-700 dark:bg-primary-500/10 dark:text-primary-300">
        <p class="flex items-center gap-1.5">
          <Link2 :size="12" />
          <span>有效期 {{ expiryLabel }} · 下载 {{ downloadsLabel }}{{ password ? ' · 已设密码' : '' }}</span>
        </p>
        <p class="mt-1 flex items-center gap-1 text-[11px] text-primary-600/80 dark:text-primary-300/80">
          <ShieldOff :size="11" /> 链接生成后可在「分享管理」撤销
        </p>
      </div>
    </div>
    <template #footer>
      <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('close')">取消</BaseButton>
      <BaseButton variant="primary" size="md" :loading="loading" @click="submit">
        生成链接
      </BaseButton>
    </template>
  </BaseDialog>
</template>

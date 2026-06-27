<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Cloud, ShieldCheck, Smartphone, X } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useAuthStore } from '@/stores/auth'

const CODE_LENGTH = 6

const router = useRouter()
const auth = useAuthStore()

const digits = ref<string[]>(Array.from({ length: CODE_LENGTH }, () => ''))
const inputs = useTemplateRef<Array<HTMLInputElement | null>>('inputs')
const focusedIndex = ref<number | null>(null)
const isShaking = ref(false)
const pasteBanner = ref<string | null>(null)

const code = computed(() => digits.value.join(''))
const isComplete = computed(() => code.value.length === CODE_LENGTH)

watch(
  () => auth.isAuthenticated,
  (val) => {
    if (val) {
      void router.replace({ name: 'home' })
    }
  },
)

onMounted(() => {
  auth.hydrate()
  void auth.discover()
  void auth.fetchStatus()
  void nextTick(() => {
    inputs.value?.[0]?.focus()
  })
})

onBeforeUnmount(() => {
  digits.value = Array.from({ length: CODE_LENGTH }, () => '')
})

function focusAt(index: number): void {
  const clamped = Math.max(0, Math.min(CODE_LENGTH - 1, index))
  void nextTick(() => {
    inputs.value?.[clamped]?.focus()
    inputs.value?.[clamped]?.select()
  })
}

function onInput(index: number, event: Event): void {
  const target = event.target as HTMLInputElement
  const raw = target.value.replace(/\D/g, '')
  if (raw.length === 0) {
    digits.value[index] = ''
    return
  }
  if (raw.length === 1) {
    digits.value[index] = raw
    if (index < CODE_LENGTH - 1) focusAt(index + 1)
    return
  }
  const chars = raw.slice(0, CODE_LENGTH - index).split('')
  for (let i = 0; i < chars.length && index + i < CODE_LENGTH; i += 1) {
    const slot = index + i
    const value = chars[i] ?? ''
    digits.value[slot] = value
  }
  const nextEmpty = digits.value.findIndex((d, i) => i >= index && !d)
  if (nextEmpty === -1) {
    inputs.value?.[CODE_LENGTH - 1]?.blur()
  } else {
    focusAt(nextEmpty)
  }
}

function onKeydown(index: number, event: KeyboardEvent): void {
  if (event.key === 'Backspace') {
    if (digits.value[index]) {
      digits.value[index] = ''
      event.preventDefault()
      return
    }
    if (index > 0) {
      digits.value[index - 1] = ''
      focusAt(index - 1)
      event.preventDefault()
    }
    return
  }
  if (event.key === 'ArrowLeft' && index > 0) {
    event.preventDefault()
    focusAt(index - 1)
    return
  }
  if (event.key === 'ArrowRight' && index < CODE_LENGTH - 1) {
    event.preventDefault()
    focusAt(index + 1)
    return
  }
  if (event.key === 'Enter' && isComplete.value) {
    event.preventDefault()
    void submit()
  }
}

function onPaste(index: number, event: ClipboardEvent): void {
  event.preventDefault()
  const text = event.clipboardData?.getData('text') ?? ''
  const cleaned = text.replace(/\D/g, '').slice(0, CODE_LENGTH)
  if (!cleaned) return
  const chars = cleaned.split('')
  for (let i = 0; i < CODE_LENGTH; i += 1) {
    digits.value[i] = chars[i] ?? ''
  }
  pasteBanner.value = `已粘贴 ${chars.length} 位`
  window.setTimeout(() => (pasteBanner.value = null), 1800)
  const lastFilled = Math.min(CODE_LENGTH - 1, index + chars.length - 1)
  focusAt(lastFilled)
  if (chars.length === CODE_LENGTH) {
    void submit()
  }
}

function onFocus(index: number): void {
  focusedIndex.value = index
}

function onBlur(): void {
  focusedIndex.value = null
}

function clearAll(): void {
  digits.value = Array.from({ length: CODE_LENGTH }, () => '')
  focusAt(0)
}

function shake(): void {
  isShaking.value = false
  void nextTick(() => {
    isShaking.value = true
    window.setTimeout(() => (isShaking.value = false), 400)
  })
  if (typeof navigator !== 'undefined' && navigator.vibrate) navigator.vibrate(60)
}

async function submit(): Promise<void> {
  if (!isComplete.value || auth.isPairing) return
  const ok = await auth.pair(code.value)
  if (!ok) {
    shake()
    clearAll()
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

    <section
      class="relative z-10 w-full max-w-md animate-fade-up"
      :class="isShaking ? 'animate-shake' : ''"
    >
      <div class="mb-6 flex flex-col items-center text-center">
        <div
          class="mb-4 flex h-14 w-14 items-center justify-center rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 text-white shadow-floating"
        >
          <Cloud :size="28" :stroke-width="2" />
        </div>
        <h1 class="text-2xl font-semibold tracking-tight text-surface-900 dark:text-surface-50">
          HcqDrive
        </h1>
        <p class="mt-1.5 text-sm text-surface-500 dark:text-surface-400">
          输入手机上的 6 位配对码
        </p>
        <p
          v-if="auth.status?.deviceName && !auth.isAuthenticated"
          class="mt-1 text-xs text-surface-400"
        >
          检测到服务端:<span class="font-medium text-surface-600 dark:text-surface-300">{{ auth.status.deviceName }}</span>
        </p>
        <p
          v-if="auth.discovery?.hostname && !auth.isAuthenticated"
          class="mt-1 text-xs text-surface-400"
        >
          mDNS:<span class="font-mono text-surface-600 dark:text-surface-300">http://{{ auth.discovery.hostname }}.local:{{ auth.discovery.port }}</span>
        </p>
      </div>

      <div
        class="rounded-xl border border-border-light bg-surface-0 p-6 shadow-floating sm:p-8 dark:border-border-dark dark:bg-surface-900"
      >
        <form
          class="space-y-6"
          @submit.prevent="submit"
        >
          <div
            class="flex items-center justify-center gap-2 sm:gap-2.5"
            :aria-label="'6 位配对码输入框'"
            role="group"
            @paste="(e) => onPaste(0, e as ClipboardEvent)"
          >
            <input
              v-for="(digit, index) in digits"
              :key="index"
              :ref="'inputs'"
              :value="digit"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="1"
              :aria-label="`配对码第 ${index + 1} 位`"
              :aria-invalid="auth.pairError ? 'true' : undefined"
              :class="[
                'h-12 w-10 sm:h-14 sm:w-12 rounded-lg border-2 bg-surface-0 text-center text-xl sm:text-2xl font-semibold tabular-nums text-surface-900 transition duration-200 ease-out-soft focus:outline-none focus:ring-4 dark:bg-surface-900 dark:text-surface-50',
                focusedIndex === index
                  ? 'border-primary-500 ring-primary-500/20'
                  : 'border-border-light hover:border-surface-300 dark:border-border-dark dark:hover:border-surface-600',
                auth.pairError
                  ? 'border-danger-500 focus:border-danger-500 focus:ring-danger-500/15'
                  : '',
              ]"
              @input="(e) => onInput(index, e)"
              @keydown="(e) => onKeydown(index, e)"
              @focus="() => onFocus(index)"
              @blur="onBlur"
              @paste="(e) => onPaste(index, e)"
            />
          </div>

          <transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0 -translate-y-1"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition duration-150"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
          >
            <p
              v-if="auth.pairError"
              class="flex items-center justify-center gap-1.5 text-xs text-danger-600 dark:text-danger-500"
              role="alert"
            >
              <span class="inline-block h-1.5 w-1.5 rounded-full bg-danger-500" />
              {{ auth.pairError }}
            </p>
            <p
              v-else-if="pasteBanner"
              class="flex items-center justify-center gap-1.5 text-xs text-primary-600 dark:text-primary-400"
            >
              <span class="inline-block h-1.5 w-1.5 rounded-full bg-primary-500" />
              {{ pasteBanner }}
            </p>
            <p
              v-else
              class="text-center text-xs text-surface-400 dark:text-surface-500"
            >
              输完 6 位后将自动开始配对
            </p>
          </transition>

          <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
            <BaseButton
              type="submit"
              variant="primary"
              size="lg"
              block
              :disabled="!isComplete"
              :loading="auth.isPairing"
            >
              配对
            </BaseButton>
            <BaseButton
              v-if="code.length > 0"
              type="button"
              variant="ghost"
              size="lg"
              icon-only
              aria-label="清空"
              @click="clearAll"
            >
              <template #icon>
                <X :size="18" />
              </template>
            </BaseButton>
          </div>
        </form>

        <div class="mt-6 flex items-start gap-3 rounded-lg bg-primary-50/60 p-3 dark:bg-primary-500/10">
          <span
            class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300"
            aria-hidden="true"
          >
            <Smartphone :size="14" />
          </span>
          <p class="text-xs leading-relaxed text-surface-600 dark:text-surface-300">
            打开手机上的 HcqDrive App,在通知栏或首页查看当前配对码。配对码 5 分钟内有效,过期后请重新生成。
          </p>
        </div>

        <div class="mt-3 flex items-center gap-2 text-[11px] text-surface-400 dark:text-surface-500">
          <ShieldCheck :size="12" />
          <span>配对成功后,本设备 30 天内免重新配对</span>
        </div>
      </div>

      <p class="mt-6 text-center text-[11px] text-surface-400 dark:text-surface-500">
        局域网直连,数据不出 WiFi
      </p>
    </section>
  </main>
</template>

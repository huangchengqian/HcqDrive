<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    label?: string
    placeholder?: string
    hint?: string
    error?: string
    maxlength?: number
    showCount?: boolean
    type?: 'text' | 'password' | 'email' | 'number' | 'tel'
    disabled?: boolean
    autocomplete?: string
    inputmode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email' | 'search' | 'url'
  }>(),
  {
    label: undefined,
    placeholder: undefined,
    hint: undefined,
    error: undefined,
    maxlength: 64,
    showCount: false,
    type: 'text',
    disabled: false,
    autocomplete: 'off',
    inputmode: 'text',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'focus', event: FocusEvent): void
  (e: 'blur', event: FocusEvent): void
  (e: 'enter', event: KeyboardEvent): void
}>()

const id = useId()

const inputClasses = computed(() => {
  const base =
    'w-full rounded-md border bg-surface-0 px-3.5 text-sm text-surface-900 placeholder:text-surface-400 transition duration-200 ease-out-soft focus:outline-none focus:ring-4 dark:bg-surface-900 dark:text-surface-100 dark:placeholder:text-surface-500'
  const state = props.error
    ? 'border-danger-500 focus:border-danger-500 focus:ring-danger-500/15'
    : 'border-border-light focus:border-primary-500 focus:ring-primary-500/15 dark:border-border-dark dark:focus:border-primary-400'
  const size = 'h-10'
  const disabled = props.disabled ? 'cursor-not-allowed opacity-60' : ''
  return [base, state, size, disabled].join(' ')
})

function onInput(event: Event): void {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') emit('enter', event)
}
</script>

<template>
  <div class="w-full">
    <div v-if="label || showCount" class="mb-1.5 flex items-center justify-between">
      <label
        v-if="label"
        :for="id"
        class="text-sm font-medium text-surface-700 dark:text-surface-300"
      >
        {{ label }}
      </label>
      <span
        v-if="showCount"
        class="text-xs tabular-nums text-surface-400 dark:text-surface-500"
      >
        {{ modelValue.length }}<span class="text-surface-300 dark:text-surface-600">/{{ maxlength }}</span>
      </span>
    </div>
    <input
      :id="id"
      :type="type"
      :class="inputClasses"
      :value="modelValue"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :disabled="disabled"
      :autocomplete="autocomplete"
      :inputmode="inputmode"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="error || hint ? `${id}-desc` : undefined"
      @input="onInput"
      @focus="(e) => emit('focus', e)"
      @blur="(e) => emit('blur', e)"
      @keydown="onKeydown"
    />
    <p
      v-if="error"
      :id="`${id}-desc`"
      class="mt-1.5 flex items-center gap-1 text-xs text-danger-600 dark:text-danger-500"
      role="alert"
    >
      <span class="inline-block h-1 w-1 rounded-full bg-danger-500" />
      {{ error }}
    </p>
    <p
      v-else-if="hint"
      :id="`${id}-desc`"
      class="mt-1.5 text-xs text-surface-500 dark:text-surface-400"
    >
      {{ hint }}
    </p>
  </div>
</template>

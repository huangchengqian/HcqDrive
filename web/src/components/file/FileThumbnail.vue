<script setup lang="ts">
import { computed, ref } from 'vue'
import { ImageOff, Play } from 'lucide-vue-next'
import { fileTypeFor } from '@/lib/fileType'
import { TRANSPARENT_PLACEHOLDER } from '@/composables/useThumbnail'
import type { FileEntry } from '@/types/api'

const props = withDefaults(
  defineProps<{
    entry: FileEntry
    size?: number
    rounded?: 'md' | 'lg' | 'xl'
    eager?: boolean
  }>(),
  { size: 240, rounded: 'md', eager: false },
)

const type = computed(() => props.entry.type ?? fileTypeFor(props.entry.name, props.entry.mime))
const isImage = computed(() => type.value === 'image')
const isVideo = computed(() => type.value === 'video')

const failed = ref(false)

const src = computed(() => {
  if (!isImage.value && !isVideo.value) return null
  if (props.entry.thumbnailUrl) return props.entry.thumbnailUrl
  const params = new URLSearchParams({
    path: props.entry.path,
    size: String(props.size),
  })
  return `/api/file/thumb?${params.toString()}`
})

const roundedClass = computed(() => {
  if (props.rounded === 'lg') return 'rounded-lg'
  if (props.rounded === 'xl') return 'rounded-xl'
  return 'rounded-md'
})

function onError(): void {
  failed.value = true
}
</script>

<template>
  <div
    :class="[
      'relative h-full w-full overflow-hidden bg-surface-100 dark:bg-surface-800',
      roundedClass,
    ]"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :alt="entry.name"
      :loading="eager ? 'eager' : 'lazy'"
      decoding="async"
      class="h-full w-full object-cover"
      draggable="false"
      @error="onError"
    />
    <img
      v-else
      :src="TRANSPARENT_PLACEHOLDER"
      alt=""
      aria-hidden="true"
      class="h-full w-full object-cover opacity-0"
    />
    <div
      v-if="failed"
      class="absolute inset-0 flex items-center justify-center text-surface-400 dark:text-surface-500"
      aria-hidden="true"
    >
      <ImageOff :size="20" :stroke-width="1.6" />
    </div>
    <div
      v-if="isVideo && !failed"
      class="pointer-events-none absolute inset-0 flex items-center justify-center"
      aria-hidden="true"
    >
      <span class="flex h-7 w-7 items-center justify-center rounded-full bg-surface-950/60 text-white backdrop-blur-sm">
        <Play :size="12" fill="currentColor" />
      </span>
    </div>
  </div>
</template>

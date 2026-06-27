import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const TRANSPARENT_GIF =
  'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'

export interface ThumbnailProps {
  path: string
  name: string
  mime: string | null
  type: 'image' | 'video' | 'audio' | 'document' | 'archive' | 'folder' | 'unknown'
  size?: number
  eager?: boolean
}

export function useThumbnailSrc(props: ThumbnailProps) {
  const src = ref<string | null>(null)
  const isImage = computed(() => props.type === 'image')
  const isVideo = computed(() => props.type === 'video')

  function build(): void {
    if (!isImage.value && !isVideo.value) {
      src.value = null
      return
    }
    const params = new URLSearchParams({
      path: props.path,
      size: String(props.size ?? 240),
    })
    src.value = `/api/file/thumb?${params.toString()}`
  }

  onMounted(build)
  watch(() => [props.path, props.size], build)
  onBeforeUnmount(() => {
    src.value = null
  })

  return { src, isImage, isVideo }
}

export const TRANSPARENT_PLACEHOLDER = TRANSPARENT_GIF

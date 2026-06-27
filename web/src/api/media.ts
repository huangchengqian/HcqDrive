/** Media endpoints (EXIF, photo roll). */

import { api } from './client'
import type { ExifInfo, MediaPhotosResponse } from '@/types/api'

export const mediaApi = {
  exif(path: string, signal?: AbortSignal) {
    return api.get<ExifInfo>('/api/media/exif', { path }, { signal })
  },

  photos(query: { year?: number; month?: number; limit?: number } = {}, signal?: AbortSignal) {
    return api.get<MediaPhotosResponse>(
      '/api/media/photos',
      query as Record<string, unknown>,
      { signal },
    )
  },
}

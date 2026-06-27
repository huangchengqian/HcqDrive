/** Sharing endpoints (create, list, revoke, info, resolve). */

import { api, buildUrl, getApiBase } from './client'
import type {
  ShareCreateRequest,
  ShareDto,
  ShareListResponse,
  ShareRevokeRequest,
  ShareRevokeResponse,
} from '@/types/api'

export const shareApi = {
  create(body: ShareCreateRequest, signal?: AbortSignal) {
    return api.post<ShareDto>('/api/share/create', body, { signal })
  },

  list(signal?: AbortSignal) {
    return api.get<ShareListResponse>('/api/share/list', undefined, { signal })
  },

  info(token: string, signal?: AbortSignal) {
    return api.get<ShareDto>('/api/share/info', { token }, { signal })
  },

  revoke(body: ShareRevokeRequest, signal?: AbortSignal) {
    return api.post<ShareRevokeResponse>('/api/share/revoke', body, { signal })
  },

  /**
   * Absolute URL to the consumer page (SPA view), e.g. `https://host/s/abc123`.
   * Distinct from [buildDownloadUrl] which returns the byte stream endpoint.
   */
  buildConsumeUrl(token: string): string {
    const base = getApiBase().replace(/\/?$/, '/')
    return `${base}s/${token}`
  },

  /** Absolute URL to the byte-stream download endpoint. */
  buildDownloadUrl(token: string, password?: string): string {
    return buildUrl(`/api/share/${token}`, password ? { password } : undefined)
  },
}

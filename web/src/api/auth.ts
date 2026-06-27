/** Auth endpoints. */

import { api } from './client'
import type { PairRequest, PairResponse, StatusResponse, VerifyRequest, VerifyResponse } from '@/types/api'

export const authApi = {
  status(signal?: AbortSignal) {
    return api.get<StatusResponse>('/api/status', undefined, { signal })
  },

  pair(body: PairRequest, signal?: AbortSignal) {
    return api.post<PairResponse>('/api/auth/pair', body, {
      signal,
      skipAuthRedirect: true,
    })
  },

  verify(body: VerifyRequest, signal?: AbortSignal) {
    return api.post<VerifyResponse>('/api/auth/verify', body, { signal })
  },
}

/** Filesystem endpoints. */

import { api } from './client'
import type {
  CopyRequest,
  DeleteRequest,
  DeleteResponse,
  ListQuery,
  ListResponse,
  MkdirRequest,
  MoveRequest,
  RenameRequest,
  SearchRequest,
  SearchResponse,
  StatQuery,
  StatResponse,
} from '@/types/api'

export const filesApi = {
  list(query: ListQuery, signal?: AbortSignal) {
    return api.get<ListResponse>('/api/fs/list', query as unknown as Record<string, unknown>, { signal })
  },

  stat(query: StatQuery, signal?: AbortSignal) {
    return api.get<StatResponse>('/api/fs/stat', query as unknown as Record<string, unknown>, { signal })
  },

  search(body: SearchRequest, signal?: AbortSignal) {
    return api.post<SearchResponse>('/api/fs/search', body, { signal })
  },

  mkdir(body: MkdirRequest, signal?: AbortSignal) {
    return api.post<StatResponse>('/api/fs/mkdir', body, { signal })
  },

  rename(body: RenameRequest, signal?: AbortSignal) {
    return api.post<StatResponse>('/api/fs/rename', body, { signal })
  },

  move(body: MoveRequest, signal?: AbortSignal) {
    return api.post<StatResponse>('/api/fs/move', body, { signal })
  },

  copy(body: CopyRequest, signal?: AbortSignal) {
    return api.post<StatResponse>('/api/fs/copy', body, { signal })
  },

  delete(body: DeleteRequest, signal?: AbortSignal) {
    return api.post<DeleteResponse>('/api/fs/delete', body, { signal })
  },
}

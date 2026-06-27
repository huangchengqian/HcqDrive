/**
 * HcqDrive M1 API contract.
 *
 * Mirrors the Android side (Ktor + kotlinx.serialization) and
 * the product spec section 5.4. Keep this file the single source
 * of truth for cross-platform types.
 */

// ---- File classification ----

/** Coarse-grained file classification used in icons / filters / sort. */
export type FileType =
  | 'image'
  | 'video'
  | 'audio'
  | 'document'
  | 'archive'
  | 'folder'
  | 'unknown'

/** Whether a filesystem entry is a file or a directory. */
export type EntryKind = 'file' | 'directory'

// ---- Common error envelope ----

/**
 * Server returned a non-2xx response.
 * The wire shape is `{ error: string, code: string }` per spec.
 */
export interface ApiErrorPayload {
  error: string
  code: string
}

// ---- Auth / status ----

export interface StatusResponse {
  /** App display name (e.g. "HcqDrive"). */
  app: string
  /** App version, semver string. */
  version: string
  /** Server status: "running" | "paused" | "starting" | "stopped". */
  status: 'running' | 'paused' | 'starting' | 'stopped'
  /** Seconds since the service started. */
  uptime: number
  /** Current active HTTP connections. */
  connections: number
  /** Human readable device name (host). */
  deviceName: string
  /** Whether the calling device has a valid token. */
  paired: boolean
}

/**
 * Service discovery payload returned by `GET /api/discover`. Lets the web client
 * pin its API base URL to the mDNS-resolvable hostname (`hcqdrive.local`) so users
 * only ever need to type `http://hcqdrive.local:8080` in a Bonjour-aware browser.
 */
export interface DiscoverResponse {
  app: string
  version: string
  /** Bonjour mDNS hostname prefix; clients resolve `${hostname}.local`. */
  hostname: string
  /** HTTP port the service is bound to. */
  port: number
  /** Best-effort LAN IPv4 (e.g. "192.168.1.100"), or "0.0.0.0" when unknown. */
  address: string
  /** Current 6-digit pairing code (or freshly generated if none active). */
  pairCode: string
  /** Feature flags advertised by the server. */
  capabilities: string[]
}

export interface PairRequest {
  /** 6-digit pairing code displayed on the phone. */
  code: string
  /** Optional client-supplied device label (browser UA, hostname). */
  deviceName?: string
}

export interface PairResponse {
  /** Bearer token to send as `Authorization: Bearer <token>`. */
  token: string
  /** Unix-ms expiration of the token. */
  expiresAt: number
  /** Server-assigned friendly device name. */
  deviceName: string
}

export interface VerifyRequest {
  token: string
}

export interface VerifyResponse {
  valid: boolean
  deviceName?: string
  expiresAt?: number
}

// ---- Filesystem ----

export interface FileEntry {
  /** Stable opaque ID (server-generated). */
  id: string
  /** Display name (no path). */
  name: string
  /** Full virtual path, always starts with `/`. */
  path: string
  /** "file" or "directory". */
  kind: EntryKind
  /** Size in bytes, `0` for directories. */
  size: number
  /** MIME type, `null` for directories or unknown. */
  mime: string | null
  /** Unix-ms last-modified time. */
  modifiedAt: number
  /** Unix-ms creation time when known, else `null`. */
  createdAt: number | null
  /** Coarse file type bucket for icons/filters. */
  type: FileType
  /** Whether the entry is hidden (name starts with "."). */
  hidden: boolean
  /** Optional thumbnail URL (absolute or relative). */
  thumbnailUrl: string | null
  /** Optional child count for directories. */
  childCount?: number | null
}

export interface ListResponse {
  path: string
  parent: string | null
  entries: FileEntry[]
  /** Total entry count (may exceed `entries.length` if paginated). */
  total: number
}

export interface StatResponse {
  entry: FileEntry
}

export interface MkdirRequest {
  path: string
  name: string
}

export interface RenameRequest {
  path: string
  newName: string
}

export interface MoveRequest {
  /** One or more source paths. */
  src: string[]
  /** Destination directory (must end in `/`). */
  dst: string
}

export interface CopyRequest {
  src: string[]
  dst: string
}

export interface DeleteRequest {
  paths: string[]
  /** Whether to also remove children of directories. */
  recursive: boolean
}

export interface DeleteResponse {
  deleted: string[]
  failed: Array<{ path: string; reason: string }>
}

export interface SearchRequest {
  /** Search query. */
  q: string
  /** Optional base path; defaults to root. */
  path?: string
  /** Limit total results. */
  limit?: number
}

export interface SearchResponse {
  query: string
  results: FileEntry[]
}

// ---- Transfer ----

/**
 * Server hints that a large file should be split.
 * Anything `>= chunkThreshold` (bytes) is uploaded via the chunked flow.
 */
export interface UploadInitRequest {
  /** Destination directory (must end with `/`). */
  path: string
  name: string
  size: number
  /** Last-modified unix-ms of the source file. */
  modifiedAt?: number
  /** SHA-256 hex if pre-computed; lets server dedupe. */
  sha256?: string
  /** Client-chosen idempotency key, e.g. `crypto.randomUUID()`. */
  clientId: string
}

export interface UploadInitResponse {
  /** Upload session id; pass to subsequent calls. */
  uploadId: string
  /** Whether server accepted a single PUT instead (small file fast path). */
  direct: boolean
  /** Server-suggested chunk size in bytes. */
  chunkSize: number
  /** Pre-existing entry id when the file already exists and policy says reuse. */
  existingEntryId?: string
}

export interface UploadChunkResponse {
  uploadId: string
  /** 0-based index of the chunk just acknowledged. */
  chunkIndex: number
  /** Bytes the server has received so far. */
  received: number
  /** Expected total (== `size` from init) for progress display. */
  total: number
  /** True when no more chunks are expected. */
  complete: boolean
}

export interface UploadCompleteRequest {
  uploadId: string
  /** Final SHA-256 hex; server verifies if provided. */
  sha256?: string
}

export interface UploadCompleteResponse {
  entry: FileEntry
}

export interface ConflictStrategy {
  /** What to do when a file with the same name exists. */
  onConflict: 'overwrite' | 'rename' | 'skip'
  /** Number of parallel chunk uploads per file. */
  concurrency: number
  /** Chunk size in bytes. */
  chunkSize: number
}

// ---- Media ----

export interface ExifInfo {
  path: string
  cameraMake?: string | null
  cameraModel?: string | null
  lensModel?: string | null
  takenAt?: number | null
  iso?: number | null
  aperture?: number | null
  shutter?: string | null
  focalLength?: number | null
  width?: number | null
  height?: number | null
  gps?: { lat: number; lng: number; altitude?: number | null } | null
}

export interface MediaPhoto {
  id: string
  path: string
  name: string
  size: number
  takenAt: number | null
  thumbnailUrl: string | null
  width?: number | null
  height?: number | null
}

export interface MediaPhotosResponse {
  year: number
  month: number | null
  photos: MediaPhoto[]
  total: number
}

// ---- Sharing ----

/**
 * Canonical share metadata returned by the Android server. Used as the wire
 * shape for create, info, and list endpoints.
 */
export interface ShareDto {
  token: string
  path: string
  /** Unix-ms creation time. */
  createdAt: number
  /** Unix-ms expiration, `null` for "never expires". */
  expiresAt: number | null
  hasPassword: boolean
  downloadCount: number
  isExpired: boolean
  /** `null` for unlimited. */
  maxDownloads: number | null
}

export interface ShareListResponse {
  shares: ShareDto[]
}

export interface ShareCreateRequest {
  path: string
  /** Seconds until the link stops working. `null` / 0 == never. */
  ttlSeconds?: number | null
  /** Max allowed downloads. `null` / 0 == unlimited. */
  maxDownloads?: number | null
  /** Optional access password; empty string is treated as "no password". */
  password?: string
}

export interface ShareRevokeRequest {
  token: string
}

export interface ShareRevokeResponse {
  ok: boolean
}

export interface ShareInfoResponse extends ShareDto {}

/** Possible server codes returned by `GET /api/share/{token}` failures. */
export type ShareConsumeErrorCode =
  | 'SHARE_NOT_FOUND'
  | 'SHARE_EXPIRED'
  | 'SHARE_LIMIT_REACHED'
  | 'SHARE_PASSWORD_REQUIRED'
  | 'SHARE_INVALID_PASSWORD'

// ---- Query parameter helpers ----

export interface ListQuery {
  path: string
  /** "asc" | "desc" — sort order. */
  order?: 'asc' | 'desc'
  /** Sort key. */
  sort?: 'name' | 'size' | 'modifiedAt' | 'type'
}

export interface StatQuery {
  path: string
}

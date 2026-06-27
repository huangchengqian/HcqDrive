/**
 * mDNS-aware service discovery + runtime base-URL resolution.
 *
 * The web bundle can be loaded from three kinds of hosts:
 *
 *   1. `localhost:5173` (Vite dev server against the phone) → talk to the phone
 *      directly via the LAN IP the user typed in the QR / notification.
 *   2. `http://<phone-ip>:8080` (built bundle served by the Android app itself) →
 *      relative URLs work, no discovery needed.
 *   3. `http://hcqdrive.local:8080` (Bonjour-aware browser hitting the mDNS
 *      hostname the Android service advertises) → relative URLs work too, but
 *      we still want to surface the device info to the UI for confirmation.
 *
 * This module probes `/api/discover` against a list of candidate base URLs in
 * order and remembers the first one that succeeds. The HTTP client (`client.ts`)
 * consults the resolved base for every call so the rest of the app stays
 * agnostic of where the API lives.
 */

import { ApiClientError, api } from './client'
import type { DiscoverResponse } from '@/types/api'

const MDNS_DEFAULT_HOST = 'hcqdrive.local'
const MDNS_DEFAULT_PORT = 8080
const DISCOVERY_TIMEOUT_MS = 2_500
const DISCOVERY_KEY = 'hcqdrive:discovery'

type StoredDiscovery = {
  baseUrl: string
  info: DiscoverResponse
  /** Unix-ms when we last successfully probed this base. */
  ts: number
}

function readStoredDiscovery(): StoredDiscovery | null {
  try {
    const raw = localStorage.getItem(DISCOVERY_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredDiscovery
    if (!parsed?.baseUrl || !parsed?.info) return null
    return parsed
  } catch {
    return null
  }
}

function writeStoredDiscovery(value: StoredDiscovery): void {
  try {
    localStorage.setItem(DISCOVERY_KEY, JSON.stringify(value))
  } catch {
    /* storage disabled */
  }
}

function normalizeBase(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, '')
  if (!trimmed) return ''
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  return `http://${trimmed}`
}

function buildMdnsCandidates(): string[] {
  const fromEnv = normalizeBase(import.meta.env.VITE_DISCOVERY_HOST ?? '')
  const port = MDNS_DEFAULT_PORT
  const list: string[] = []
  if (fromEnv) list.push(fromEnv)
  list.push(`http://${MDNS_DEFAULT_HOST}:${port}`)
  // Loopback variants — useful when the dev bundle runs on the phone itself.
  list.push(`http://127.0.0.1:${port}`)
  list.push(`http://localhost:${port}`)
  return Array.from(new Set(list))
}

async function probe(base: string, signal: AbortSignal): Promise<DiscoverResponse | null> {
  const url = `${base.replace(/\/+$/, '')}/api/discover`
  try {
    return await api.get<DiscoverResponse>(url, undefined, {
      signal,
      skipAuthRedirect: true,
    })
  } catch (err) {
    if (err instanceof ApiClientError && err.isNetwork()) return null
    throw err
  }
}

/**
 * Probe candidate base URLs in order and resolve the first one that responds
 * to `/api/discover`. The result is cached in `localStorage` so subsequent
 * navigations don't pay the network round-trip.
 */
export async function discoverBase(signal?: AbortSignal): Promise<StoredDiscovery | null> {
  const stored = readStoredDiscovery()
  if (stored) {
    // Re-validate the cached base in the background; do not block the caller.
    void revalidate(stored.baseUrl).catch(() => {
      /* stale cache is fine, will refresh on next call */
    })
    return stored
  }
  return resolveBase(signal)
}

async function revalidate(base: string): Promise<void> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), DISCOVERY_TIMEOUT_MS)
  try {
    const info = await probe(base, controller.signal)
    if (info) writeStoredDiscovery({ baseUrl: base, info, ts: Date.now() })
  } finally {
    window.clearTimeout(timer)
  }
}

async function resolveBase(signal?: AbortSignal): Promise<StoredDiscovery | null> {
  for (const base of buildMdnsCandidates()) {
    if (signal?.aborted) return null
    const controller = new AbortController()
    const timer = window.setTimeout(() => controller.abort(), DISCOVERY_TIMEOUT_MS)
    const composite = signal
      ? anySignal([signal, controller.signal])
      : controller.signal
    let info: DiscoverResponse | null
    try {
      info = await probe(base, composite)
    } finally {
      window.clearTimeout(timer)
    }
    if (info) {
      const stored: StoredDiscovery = { baseUrl: base, info, ts: Date.now() }
      writeStoredDiscovery(stored)
      return stored
    }
  }
  return null
}

function anySignal(signals: AbortSignal[]): AbortSignal {
  const controller = new AbortController()
  for (const s of signals) {
    if (s.aborted) {
      controller.abort(s.reason)
      break
    }
    s.addEventListener('abort', () => controller.abort(s.reason), { once: true })
  }
  return controller.signal
}

/**
 * Returns the currently-known base URL for the API, if any. `null` means we
 * haven't discovered a service yet — callers should fall back to `getApiBase()`.
 */
export function getDiscoveredBase(): string | null {
  return readStoredDiscovery()?.baseUrl ?? null
}

/** Returns the cached `/api/discover` payload, or `null` if unknown. */
export function getDiscoveredInfo(): DiscoverResponse | null {
  return readStoredDiscovery()?.info ?? null
}

/** Forget the cached discovery result. Useful when the service appears offline. */
export function clearDiscoveredBase(): void {
  try {
    localStorage.removeItem(DISCOVERY_KEY)
  } catch {
    /* storage disabled */
  }
}

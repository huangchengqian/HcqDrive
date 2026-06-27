/**
 * Subtitle utilities.
 *
 * `srtToVtt(input)` is the primary export used when the server returns a
 * raw SubRip (.srt) document; browsers only understand WebVTT, so we
 * rewrite the small handful of differences locally instead of pulling in
 * a third-party library.
 */

const TIMECODE = '(\\d{2}:\\d{2}:\\d{2})[.,](\\d{3})'

/**
 * Convert an SRT document to WebVTT.
 *
 * Differences handled:
 *  - normalise CRLF/CR to LF
 *  - strip BOM if present
 *  - rewrite SRT timecodes (`,` decimal) into WebVTT (`.` decimal)
 *  - remove the leading numeric cue identifier that SRT requires but VTT forbids
 *  - prepend the mandatory `WEBVTT` signature
 */
export function srtToVtt(input: string): string {
  const cleaned = input.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')

  const cues: string[] = []
  const blocks = cleaned.split(/\n{2,}/)

  for (const raw of blocks) {
    const block = raw.trim()
    if (!block) continue
    const lines = block.split('\n')
    if (lines.length < 2) continue

    let idx = 0
    if (/^\d+$/.test(lines[0] ?? '')) idx = 1
    const timing = lines[idx]
    if (!timing || !timing.includes('-->')) continue

    const tc = new RegExp(TIMECODE, 'g')
    const vttTiming = timing.replace(tc, (_m, hms: string, ms: string) => `${hms}.${ms}`)
    const body = lines.slice(idx + 1).join('\n').trim()
    if (!body) continue
    cues.push(`${vttTiming}\n${body}`)
  }

  return `WEBVTT\n\n${cues.join('\n\n')}\n`
}

/** Detect whether a filename looks like a subtitle we know how to load. */
const SUBTITLE_EXTS = new Set(['srt', 'vtt', 'ass', 'ssa'])

export function isSubtitleName(name: string): boolean {
  const i = name.lastIndexOf('.')
  if (i <= 0) return false
  return SUBTITLE_EXTS.has(name.slice(i + 1).toLowerCase())
}

export function subtitleExt(name: string): string | null {
  const i = name.lastIndexOf('.')
  if (i <= 0) return null
  const ext = name.slice(i + 1).toLowerCase()
  return SUBTITLE_EXTS.has(ext) ? ext : null
}

/** Strip the extension from a video filename to derive the base. */
export function subtitleBase(videoPath: string): string {
  const i = videoPath.lastIndexOf('.')
  if (i <= 0) return videoPath
  return videoPath.slice(0, i)
}

export interface SubtitleCandidate {
  /** Display label, e.g. `movie.zh.srt` (without path). */
  label: string
  /** Path on the server. */
  path: string
  /** Extension in lowercase. */
  ext: string
}

/**
 * Build the ordered subtitle candidates for a video file.
 * Same-basename candidates come first (most likely to match), then any
 * other subtitles in the same directory.
 */
export function pickSubtitleCandidates(
  videoPath: string,
  siblings: ReadonlyArray<{ name: string; path: string }>,
): SubtitleCandidate[] {
  const base = subtitleBase(videoPath).split('/').pop() ?? ''
  const exts = ['srt', 'vtt', 'ass', 'ssa']
  const seen = new Set<string>()
  const out: SubtitleCandidate[] = []

  for (const ext of exts) {
    const target = `${base}.${ext}`
    const hit = siblings.find((s) => s.name.toLowerCase() === target)
    if (hit && !seen.has(hit.path)) {
      seen.add(hit.path)
      out.push({ label: hit.name, path: hit.path, ext })
    }
  }

  for (const sibling of siblings) {
    const ext = subtitleExt(sibling.name)
    if (!ext) continue
    if (seen.has(sibling.path)) continue
    seen.add(sibling.path)
    const stem = sibling.name.slice(0, sibling.name.length - ext.length - 1)
    out.push({
      label: stem === base ? `${ext.toUpperCase()}` : sibling.name,
      path: sibling.path,
      ext,
    })
  }

  return out
}
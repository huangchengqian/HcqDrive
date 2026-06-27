const SIZE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'] as const

export function formatBytes(bytes: number, fractionDigits = 1): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1) return '0 B'
  const i = Math.min(SIZE_UNITS.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / Math.pow(1024, i)
  const unit = SIZE_UNITS[i] ?? 'B'
  if (i === 0) return `${Math.round(value)} ${unit}`
  return `${value.toFixed(fractionDigits)} ${unit}`
}

const RELATIVE_FORMATTER = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' })
const ABSOLUTE_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

const RELATIVE_THRESHOLDS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 60 * 60 * 24 * 365],
  ['month', 60 * 60 * 24 * 30],
  ['day', 60 * 60 * 24],
  ['hour', 60 * 60],
  ['minute', 60],
  ['second', 1],
]

export function formatRelativeTime(timestamp: number, now: number = Date.now()): string {
  if (!timestamp || timestamp < 10000000000) return '-'
  const diff = (timestamp - now) / 1000
  const abs = Math.abs(diff)
  for (const [unit, secondsInUnit] of RELATIVE_THRESHOLDS) {
    if (abs >= secondsInUnit || unit === 'second') {
      return RELATIVE_FORMATTER.format(Math.round(diff / secondsInUnit), unit)
    }
  }
  return RELATIVE_FORMATTER.format(0, 'second')
}

export function formatAbsoluteTime(timestamp: number): string {
  if (!timestamp || timestamp < 10000000000) return '-'
  return ABSOLUTE_FORMATTER.format(new Date(timestamp))
}

export function formatPairCode(code: string): string {
  return code.replace(/\D/g, '').slice(0, 6)
}

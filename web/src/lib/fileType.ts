import type { FileType } from '@/types/api'

const IMAGE_EXT = new Set([
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif', 'bmp', 'svg', 'tiff', 'tif', 'avif',
])

const VIDEO_EXT = new Set([
  'mp4', 'mov', 'mkv', 'webm', 'avi', 'm4v', 'flv', 'wmv', '3gp',
])

const AUDIO_EXT = new Set([
  'mp3', 'wav', 'flac', 'm4a', 'ogg', 'opus', 'aac', 'wma', 'aiff',
])

const DOC_EXT = new Set([
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'odp', 'rtf', 'txt',
  'md', 'markdown', 'epub',
])

const ARCHIVE_EXT = new Set([
  'zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz', 'tbz2', 'iso',
])

const CODE_EXT = new Set([
  'js', 'ts', 'tsx', 'jsx', 'json', 'css', 'html', 'xml', 'yaml', 'yml',
  'py', 'rb', 'rs', 'go', 'java', 'kt', 'swift', 'c', 'cpp', 'h', 'hpp',
  'sh', 'bash', 'zsh', 'sql', 'toml',
])

export function fileTypeFor(name: string, mime: string | null | undefined): FileType {
  const ext = extensionOf(name)
  if (mime) {
    if (mime.startsWith('image/')) return 'image'
    if (mime.startsWith('video/')) return 'video'
    if (mime.startsWith('audio/')) return 'audio'
  }
  if (IMAGE_EXT.has(ext)) return 'image'
  if (VIDEO_EXT.has(ext)) return 'video'
  if (AUDIO_EXT.has(ext)) return 'audio'
  if (ARCHIVE_EXT.has(ext)) return 'archive'
  if (DOC_EXT.has(ext) || CODE_EXT.has(ext)) return 'document'
  return 'unknown'
}

export function extensionOf(name: string): string {
  const i = name.lastIndexOf('.')
  if (i <= 0 || i === name.length - 1) return ''
  return name.slice(i + 1).toLowerCase()
}

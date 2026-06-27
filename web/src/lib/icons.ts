import {
  FileText,
  Image as ImageIcon,
  Film,
  Music,
  Archive,
  Folder,
  FolderOpen,
  type LucideIcon,
} from 'lucide-vue-next'

const ICON_BY_EXT: Record<string, LucideIcon> = {
  jpg: ImageIcon,
  jpeg: ImageIcon,
  png: ImageIcon,
  gif: ImageIcon,
  webp: ImageIcon,
  heic: ImageIcon,
  bmp: ImageIcon,
  svg: ImageIcon,
  mp4: Film,
  mov: Film,
  mkv: Film,
  webm: Film,
  avi: Film,
  mp3: Music,
  wav: Music,
  flac: Music,
  m4a: Music,
  ogg: Music,
  zip: Archive,
  rar: Archive,
  '7z': Archive,
  tar: Archive,
  gz: Archive,
}

const COLOR_BY_EXT: Record<string, string> = {
  jpg: 'text-rose-500 dark:text-rose-400',
  jpeg: 'text-rose-500 dark:text-rose-400',
  png: 'text-rose-500 dark:text-rose-400',
  gif: 'text-rose-500 dark:text-rose-400',
  webp: 'text-rose-500 dark:text-rose-400',
  heic: 'text-rose-500 dark:text-rose-400',
  bmp: 'text-rose-500 dark:text-rose-400',
  svg: 'text-rose-500 dark:text-rose-400',
  mp4: 'text-violet-500 dark:text-violet-400',
  mov: 'text-violet-500 dark:text-violet-400',
  mkv: 'text-violet-500 dark:text-violet-400',
  webm: 'text-violet-500 dark:text-violet-400',
  avi: 'text-violet-500 dark:text-violet-400',
  mp3: 'text-amber-500 dark:text-amber-400',
  wav: 'text-amber-500 dark:text-amber-400',
  flac: 'text-amber-500 dark:text-amber-400',
  m4a: 'text-amber-500 dark:text-amber-400',
  ogg: 'text-amber-500 dark:text-amber-400',
  zip: 'text-yellow-600 dark:text-yellow-400',
  rar: 'text-yellow-600 dark:text-yellow-400',
  '7z': 'text-yellow-600 dark:text-yellow-400',
  tar: 'text-yellow-600 dark:text-yellow-400',
  gz: 'text-yellow-600 dark:text-yellow-400',
}

export function getFileIcon(name: string, isDirectory: boolean, isOpen = false): LucideIcon {
  if (isDirectory) return isOpen ? FolderOpen : Folder
  const ext = name.includes('.') ? name.split('.').pop()?.toLowerCase() ?? '' : ''
  return ICON_BY_EXT[ext] ?? FileText
}

export function getFileIconColor(name: string, isDirectory: boolean): string {
  if (isDirectory) return 'text-primary-500 dark:text-primary-400'
  const ext = name.includes('.') ? name.split('.').pop()?.toLowerCase() ?? '' : ''
  return COLOR_BY_EXT[ext] ?? 'text-surface-400 dark:text-surface-500'
}

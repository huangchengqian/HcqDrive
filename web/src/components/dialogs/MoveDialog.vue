<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ChevronRight, Folder, FolderOpen, Check } from 'lucide-vue-next'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import Spinner from '@/components/ui/Spinner.vue'

interface TreeEntry {
  name: string
  path: string
  hasChildren: boolean
  loading: boolean
  loaded: boolean
  children: TreeEntry[]
}

const props = defineProps<{
  open: boolean
  sources: Array<{ name: string; path: string }>
  initialPath?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', destination: string): void
}>()

const root = ref<TreeEntry>({
  name: '根目录',
  path: '/',
  hasChildren: true,
  loading: false,
  loaded: false,
  children: [],
})
const expanded = ref<Set<string>>(new Set(['/']))
const selected = ref<string>('/')
const error = ref<string | null>(null)

const selectionLabel = computed(() => {
  if (selected.value === '/') return '根目录'
  return selected.value
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      selected.value = props.initialPath && props.initialPath !== '/' ? dirnameOf(props.initialPath) : '/'
      if (root.value.loaded) return
      void loadChildren(root.value)
    }
  },
  { immediate: true },
)

async function loadChildren(node: TreeEntry): Promise<void> {
  if (node.loaded || node.loading) return
  node.loading = true
  try {
    const { filesApi } = await import('@/api/files')
    const { fileTypeFor } = await import('@/lib/fileType')
    const result = await filesApi.list({ path: node.path })
    node.children = result.entries
      .filter((e) => e.kind === 'directory' || fileTypeFor(e.name, e.mime) === 'folder')
      .filter((e) => !props.sources.some((s) => s.path === e.path))
      .map((e) => ({
        name: e.name,
        path: e.path,
        hasChildren: true,
        loading: false,
        loaded: false,
        children: [],
      }))
    node.loaded = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载目录失败'
  } finally {
    node.loading = false
  }
}

function dirnameOf(path: string): string {
  const idx = path.lastIndexOf('/')
  if (idx <= 0) return '/'
  return path.slice(0, idx)
}

async function toggle(node: TreeEntry): Promise<void> {
  if (expanded.value.has(node.path)) {
    const next = new Set(expanded.value)
    next.delete(node.path)
    expanded.value = next
  } else {
    expanded.value = new Set([...expanded.value, node.path])
    if (!node.loaded) await loadChildren(node)
  }
}

function pick(node: TreeEntry): void {
  selected.value = node.path
}

function isExpanded(path: string): boolean {
  return expanded.value.has(path)
}

function onSubmit(): void {
  if (!selected.value) return
  const path = selected.value === '/' ? '/' : `${selected.value}/`
  emit('submit', path)
}
</script>

<template>
  <BaseDialog :open="open" title="移动到" size="md" @close="emit('close')">
    <div class="-mx-1 max-h-[55vh] overflow-y-auto px-1">
      <p v-if="error" class="mb-2 text-xs text-danger-600 dark:text-danger-500">{{ error }}</p>
      <ul class="text-sm">
        <li>
          <button
            type="button"
            class="flex w-full items-center gap-1 rounded-md px-2 py-1.5 text-left transition hover:bg-surface-100 dark:hover:bg-surface-800"
            @click="toggle(root)"
            @dblclick="toggle(root)"
          >
            <ChevronRight
              :class="[
                'h-3.5 w-3.5 shrink-0 text-surface-400 transition',
                isExpanded('/') ? 'rotate-90' : '',
              ]"
            />
            <Folder v-if="!isExpanded('/')" :size="14" class="text-primary-500" />
            <FolderOpen v-else :size="14" class="text-primary-500" />
            <span class="ml-1 flex-1 font-medium text-surface-700 dark:text-surface-200">根目录</span>
            <Check
              v-if="selected === '/'"
              :size="14"
              class="text-primary-500"
            />
          </button>
          <ul v-if="isExpanded('/')" class="ml-4 border-l border-border-light pl-2 dark:border-border-dark">
            <li v-if="root.loading" class="flex items-center gap-2 py-1 text-xs text-surface-500">
              <Spinner size="sm" inline /> 加载中…
            </li>
            <li v-else-if="root.loaded && root.children.length === 0" class="py-1 text-xs text-surface-400">
              空目录
            </li>
            <template v-else>
              <li v-for="child in root.children" :key="child.path" class="ml-1">
                <button
                  type="button"
                  class="flex w-full items-center gap-1 rounded-md px-2 py-1.5 text-left text-sm transition hover:bg-surface-100 dark:hover:bg-surface-800"
                  @click="toggle(child)"
                  @dblclick="toggle(child)"
                >
                  <ChevronRight
                    :class="[
                      'h-3.5 w-3.5 shrink-0 text-surface-400 transition',
                      isExpanded(child.path) ? 'rotate-90' : '',
                    ]"
                  />
                  <Folder v-if="!isExpanded(child.path)" :size="13" class="text-primary-500" />
                  <FolderOpen v-else :size="13" class="text-primary-500" />
                  <span class="ml-1 flex-1 truncate text-surface-700 dark:text-surface-200">
                    {{ child.name }}
                  </span>
                  <Check
                    v-if="selected === child.path"
                    :size="13"
                    class="text-primary-500"
                  />
                </button>
                <ul
                  v-if="isExpanded(child.path)"
                  class="ml-3 border-l border-border-light pl-2 dark:border-border-dark"
                >
                  <li v-if="child.loading" class="flex items-center gap-2 py-1 text-xs text-surface-500">
                    <Spinner size="sm" inline /> 加载中…
                  </li>
                  <li
                    v-else-if="child.loaded && child.children.length === 0"
                    class="py-1 text-xs text-surface-400"
                  >
                    空目录
                  </li>
                  <li v-for="grand in child.children" :key="grand.path" class="ml-1">
                    <button
                      type="button"
                      class="flex w-full items-center gap-1 rounded-md px-2 py-1.5 text-left text-sm transition hover:bg-surface-100 dark:hover:bg-surface-800"
                      @click="pick(grand)"
                    >
                      <Folder :size="13" class="text-primary-500" />
                      <span class="ml-1 flex-1 truncate text-surface-700 dark:text-surface-200">
                        {{ grand.name }}
                      </span>
                      <Check
                        v-if="selected === grand.path"
                        :size="13"
                        class="text-primary-500"
                      />
                    </button>
                  </li>
                </ul>
              </li>
            </template>
          </ul>
        </li>
      </ul>
    </div>
    <p class="mt-2 text-xs text-surface-500 dark:text-surface-400">
      移动到:<span class="font-medium text-surface-700 dark:text-surface-200">{{ selectionLabel }}</span>
    </p>
    <template #footer>
      <BaseButton variant="ghost" size="md" :disabled="loading" @click="emit('close')">取消</BaseButton>
      <BaseButton variant="primary" size="md" :loading="loading" @click="onSubmit">
        移动到此处
      </BaseButton>
    </template>
  </BaseDialog>
</template>

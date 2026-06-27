<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'

const props = defineProps<{
  current: number
  total: number
  pageSize: number
}>()

const emit = defineEmits<{ (e: 'change', page: number): void }>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const page = computed(() => Math.min(props.current, totalPages.value))

function go(p: number): void {
  if (p < 1 || p > totalPages.value) return
  emit('change', p)
}
</script>

<template>
  <div v-if="total > pageSize" class="flex items-center justify-between gap-2 border-t border-border-light px-3 py-2 text-xs dark:border-border-dark">
    <span class="text-surface-500 dark:text-surface-400">
      第 {{ page }} / {{ totalPages }} 页
    </span>
    <div class="flex items-center gap-1">
      <BaseButton variant="ghost" size="sm" :disabled="page <= 1" @click="go(1)">
        <template #icon>
          <ChevronsLeft :size="14" />
        </template>
      </BaseButton>
      <BaseButton variant="ghost" size="sm" :disabled="page <= 1" @click="go(page - 1)">
        <template #icon>
          <ChevronLeft :size="14" />
        </template>
      </BaseButton>
      <BaseButton variant="ghost" size="sm" :disabled="page >= totalPages" @click="go(page + 1)">
        <template #icon>
          <ChevronRight :size="14" />
        </template>
      </BaseButton>
      <BaseButton variant="ghost" size="sm" :disabled="page >= totalPages" @click="go(totalPages)">
        <template #icon>
          <ChevronsRight :size="14" />
        </template>
      </BaseButton>
    </div>
  </div>
</template>

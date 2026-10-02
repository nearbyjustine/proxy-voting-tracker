<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { DeadlineStatus } from '@/api/types'

defineProps<{ status: DeadlineStatus }>()
const { t } = useI18n()
</script>

<template>
  <span
    class="cond inline-flex items-center gap-1.5 rounded-[4px] px-2 py-0.5 text-[0.78rem] font-bold uppercase tracking-wider"
    :class="{
      'bg-lit text-[#2b2000]': status === 'CLOSING_SOON',
      'bg-panel-2 text-ink-2': status === 'CLOSED',
      'bg-for-wash text-for': status === 'OPEN',
    }"
  >
    <span v-if="status === 'CLOSING_SOON'" class="size-1.5 rounded-full bg-[#2b2000] motion-safe:animate-pulse" aria-hidden="true" />
    {{ t(`status.${status}`) }}
  </span>
</template>

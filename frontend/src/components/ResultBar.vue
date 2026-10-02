<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { motion } from 'motion-v'
import type { Decision } from '@/api/types'

/**
 * The segmented results bar: one segment per proposal. A segment is OUTLINED in the policy's call
 * until the organisation votes, then FILLS with the vote cast, like a race being declared.
 */
export interface Segment {
  id: number
  call: Decision | null
  vote: Decision | null
}
defineProps<{ segments: Segment[]; height?: string }>()
const { t } = useI18n()
const color = (d: Decision | null) => (d === 'FOR' ? 'var(--for)' : d === 'AGAINST' ? 'var(--against)' : d === 'ABSTAIN' ? 'var(--abstain)' : 'var(--rule)')
</script>

<template>
  <div
    class="flex w-full gap-[3px]"
    :class="height ?? 'h-3'"
    role="img"
    :aria-label="t('meetings.barLabel', { voted: segments.filter((s) => s.vote).length, total: segments.length })"
  >
    <div v-for="s in segments" :key="s.id" class="relative flex-1 overflow-hidden rounded-[3px]" :style="{ boxShadow: `inset 0 0 0 1.5px ${color(s.call)}` }">
      <motion.div
        class="absolute inset-0 origin-left"
        :style="{ background: color(s.vote) }"
        :initial="false"
        :animate="{ scaleX: s.vote ? 1 : 0 }"
        :transition="{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }"
      />
    </div>
  </div>
</template>

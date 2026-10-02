<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { motion } from 'motion-v'
import { ChevronRight } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import type { MeetingSummary } from '@/api/types'
import { formatInZone, formatLocal, marketDiffers } from '@/utils/time'
import Countdown from '@/components/Countdown.vue'
import DeadlineBadge from '@/components/DeadlineBadge.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'

const { t, locale } = useI18n()
const meetings = ref<MeetingSummary[] | null>(null)
const error = ref<Error | null>(null)

onMounted(async () => {
  try {
    meetings.value = await Api.meetings()
  } catch (e) {
    error.value = e as Error
  }
})

const city = (zone: string) => zone.split('/').pop()!.replace(/_/g, ' ')
</script>

<template>
  <header class="mb-5 flex flex-wrap items-end justify-between gap-3">
    <div>
      <h1 class="cond text-4xl font-bold uppercase tracking-tight md:text-5xl">{{ t('meetings.title') }}</h1>
      <p class="mt-1 text-ink-2">{{ t('meetings.lead') }}</p>
    </div>
  </header>

  <ErrorBanner :error="error" />

  <div class="board overflow-hidden">
    <div class="hidden grid-cols-[minmax(220px,1.6fr)_minmax(120px,0.8fr)_minmax(210px,1.2fr)_120px_minmax(150px,1fr)_24px] gap-4 border-b border-rule bg-panel-2 px-5 py-2.5 lg:grid">
      <span class="label">{{ t('meetings.company') }}</span>
      <span class="label">{{ t('meetings.market') }}</span>
      <span class="label">{{ t('meetings.deadline') }}</span>
      <span class="label">{{ t('meetings.status') }}</span>
      <span class="label">{{ t('meetings.count') }}</span>
      <span />
    </div>

    <div v-if="!meetings && !error" class="space-y-3 p-5" aria-busy="true">
      <div v-for="n in 5" :key="n" class="skeleton h-14" />
    </div>

    <p v-else-if="meetings && meetings.length === 0" class="px-5 py-10 text-center text-ink-2">{{ t('meetings.empty') }}</p>

    <motion.div
      v-for="(m, i) in meetings ?? []"
      :key="m.id"
      :initial="{ opacity: 0, y: 6 }"
      :animate="{ opacity: 1, y: 0 }"
      :transition="{ delay: Math.min(i, 8) * 0.035, duration: 0.25, ease: [0.16, 1, 0.3, 1] }"
    >
      <RouterLink
        :to="`/meetings/${m.id}`"
        class="group grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 border-b border-rule px-5 py-4 transition-colors last:border-b-0 hover:bg-panel-2 lg:grid-cols-[minmax(220px,1.6fr)_minmax(120px,0.8fr)_minmax(210px,1.2fr)_120px_minmax(150px,1fr)_24px]"
        :class="{ 'bg-lit-wash hover:!bg-lit-wash': m.deadlineStatus === 'CLOSING_SOON', 'opacity-60': m.deadlineStatus === 'CLOSED' }"
      >
        <div class="flex min-w-0 items-baseline gap-3">
          <span class="cond w-[4.5rem] shrink-0 text-[1.7rem] font-bold leading-none">{{ m.ticker }}</span>
          <span class="min-w-0">
            <span class="block font-semibold leading-tight">{{ m.companyName }}</span>
            <span class="whitespace-nowrap text-sm text-ink-2">{{ m.meetingType }} · {{ m.meetingDate }}</span>
          </span>
        </div>
        <div class="justify-self-end lg:hidden"><DeadlineBadge :status="m.deadlineStatus" /></div>
        <!-- On phones market and deadline share one line; on desktop they become their own columns -->
        <div class="col-span-2 flex items-end justify-between gap-3 lg:contents">
          <div class="text-sm">
            <span class="cond text-base font-bold">{{ m.country }}</span>
            <span class="text-ink-2"> · {{ city(m.marketTimeZone) }}</span>
          </div>
          <div class="text-right lg:text-left">
            <Countdown :deadline="m.voteDeadline" />
            <div class="mt-1 text-xs text-ink-2">
              {{ formatLocal(m.voteDeadline, locale) }}
              <span v-if="marketDiffers(m.voteDeadline, m.marketTimeZone)" class="hidden xl:block">{{ formatInZone(m.voteDeadline, m.marketTimeZone, locale) }} · {{ t('meetings.marketTime') }}</span>
            </div>
          </div>
        </div>
        <div class="hidden lg:block"><DeadlineBadge :status="m.deadlineStatus" /></div>
        <div class="col-span-2 lg:col-span-1">
          <div class="flex h-2.5 gap-[3px]" role="img" :aria-label="t('meetings.barLabel', { voted: m.votedCount, total: m.proposalCount })">
            <span
              v-for="n in m.proposalCount"
              :key="n"
              class="flex-1 rounded-[2px] shadow-[inset_0_0_0_1.5px_var(--rule)]"
              :class="n <= m.votedCount ? 'bg-ink shadow-none' : ''"
            />
          </div>
          <span class="cond mt-1 block text-sm font-semibold uppercase tracking-wide text-ink-2">{{ t('meetings.votesIn', { voted: m.votedCount, total: m.proposalCount }) }}</span>
        </div>
        <ChevronRight class="hidden size-5 text-ink-2 transition-transform group-hover:translate-x-0.5 lg:block" aria-hidden="true" />
      </RouterLink>
    </motion.div>
  </div>
</template>

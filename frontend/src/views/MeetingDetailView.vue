<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnimatePresence, motion } from 'motion-v'
import { ArrowLeft, Check, LoaderCircle, Minus, ScrollText, TriangleAlert, X } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import type { Decision, MeetingDetail, Proposal } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatInZone, formatLocal, marketDiffers } from '@/utils/time'
import Countdown from '@/components/Countdown.vue'
import DeadlineBadge from '@/components/DeadlineBadge.vue'
import DecisionMark from '@/components/DecisionMark.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import ResultBar from '@/components/ResultBar.vue'

const props = defineProps<{ id: string }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const detail = ref<MeetingDetail | null>(null)
const error = ref<Error | null>(null)
const busy = ref<number | null>(null)
const justVoted = ref<number | null>(null)
const decisions: { d: Decision; icon: unknown }[] = [
  { d: 'FOR', icon: Check },
  { d: 'AGAINST', icon: X },
  { d: 'ABSTAIN', icon: Minus },
]

const closed = computed(() => detail.value?.meeting.deadlineStatus === 'CLOSED')
const canVote = computed(() => auth.hasRole('VOTER') && !closed.value)
const segments = computed(() => (detail.value?.proposals ?? []).map((p) => ({ id: p.id, call: p.recommendation?.decision ?? null, vote: p.vote?.decision ?? null })))
const voted = computed(() => segments.value.filter((s) => s.vote).length)

async function load() {
  try {
    detail.value = await Api.meeting(Number(props.id))
  } catch (e) {
    error.value = e as Error
  }
}

async function vote(p: Proposal, decision: Decision) {
  if (p.vote?.decision === decision) return
  busy.value = p.id
  error.value = null
  try {
    const r = await Api.vote(p.id, decision, p.vote?.version)
    p.vote = { decision: r.decision, submittedBy: r.submittedBy, submittedAt: r.submittedAt, version: r.version }
    justVoted.value = p.id
  } catch (e) {
    error.value = e as Error
    await load()
  } finally {
    busy.value = null
  }
}

async function summarize(p: Proposal) {
  busy.value = p.id
  try {
    const s = await Api.summarize(p.id)
    p.aiSummary = s.summary
    p.aiSummarySource = s.source
  } catch (e) {
    error.value = e as Error
  } finally {
    busy.value = null
  }
}

const fill = (d: Decision) =>
  ({
    FOR: 'bg-for text-white dark:text-[#06102a] border-for',
    AGAINST: 'bg-against text-white dark:text-[#2a0904] border-against',
    ABSTAIN: 'bg-abstain text-white dark:text-[#0b1220] border-abstain',
  })[d]

onMounted(load)
</script>

<template>
  <RouterLink to="/meetings" class="mb-4 inline-flex items-center gap-1.5 text-sm text-ink-2 hover:text-ink">
    <ArrowLeft class="size-4" aria-hidden="true" />{{ t('common.back') }}
  </RouterLink>

  <div v-if="!detail && !error" class="space-y-4" aria-busy="true">
    <div class="skeleton h-40" />
    <div class="skeleton h-56" />
  </div>
  <ErrorBanner :error="error" />

  <template v-if="detail">
    <!-- The race header -->
    <section class="board overflow-hidden" :class="{ 'ring-2 ring-lit': detail.meeting.deadlineStatus === 'CLOSING_SOON' }">
      <div class="grid gap-6 p-5 md:grid-cols-[1fr_auto] md:p-7">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <DeadlineBadge :status="detail.meeting.deadlineStatus" />
            <span class="text-sm text-ink-2">{{ detail.meeting.meetingType }} · {{ detail.meeting.meetingDate }} · {{ detail.meeting.country }}</span>
          </div>
          <h1 class="mt-3 flex flex-wrap items-baseline gap-x-4 gap-y-1">
            <span class="cond text-6xl font-bold leading-none tracking-tight md:text-7xl">{{ detail.meeting.ticker }}</span>
            <span class="text-2xl font-semibold md:text-3xl">{{ detail.meeting.companyName }}</span>
          </h1>
        </div>
        <div class="md:text-right">
          <Countdown :deadline="detail.meeting.voteDeadline" size="lg" />
          <dl class="mt-2 space-y-0.5 text-sm text-ink-2">
            <div><dd class="inline">{{ formatLocal(detail.meeting.voteDeadline, locale) }}</dd> <dt class="inline">· {{ t('meetings.yourTime') }}</dt></div>
            <div v-if="marketDiffers(detail.meeting.voteDeadline, detail.meeting.marketTimeZone)">
              <dd class="inline">{{ formatInZone(detail.meeting.voteDeadline, detail.meeting.marketTimeZone, locale) }}</dd> <dt class="inline">· {{ t('meetings.marketTime') }}</dt>
            </div>
          </dl>
        </div>
      </div>
      <div class="flex items-center gap-4 border-t border-rule bg-panel-2 px-5 py-3.5 md:px-7">
        <ResultBar :segments="segments" height="h-4" />
        <span class="cond shrink-0 text-xl font-bold uppercase">{{ t('meetings.votesIn', { voted, total: segments.length }) }}</span>
      </div>
    </section>

    <p v-if="closed" class="mt-4 rounded-lg bg-panel-2 px-4 py-3 text-sm text-ink-2">{{ t('meetings.closedNote') }}</p>

    <!-- Proposals -->
    <article v-for="p in detail.proposals" :key="p.id" class="board mt-4 grid gap-5 p-5 md:grid-cols-[3.5rem_1fr_minmax(260px,320px)] md:p-6">
      <div class="cond text-5xl font-bold leading-none text-ink-2" aria-hidden="true">{{ p.seq }}</div>

      <div class="min-w-0">
        <div class="label">{{ t(`category.${p.category}`) }}</div>
        <h2 class="mt-1 text-xl font-semibold leading-snug">{{ p.title }}</h2>
        <p v-if="p.description" class="mt-2 max-w-[68ch] text-ink-2">{{ p.description }}</p>

        <div v-if="p.payScore !== null || p.boardIndependencePct !== null" class="mt-4 grid max-w-md gap-3">
          <div v-if="p.payScore !== null">
            <div class="flex justify-between text-sm"><span class="text-ink-2">{{ t('meetings.payScore') }}</span><span class="cond text-lg font-bold">{{ p.payScore }}<span class="text-ink-2">/100</span></span></div>
            <div class="mt-1 h-1.5 rounded-full bg-panel-2"><div class="h-full rounded-full bg-ink" :style="{ width: p.payScore + '%' }" /></div>
          </div>
          <div v-if="p.boardIndependencePct !== null">
            <div class="flex justify-between text-sm"><span class="text-ink-2">{{ t('meetings.independence') }}</span><span class="cond text-lg font-bold">{{ p.boardIndependencePct }}%</span></div>
            <div class="mt-1 h-1.5 rounded-full bg-panel-2"><div class="h-full rounded-full bg-ink" :style="{ width: p.boardIndependencePct + '%' }" /></div>
          </div>
        </div>

        <AnimatePresence>
          <motion.div
            v-if="p.aiSummary"
            class="mt-4 max-w-[68ch] rounded-lg bg-panel-2 px-4 py-3 text-[0.95rem]"
            :initial="{ opacity: 0, height: 0 }"
            :animate="{ opacity: 1, height: 'auto' }"
            :transition="{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }"
          >
            <div class="label mb-1 flex items-center gap-1.5"><ScrollText class="size-3.5" aria-hidden="true" />{{ t('meetings.summary') }} · {{ p.aiSummarySource }}</div>
            {{ p.aiSummary }}
          </motion.div>
        </AnimatePresence>
        <button v-if="!p.aiSummary && auth.hasRole('ANALYST')" class="btn btn-ghost mt-4 py-1.5 text-sm" :disabled="busy === p.id" @click="summarize(p)">
          <LoaderCircle v-if="busy === p.id" class="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          <ScrollText v-else class="size-4" aria-hidden="true" />{{ t('meetings.summarize') }}
        </button>
      </div>

      <aside class="flex flex-col gap-4 border-t border-rule pt-4 md:border-l md:border-t-0 md:pl-5 md:pt-0">
        <div v-if="p.recommendation">
          <div class="label mb-1.5">{{ t('meetings.recommendation') }}</div>
          <DecisionMark :decision="p.recommendation.decision" size="lg" />
          <p class="mt-2 text-sm leading-relaxed text-ink-2">{{ p.recommendation.rationale }}</p>
        </div>
        <div class="flex items-center gap-2 text-sm">
          <span class="label">{{ t('meetings.board') }}</span>
          <DecisionMark :decision="p.boardRecommendation" />
        </div>

        <div>
          <div class="label mb-1.5">{{ t('meetings.yourVote') }}</div>
          <div class="relative">
            <div class="grid grid-cols-3 gap-1.5" role="radiogroup" :aria-label="t('meetings.yourVote')">
              <button
                v-for="o in decisions"
                :key="o.d"
                role="radio"
                :aria-checked="p.vote?.decision === o.d"
                class="cond flex items-center justify-center gap-1 rounded-md border px-2 py-2 text-[0.95rem] font-bold uppercase tracking-wide transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50"
                :class="p.vote?.decision === o.d ? fill(o.d) : 'border-rule bg-panel hover:bg-panel-2'"
                :disabled="!canVote || busy === p.id"
                @click="vote(p, o.d)"
              >
                <component :is="o.icon" class="size-4" stroke-width="3" aria-hidden="true" />{{ t(`decision.${o.d}`) }}
              </button>
            </div>
            <!-- The signature moment: the vote is stamped in. -->
            <AnimatePresence>
              <motion.span
                v-if="justVoted === p.id && p.vote"
                :key="p.vote.decision + p.vote.version"
                class="cond pointer-events-none absolute -right-2 -top-7 rounded-[4px] border-2 px-2 py-0.5 text-sm font-bold uppercase tracking-[0.12em]"
                :class="{ FOR: 'border-for text-for', AGAINST: 'border-against text-against', ABSTAIN: 'border-abstain text-abstain' }[p.vote.decision]"
                :style="{ background: 'var(--panel)' }"
                :initial="{ opacity: 0, scale: 1.8, rotate: -14 }"
                :animate="{ opacity: 1, scale: 1, rotate: -6 }"
                :exit="{ opacity: 0 }"
                :transition="{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }"
              >
                {{ t('meetings.voteIn') }}
              </motion.span>
            </AnimatePresence>
          </div>
          <p v-if="p.vote" class="mt-2 text-xs text-ink-2">{{ t('meetings.votedBy', { user: p.vote.submittedBy }) }}</p>
          <p v-if="p.vote && p.recommendation && p.vote.decision !== p.recommendation.decision" class="mt-1 flex items-center gap-1.5 text-xs font-semibold text-against">
            <TriangleAlert class="size-3.5" aria-hidden="true" />{{ t('meetings.againstPolicy') }}
          </p>
        </div>
      </aside>
    </article>
  </template>
</template>

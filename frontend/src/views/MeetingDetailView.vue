<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import type { Decision, MeetingDetail, Proposal } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatInZone, formatLocal } from '@/utils/time'
import DecisionBadge from '@/components/DecisionBadge.vue'
import DeadlineBadge from '@/components/DeadlineBadge.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'

const props = defineProps<{ id: string }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const detail = ref<MeetingDetail | null>(null)
const error = ref<Error | null>(null)
const busy = ref<number | null>(null)
const decisions: Decision[] = ['FOR', 'AGAINST', 'ABSTAIN']

const closed = computed(() => detail.value?.meeting.deadlineStatus === 'CLOSED')
const canVote = computed(() => auth.hasRole('VOTER') && !closed.value)

async function load() {
  try {
    detail.value = await Api.meeting(Number(props.id))
  } catch (e) {
    error.value = e as Error
  }
}

async function vote(p: Proposal, decision: Decision) {
  busy.value = p.id
  error.value = null
  try {
    await Api.vote(p.id, decision, p.vote?.version)
    await load()
  } catch (e) {
    error.value = e as Error
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

onMounted(load)
</script>

<template>
  <section v-if="detail" class="card">
    <div class="row between">
      <h1>{{ detail.meeting.ticker }} · {{ detail.meeting.companyName }} <span class="muted">{{ detail.meeting.meetingType }}</span></h1>
      <DeadlineBadge :status="detail.meeting.deadlineStatus" />
    </div>
    <p class="muted">
      {{ t('meetings.deadline') }}: {{ formatLocal(detail.meeting.voteDeadline, locale) }} ({{ t('meetings.yourTime') }}) ·
      {{ formatInZone(detail.meeting.voteDeadline, detail.meeting.marketTimeZone, locale) }} ({{ t('meetings.marketTime') }})
    </p>
    <p v-if="closed" class="banner warn">{{ t('meetings.closedNote') }}</p>
    <ErrorBanner :error="error" />
  </section>

  <article v-for="p in detail?.proposals ?? []" :key="p.id" class="card proposal">
    <header class="row between">
      <div>
        <span class="muted">#{{ p.seq }} · {{ t(`category.${p.category}`) }}</span>
        <h2>{{ p.title }}</h2>
      </div>
      <div class="facts">
        <span v-if="p.payScore !== null">{{ t('meetings.payScore') }}: <strong>{{ p.payScore }}</strong>/100</span>
        <span v-if="p.boardIndependencePct !== null">{{ t('meetings.independence') }}: <strong>{{ p.boardIndependencePct }}%</strong></span>
      </div>
    </header>
    <p>{{ p.description }}</p>
    <p v-if="p.aiSummary" class="summary"><strong>{{ t('meetings.summary') }}</strong> <small class="muted">({{ p.aiSummarySource }})</small>: {{ p.aiSummary }}</p>
    <button v-else-if="auth.hasRole('ANALYST')" class="ghost small" :disabled="busy === p.id" @click="summarize(p)">{{ t('meetings.summarize') }}</button>

    <div class="grid3">
      <div><div class="label">{{ t('meetings.board') }}</div><DecisionBadge :decision="p.boardRecommendation" /></div>
      <div v-if="p.recommendation">
        <div class="label">{{ t('meetings.recommendation') }}</div>
        <DecisionBadge :decision="p.recommendation.decision" />
        <div class="rationale">{{ p.recommendation.rationale }}</div>
      </div>
      <div>
        <div class="label">{{ t('meetings.yourVote') }}</div>
        <div class="votes">
          <button
            v-for="d in decisions" :key="d" class="small" :class="{ ghost: p.vote?.decision !== d }"
            :disabled="!canVote || busy === p.id" @click="vote(p, d)">{{ t(`decision.${d}`) }}</button>
        </div>
        <small v-if="p.vote" class="muted">{{ t('meetings.votedBy', { user: p.vote.submittedBy }) }}</small>
        <small v-if="p.vote && p.recommendation && p.vote.decision !== p.recommendation.decision" class="flag"> · {{ t('meetings.againstPolicy') }}</small>
      </div>
    </div>
  </article>
</template>

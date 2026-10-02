<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import type { MeetingSummary } from '@/api/types'
import { formatInZone, formatLocal, relative } from '@/utils/time'
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
</script>

<template>
  <section class="card">
    <h1>{{ t('meetings.title') }}</h1>
    <ErrorBanner :error="error" />
    <p v-if="!meetings && !error" class="muted">{{ t('common.loading') }}</p>
    <div v-if="meetings" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>{{ t('meetings.company') }}</th>
            <th>{{ t('meetings.market') }}</th>
            <th>{{ t('meetings.deadline') }}</th>
            <th>{{ t('meetings.status') }}</th>
            <th>{{ t('meetings.progress') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in meetings" :key="m.id">
            <td><strong>{{ m.ticker }}</strong> {{ m.companyName }} <span class="muted">· {{ m.meetingType }}</span></td>
            <td>{{ m.country }}</td>
            <td>
              <div>{{ formatLocal(m.voteDeadline, locale) }} <span class="muted">({{ relative(m.voteDeadline, locale) }})</span></div>
              <small class="muted">{{ formatInZone(m.voteDeadline, m.marketTimeZone, locale) }} · {{ t('meetings.marketTime') }}</small>
            </td>
            <td><DeadlineBadge :status="m.deadlineStatus" /></td>
            <td><progress :value="m.votedCount" :max="m.proposalCount"></progress> {{ m.votedCount }}/{{ m.proposalCount }}</td>
            <td><RouterLink class="button ghost" :to="`/meetings/${m.id}`">{{ t('meetings.open') }}</RouterLink></td>
          </tr>
          <tr v-if="meetings.length === 0"><td colspan="6" class="muted">{{ t('common.none') }}</td></tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { motion } from 'motion-v'
import { ArrowRight, LogIn } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import type { MeetingSummary } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import Countdown from '@/components/Countdown.vue'
import DeadlineBadge from '@/components/DeadlineBadge.vue'

const auth = useAuthStore()
const { t } = useI18n()
const meetings = ref<MeetingSummary[] | null>(null)

watch(
  () => auth.isAuthenticated && auth.hasRole('ANALYST', 'VOTER'),
  async (canSee) => {
    meetings.value = canSee ? await Api.meetings().catch(() => null) : null
  },
  { immediate: true },
)

const open = computed(() => (meetings.value ?? []).filter((m) => m.deadlineStatus !== 'CLOSED'))
const closingSoon = computed(() => open.value.filter((m) => m.deadlineStatus === 'CLOSING_SOON'))
const votesNeeded = computed(() => open.value.reduce((n, m) => n + (m.proposalCount - m.votedCount), 0))

const demoUsers = [
  { user: 'sam', org: 'Stewardship Pension Fund', roles: 'Analyst · Voter · Policy admin · Ops' },
  { user: 'vic', org: 'Stewardship Pension Fund', roles: 'Voter' },
  { user: 'gina', org: 'Growth Capital Partners', roles: 'Analyst · Voter · Policy admin' },
]
</script>

<template>
  <section v-if="!auth.isAuthenticated" class="grid gap-8 py-6 md:grid-cols-[1.1fr_1fr] md:items-end md:py-14">
    <div>
      <h1 class="cond text-5xl font-bold uppercase leading-[0.95] tracking-tight md:text-7xl">{{ t('home.signedOutTitle') }}</h1>
      <p class="mt-5 max-w-[52ch] text-lg text-ink-2">{{ t('home.intro') }}</p>
      <button class="btn mt-7 px-5 py-2.5 text-base" @click="auth.login('/')">
        <LogIn class="size-4" aria-hidden="true" />{{ t('app.signIn') }}
      </button>
    </div>
    <div class="board overflow-hidden">
      <p class="border-b border-rule px-4 py-3 text-sm text-ink-2">{{ t('home.signedOut') }}</p>
      <table class="results">
        <thead>
          <tr>
            <th class="label">{{ t('home.demo.user') }}</th>
            <th class="label">{{ t('home.demo.org') }}</th>
            <th class="label">{{ t('home.demo.roles') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in demoUsers" :key="d.user">
            <td class="cond text-lg font-bold">{{ d.user }}</td>
            <td>{{ d.org }}</td>
            <td class="text-ink-2">{{ d.roles }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <section v-else class="py-2 md:py-6">
    <h1 class="cond text-4xl font-bold uppercase tracking-tight md:text-5xl">{{ t('home.welcome', { name: auth.displayName }) }}</h1>
    <p class="mt-3 max-w-[60ch] text-ink-2">{{ t('home.intro') }}</p>

    <template v-if="meetings">
      <p class="cond mt-6 text-2xl font-semibold uppercase tracking-wide">
        <span :class="closingSoon.length ? 'bg-lit px-1.5 text-[#2b2000]' : ''">{{ t('home.closingSoon', closingSoon.length) }}</span>
        <span class="mx-2 text-ink-2">·</span>
        {{ t('home.votesNeeded', votesNeeded) }}
      </p>
      <div v-if="closingSoon.length" class="board mt-5 divide-y divide-rule">
        <motion.div
          v-for="(m, i) in closingSoon"
          :key="m.id"
          :initial="{ opacity: 0, x: -8 }"
          :animate="{ opacity: 1, x: 0 }"
          :transition="{ delay: i * 0.05, duration: 0.25 }"
        >
          <RouterLink :to="`/meetings/${m.id}`" class="flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3.5 hover:bg-panel-2">
            <span class="cond w-20 text-2xl font-bold">{{ m.ticker }}</span>
            <span class="min-w-40 flex-1 font-medium">{{ m.companyName }}</span>
            <DeadlineBadge :status="m.deadlineStatus" />
            <Countdown :deadline="m.voteDeadline" />
          </RouterLink>
        </motion.div>
      </div>
      <RouterLink to="/meetings" class="btn mt-6">{{ t('home.openBoard') }}<ArrowRight class="size-4" aria-hidden="true" /></RouterLink>
    </template>
  </section>
</template>

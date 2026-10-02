<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { countdownParts, pad, useNow } from '@/utils/clock'

/** The broadcast clock: days + HH:MM:SS to the vote deadline, from one shared ticking "now". */
const props = withDefaults(defineProps<{ deadline: string; size?: 'sm' | 'lg' }>(), { size: 'sm' })
const { t } = useI18n()
const now = useNow()
const c = computed(() => countdownParts(props.deadline, now.value))
</script>

<template>
  <span v-if="c.closed" class="cond font-bold uppercase text-ink-2" :class="size === 'lg' ? 'text-4xl' : 'text-lg'">{{ t('status.CLOSED') }}</span>
  <span
    v-else
    class="cond inline-flex items-baseline gap-1 font-bold leading-none"
    :class="size === 'lg' ? 'text-5xl md:text-6xl' : 'text-2xl'"
    role="timer"
    :aria-label="t('meetings.timeLeft', { d: c.days, h: c.hours, m: c.minutes })"
  >
    <template v-if="c.days > 0">{{ c.days }}<span class="text-[0.45em] font-semibold text-ink-2">{{ t('meetings.d') }}</span></template>
    <span>{{ pad(c.hours) }}:{{ pad(c.minutes) }}</span><span class="text-[0.55em] text-ink-2">:{{ pad(c.seconds) }}</span>
  </span>
</template>

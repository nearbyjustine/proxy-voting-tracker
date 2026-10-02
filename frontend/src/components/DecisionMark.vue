<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Check, Minus, X } from 'lucide-vue-next'
import type { Decision } from '@/api/types'

/** A decision always reads by glyph AND word, never by colour alone. */
withDefaults(defineProps<{ decision: Decision; size?: 'sm' | 'lg'; solid?: boolean }>(), { size: 'sm', solid: false })
const { t } = useI18n()
const icon = { FOR: Check, AGAINST: X, ABSTAIN: Minus }
</script>

<template>
  <span
    class="cond inline-flex items-center gap-1.5 rounded-[5px] font-semibold uppercase"
    :class="[
      size === 'lg' ? 'px-2.5 py-1 text-base tracking-wide' : 'px-1.5 py-0.5 text-[0.8rem] tracking-wider',
      solid
        ? { FOR: 'bg-for text-white dark:text-[#06102a]', AGAINST: 'bg-against text-white dark:text-[#2a0904]', ABSTAIN: 'bg-abstain text-white dark:text-[#0b1220]' }[decision]
        : { FOR: 'bg-for-wash text-for', AGAINST: 'bg-against-wash text-against', ABSTAIN: 'bg-abstain-wash text-abstain' }[decision],
    ]"
  >
    <component :is="icon[decision]" :class="size === 'lg' ? 'size-4' : 'size-3.5'" stroke-width="3" aria-hidden="true" />
    {{ t(`decision.${decision}`) }}
  </span>
</template>

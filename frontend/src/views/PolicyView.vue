<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnimatePresence, motion } from 'motion-v'
import { ArrowDown, ArrowRight, ArrowUp, Check, CircleCheck, CornerDownRight, Minus, Plus, RefreshCw, Trash2, X } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { Category, ConditionType, Decision, Policy, Rule } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import ErrorBanner from '@/components/ErrorBanner.vue'

type EditableRule = Rule & { uid: number }

const { t } = useI18n()
const auth = useAuthStore()
const policy = ref<Policy | null>(null)
const rules = ref<EditableRule[]>([])
const error = ref<Error | null>(null)
const notice = ref('')
const saving = ref(false)
const editable = auth.hasRole('POLICY_ADMIN')
const categories: Category[] = ['DIRECTOR_ELECTION', 'SAY_ON_PAY', 'AUDITOR', 'MERGER', 'SHAREHOLDER_ENV', 'SHAREHOLDER_SOCIAL', 'OTHER']
const conditions: ConditionType[] = ['PAY_SCORE_BELOW', 'BOARD_INDEPENDENCE_BELOW', 'BOARD_RECOMMENDS_AGAINST']
const decisions: Decision[] = ['FOR', 'AGAINST', 'ABSTAIN']
let uid = 0

function adopt(p: Policy) {
  policy.value = p
  rules.value = p.rules.map((r) => ({ ...r, uid: ++uid }))
}

async function load() {
  adopt(await Api.policy())
}

function add() {
  rules.value.push({ uid: ++uid, priority: rules.value.length + 1, category: null, conditionType: null, threshold: null, decision: 'AGAINST', rationale: '' })
}
function remove(i: number) {
  rules.value.splice(i, 1)
}
function move(i: number, delta: number) {
  const j = i + delta
  if (j < 0 || j >= rules.value.length) return
  const copy = [...rules.value]
  ;[copy[i], copy[j]] = [copy[j], copy[i]]
  rules.value = copy
}
const needsThreshold = (c: ConditionType | null) => !!c && c !== 'BOARD_RECOMMENDS_AGAINST'
const glyph = { FOR: Check, AGAINST: X, ABSTAIN: Minus }
const solid = {
  FOR: 'border-for bg-for text-white dark:text-[#06102a]',
  AGAINST: 'border-against bg-against text-white dark:text-[#2a0904]',
  ABSTAIN: 'border-abstain bg-abstain text-white dark:text-[#0b1220]',
}

async function save() {
  error.value = null
  notice.value = ''
  saving.value = true
  const body = {
    name: policy.value!.name,
    version: policy.value!.version,
    rules: rules.value.map((r, i) => ({
      priority: i + 1,
      category: r.category,
      conditionType: r.conditionType,
      threshold: needsThreshold(r.conditionType) ? r.threshold : null,
      decision: r.decision,
      rationale: r.rationale,
    })),
  }
  try {
    adopt(await Api.savePolicy(body))
    notice.value = t('policy.saved')
  } catch (e) {
    error.value = e as Error
    if (e instanceof ApiError && e.status === 409) await load()
  } finally {
    saving.value = false
  }
}

async function recalc() {
  const r = await Api.recalculate()
  notice.value = t('policy.recalculated', { n: r.recommendations })
}

onMounted(load)
</script>

<template>
  <header class="mb-5">
    <h1 class="cond text-4xl font-bold uppercase tracking-tight md:text-5xl">{{ t('policy.title') }}</h1>
    <p class="mt-1 max-w-[64ch] text-ink-2">{{ t('policy.lead') }}</p>
  </header>

  <div v-if="!policy" class="skeleton h-72" aria-busy="true" />

  <section v-else class="board p-5 md:p-6">
    <div class="flex flex-wrap items-end justify-between gap-4 border-b border-rule pb-5">
      <label class="grid gap-1.5">
        <span class="label">{{ t('policy.name') }}</span>
        <input v-model="policy.name" class="input w-80 max-w-full text-base font-semibold" :disabled="!editable" />
      </label>
      <span class="text-sm text-ink-2">{{ t('policy.updated', { by: policy.updatedBy }) }} · v{{ policy.version }}</span>
    </div>

    <ErrorBanner :error="error" />
    <AnimatePresence>
      <motion.p
        v-if="notice"
        class="mt-4 flex items-center gap-2 rounded-lg bg-for-wash px-3.5 py-2.5 text-sm font-medium text-for"
        :initial="{ opacity: 0, y: -4 }"
        :animate="{ opacity: 1, y: 0 }"
        :exit="{ opacity: 0 }"
      >
        <CircleCheck class="size-4" aria-hidden="true" />{{ notice }}
      </motion.p>
    </AnimatePresence>

    <ol class="mt-5 grid gap-2.5">
      <AnimatePresence>
        <motion.li
          v-for="(r, i) in rules"
          :key="r.uid"
          layout
          :initial="{ opacity: 0, y: -6 }"
          :animate="{ opacity: 1, y: 0 }"
          :exit="{ opacity: 0, x: -12, transition: { duration: 0.15 } }"
          :transition="{ layout: { duration: 0.3, ease: [0.16, 1, 0.3, 1] }, duration: 0.2 }"
          class="grid grid-cols-[2.5rem_1fr] gap-3 rounded-lg border border-rule bg-panel-2 p-3 md:grid-cols-[2.5rem_1fr_auto] md:items-center"
        >
          <span class="cond text-3xl font-bold leading-none text-ink-2">{{ i + 1 }}</span>
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <span class="label">{{ t('policy.when') }}</span>
            <select v-model="r.category" class="input py-1.5" :disabled="!editable" :aria-label="t('policy.when')">
              <option :value="null">{{ t('policy.anyCategory') }}</option>
              <option v-for="c in categories" :key="c" :value="c">{{ t(`category.${c}`) }}</option>
            </select>
            <span class="label">{{ t('policy.and') }}</span>
            <select v-model="r.conditionType" class="input py-1.5" :disabled="!editable" :aria-label="t('policy.and')">
              <option :value="null">{{ t('condition.none') }}</option>
              <option v-for="c in conditions" :key="c" :value="c">{{ t(`condition.${c}`) }}</option>
            </select>
            <input
              v-if="needsThreshold(r.conditionType)"
              v-model.number="r.threshold"
              type="number"
              min="0"
              max="100"
              class="input w-20 py-1.5 tabular-nums"
              :disabled="!editable"
              aria-label="threshold"
            />
            <ArrowRight class="size-4 text-ink-2" aria-hidden="true" />
            <span class="label">{{ t('policy.call') }}</span>
            <div class="inline-flex gap-1" role="radiogroup" :aria-label="t('policy.call')">
              <button
                v-for="d in decisions"
                :key="d"
                type="button"
                role="radio"
                :aria-checked="r.decision === d"
                :disabled="!editable"
                class="cond inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[0.9rem] font-bold uppercase tracking-wide transition-colors duration-150 disabled:cursor-default"
                :class="r.decision === d ? solid[d] : 'border-rule bg-panel text-ink-2 enabled:hover:bg-panel'"
                @click="r.decision = d"
              >
                <component :is="glyph[d]" class="size-3.5" stroke-width="3" aria-hidden="true" />{{ t(`decision.${d}`) }}
              </button>
            </div>
            <label class="mt-1 flex w-full items-start gap-2">
              <span class="label pt-2">{{ t('policy.because') }}</span>
              <textarea
                v-model="r.rationale"
                rows="1"
                class="input field-sizing-content min-h-9 flex-1 resize-none py-1.5 leading-snug"
                :placeholder="t('policy.rationalePlaceholder')"
                :disabled="!editable"
              />
            </label>
          </div>
          <div v-if="editable" class="col-start-2 flex gap-1 md:col-start-auto">
            <button class="btn btn-ghost btn-icon" :disabled="i === 0" :aria-label="t('policy.moveUp')" @click="move(i, -1)"><ArrowUp class="size-4" aria-hidden="true" /></button>
            <button class="btn btn-ghost btn-icon" :disabled="i === rules.length - 1" :aria-label="t('policy.moveDown')" @click="move(i, 1)"><ArrowDown class="size-4" aria-hidden="true" /></button>
            <button class="btn btn-ghost btn-icon hover:!bg-against-wash hover:text-against" :aria-label="t('policy.remove')" @click="remove(i)"><Trash2 class="size-4" aria-hidden="true" /></button>
          </div>
        </motion.li>
      </AnimatePresence>
    </ol>

    <p class="mt-3 flex items-center gap-2 pl-1 text-sm text-ink-2">
      <CornerDownRight class="size-4" aria-hidden="true" />{{ t('policy.fallback') }}
    </p>

    <div v-if="editable" class="mt-6 flex flex-wrap items-center gap-2 border-t border-rule pt-5">
      <button class="btn btn-ghost" @click="add"><Plus class="size-4" aria-hidden="true" />{{ t('policy.addRule') }}</button>
      <button class="btn btn-ghost" @click="recalc"><RefreshCw class="size-4" aria-hidden="true" />{{ t('policy.recalculate') }}</button>
      <button class="btn ml-auto" :disabled="saving" @click="save">{{ t('common.save') }}</button>
    </div>
  </section>
</template>

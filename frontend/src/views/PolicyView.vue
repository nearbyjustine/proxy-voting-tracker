<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { Category, ConditionType, Policy, Rule } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import ErrorBanner from '@/components/ErrorBanner.vue'

const { t } = useI18n()
const auth = useAuthStore()
const policy = ref<Policy | null>(null)
const error = ref<Error | null>(null)
const notice = ref('')
const categories: Category[] = ['DIRECTOR_ELECTION', 'SAY_ON_PAY', 'AUDITOR', 'MERGER', 'SHAREHOLDER_ENV', 'SHAREHOLDER_SOCIAL', 'OTHER']
const conditions: ConditionType[] = ['PAY_SCORE_BELOW', 'BOARD_INDEPENDENCE_BELOW', 'BOARD_RECOMMENDS_AGAINST']
const editable = auth.hasRole('POLICY_ADMIN')

async function load() {
  policy.value = await Api.policy()
}

function add() {
  policy.value!.rules.push({ priority: policy.value!.rules.length + 1, category: null, conditionType: null, threshold: null, decision: 'AGAINST', rationale: '' })
}
function remove(i: number) {
  policy.value!.rules.splice(i, 1)
}
function move(i: number, delta: number) {
  const rules = policy.value!.rules
  const j = i + delta
  if (j < 0 || j >= rules.length) return
  ;[rules[i], rules[j]] = [rules[j], rules[i]]
}

async function save() {
  error.value = null
  notice.value = ''
  const p = policy.value!
  const rules: Rule[] = p.rules.map((r, i) => ({ ...r, priority: i + 1, threshold: r.conditionType && r.conditionType !== 'BOARD_RECOMMENDS_AGAINST' ? r.threshold : null }))
  try {
    policy.value = await Api.savePolicy({ name: p.name, version: p.version, rules })
    notice.value = t('policy.saved', { n: '' }).replace('  ', ' ')
  } catch (e) {
    error.value = e as Error
    if (e instanceof ApiError && e.status === 409) await load()
  }
}

async function recalc() {
  const r = await Api.recalculate()
  notice.value = t('policy.recalculated', { n: r.recommendations })
}

onMounted(load)
</script>

<template>
  <section v-if="policy" class="card">
    <h1>{{ t('policy.title') }}</h1>
    <label class="field">{{ t('policy.name') }} <input v-model="policy.name" :disabled="!editable" /></label>
    <p class="muted">{{ t('policy.updated', { by: policy.updatedBy }) }}</p>
    <ErrorBanner :error="error" />
    <p v-if="notice" class="banner ok">{{ notice }}</p>

    <h2>{{ t('policy.rules') }}</h2>
    <ol class="rules">
      <li v-for="(r, i) in policy.rules" :key="i" class="rule">
        <span>{{ t('policy.when') }}</span>
        <select v-model="r.category" :disabled="!editable">
          <option :value="null">{{ t('policy.anyCategory') }}</option>
          <option v-for="c in categories" :key="c" :value="c">{{ t(`category.${c}`) }}</option>
        </select>
        <span>{{ t('policy.and') }}</span>
        <select v-model="r.conditionType" :disabled="!editable">
          <option :value="null">{{ t('condition.none') }}</option>
          <option v-for="c in conditions" :key="c" :value="c">{{ t(`condition.${c}`) }}</option>
        </select>
        <input v-if="r.conditionType && r.conditionType !== 'BOARD_RECOMMENDS_AGAINST'" v-model.number="r.threshold" type="number" min="0" max="100" class="num-input" :disabled="!editable" />
        <span>{{ t('policy.then') }}</span>
        <select v-model="r.decision" :disabled="!editable">
          <option value="FOR">{{ t('decision.FOR') }}</option>
          <option value="AGAINST">{{ t('decision.AGAINST') }}</option>
          <option value="ABSTAIN">{{ t('decision.ABSTAIN') }}</option>
        </select>
        <span>{{ t('policy.because') }}</span>
        <input v-model="r.rationale" class="grow" :disabled="!editable" />
        <span v-if="editable" class="row">
          <button class="ghost small" @click="move(i, -1)">↑</button>
          <button class="ghost small" @click="move(i, 1)">↓</button>
          <button class="ghost small" @click="remove(i)">✕</button>
        </span>
      </li>
    </ol>
    <p class="muted">{{ t('policy.fallback') }}</p>
    <div v-if="editable" class="row">
      <button class="ghost" @click="add">{{ t('policy.addRule') }}</button>
      <button @click="save">{{ t('common.save') }}</button>
      <button class="ghost" @click="recalc">{{ t('policy.recalculate') }}</button>
    </div>
  </section>
</template>

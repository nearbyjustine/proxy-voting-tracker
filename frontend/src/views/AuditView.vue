<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import type { AuditEvent, Page } from '@/api/types'
import { formatLocal } from '@/utils/time'

const { t, locale } = useI18n()
const page = ref<Page<AuditEvent> | null>(null)

async function load(n = 0) {
  page.value = await Api.audit(n)
}
onMounted(() => load())

const tone = (action: string) =>
  action.startsWith('VOTE') ? 'bg-for-wash text-for' : action.startsWith('POLICY') ? 'bg-lit-wash text-lit-ink' : 'bg-panel-2 text-ink-2'
const show = (v: unknown) => (v === null || v === undefined ? '—' : String(v))
</script>

<template>
  <header class="mb-5">
    <h1 class="cond text-4xl font-bold uppercase tracking-tight md:text-5xl">{{ t('audit.title') }}</h1>
    <p class="mt-1 text-ink-2">{{ t('audit.lead') }}</p>
  </header>

  <div v-if="!page" class="skeleton h-72" aria-busy="true" />
  <div v-else class="board overflow-x-auto">
    <table class="results">
      <thead>
        <tr>
          <th class="label">{{ t('audit.when') }}</th>
          <th class="label">{{ t('audit.who') }}</th>
          <th class="label">{{ t('audit.what') }}</th>
          <th class="label">{{ t('audit.details') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="e in page.content" :key="e.id">
          <td class="whitespace-nowrap text-sm tabular-nums text-ink-2">{{ formatLocal(e.occurredAt, locale) }}</td>
          <td class="cond whitespace-nowrap text-lg font-bold">{{ e.actor }}</td>
          <td><span class="cond whitespace-nowrap rounded-[4px] px-2 py-0.5 text-sm font-bold uppercase tracking-wider" :class="tone(e.action)">{{ e.action.replace(/_/g, ' ') }}</span></td>
          <td>
            <dl class="flex flex-wrap gap-x-4 gap-y-1 text-sm">
              <div v-for="(v, k) in e.details" :key="k" class="flex gap-1.5">
                <dt class="text-ink-2">{{ k }}</dt>
                <dd class="font-medium">{{ show(v) }}</dd>
              </div>
            </dl>
          </td>
        </tr>
        <tr v-if="page.content.length === 0"><td colspan="4" class="py-10 text-center text-ink-2">{{ t('common.none') }}</td></tr>
      </tbody>
    </table>
    <nav v-if="page.page.totalPages > 1" class="flex items-center justify-end gap-3 border-t border-rule px-4 py-3 text-sm">
      <button class="btn btn-ghost btn-icon" :disabled="page.page.number === 0" :aria-label="t('common.prev')" @click="load(page.page.number - 1)"><ChevronLeft class="size-4" /></button>
      <span class="text-ink-2">{{ t('common.page', { n: page.page.number + 1, total: page.page.totalPages }) }}</span>
      <button class="btn btn-ghost btn-icon" :disabled="page.page.number + 1 >= page.page.totalPages" :aria-label="t('common.next')" @click="load(page.page.number + 1)"><ChevronRight class="size-4" /></button>
    </nav>
  </div>
</template>

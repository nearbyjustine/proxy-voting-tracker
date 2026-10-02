<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import type { AuditEvent, Page } from '@/api/types'
import { formatLocal } from '@/utils/time'

const { t, locale } = useI18n()
const page = ref<Page<AuditEvent> | null>(null)

async function load(n = 0) {
  page.value = await Api.audit(n)
}
onMounted(() => load())
</script>

<template>
  <section class="card">
    <h1>{{ t('audit.title') }}</h1>
    <div v-if="page" class="table-wrap">
      <table>
        <thead><tr><th>{{ t('audit.when') }}</th><th>{{ t('audit.who') }}</th><th>{{ t('audit.what') }}</th><th>{{ t('audit.details') }}</th></tr></thead>
        <tbody>
          <tr v-for="e in page.content" :key="e.id">
            <td>{{ formatLocal(e.occurredAt, locale) }}</td>
            <td>{{ e.actor }}</td>
            <td><code>{{ e.action }}</code></td>
            <td class="wrap"><code>{{ JSON.stringify(e.details) }}</code></td>
          </tr>
          <tr v-if="page.content.length === 0"><td colspan="4" class="muted">{{ t('common.none') }}</td></tr>
        </tbody>
      </table>
    </div>
    <div v-if="page && page.page.totalPages > 1" class="pager">
      <button class="ghost" :disabled="page.page.number === 0" @click="load(page.page.number - 1)">{{ t('common.prev') }}</button>
      <span>{{ t('common.page', { n: page.page.number + 1, total: page.page.totalPages }) }}</span>
      <button class="ghost" :disabled="page.page.number + 1 >= page.page.totalPages" @click="load(page.page.number + 1)">{{ t('common.next') }}</button>
    </div>
  </section>
</template>

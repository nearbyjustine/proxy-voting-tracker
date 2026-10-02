<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import ErrorBanner from '@/components/ErrorBanner.vue'

const { t } = useI18n()
const file = ref<File | null>(null)
const notice = ref('')
const error = ref<Error | null>(null)

/** Two steps: ask our API for a pre-signed URL, then PUT the file straight to S3 (it never touches our server). */
async function upload() {
  if (!file.value) return
  error.value = null
  try {
    const { url, key } = await Api.uploadUrl(file.value.name)
    const res = await fetch(url, { method: 'PUT', headers: { 'Content-Type': 'text/csv' }, body: file.value })
    if (!res.ok) throw new Error(`S3 upload failed: ${res.status}`)
    notice.value = t('ingest.done', { key })
  } catch (e) {
    error.value = e as Error
  }
}
</script>

<template>
  <section class="card narrow">
    <h1>{{ t('ingest.title') }}</h1>
    <p>{{ t('ingest.help') }}</p>
    <p class="muted">{{ t('ingest.sample') }}</p>
    <ErrorBanner :error="error" />
    <p v-if="notice" class="banner ok">{{ notice }}</p>
    <div class="row">
      <input type="file" accept=".csv,text/csv" @change="file = ($event.target as HTMLInputElement).files?.[0] ?? null" />
      <button :disabled="!file" @click="upload">{{ t('ingest.upload') }}</button>
    </div>
  </section>
</template>

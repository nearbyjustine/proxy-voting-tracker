<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnimatePresence, motion } from 'motion-v'
import { FileSpreadsheet, LoaderCircle, Upload } from 'lucide-vue-next'
import { Api } from '@/api/endpoints'
import type { MeetingSummary } from '@/api/types'
import ErrorBanner from '@/components/ErrorBanner.vue'

const { t } = useI18n()
const file = ref<File | null>(null)
const phase = ref<'idle' | 'uploading' | 'waiting' | 'done'>('idle')
const arrived = ref<MeetingSummary[]>([])
const error = ref<Error | null>(null)
let poll: ReturnType<typeof setInterval> | undefined

/** Two steps: ask our API for a pre-signed URL, then PUT the file straight to S3 (it never passes through our servers). */
async function upload() {
  if (!file.value) return
  error.value = null
  arrived.value = []
  phase.value = 'uploading'
  try {
    const before = new Set((await Api.meetings()).map((m) => m.externalId))
    const { url } = await Api.uploadUrl(file.value.name)
    const res = await fetch(url, { method: 'PUT', headers: { 'Content-Type': 'text/csv' }, body: file.value })
    if (!res.ok) throw new Error(`S3 upload failed: ${res.status}`)
    phase.value = 'waiting'
    watchForResults(before)
  } catch (e) {
    error.value = e as Error
    phase.value = 'idle'
  }
}

/** S3 → Lambda → SQS → API takes a few seconds; check the board until new meetings land (max ~45 s). */
function watchForResults(before: Set<string>) {
  let tries = 0
  clearInterval(poll)
  poll = setInterval(async () => {
    tries++
    const now = await Api.meetings().catch(() => [])
    arrived.value = now.filter((m) => !before.has(m.externalId))
    if (arrived.value.length || tries >= 15) {
      clearInterval(poll)
      phase.value = 'done'
    }
  }, 3000)
}
onBeforeUnmount(() => clearInterval(poll))
</script>

<template>
  <header class="mb-5">
    <h1 class="cond text-4xl font-bold uppercase tracking-tight md:text-5xl">{{ t('ingest.title') }}</h1>
    <p class="mt-1 max-w-[64ch] text-ink-2">{{ t('ingest.lead') }}</p>
  </header>

  <section class="board max-w-2xl p-5 md:p-6">
    <label
      class="flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed border-rule px-6 py-10 text-center transition-colors hover:border-for hover:bg-for-wash/40"
    >
      <FileSpreadsheet class="size-8 text-ink-2" aria-hidden="true" />
      <span class="font-semibold">{{ file ? file.name : t('ingest.choose') }}</span>
      <span class="text-sm text-ink-2">{{ t('ingest.sample') }}</span>
      <input type="file" accept=".csv,text/csv" class="sr-only" @change="file = ($event.target as HTMLInputElement).files?.[0] ?? null" />
    </label>
    <ErrorBanner :error="error" />
    <div class="mt-4 flex items-center gap-3">
      <button class="btn" :disabled="!file || phase === 'uploading' || phase === 'waiting'" @click="upload">
        <LoaderCircle v-if="phase === 'uploading'" class="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
        <Upload v-else class="size-4" aria-hidden="true" />{{ phase === 'uploading' ? t('ingest.uploading') : t('ingest.upload') }}
      </button>
      <span v-if="phase === 'waiting'" class="flex items-center gap-2 text-sm text-ink-2" role="status">
        <span class="size-2 rounded-full bg-lit motion-safe:animate-pulse" aria-hidden="true" />{{ t('ingest.waiting') }}
      </span>
    </div>

    <div v-if="phase === 'done'" class="mt-5 border-t border-rule pt-4" role="status">
      <p class="cond text-xl font-bold uppercase">{{ arrived.length ? t('ingest.arrived', arrived.length) : t('ingest.noneYet') }}</p>
      <ul class="mt-2 divide-y divide-rule">
        <AnimatePresence>
          <motion.li v-for="(m, i) in arrived" :key="m.id" :initial="{ opacity: 0, x: -8 }" :animate="{ opacity: 1, x: 0 }" :transition="{ delay: i * 0.06 }">
            <RouterLink :to="`/meetings/${m.id}`" class="flex items-center gap-4 py-2.5 hover:text-for">
              <span class="cond w-16 text-xl font-bold">{{ m.ticker }}</span>
              <span>{{ m.companyName }}</span>
            </RouterLink>
          </motion.li>
        </AnimatePresence>
      </ul>
    </div>
  </section>
</template>

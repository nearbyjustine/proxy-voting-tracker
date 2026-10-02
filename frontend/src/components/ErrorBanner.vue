<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CircleAlert } from 'lucide-vue-next'
import { AnimatePresence, motion } from 'motion-v'
import type { ApiError } from '@/api/client'

defineProps<{ error: ApiError | Error | null }>()
const { t } = useI18n()
</script>

<template>
  <AnimatePresence>
    <motion.div
      v-if="error"
      role="alert"
      class="my-3 flex items-start gap-2.5 rounded-lg bg-against-wash px-3.5 py-2.5 text-sm text-against"
      :initial="{ opacity: 0, y: -4 }"
      :animate="{ opacity: 1, y: 0 }"
      :exit="{ opacity: 0 }"
      :transition="{ duration: 0.2 }"
    >
      <CircleAlert class="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>
        {{ error.message }}
        <span v-if="'correlationId' in error && error.correlationId" class="mt-0.5 block text-xs opacity-80">{{ t('common.ref', { id: error.correlationId }) }}</span>
      </div>
    </motion.div>
  </AnimatePresence>
</template>

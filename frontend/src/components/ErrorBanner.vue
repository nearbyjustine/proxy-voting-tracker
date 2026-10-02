<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ApiError } from '@/api/client'

defineProps<{ error: ApiError | Error | null }>()
const { t } = useI18n()
</script>

<template>
  <div v-if="error" class="banner error" role="alert">
    <strong>{{ t('common.error') }}:</strong> {{ error.message }}
    <small v-if="'correlationId' in error && error.correlationId"> · {{ t('common.ref', { id: error.correlationId }) }}</small>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { DeadlineStatus } from '@/api/types'

defineProps<{ status: DeadlineStatus }>()
const { t } = useI18n()
</script>

<template>
  <span class="badge" :class="'s-' + status.toLowerCase()">{{ t(`status.${status}`) }}</span>
</template>

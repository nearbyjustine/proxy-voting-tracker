<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Decision } from '@/api/types'

defineProps<{ decision: Decision }>()
const { t } = useI18n()
</script>

<template>
  <span class="badge" :class="'d-' + decision.toLowerCase()">{{ t(`decision.${decision}`) }}</span>
</template>

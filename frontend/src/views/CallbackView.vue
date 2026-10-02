<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const returnTo = await auth.handleCallback()
    await router.replace(returnTo)
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <p v-if="error" class="banner error">{{ error }}</p>
  <p v-else class="muted">Signing you in…</p>
</template>

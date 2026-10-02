<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LoaderCircle } from 'lucide-vue-next'
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
  <p v-if="error" role="alert" class="rounded-lg bg-against-wash px-4 py-3 text-against">{{ error }}</p>
  <p v-else class="flex items-center gap-2 py-16 text-ink-2" role="status"><LoaderCircle class="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />Signing you in…</p>
</template>

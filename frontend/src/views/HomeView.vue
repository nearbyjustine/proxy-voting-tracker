<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Api } from '@/api/endpoints'
import type { Me } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { t } = useI18n()
const me = ref<Me | null>(null)

async function load() {
  if (auth.isAuthenticated) me.value = await Api.me()
}
onMounted(load)
watch(() => auth.isAuthenticated, load)
</script>

<template>
  <section class="card">
    <template v-if="auth.isAuthenticated">
      <h1>{{ t('home.welcome', { name: auth.displayName }) }}</h1>
      <p v-if="me">{{ t('home.org', { org: me.orgName }) }} · <span v-for="r in me.roles" :key="r" class="badge">{{ r }}</span></p>
      <p>{{ t('home.intro') }}</p>
      <RouterLink class="button" to="/meetings">{{ t('nav.meetings') }}</RouterLink>
    </template>
    <template v-else>
      <h1>{{ t('app.title') }}</h1>
      <p>{{ t('home.intro') }}</p>
      <p class="muted">{{ t('home.signedOut') }}</p>
      <button @click="auth.login('/')">{{ t('app.signIn') }}</button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { t, locale } = useI18n()

const links = computed(() =>
  [
    { to: '/', label: t('nav.home'), show: true },
    { to: '/meetings', label: t('nav.meetings'), show: auth.hasRole('ANALYST', 'VOTER') },
    { to: '/policy', label: t('nav.policy'), show: auth.hasRole('ANALYST', 'POLICY_ADMIN') },
    { to: '/audit', label: t('nav.audit'), show: auth.hasRole('POLICY_ADMIN') },
    { to: '/ingest', label: t('nav.ingest'), show: auth.hasRole('OPS') },
  ].filter((l) => l.show),
)

function setLocale(value: string) {
  locale.value = value
  document.documentElement.lang = value
  try {
    localStorage.setItem('locale', value)
  } catch {
    /* private mode */
  }
}
</script>

<template>
  <header class="topbar">
    <div class="brand">{{ t('app.title') }}</div>
    <nav>
      <RouterLink v-for="l in links" :key="l.to" :to="l.to">{{ l.label }}</RouterLink>
    </nav>
    <div class="right">
      <label>
        <span class="sr-only">{{ t('app.language') }}</span>
        <select class="lang" :value="locale" @change="setLocale(($event.target as HTMLSelectElement).value)">
          <option value="en">EN</option>
          <option value="fr">FR</option>
        </select>
      </label>
      <template v-if="auth.isAuthenticated">
        <span class="user">{{ auth.displayName }}</span>
        <button class="ghost" @click="auth.logout()">{{ t('app.signOut') }}</button>
      </template>
      <button v-else @click="auth.login('/')">{{ t('app.signIn') }}</button>
    </div>
  </header>
  <main class="container">
    <RouterView />
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnimatePresence, MotionConfig, motion } from 'motion-v'
import { LogOut, Monitor, Moon, Sun } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { cycleTheme, themePref } from '@/stores/theme'
import { Api } from '@/api/endpoints'
import type { Me } from '@/api/types'
import { pad, useNow } from '@/utils/clock'

const auth = useAuthStore()
const { t, locale } = useI18n()
const me = ref<Me | null>(null)
const now = useNow()
const clock = computed(() => {
  const d = new Date(now.value)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

watch(
  () => auth.isAuthenticated,
  async (signedIn) => {
    me.value = signedIn ? await Api.me().catch(() => null) : null
  },
  { immediate: true },
)

const links = computed(() =>
  [
    { to: '/meetings', label: t('nav.meetings'), show: auth.hasRole('ANALYST', 'VOTER') },
    { to: '/policy', label: t('nav.policy'), show: auth.hasRole('ANALYST', 'POLICY_ADMIN') },
    { to: '/audit', label: t('nav.audit'), show: auth.hasRole('POLICY_ADMIN') },
    { to: '/ingest', label: t('nav.ingest'), show: auth.hasRole('OPS') },
  ].filter((l) => l.show),
)

const themeIcon = computed(() => ({ system: Monitor, dark: Moon, light: Sun })[themePref.value])

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
  <MotionConfig reduced-motion="user">
    <header class="bg-[#0e1526] text-[#e8edf6] dark:bg-[#050913]">
      <div class="mx-auto flex max-w-[1240px] flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6">
        <RouterLink to="/" class="flex items-center gap-2.5">
          <span class="grid size-7 place-items-center rounded-[5px] bg-[#1f4fcc]" aria-hidden="true">
            <svg viewBox="0 0 16 16" class="size-4" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M3 8.5l3 3 7-7" /></svg>
          </span>
          <span class="cond text-xl font-bold uppercase tracking-wide">{{ t('app.title') }}</span>
        </RouterLink>

        <div v-if="me?.orgName" class="hidden items-center gap-2 border-l border-white/15 pl-6 md:flex">
          <span class="cond text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[#98a4b9]">{{ t('app.actingFor') }}</span>
          <span class="font-semibold">{{ me.orgName }}</span>
        </div>

        <div class="ml-auto flex items-center gap-1.5">
          <span class="cond mr-2 hidden text-lg font-bold tabular-nums text-[#ffcf3a] sm:inline" aria-hidden="true">{{ clock }}</span>
          <label class="sr-only" for="lang">{{ t('app.language') }}</label>
          <select
            id="lang"
            class="cond cursor-pointer rounded-md bg-transparent px-1.5 py-1 text-sm font-bold uppercase text-[#e8edf6] hover:bg-white/10 [&>option]:text-[#0e1526]"
            :value="locale"
            @change="setLocale(($event.target as HTMLSelectElement).value)"
          >
            <option value="en">EN</option>
            <option value="fr">FR</option>
          </select>
          <button class="rounded-md p-2 hover:bg-white/10" :title="t(`app.theme.${themePref}`)" :aria-label="t(`app.theme.${themePref}`)" @click="cycleTheme">
            <component :is="themeIcon" class="size-4" aria-hidden="true" />
          </button>
          <template v-if="auth.isAuthenticated">
            <span class="ml-1 hidden text-sm text-[#98a4b9] sm:inline">{{ auth.displayName }}</span>
            <button class="rounded-md p-2 hover:bg-white/10" :title="t('app.signOut')" :aria-label="t('app.signOut')" @click="auth.logout()">
              <LogOut class="size-4" aria-hidden="true" />
            </button>
          </template>
          <button v-else class="btn ml-1 bg-[#1f4fcc] px-3 py-1.5 text-white hover:bg-[#2b5be0]" @click="auth.login('/')">{{ t('app.signIn') }}</button>
        </div>
        <!-- Phones: the tenant and the desk clock stay visible on their own line -->
        <div v-if="me?.orgName" class="flex w-full items-center justify-between gap-3 border-t border-white/10 pt-2 text-sm md:hidden">
          <span class="truncate"><span class="cond mr-1.5 text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[#98a4b9]">{{ t('app.actingFor') }}</span>{{ me.orgName }}</span>
          <span class="cond shrink-0 text-base font-bold tabular-nums text-[#ffcf3a]" aria-hidden="true">{{ clock }}</span>
        </div>
      </div>
      <nav
        v-if="links.length"
        class="mx-auto flex max-w-[1240px] gap-1 overflow-x-auto px-3 [mask-image:linear-gradient(to_right,black_82%,transparent)] sm:px-5 md:[mask-image:none]"
        :aria-label="t('app.title')"
      >
        <RouterLink
          v-for="l in links"
          :key="l.to"
          :to="l.to"
          class="cond relative whitespace-nowrap px-3 pb-2.5 pt-1 text-[0.95rem] font-semibold uppercase tracking-wider text-[#98a4b9] transition-colors hover:text-white"
          active-class="!text-white after:absolute after:inset-x-3 after:bottom-0 after:h-[3px] after:rounded-t after:bg-[#ffcf3a]"
        >
          {{ l.label }}
        </RouterLink>
      </nav>
    </header>

    <main class="mx-auto max-w-[1240px] px-4 py-6 sm:px-6 sm:py-8">
      <RouterView v-slot="{ Component, route }">
        <AnimatePresence mode="wait">
          <motion.div
            :key="route.path"
            :initial="{ opacity: 0, y: 6 }"
            :animate="{ opacity: 1, y: 0 }"
            :exit="{ opacity: 0, transition: { duration: 0.1 } }"
            :transition="{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }"
          >
            <component :is="Component" />
          </motion.div>
        </AnimatePresence>
      </RouterView>
    </main>
  </MotionConfig>
</template>

import { ref, watchEffect } from 'vue'

export type ThemePref = 'light' | 'dark' | 'system'
const KEY = 'theme'

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

/** Light/dark follows the OS unless the user picks one; the choice is a per-device convenience. */
export const themePref = ref<ThemePref>(read())
const media = typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null
const systemDark = ref(media?.matches ?? false)
media?.addEventListener('change', (e) => (systemDark.value = e.matches))

watchEffect(() => {
  const dark = themePref.value === 'dark' || (themePref.value === 'system' && systemDark.value)
  document.documentElement.classList.toggle('dark', dark)
  try {
    if (themePref.value === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, themePref.value)
  } catch {
    /* storage unavailable */
  }
})

export function cycleTheme() {
  themePref.value = themePref.value === 'system' ? 'dark' : themePref.value === 'dark' ? 'light' : 'system'
}

import { createI18n } from 'vue-i18n'
import en from './en'
import fr from './fr'

const saved = (() => {
  try {
    return localStorage.getItem('locale')
  } catch {
    return null
  }
})()

export const i18n = createI18n({ legacy: false, locale: saved === 'fr' ? 'fr' : 'en', fallbackLocale: 'en', messages: { en, fr } })

import { onBeforeUnmount, ref } from 'vue'

/** One shared "now" that ticks, so every countdown on screen agrees. */
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
let users = 0

export function useNow(intervalMs = 1000) {
  users++
  if (!timer) timer = setInterval(() => (now.value = Date.now()), intervalMs)
  onBeforeUnmount(() => {
    users--
    if (users === 0 && timer) {
      clearInterval(timer)
      timer = undefined
    }
  })
  return now
}

/** Split a remaining duration into broadcast-countdown parts. */
export function countdownParts(deadline: string, nowMs: number) {
  const ms = new Date(deadline).getTime() - nowMs
  if (ms <= 0) return { closed: true, days: 0, hours: 0, minutes: 0, seconds: 0, ms }
  const s = Math.floor(ms / 1000)
  return { closed: false, days: Math.floor(s / 86400), hours: Math.floor((s % 86400) / 3600), minutes: Math.floor((s % 3600) / 60), seconds: s % 60, ms }
}

export const pad = (n: number) => String(n).padStart(2, '0')

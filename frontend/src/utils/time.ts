// Note: Intl forbids combining dateStyle/timeStyle with timeZoneName (it throws a TypeError),
// so we list the individual fields explicitly.
const FIELDS: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' }

/** A deadline is ONE instant; we show it in the viewer's zone AND in the market's zone, because both matter. */
export function formatInZone(iso: string, timeZone: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { ...FIELDS, timeZone }).format(new Date(iso))
}

export function formatLocal(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, FIELDS).format(new Date(iso))
}

/** "in 30 hours" / "2 days ago" */
export function relative(iso: string, locale: string, now = Date.now()): string {
  const diffMs = new Date(iso).getTime() - now
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const hours = Math.round(diffMs / 3_600_000)
  if (Math.abs(hours) < 48) return rtf.format(hours, 'hour')
  return rtf.format(Math.round(hours / 24), 'day')
}

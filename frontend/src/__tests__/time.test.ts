import { describe, expect, it } from 'vitest'
import { formatInZone, relative } from '@/utils/time'

describe('time', () => {
  it('shows one instant in the market time zone', () => {
    // 2026-11-10T21:00Z is 4pm in New York (EST, UTC-5)
    expect(formatInZone('2026-11-10T21:00:00Z', 'America/New_York', 'en-US')).toContain('4:00')
    expect(formatInZone('2026-11-10T21:00:00Z', 'Asia/Tokyo', 'en-US')).toContain('6:00')   // next day 6am JST
  })
  it('describes deadlines relatively', () => {
    const now = Date.parse('2026-11-09T21:00:00Z')
    expect(relative('2026-11-10T21:00:00Z', 'en', now)).toBe('in 24 hours')
    expect(relative('2026-11-05T21:00:00Z', 'en', now)).toBe('4 days ago')
  })
})

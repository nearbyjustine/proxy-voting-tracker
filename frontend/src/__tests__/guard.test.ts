import { describe, expect, it, vi } from 'vitest'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({}) }))
const { canAccess } = await import('@/router')

describe('route guard', () => {
  it('requires one of the listed roles', () => {
    expect(canAccess({ roles: ['OPS'] }, true, ['VOTER'])).toBe('forbidden')
    expect(canAccess({ roles: ['ANALYST', 'VOTER'] }, true, ['VOTER'])).toBe('ok')
    expect(canAccess({}, false, [])).toBe('login')
  })
})

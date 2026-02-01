import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { AuthApi } from '@/api/endpoints'

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('stores the token for reviewers', async () => {
    vi.spyOn(AuthApi, 'login').mockResolvedValue({ token: 't0k', user: { id: 1, email: 'r@x', name: 'Wanjiru Kamau', role: 'reviewer', pharmacy: null } })
    const auth = useAuthStore()
    await auth.login('r@x', 'pw')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.initials).toBe('WK')
    expect(localStorage.getItem('afyaclaim.token')).toBe('t0k')
  })

  it('keeps pharmacists out of the back office', async () => {
    vi.spyOn(AuthApi, 'login').mockResolvedValue({ token: 't', user: { id: 2, email: 'p@x', name: 'P', role: 'pharmacist', pharmacy: null } })
    await expect(useAuthStore().login('p@x', 'pw')).rejects.toThrow(/POS app/)
    expect(localStorage.getItem('afyaclaim.token')).toBeNull()
  })
})

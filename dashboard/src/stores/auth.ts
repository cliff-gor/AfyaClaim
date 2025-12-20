import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { AuthApi } from '@/api/endpoints'
import { tokenStore } from '@/api/client'
import type { User } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(tokenStore.get())

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')
  const initials = computed(() =>
    (user.value?.name ?? '')
      .split(' ')
      .map((p) => p[0])
      .slice(0, 2)
      .join(''),
  )

  async function login(email: string, password: string) {
    const res = await AuthApi.login(email, password)
    if (res.user.role === 'pharmacist') throw new Error('The back office is for reviewers and admins. Pharmacists use the POS app.')
    tokenStore.set(res.token)
    token.value = res.token
    user.value = res.user
  }

  async function restore() {
    if (!token.value || user.value) return
    try {
      user.value = (await AuthApi.me()).user
    } catch {
      logout()
    }
  }

  function logout() {
    tokenStore.clear()
    token.value = null
    user.value = null
  }

  return { user, token, isAuthenticated, isAdmin, initials, login, restore, logout }
})

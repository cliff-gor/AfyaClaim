import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/components/AppShell.vue'),
      children: [
        { path: '', name: 'overview', component: () => import('@/views/OverviewView.vue'), meta: { title: 'Overview' } },
        { path: 'claims', name: 'claims', component: () => import('@/views/ClaimsQueueView.vue'), meta: { title: 'Review queue' } },
        { path: 'claims/:id', name: 'claim', component: () => import('@/views/ClaimsQueueView.vue'), meta: { title: 'Review queue' } },
        { path: 'pharmacies', name: 'pharmacies', component: () => import('@/views/PharmaciesView.vue'), meta: { title: 'Pharmacies' } },
        { path: 'pharmacies/:id', name: 'pharmacy', component: () => import('@/views/PharmacyDetailView.vue'), meta: { title: 'Pharmacy' } },
        { path: 'analytics', name: 'analytics', component: () => import('@/views/AnalyticsView.vue'), meta: { title: 'Analytics' } },
        { path: 'payouts', name: 'payouts', component: () => import('@/views/PayoutsView.vue'), meta: { title: 'Payouts' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.isAuthenticated ? { name: 'overview' } : true
  if (!auth.isAuthenticated) return { name: 'login', query: { next: to.fullPath } }
  await auth.restore()
  return auth.isAuthenticated ? true : { name: 'login' }
})

router.afterEach((to) => {
  document.title = `${(to.meta.title as string) ?? 'Sign in'} · AfyaClaim`
})

export default router

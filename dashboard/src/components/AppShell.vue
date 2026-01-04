<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Activity, BarChart3, Building2, ClipboardCheck, LayoutGrid, LogOut, Search, Wallet } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { AnalyticsApi } from '@/api/endpoints'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const pending = ref<number | null>(null)
const search = ref('')

const nav = [
  { to: '/', label: 'Overview', icon: LayoutGrid, exact: true },
  { to: '/claims', label: 'Review queue', icon: ClipboardCheck, badge: true },
  { to: '/pharmacies', label: 'Pharmacies', icon: Building2 },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
  { to: '/payouts', label: 'Payouts', icon: Wallet },
]

const isActive = (item: (typeof nav)[number]) =>
  item.exact ? route.path === item.to : route.path.startsWith(item.to)

const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 12 ? 'Habari ya asubuhi' : h < 17 ? 'Habari ya mchana' : 'Habari ya jioni'
})

onMounted(async () => {
  try {
    pending.value = (await AnalyticsApi.overview()).data.pending_review
  } catch {
    pending.value = null
  }
})

function submitSearch() {
  router.push({ name: 'claims', query: { q: search.value || undefined } })
}

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <a href="#main" class="skip">Skip to content</a>
    <aside class="side" aria-label="Primary">
      <RouterLink to="/" class="brand">
        <span class="brand-mark"><Activity :size="18" :stroke-width="2.5" /></span>
        <span>AfyaClaim</span>
      </RouterLink>
      <nav>
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item) }"
          :aria-current="isActive(item) ? 'page' : undefined"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <span v-if="item.badge && pending" class="nav-badge num">{{ pending > 999 ? '999+' : pending }}</span>
        </RouterLink>
      </nav>
      <div class="side-foot">
        <div class="programme">
          <span class="muted-ink">Programme</span>
          <strong>Western Kenya Malaria &amp; FP</strong>
          <span class="muted-ink">FY 2026 · KES subsidies</span>
        </div>
        <button class="nav-item logout" @click="logout"><LogOut :size="18" /><span>Sign out</span></button>
      </div>
    </aside>

    <div class="main-col">
      <header class="top">
        <div>
          <p class="muted hello">{{ greeting }}, {{ auth.user?.name.split(' ')[0] }}</p>
          <h1 class="page-title">{{ route.meta.title }}</h1>
        </div>
        <form class="search" role="search" @submit.prevent="submitSearch">
          <Search :size="16" class="search-icon" />
          <label for="global-search" class="sr-only">Search claims</label>
          <input id="global-search" v-model="search" class="input" placeholder="Search claim ref or pharmacy" />
        </form>
        <div class="user">
          <div class="avatar" aria-hidden="true">{{ auth.initials }}</div>
          <div class="user-meta">
            <strong>{{ auth.user?.name }}</strong>
            <span class="muted">{{ auth.user?.role === 'admin' ? 'Admin' : 'Claims reviewer' }}</span>
          </div>
        </div>
      </header>
      <main id="main" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell { display: grid; grid-template-columns: 248px 1fr; min-height: 100vh; }
.skip { position: absolute; left: -999px; top: 8px; background: var(--lime); padding: 8px 12px; border-radius: 8px; z-index: 100; }
.skip:focus { left: 8px; }
.side {
  position: sticky; top: 0; height: 100vh; background: var(--ink); color: #f2f3ee;
  padding: 22px 14px; display: flex; flex-direction: column; gap: 28px;
}
.brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 18px; padding: 0 10px; letter-spacing: -0.02em; }
.brand-mark { width: 34px; height: 34px; border-radius: 11px; background: var(--lime); color: var(--ink); display: grid; place-items: center; }
nav { display: flex; flex-direction: column; gap: 4px; }
.nav-item {
  display: flex; align-items: center; gap: 12px; height: 44px; padding: 0 14px; border-radius: 14px;
  color: #b8bcb2; font-weight: 500; font-size: 14.5px; border: 0; background: none; width: 100%; text-align: left;
  transition: background 150ms var(--ease), color 150ms;
}
.nav-item:hover { background: #1f221d; color: #fff; }
.nav-item.active { background: var(--lime); color: var(--ink); font-weight: 600; }
.nav-badge { margin-left: auto; font-size: 11.5px; background: #2a2e27; color: var(--lime); padding: 2px 8px; border-radius: 999px; }
.active .nav-badge { background: var(--ink); color: var(--lime); }
.side-foot { margin-top: auto; display: flex; flex-direction: column; gap: 10px; }
.programme { background: #171915; border: 1px solid #262923; border-radius: 18px; padding: 14px; display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
.muted-ink { color: #8d9188; font-size: 12px; }
.logout { color: #8d9188; }

.main-col { min-width: 0; display: flex; flex-direction: column; }
.top { display: flex; align-items: center; gap: 20px; padding: 22px 32px 8px; }
.hello { font-size: 13.5px; }
.page-title { font-size: 28px; font-weight: 700; }
.search { position: relative; margin-left: auto; width: min(360px, 40%); }
.search .input { padding-left: 38px; border-radius: 999px; background: var(--surface); border-color: transparent; box-shadow: var(--shadow); }
.search-icon { position: absolute; left: 14px; top: 13px; color: var(--muted); }
.user { display: flex; align-items: center; gap: 10px; }
.avatar { width: 40px; height: 40px; border-radius: 50%; background: var(--sage); color: var(--ink); display: grid; place-items: center; font-weight: 700; font-size: 14px; }
.user-meta { display: flex; flex-direction: column; font-size: 13.5px; line-height: 1.3; }
main { padding: 16px 32px 40px; outline: none; }

@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
  .side { position: static; height: auto; flex-direction: row; align-items: center; overflow-x: auto; padding: 12px; }
  nav { flex-direction: row; }
  .side-foot, .user-meta, .nav-item span:not(.nav-badge) { display: none; }
  .top { padding: 16px; flex-wrap: wrap; }
  .search { width: 100%; order: 3; }
  main { padding: 12px 16px 32px; }
}
</style>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { AlertTriangle, MapPin, Search } from 'lucide-vue-next'
import { PharmaciesApi } from '@/api/endpoints'
import type { Pharmacy } from '@/api/types'
import RiskMeter from '@/components/RiskMeter.vue'
import Skeleton from '@/components/Skeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { moneyCompact } from '@/utils/format'

const q = ref('')
const highRisk = ref(false)
const rows = ref<Pharmacy[]>([])
const loading = ref(true)
let timer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  rows.value = (await PharmaciesApi.list({ q: q.value, high_risk: highRisk.value || undefined, per_page: 60 })).data
  loading.value = false
}
watch(q, () => { clearTimeout(timer); timer = setTimeout(load, 250) })
watch(highRisk, load)
onMounted(load)
</script>

<template>
  <div class="ph">
    <div class="bar">
      <div class="qbox">
        <Search :size="16" />
        <label for="pq" class="sr-only">Search pharmacies</label>
        <input id="pq" v-model="q" class="input" placeholder="Search by pharmacy name" />
      </div>
      <button class="btn btn--sm" :class="highRisk ? 'btn--ink' : 'btn--ghost'" :aria-pressed="highRisk" @click="highRisk = !highRisk">
        <AlertTriangle :size="14" /> High risk only
      </button>
      <span class="muted count">{{ rows.length }} pharmacies</span>
    </div>

    <div v-if="loading" class="grid-cards">
      <div v-for="i in 9" :key="i" class="card"><Skeleton :height="120" /></div>
    </div>
    <div v-else class="grid-cards">
      <RouterLink v-for="p in rows" :key="p.id" :to="`/pharmacies/${p.id}`" class="card pcard" :class="{ risk: p.high_risk, suspended: p.status === 'suspended' }">
        <div class="top">
          <div class="avatar">{{ p.name.slice(0, 2).toUpperCase() }}</div>
          <div class="who">
            <strong>{{ p.name }}</strong>
            <span class="muted"><MapPin :size="12" /> {{ p.town }}, {{ p.region.name }}</span>
          </div>
          <span v-if="p.status === 'suspended'" class="chip sus">Suspended</span>
          <span v-else-if="p.high_risk" class="chip hr"><AlertTriangle :size="12" /> Watch</span>
        </div>
        <div class="stats">
          <div><span class="num big">{{ p.stats.total }}</span><small>claims</small></div>
          <div><span class="num big">{{ p.stats.pending }}</span><small>pending</small></div>
          <div><span class="num big" :class="{ red: p.stats.flagged }">{{ p.stats.flagged }}</span><small>flagged</small></div>
          <div><span class="num big">{{ moneyCompact(p.stats.approved_value_cents).replace('KES ', '') }}</span><small>KES approved</small></div>
        </div>
        <div class="foot">
          <span class="muted small">Risk score</span>
          <RiskMeter :score="p.risk_score" />
        </div>
      </RouterLink>
    </div>
    <EmptyState v-if="!loading && !rows.length" title="No pharmacies found" body="Try a different search." />
  </div>
</template>

<style scoped>
.ph { display: flex; flex-direction: column; gap: 16px; }
.bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.qbox { position: relative; width: min(340px, 100%); }
.qbox svg { position: absolute; left: 14px; top: 13px; color: var(--muted); }
.qbox .input { padding-left: 38px; border-radius: 999px; border-color: transparent; box-shadow: var(--shadow); }
.count { margin-left: auto; font-size: 13.5px; }
.grid-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.pcard { display: flex; flex-direction: column; gap: 16px; transition: transform 150ms var(--ease); border: 1px solid transparent; }
.pcard:hover { transform: translateY(-2px); border-color: var(--sage); }
.pcard.risk { border-color: var(--coral-soft); }
.pcard.suspended { opacity: 0.6; }
.top { display: flex; gap: 12px; align-items: center; }
.avatar { width: 44px; height: 44px; border-radius: 14px; background: var(--sage-soft); color: var(--moss); display: grid; place-items: center; font-weight: 700; flex-shrink: 0; }
.risk .avatar { background: var(--coral-soft); color: var(--coral); }
.who { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.who strong { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.who span { font-size: 12.5px; display: flex; gap: 4px; align-items: center; }
.hr { background: var(--coral-soft); color: var(--coral); }
.sus { background: var(--surface-2); }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; background: var(--surface-2); border-radius: 16px; padding: 12px; }
.stats div { display: flex; flex-direction: column; }
.big { font-size: 18px; font-weight: 600; }
.red { color: var(--coral); }
small { font-size: 11.5px; color: var(--muted); }
.foot { display: flex; justify-content: space-between; align-items: center; }
.small { font-size: 12.5px; }
</style>

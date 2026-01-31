<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Bar } from 'vue-chartjs'
import { ArrowLeft, BadgeCheck, MapPin, Phone } from 'lucide-vue-next'
import { ClaimsApi, PharmaciesApi } from '@/api/endpoints'
import type { ClaimRow, PharmacyDetail } from '@/api/types'
import RiskMeter from '@/components/RiskMeter.vue'
import FlagBadge from '@/components/FlagBadge.vue'
import StatusPill from '@/components/StatusPill.vue'
import Skeleton from '@/components/Skeleton.vue'
import { dateTime, money, relative } from '@/utils/format'
import { axis, palette } from '@/utils/charts'

const route = useRoute()
const p = ref<PharmacyDetail | null>(null)
const claims = ref<ClaimRow[]>([])

onMounted(async () => {
  const id = Number(route.params.id)
  const [detail, list] = await Promise.all([PharmaciesApi.get(id), ClaimsApi.list({ pharmacy_id: id, per_page: 12, status: '' })])
  p.value = detail.data
  claims.value = list.data
})

const chart = computed(() => ({
  labels: p.value?.monthly.map((m) => new Date(m.month).toLocaleDateString('en-KE', { month: 'short' })) ?? [],
  datasets: [
    { label: 'Clean', data: p.value?.monthly.map((m) => m.claims - m.flagged) ?? [], backgroundColor: palette.ink, borderRadius: 6, stack: 's' },
    { label: 'Flagged', data: p.value?.monthly.map((m) => m.flagged) ?? [], backgroundColor: palette.coral, borderRadius: 6, stack: 's' },
  ],
}))
const options = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: { position: 'top' as const, align: 'end' as const } },
  scales: { x: { ...axis, stacked: true, grid: { display: false } }, y: { ...axis, stacked: true, beginAtZero: true } },
}
</script>

<template>
  <div class="pd">
    <RouterLink to="/pharmacies" class="back muted"><ArrowLeft :size="16" /> All pharmacies</RouterLink>
    <div v-if="!p"><Skeleton :height="180" :radius="24" /></div>
    <template v-else>
      <section class="header card--ink card">
        <div class="id">
          <div class="avatar">{{ p.name.slice(0, 2).toUpperCase() }}</div>
          <div>
            <h2>{{ p.name }}</h2>
            <p class="meta"><MapPin :size="14" /> {{ p.town }}, {{ p.region.name }} · <BadgeCheck :size="14" /> {{ p.license_number }} · <Phone :size="14" /> M-Pesa {{ p.mpesa_phone }}</p>
          </div>
        </div>
        <div class="risk-box">
          <span class="lbl">30-day risk score</span>
          <span class="dot score" :class="{ hot: p.high_risk }">{{ p.risk_score }}</span>
          <RiskMeter :score="p.risk_score" compact />
        </div>
      </section>

      <section class="stats">
        <div class="card stat"><span class="muted">Claims</span><strong class="num">{{ p.stats.total }}</strong></div>
        <div class="card stat"><span class="muted">Pending review</span><strong class="num">{{ p.stats.pending }}</strong></div>
        <div class="card stat"><span class="muted">Flagged</span><strong class="num red">{{ p.stats.flagged }}</strong></div>
        <div class="card stat"><span class="muted">Rejected</span><strong class="num">{{ p.stats.rejected }}</strong></div>
        <div class="card stat lime"><span>Approved value</span><strong class="num">{{ money(p.stats.approved_value_cents) }}</strong></div>
      </section>

      <section class="two">
        <div class="card">
          <div class="card-head"><h3 class="card-title">Claims per month</h3></div>
          <div class="chart"><Bar :data="chart" :options="options" role="img" aria-label="Monthly claims split into clean and flagged" /></div>
        </div>
        <div class="card">
          <div class="card-head"><h3 class="card-title">Open flag alerts</h3><span class="chip">{{ p.recent_flags.length }}</span></div>
          <ul v-if="p.recent_flags.length" class="alerts">
            <li v-for="f in p.recent_flags" :key="f.id">
              <RouterLink :to="`/claims/${f.claim_id}`">
                <div class="al-top"><FlagBadge :flag="f" /><span class="muted small">{{ relative(f.created_at!) }}</span></div>
                <p>{{ f.message }}</p>
                <span class="num small muted">{{ f.claim_reference }}</span>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="muted">No open alerts for this pharmacy.</p>
        </div>
      </section>

      <section class="two">
        <div class="card">
          <div class="card-head"><h3 class="card-title">Claim history</h3></div>
          <table>
            <tbody>
              <tr v-for="c in claims" :key="c.id">
                <td><RouterLink :to="`/claims/${c.id}`" class="num ref">{{ c.reference }}</RouterLink><div class="muted small">{{ dateTime(c.submitted_at) }}</div></td>
                <td>{{ c.quantity }} × {{ c.product.name }}</td>
                <td class="num right">{{ money(c.amount_cents) }}</td>
                <td><StatusPill :status="c.status" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="card">
          <div class="card-head"><h3 class="card-title">Stock on hand</h3></div>
          <ul class="stock">
            <li v-for="i in p.inventory" :key="i.id" :class="{ low: i.low_stock, out: i.quantity_on_hand === 0 }">
              <span class="pname">{{ i.product?.name }}</span>
              <div class="bar"><span :style="{ width: `${Math.min(100, (i.quantity_on_hand / 220) * 100)}%` }" /></div>
              <span class="num qty">{{ i.quantity_on_hand }}</span>
            </li>
          </ul>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.pd { display: flex; flex-direction: column; gap: 16px; }
.back { display: inline-flex; gap: 6px; align-items: center; font-size: 14px; font-weight: 500; }
.header { display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; padding: 26px; }
.card--ink { background: var(--ink); color: #f2f3ee; }
.id { display: flex; gap: 16px; align-items: center; }
.avatar { width: 64px; height: 64px; border-radius: 20px; background: var(--lime); color: var(--ink); display: grid; place-items: center; font-weight: 800; font-size: 22px; }
.header h2 { font-size: 28px; }
.meta { color: #9a9e95; display: flex; align-items: center; gap: 6px; font-size: 13.5px; flex-wrap: wrap; margin-top: 4px; }
.risk-box { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.lbl { color: #9a9e95; font-size: 12.5px; }
.score { font-size: 56px; line-height: 1; color: var(--lime); }
.score.hot { color: #ff7a76; }
.stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.stat { display: flex; flex-direction: column; gap: 6px; padding: 18px; }
.stat span { font-size: 13px; }
.stat strong { font-size: 24px; }
.stat.lime { background: var(--lime); }
.red { color: var(--coral); }
.two { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr); gap: 16px; }
.chart { height: 240px; }
.alerts { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow: auto; }
.alerts a { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border-radius: 14px; background: var(--surface-2); }
.alerts p { font-size: 13.5px; }
.al-top { display: flex; justify-content: space-between; align-items: center; }
.small { font-size: 12px; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
td { padding: 10px 6px; border-bottom: 1px solid var(--line); }
.ref { font-weight: 600; }
.right { text-align: right; }
.stock { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.stock li { display: grid; grid-template-columns: 1fr 90px 40px; gap: 10px; align-items: center; font-size: 13.5px; }
.pname { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bar { height: 8px; background: var(--surface-2); border-radius: 999px; overflow: hidden; }
.bar span { display: block; height: 100%; background: var(--sage); border-radius: 999px; }
.low .bar span { background: #f2b33d; }
.out .pname, .out .qty { color: var(--coral); font-weight: 600; }
.qty { text-align: right; }
@media (max-width: 1100px) { .two { grid-template-columns: 1fr; } .stats { grid-template-columns: repeat(2, 1fr); } }
</style>

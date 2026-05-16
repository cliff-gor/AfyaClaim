<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bar } from 'vue-chartjs'
import { ArrowUpRight, ShieldAlert, Timer, TrendingUp, Wallet } from 'lucide-vue-next'
import { AnalyticsApi, ClaimsApi, PharmaciesApi } from '@/api/endpoints'
import type { ClaimRow, Overview, Pharmacy, TrendPoint } from '@/api/types'
import KpiCard from '@/components/KpiCard.vue'
import RiskMeter from '@/components/RiskMeter.vue'
import FlagBadge from '@/components/FlagBadge.vue'
import Skeleton from '@/components/Skeleton.vue'
import { money, moneyCompact, number, percent, relative } from '@/utils/format'
import { axis, palette } from '@/utils/charts'

const overview = ref<Overview | null>(null)
const trend = ref<TrendPoint[]>([])
const risky = ref<Pharmacy[]>([])
const flagged = ref<ClaimRow[]>([])
const error = ref('')

onMounted(async () => {
  try {
    const [o, t, p, c] = await Promise.all([
      AnalyticsApi.overview(),
      AnalyticsApi.trend(30),
      PharmaciesApi.list({ per_page: 5 }),
      ClaimsApi.list({ status: 'submitted,under_review', flagged: 'true', per_page: 6, sort: 'risk' }),
    ])
    overview.value = o.data
    trend.value = t.data
    risky.value = p.data
    flagged.value = c.data
  } catch (e) {
    error.value = (e as Error).message
  }
})

const chartData = computed(() => ({
  labels: trend.value.map((d) => new Date(d.date).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' })),
  datasets: [
    {
      type: 'bar' as const,
      label: 'Claims submitted',
      data: trend.value.map((d) => d.claims),
      backgroundColor: trend.value.map((_, i) => (i === trend.value.length - 1 ? palette.lime : palette.ink)),
      borderRadius: 6,
      barPercentage: 0.62,
      yAxisID: 'y',
    },
    {
      type: 'line' as const,
      label: 'Malaria positivity',
      data: trend.value.map((d) => (d.positivity === null ? null : Math.round(d.positivity * 100))),
      borderColor: palette.coral,
      backgroundColor: palette.coral,
      borderWidth: 2,
      pointRadius: 0,
      tension: 0.35,
      yAxisID: 'y1',
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { position: 'top' as const, align: 'end' as const } },
  scales: {
    x: { ...axis, grid: { display: false }, ticks: { maxTicksLimit: 8, padding: 6 } },
    y: { ...axis, beginAtZero: true, title: { display: true, text: 'Claims / day' } },
    y1: { ...axis, position: 'right' as const, beginAtZero: true, max: 100, grid: { display: false }, ticks: { callback: (v: string | number) => `${v}%` } },
  },
}
</script>

<template>
  <div class="overview">
    <p v-if="error" class="card error" role="alert">{{ error }}</p>

    <section class="kpis">
      <template v-if="overview">
        <KpiCard tone="ink" label="Awaiting review" :value="number(overview.pending_review)" dot>
          <template #icon><RouterLink to="/claims" class="kpi-link" aria-label="Open review queue"><ArrowUpRight :size="18" /></RouterLink></template>
          <template #hint><ShieldAlert :size="14" /> {{ overview.flagged_pending }} flagged by fraud checks</template>
        </KpiCard>
        <KpiCard tone="lime" label="Approved, awaiting payout" :value="moneyCompact(overview.awaiting_payout_cents)">
          <template #icon><Wallet :size="18" /></template>
          <template #hint>{{ money(overview.paid_cents) }} paid via M-Pesa in 30 days</template>
        </KpiCard>
        <KpiCard label="Approval rate · 30d" :value="percent(overview.approval_rate)">
          <template #icon><TrendingUp :size="18" class="muted" /></template>
          <template #hint>{{ number(overview.claims_in_period) }} claims · {{ moneyCompact(overview.claims_value_cents) }}</template>
        </KpiCard>
        <KpiCard label="Median time to decision" :value="overview.median_review_hours === null ? '—' : `${overview.median_review_hours}h`">
          <template #icon><Timer :size="18" class="muted" /></template>
          <template #hint>Flag rate {{ percent(overview.flag_rate) }} · {{ overview.stockouts }} stock-outs</template>
        </KpiCard>
      </template>
      <template v-else>
        <div v-for="i in 4" :key="i" class="card"><Skeleton :height="104" /></div>
      </template>
    </section>

    <section class="row">
      <div class="card trend">
        <div class="card-head">
          <div>
            <h2 class="card-title">Claims &amp; malaria positivity</h2>
            <p class="muted small">Last 30 days, all counties</p>
          </div>
          <RouterLink to="/analytics" class="btn btn--ghost btn--sm">Analytics <ArrowUpRight :size="14" /></RouterLink>
        </div>
        <div class="chart">
          <Bar v-if="trend.length" :data="chartData as any" :options="chartOptions as any" aria-label="Daily claims and malaria positivity over 30 days" role="img" />
          <Skeleton v-else :height="260" />
        </div>
      </div>

      <div class="card risky">
        <div class="card-head">
          <div>
            <h2 class="card-title">Pharmacies to watch</h2>
            <p class="muted small">Highest 30-day risk score</p>
          </div>
        </div>
        <ul class="risk-list">
          <li v-for="p in risky" :key="p.id">
            <RouterLink :to="`/pharmacies/${p.id}`" class="risk-row">
              <div class="risk-name">
                <strong>{{ p.name }}</strong>
                <span class="muted small">{{ p.town }}, {{ p.region.name }} · {{ p.stats.flagged }} flagged</span>
              </div>
              <RiskMeter :score="p.risk_score" />
            </RouterLink>
          </li>
        </ul>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <h2 class="card-title">Flagged claims waiting for you</h2>
          <p class="muted small">Sorted by number of open flags</p>
        </div>
        <RouterLink :to="{ name: 'claims', query: { flagged: 'true' } }" class="btn btn--lime btn--sm">Review flagged <ArrowUpRight :size="14" /></RouterLink>
      </div>
      <div class="flag-grid">
        <RouterLink v-for="c in flagged" :key="c.id" :to="`/claims/${c.id}`" class="flag-card">
          <div class="fc-top">
            <span class="num ref">{{ c.reference }}</span>
            <span class="muted small">{{ relative(c.submitted_at) }}</span>
          </div>
          <strong>{{ c.pharmacy.name }}</strong>
          <span class="muted small">{{ c.quantity }} × {{ c.product.name }} · <span class="num">{{ money(c.amount_cents) }}</span></span>
          <FlagBadge v-if="c.top_flag" :flag="c.top_flag" />
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.overview { display: flex; flex-direction: column; gap: 16px; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.kpi-link { width: 32px; height: 32px; border-radius: 50%; background: #23261f; color: var(--lime); display: grid; place-items: center; }
.row { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(0, 1fr); gap: 16px; }
.chart { height: 280px; }
.small { font-size: 12.5px; }
.risk-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.risk-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; border-radius: 16px; transition: background 150ms; }
.risk-row:hover { background: var(--surface-2); }
.risk-name { display: flex; flex-direction: column; min-width: 0; }
.risk-name strong { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.flag-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.flag-card { border: 1px solid var(--line); border-radius: 18px; padding: 14px; display: flex; flex-direction: column; gap: 6px; transition: border-color 150ms, transform 150ms var(--ease); }
.flag-card:hover { border-color: var(--sage); transform: translateY(-1px); }
.fc-top { display: flex; justify-content: space-between; }
.ref { font-size: 12.5px; color: var(--muted); }
.flag-card .flag { align-self: flex-start; margin-top: 4px; }
.error { color: var(--coral); }
@media (max-width: 1200px) {
  .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .row { grid-template-columns: 1fr; }
  .flag-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .kpis, .flag-grid { grid-template-columns: 1fr; }
}
</style>

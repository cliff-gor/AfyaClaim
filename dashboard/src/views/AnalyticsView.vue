<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Bar, Doughnut, Line } from 'vue-chartjs'
import { Download, PackageX } from 'lucide-vue-next'
import { AnalyticsApi } from '@/api/endpoints'
import type { Overview, ProductRow, RegionRow, Stockout, TrendPoint } from '@/api/types'
import Skeleton from '@/components/Skeleton.vue'
import { humanize, money, moneyCompact, number, percent, relative } from '@/utils/format'
import { axis, categoryColors, palette } from '@/utils/charts'

const days = ref(30)
const overview = ref<Overview | null>(null)
const regions = ref<RegionRow[]>([])
const products = ref<ProductRow[]>([])
const trend = ref<TrendPoint[]>([])
const stockouts = ref<Stockout[]>([])

async function load() {
  const [o, r, p, t, s] = await Promise.all([
    AnalyticsApi.overview(days.value), AnalyticsApi.byRegion(days.value), AnalyticsApi.byProduct(days.value),
    AnalyticsApi.trend(days.value), AnalyticsApi.stockouts(),
  ])
  overview.value = o.data
  regions.value = [...r.data].sort((a, b) => b.claims - a.claims)
  products.value = p.data
  trend.value = t.data
  stockouts.value = s.data
}
watch(days, load)
onMounted(load)

const regionChart = computed(() => ({
  labels: regions.value.map((r) => r.region),
  datasets: [
    { label: 'Clean claims', data: regions.value.map((r) => r.claims - r.flagged), backgroundColor: palette.ink, borderRadius: 6, stack: 'a' },
    { label: 'Flagged', data: regions.value.map((r) => r.flagged), backgroundColor: palette.coral, borderRadius: 6, stack: 'a' },
  ],
}))
const regionOptions = {
  indexAxis: 'y' as const, responsive: true, maintainAspectRatio: false,
  plugins: { legend: { position: 'top' as const, align: 'end' as const } },
  scales: { x: { ...axis, stacked: true }, y: { ...axis, stacked: true, grid: { display: false } } },
}

const byCategory = computed(() => {
  const acc: Record<string, number> = {}
  products.value.forEach((p) => (acc[p.category] = (acc[p.category] ?? 0) + p.value_cents))
  return Object.entries(acc).sort((a, b) => b[1] - a[1])
})
const donut = computed(() => ({
  labels: byCategory.value.map(([c]) => humanize(c)),
  datasets: [{ data: byCategory.value.map(([, v]) => Math.round(v / 100)), backgroundColor: byCategory.value.map(([c]) => categoryColors[c]), borderWidth: 0, spacing: 3, borderRadius: 8 }],
}))
const donutOptions = { responsive: true, maintainAspectRatio: false, cutout: '68%', plugins: { legend: { position: 'bottom' as const } } }

const positivity = computed(() => ({
  labels: trend.value.map((d) => new Date(d.date).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' })),
  datasets: [{
    label: 'RDT positivity', data: trend.value.map((d) => (d.positivity === null ? null : +(d.positivity * 100).toFixed(1))),
    borderColor: palette.moss, backgroundColor: 'rgba(200, 241, 105, 0.35)', fill: true, tension: 0.35, pointRadius: 0, borderWidth: 2,
  }],
}))
const lineOptions = {
  responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } },
  scales: { x: { ...axis, grid: { display: false }, ticks: { maxTicksLimit: 8 } }, y: { ...axis, beginAtZero: true, ticks: { callback: (v: string | number) => `${v}%` } } },
}

const outCount = computed(() => stockouts.value.filter((s) => s.stocked_out).length)

function exportCsv() {
  const header = 'region,claims,flagged,value_kes\n'
  const body = regions.value.map((r) => `${r.region},${r.claims},${r.flagged},${r.value_cents / 100}`).join('\n')
  const url = URL.createObjectURL(new Blob([header + body], { type: 'text/csv' }))
  const a = Object.assign(document.createElement('a'), { href: url, download: `afyaclaim-regions-${days.value}d.csv` })
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="an">
    <div class="bar">
      <div class="seg" role="radiogroup" aria-label="Time range">
        <button v-for="d in [7, 30, 90]" :key="d" role="radio" :aria-checked="days === d" :class="{ on: days === d }" @click="days = d">{{ d }} days</button>
      </div>
      <button class="btn btn--ghost btn--sm" @click="exportCsv"><Download :size="14" /> Export CSV</button>
    </div>

    <section class="kpis">
      <div class="card k ink">
        <span>Claims in period</span>
        <strong class="dot">{{ overview ? number(overview.claims_in_period) : '—' }}</strong>
        <small>{{ overview ? moneyCompact(overview.claims_value_cents) : '' }} in subsidies</small>
      </div>
      <div class="card k"><span>Flag rate</span><strong class="num">{{ overview ? percent(overview.flag_rate) : '—' }}</strong><small>claims caught by fraud checks</small></div>
      <div class="card k"><span>Approval rate</span><strong class="num">{{ overview ? percent(overview.approval_rate) : '—' }}</strong><small>of decided claims</small></div>
      <div class="card k"><span>Stock-outs</span><strong class="num red">{{ outCount }}</strong><small>{{ stockouts.length }} below reorder level</small></div>
    </section>

    <section class="two">
      <div class="card">
        <div class="card-head"><div><h2 class="card-title">Claims by county</h2><p class="muted small">Where subsidies are flowing, and where the flags are</p></div></div>
        <div class="chart tall"><Bar v-if="regions.length" :data="regionChart" :options="regionOptions" role="img" aria-label="Claims by county" /><Skeleton v-else :height="320" /></div>
      </div>
      <div class="card">
        <div class="card-head"><div><h2 class="card-title">Subsidy spend by programme</h2><p class="muted small">KES, {{ days }} days</p></div></div>
        <div class="chart tall"><Doughnut v-if="products.length" :data="donut" :options="donutOptions" role="img" aria-label="Subsidy spend by product category" /><Skeleton v-else :height="320" /></div>
      </div>
    </section>

    <section class="two">
      <div class="card">
        <div class="card-head"><div><h2 class="card-title">Malaria RDT positivity</h2><p class="muted small">A rising line is an early outbreak signal for county health teams</p></div></div>
        <div class="chart"><Line v-if="trend.length" :data="positivity" :options="lineOptions" role="img" aria-label="Malaria test positivity trend" /><Skeleton v-else :height="240" /></div>
      </div>
      <div class="card">
        <div class="card-head"><div><h2 class="card-title">Top products</h2><p class="muted small">By claim volume</p></div></div>
        <table class="tbl">
          <thead><tr><th>Product</th><th class="right">Claims</th><th class="right">Value</th></tr></thead>
          <tbody>
            <tr v-for="p in products.slice(0, 7)" :key="p.product_id">
              <td><span class="sw" :style="{ background: categoryColors[p.category] }" />{{ p.product }}</td>
              <td class="num right">{{ number(p.claims) }}</td>
              <td class="num right">{{ moneyCompact(p.value_cents) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <div><h2 class="card-title"><PackageX :size="18" /> Stock-outs &amp; low stock</h2><p class="muted small">Subsidised products at or below reorder level</p></div>
      </div>
      <table class="tbl">
        <thead><tr><th>Pharmacy</th><th>County</th><th>Product</th><th class="right">On hand</th><th class="right">Reorder at</th><th>Updated</th></tr></thead>
        <tbody>
          <tr v-for="s in stockouts" :key="`${s.pharmacy_id}-${s.product}`">
            <td><RouterLink :to="`/pharmacies/${s.pharmacy_id}`" class="link">{{ s.pharmacy }}</RouterLink></td>
            <td>{{ s.region }}</td>
            <td>{{ s.product }}</td>
            <td class="num right"><span class="chip" :class="s.stocked_out ? 'out' : 'low'">{{ s.stocked_out ? 'Out' : s.quantity_on_hand }}</span></td>
            <td class="num right">{{ s.reorder_level }}</td>
            <td class="muted">{{ relative(s.updated_at) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="overview" class="muted foot">Total paid to pharmacies in period: <strong class="num">{{ money(overview.paid_cents) }}</strong></p>
    </section>
  </div>
</template>

<style scoped>
.an { display: flex; flex-direction: column; gap: 16px; }
.bar { display: flex; justify-content: space-between; align-items: center; }
.seg { display: flex; background: var(--surface); padding: 4px; border-radius: 999px; box-shadow: var(--shadow); }
.seg button { border: 0; background: none; height: 34px; padding: 0 16px; border-radius: 999px; font-weight: 600; font-size: 13.5px; color: var(--muted); }
.seg .on { background: var(--lime); color: var(--ink); }
.kpis { display: grid; grid-template-columns: 1.3fr 1fr 1fr 1fr; gap: 16px; }
.k { display: flex; flex-direction: column; gap: 6px; }
.k span { font-size: 13.5px; color: var(--muted); }
.k strong { font-size: 32px; letter-spacing: -0.03em; }
.k small { font-size: 12.5px; color: var(--muted); }
.k.ink { background: var(--ink); color: #f2f3ee; }
.k.ink strong { color: var(--lime); font-size: 46px; line-height: 1; }
.k.ink span, .k.ink small { color: #9a9e95; }
.red { color: var(--coral); }
.two { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 16px; }
.chart { height: 260px; }
.chart.tall { height: 340px; }
.small { font-size: 12.5px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.tbl th { text-align: left; font-size: 12px; color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: .05em; padding: 8px 6px; border-bottom: 1px solid var(--line); }
.tbl td { padding: 10px 6px; border-bottom: 1px solid var(--line); }
.right { text-align: right !important; }
.sw { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 8px; }
.out { background: var(--coral-soft); color: var(--coral); }
.low { background: var(--amber-soft); color: var(--amber); }
.link { font-weight: 600; }
.foot { margin-top: 12px; font-size: 13.5px; }
@media (max-width: 1100px) { .two { grid-template-columns: 1fr; } .kpis { grid-template-columns: 1fr 1fr; } }
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CircleDollarSign, Loader2, Send } from 'lucide-vue-next'
import { AnalyticsApi, ReimbursementsApi } from '@/api/endpoints'
import type { Reimbursement } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import EmptyState from '@/components/EmptyState.vue'
import { dateTime, humanize, money } from '@/utils/format'

const auth = useAuthStore()
const toast = useToastStore()
const rows = ref<Reimbursement[]>([])
const awaiting = ref(0)
const busy = ref(false)

async function load() {
  const [r, o] = await Promise.all([ReimbursementsApi.list({}), AnalyticsApi.overview()])
  rows.value = r.data
  awaiting.value = o.data.awaiting_payout_cents
}
onMounted(load)

const paidTotal = computed(() => rows.value.filter((r) => r.status === 'paid').reduce((s, r) => s + r.amount_cents, 0))

async function runBatch() {
  busy.value = true
  try {
    const res = await ReimbursementsApi.create()
    toast.success(res.data.length ? `${res.data.length} payouts queued for M-Pesa` : 'Nothing to pay — no approved claims waiting')
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="po">
    <section class="hero card">
      <div>
        <span class="lbl">Approved and waiting for payout</span>
        <div class="dot big">{{ money(awaiting).replace('KES ', '') }}</div>
        <span class="lbl">KES · ledger is debited before any M-Pesa B2C request is sent</span>
      </div>
      <div class="cta">
        <button class="btn btn--lime" :disabled="busy || !auth.isAdmin || !awaiting" @click="runBatch">
          <component :is="busy ? Loader2 : Send" :size="16" :class="{ spin: busy }" /> Pay approved claims
        </button>
        <small v-if="!auth.isAdmin" class="lbl">Admins only</small>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <div><h2 class="card-title"><CircleDollarSign :size="18" /> Reimbursements</h2><p class="muted small">Showing latest {{ rows.length }} · {{ money(paidTotal) }} paid</p></div>
      </div>
      <table v-if="rows.length">
        <thead><tr><th>#</th><th>Pharmacy</th><th class="right">Claims</th><th class="right">Amount</th><th>Status</th><th>M-Pesa receipt</th><th>Paid</th></tr></thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="num muted">{{ r.id }}</td>
            <td><strong>{{ r.pharmacy.name }}</strong><div class="muted small num">{{ r.pharmacy.mpesa_phone }}</div></td>
            <td class="num right">{{ r.claims_count }}</td>
            <td class="num right">{{ money(r.amount_cents) }}</td>
            <td><span class="chip" :class="r.status">{{ humanize(r.status) }}</span></td>
            <td class="num">{{ r.mpesa_receipt ?? '—' }}</td>
            <td class="muted">{{ r.paid_at ? dateTime(r.paid_at) : r.failure_reason ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-else title="No payouts yet" body="Approved claims will appear here once a batch is run." />
    </section>
  </div>
</template>

<style scoped>
.po { display: flex; flex-direction: column; gap: 16px; }
.hero { background: var(--ink); color: #f2f3ee; display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; flex-wrap: wrap; padding: 28px; }
.lbl { color: #9a9e95; font-size: 13px; }
.big { font-size: 64px; color: var(--lime); line-height: 1.05; margin: 6px 0; }
.cta { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.cta .btn { height: 48px; padding: 0 22px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.small { font-size: 12.5px; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .05em; font-weight: 600; padding: 8px; border-bottom: 1px solid var(--line); }
td { padding: 12px 8px; border-bottom: 1px solid var(--line); }
.right { text-align: right; }
.paid { background: var(--ink); color: var(--lime); }
.processing, .pending { background: var(--amber-soft); color: var(--amber); }
.failed { background: var(--coral-soft); color: var(--coral); }
</style>

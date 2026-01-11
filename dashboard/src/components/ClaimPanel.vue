<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, Fingerprint, Lock, Package, ShieldCheck, ShieldX, User, X } from 'lucide-vue-next'
import { ClaimsApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { ClaimDetail } from '@/api/types'
import StatusPill from './StatusPill.vue'
import FlagBadge from './FlagBadge.vue'
import Skeleton from './Skeleton.vue'
import { useToastStore } from '@/stores/toast'
import { dateTime, humanize, money } from '@/utils/format'

const props = defineProps<{ claimId: number }>()
const emit = defineEmits<{ close: []; changed: [claim: ClaimDetail] }>()
const toast = useToastStore()

const claim = ref<ClaimDetail | null>(null)
const busy = ref(false)
const mode = ref<'idle' | 'reject' | 'override'>('idle')
const reason = ref('')
const note = ref('')

const REASONS = [
  'Sale record does not match claimed quantity',
  'Duplicate of an earlier claim for the same patient',
  'Claimed amount exceeds programme rate',
  'Receipt photo unreadable — please resubmit',
]

async function load() {
  claim.value = null
  mode.value = 'idle'
  reason.value = ''
  note.value = ''
  claim.value = (await ClaimsApi.get(props.claimId)).data
}
watch(() => props.claimId, load, { immediate: true })

const open = computed(() => claim.value && ['submitted', 'under_review'].includes(claim.value.status))
const blocking = computed(() => claim.value?.flags.some((f) => f.severity === 'high' && !f.resolved) ?? false)

async function run(action: () => Promise<{ data: ClaimDetail }>, success: string) {
  busy.value = true
  try {
    claim.value = (await action()).data
    mode.value = 'idle'
    toast.success(success)
    emit('changed', claim.value)
  } catch (e) {
    const err = e as ApiError
    if (err.code === 'blocked_by_flags') mode.value = 'override'
    toast.error(err.message)
  } finally {
    busy.value = false
  }
}

const approve = () => {
  if (blocking.value && mode.value !== 'override') {
    mode.value = 'override'
    return
  }
  run(() => ClaimsApi.approve(props.claimId, { note: note.value || undefined, override: mode.value === 'override' }),
    `${claim.value?.reference} approved`)
}
const reject = () => {
  if (mode.value !== 'reject') {
    mode.value = 'reject'
    return
  }
  run(() => ClaimsApi.reject(props.claimId, reason.value), `${claim.value?.reference} rejected`)
}
const startReview = () => run(() => ClaimsApi.startReview(props.claimId), 'Marked as under review')

defineExpose({ approve, reject })
</script>

<template>
  <aside class="panel" aria-label="Claim detail">
    <div v-if="!claim" class="pad"><Skeleton :height="28" /><br /><Skeleton :height="140" :radius="20" /><br /><Skeleton :height="200" :radius="20" /></div>
    <template v-else>
      <header class="head">
        <div>
          <span class="num ref">{{ claim.reference }}</span>
          <h2>{{ claim.pharmacy.name }}</h2>
          <p class="muted">{{ claim.pharmacy.town }} · submitted {{ dateTime(claim.submitted_at) }}</p>
        </div>
        <button class="btn btn--ghost btn--icon" aria-label="Close panel" @click="emit('close')"><X :size="18" /></button>
      </header>

      <div class="body">
        <div class="hero card--ink">
          <div>
            <span class="label">Claim value</span>
            <div class="dot amount">{{ money(claim.amount_cents).replace('KES ', '') }}</div>
            <span class="label">KES · {{ claim.quantity }} × {{ claim.product.name }}</span>
          </div>
          <StatusPill :status="claim.status" />
        </div>

        <section v-if="claim.flags.length" class="block">
          <h3>Fraud &amp; quality checks</h3>
          <ul class="flags">
            <li v-for="f in claim.flags" :key="f.id" :class="{ resolved: f.resolved }">
              <FlagBadge :flag="f" />
              <p>{{ f.message }}</p>
            </li>
          </ul>
        </section>
        <section v-else class="block clean"><ShieldCheck :size="18" /> All 7 checks passed</section>

        <section class="block">
          <h3>Linked sale</h3>
          <div v-if="claim.sale" class="sale">
            <div class="kv"><span>Sold</span><strong>{{ dateTime(claim.sale.sold_at) }}</strong></div>
            <div class="kv"><span><User :size="14" /> Patient</span><strong>{{ claim.sale.patient ? `${claim.sale.patient.sex}, ${claim.sale.patient.age_band}` : 'Walk-in' }}</strong></div>
            <div v-if="claim.sale.patient" class="kv"><span><Fingerprint :size="14" /> Pseudonym</span><strong class="num">{{ claim.sale.patient.pseudonym }}…</strong></div>
            <div v-if="claim.test_result" class="kv"><span>RDT result</span><strong :class="claim.test_result">{{ humanize(claim.test_result) }}</strong></div>
            <ul class="items">
              <li v-for="i in claim.sale.items" :key="i.product_id">
                <Package :size="14" /> <span>{{ i.quantity }} × {{ i.product_name }}</span>
                <span class="num">{{ money(i.unit_price_cents * i.quantity) }}</span>
              </li>
            </ul>
          </div>
          <p v-else class="nosale">No sale recorded for this claim.</p>
        </section>

        <section class="block">
          <h3 class="audit-head">
            Audit trail
            <span class="chip" :class="claim.audit_intact ? 'ok' : 'bad'">
              <component :is="claim.audit_intact ? Lock : ShieldX" :size="12" />{{ claim.audit_intact ? 'Chain verified' : 'Chain broken' }}
            </span>
          </h3>
          <ol class="timeline">
            <li v-for="e in claim.events" :key="e.id">
              <span class="node" :class="e.to_status" />
              <div>
                <strong>{{ humanize(e.to_status) }}</strong>
                <span class="muted"> · {{ e.actor }} · {{ dateTime(e.created_at) }}</span>
                <p v-if="e.note" class="note">“{{ e.note }}”</p>
                <p v-if="e.metadata?.override" class="note warn">Override of {{ (e.metadata.overridden_flags as string[]).join(', ') }}</p>
                <code class="digest">{{ e.digest.slice(0, 16) }}</code>
              </div>
            </li>
          </ol>
        </section>
      </div>

      <footer v-if="open" class="actions">
        <div v-if="mode === 'reject'" class="extra">
          <label for="reason">Reason for rejection <span aria-hidden="true">*</span></label>
          <div class="reasons">
            <button v-for="r in REASONS" :key="r" type="button" class="chip pick" :class="{ on: reason === r }" @click="reason = r">{{ r }}</button>
          </div>
          <textarea id="reason" v-model="reason" class="input area" rows="2" placeholder="Visible to the pharmacy in the POS app" />
        </div>
        <div v-if="mode === 'override'" class="extra warnbox">
          <strong>High-severity flags are open.</strong>
          <p>Approving records an override in the audit trail. Add a note explaining why.</p>
          <textarea v-model="note" class="input area" rows="2" placeholder="e.g. Outbreak confirmed with county health office" />
        </div>
        <div class="buttons">
          <button v-if="claim.status === 'submitted' && mode === 'idle'" class="btn btn--ghost" :disabled="busy" @click="startReview">Start review</button>
          <button v-if="mode !== 'idle'" class="btn btn--ghost" :disabled="busy" @click="mode = 'idle'">Cancel</button>
          <button v-if="mode !== 'override'" class="btn btn--danger" :disabled="busy || (mode === 'reject' && !reason.trim())" @click="reject">
            <X :size="16" /> {{ mode === 'reject' ? 'Confirm reject' : 'Reject' }} <kbd v-if="mode === 'idle'">R</kbd>
          </button>
          <button v-if="mode !== 'reject'" class="btn btn--lime" :disabled="busy || (mode === 'override' && !note.trim())" @click="approve">
            <Check :size="16" /> {{ mode === 'override' ? 'Approve with override' : 'Approve' }} <kbd v-if="mode === 'idle'">A</kbd>
          </button>
        </div>
      </footer>
      <footer v-else-if="claim.rejection_reason" class="actions closed"><strong>Rejected:</strong> {{ claim.rejection_reason }}</footer>
      <footer v-else-if="claim.reimbursement" class="actions closed">
        Reimbursement #{{ claim.reimbursement.id }} · {{ humanize(claim.reimbursement.status) }}
        <span v-if="claim.reimbursement.mpesa_receipt" class="num"> · M-Pesa {{ claim.reimbursement.mpesa_receipt }}</span>
      </footer>
    </template>
  </aside>
</template>

<style scoped>
.panel { background: var(--surface); border-radius: var(--radius-lg); box-shadow: var(--shadow); display: flex; flex-direction: column; max-height: calc(100vh - 140px); position: sticky; top: 16px; overflow: hidden; }
.pad { padding: 22px; }
.head { display: flex; justify-content: space-between; gap: 12px; padding: 20px 22px 12px; }
.head h2 { font-size: 20px; margin-top: 2px; }
.head p { font-size: 13px; }
.ref { font-size: 12.5px; color: var(--muted); }
.body { overflow-y: auto; padding: 0 22px 16px; display: flex; flex-direction: column; gap: 18px; }
.hero { border-radius: 20px; padding: 18px; display: flex; justify-content: space-between; align-items: flex-start; }
.card--ink { background: var(--ink); color: #f2f3ee; }
.label { color: #9a9e95; font-size: 12.5px; }
.amount { font-size: 44px; color: var(--lime); line-height: 1.1; margin: 4px 0; }
.block h3 { font-size: 13px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); margin-bottom: 10px; font-weight: 600; }
.flags { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.flags li { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
.flags p { font-size: 13.5px; }
.flags .resolved { opacity: 0.5; }
.clean { display: flex; gap: 8px; align-items: center; color: var(--moss); background: var(--lime-soft); padding: 12px 14px; border-radius: 14px; font-weight: 600; font-size: 14px; }
.sale { background: var(--surface-2); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; }
.kv { display: flex; justify-content: space-between; gap: 8px; font-size: 13.5px; }
.kv span { color: var(--muted); display: flex; gap: 6px; align-items: center; }
.positive { color: var(--coral); }
.negative { color: var(--moss); }
.items { list-style: none; margin: 6px 0 0; padding: 8px 0 0; border-top: 1px dashed var(--line); display: flex; flex-direction: column; gap: 6px; }
.items li { display: flex; gap: 8px; align-items: center; font-size: 13.5px; }
.items li span:first-of-type { flex: 1; }
.nosale { background: var(--coral-soft); color: var(--coral); padding: 12px 14px; border-radius: 14px; font-size: 13.5px; font-weight: 500; }
.audit-head { display: flex; align-items: center; justify-content: space-between; }
.audit-head .chip { text-transform: none; letter-spacing: 0; font-size: 11.5px; height: 22px; }
.ok { background: var(--lime-soft); color: var(--moss); }
.bad { background: var(--coral-soft); color: var(--coral); }
.timeline { list-style: none; margin: 0; padding: 0 0 0 4px; display: flex; flex-direction: column; }
.timeline li { display: flex; gap: 12px; position: relative; padding-bottom: 14px; font-size: 13.5px; }
.timeline li:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 14px; bottom: 0; width: 2px; background: var(--line); }
.node { width: 12px; height: 12px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; background: var(--lavender); border: 2px solid var(--indigo); }
.node.under_review { background: var(--amber-soft); border-color: #f2b33d; }
.node.approved { background: var(--lime); border-color: var(--moss); }
.node.rejected { background: var(--coral-soft); border-color: var(--coral); }
.node.paid { background: var(--ink); border-color: var(--ink); }
.note { font-style: italic; color: var(--muted); margin-top: 2px; }
.warn { color: var(--amber); font-style: normal; }
.digest { display: block; margin-top: 2px; font-family: var(--mono); font-size: 11px; color: var(--muted); opacity: 0.7; }
.actions { border-top: 1px solid var(--line); padding: 14px 22px; display: flex; flex-direction: column; gap: 10px; }
.buttons { display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; }
kbd { font-family: var(--mono); font-size: 11px; background: rgba(0,0,0,.08); padding: 1px 6px; border-radius: 6px; }
.extra { display: flex; flex-direction: column; gap: 8px; }
.extra label { font-size: 13px; font-weight: 600; }
.reasons { display: flex; flex-wrap: wrap; gap: 6px; }
.pick { border: 1px solid var(--line); background: var(--surface); cursor: pointer; height: auto; padding: 6px 10px; white-space: normal; text-align: left; }
.pick.on { background: var(--coral-soft); color: var(--coral); border-color: transparent; }
.area { height: auto; padding: 10px 14px; resize: vertical; }
.warnbox { background: var(--amber-soft); padding: 12px; border-radius: 14px; font-size: 13.5px; }
.closed { font-size: 13.5px; color: var(--muted); }
</style>

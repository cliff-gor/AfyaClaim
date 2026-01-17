<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCheck, ChevronLeft, ChevronRight, Filter, ShieldAlert } from 'lucide-vue-next'
import { ClaimsApi } from '@/api/endpoints'
import type { ClaimDetail, ClaimRow, Meta } from '@/api/types'
import StatusPill from '@/components/StatusPill.vue'
import FlagBadge from '@/components/FlagBadge.vue'
import ClaimPanel from '@/components/ClaimPanel.vue'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import { useToastStore } from '@/stores/toast'
import { money, relative } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const TABS = [
  { key: 'submitted,under_review', label: 'To review' },
  { key: 'approved', label: 'Approved' },
  { key: 'rejected', label: 'Rejected' },
  { key: 'paid', label: 'Paid' },
  { key: '', label: 'All' },
]

const filters = reactive({
  status: (route.query.status as string) ?? 'submitted,under_review',
  flagged: (route.query.flagged as string) ?? '',
  q: (route.query.q as string) ?? '',
  page: 1,
})
const rows = ref<ClaimRow[]>([])
const meta = ref<Meta | null>(null)
const loading = ref(true)
const selected = ref<Set<number>>(new Set())
const cursor = ref(0)
const panel = ref<InstanceType<typeof ClaimPanel> | null>(null)

const activeId = computed(() => (route.params.id ? Number(route.params.id) : null))
const selectable = computed(() => rows.value.filter((r) => r.flags_count === 0 && ['submitted', 'under_review'].includes(r.status)))
const allSelected = computed(() => selectable.value.length > 0 && selectable.value.every((r) => selected.value.has(r.id)))

async function load() {
  loading.value = true
  try {
    const res = await ClaimsApi.list({ ...filters, per_page: 20, sort: filters.flagged === 'true' ? 'risk' : undefined })
    rows.value = res.data
    meta.value = res.meta
    selected.value = new Set()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

watch(() => [filters.status, filters.flagged, filters.q], () => {
  filters.page = 1
  router.replace({ query: { status: filters.status || undefined, flagged: filters.flagged || undefined, q: filters.q || undefined } })
  load()
})
watch(() => route.query.q, (q) => { if ((q ?? '') !== filters.q) filters.q = (q as string) ?? '' })
watch(() => filters.page, load)
onMounted(load)

function open(row: ClaimRow, index?: number) {
  if (index !== undefined) cursor.value = index
  router.push({ name: 'claim', params: { id: row.id }, query: route.query })
}

function close() {
  router.push({ name: 'claims', query: route.query })
}

function onChanged(updated: ClaimDetail) {
  const i = rows.value.findIndex((r) => r.id === updated.id)
  if (i >= 0) rows.value[i] = { ...rows.value[i], status: updated.status, flags_count: updated.flags_count }
}

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(selectable.value.map((r) => r.id))
}

async function bulkApprove() {
  const ids = [...selected.value]
  const res = await ClaimsApi.bulkApprove(ids)
  const ok = res.results.filter((r) => r.status === 'approved').length
  toast.success(`${ok} claim${ok === 1 ? '' : 's'} approved${ok < ids.length ? `, ${ids.length - ok} skipped` : ''}`)
  load()
}

function onKey(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || e.metaKey || e.ctrlKey) return
  if (e.key === 'j' || e.key === 'k') {
    cursor.value = Math.min(Math.max(cursor.value + (e.key === 'j' ? 1 : -1), 0), rows.value.length - 1)
    const row = rows.value[cursor.value]
    if (row) open(row)
  } else if (e.key === 'a' && activeId.value) panel.value?.approve()
  else if (e.key === 'r' && activeId.value) panel.value?.reject()
  else if (e.key === 'Escape' && activeId.value) close()
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="queue" :class="{ split: activeId }">
    <section class="list card">
      <div class="toolbar">
        <div class="tabs" role="tablist" aria-label="Claim status">
          <button
            v-for="t in TABS" :key="t.label" role="tab" class="tab" :class="{ on: filters.status === t.key }"
            :aria-selected="filters.status === t.key" @click="filters.status = t.key"
          >
            {{ t.label }}
          </button>
        </div>
        <div class="tools">
          <button
            class="btn btn--sm" :class="filters.flagged === 'true' ? 'btn--ink' : 'btn--ghost'" :aria-pressed="filters.flagged === 'true'"
            @click="filters.flagged = filters.flagged === 'true' ? '' : 'true'"
          >
            <ShieldAlert :size="14" /> Flagged only
          </button>
          <label class="sr-only" for="q">Filter</label>
          <div class="qbox"><Filter :size="14" /><input id="q" v-model.lazy="filters.q" class="input" placeholder="Ref or pharmacy" /></div>
        </div>
      </div>

      <div v-if="selected.size" class="bulkbar">
        <span><strong>{{ selected.size }}</strong> clean claims selected</span>
        <button class="btn btn--lime btn--sm" @click="bulkApprove"><CheckCheck :size="14" /> Approve selected</button>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="cb"><input type="checkbox" :checked="allSelected" :disabled="!selectable.length" aria-label="Select all clean claims" @change="toggleAll" /></th>
              <th>Claim</th>
              <th>Pharmacy</th>
              <th class="col-product">Product</th>
              <th class="right">Amount</th>
              <th>Checks</th>
              <th class="col-status">Status</th>
            </tr>
          </thead>
          <tbody v-if="loading">
            <tr v-for="i in 8" :key="i"><td colspan="7"><Skeleton :height="22" /></td></tr>
          </tbody>
          <tbody v-else>
            <tr
              v-for="(r, i) in rows" :key="r.id" :class="{ active: r.id === activeId, flagged: r.flags_count > 0 }"
              tabindex="0" @click="open(r, i)" @keydown.enter="open(r, i)"
            >
              <td class="cb" @click.stop>
                <input type="checkbox" :checked="selected.has(r.id)" :disabled="!selectable.includes(r)" :aria-label="`Select ${r.reference}`" @change="toggle(r.id)" />
              </td>
              <td>
                <div class="num ref">{{ r.reference }}</div>
                <div class="muted sub">{{ relative(r.submitted_at) }}</div>
              </td>
              <td>
                <div class="pname">{{ r.pharmacy.name }}</div>
                <div class="muted sub">{{ r.pharmacy.town }}<span class="split-only"> · {{ r.quantity }} × {{ r.product.name }}</span></div>
              </td>
              <td class="col-product"><span class="pname">{{ r.quantity }} × {{ r.product.name }}</span></td>
              <td class="right num">{{ money(r.amount_cents) }}</td>
              <td>
                <FlagBadge v-if="r.top_flag" :flag="r.top_flag" />
                <span v-if="r.flags_count > 1" class="more">+{{ r.flags_count - 1 }}</span>
                <span v-if="!r.flags_count" class="clean">✓ Clean</span>
              </td>
              <td class="col-status"><StatusPill :status="r.status" /></td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!loading && !rows.length" title="Queue is clear" body="No claims match these filters." />
      </div>

      <footer v-if="meta && meta.pages > 1" class="pager">
        <span class="muted">{{ (meta.page - 1) * meta.per_page + 1 }}–{{ Math.min(meta.page * meta.per_page, meta.total) }} of {{ meta.total.toLocaleString() }}</span>
        <div class="pager-btns">
          <span class="muted kbd-hint"><kbd>J</kbd>/<kbd>K</kbd> move · <kbd>A</kbd> approve · <kbd>R</kbd> reject</span>
          <button class="btn btn--ghost btn--icon btn--sm" :disabled="meta.page <= 1" aria-label="Previous page" @click="filters.page--"><ChevronLeft :size="16" /></button>
          <button class="btn btn--ghost btn--icon btn--sm" :disabled="meta.page >= meta.pages" aria-label="Next page" @click="filters.page++"><ChevronRight :size="16" /></button>
        </div>
      </footer>
    </section>

    <ClaimPanel v-if="activeId" ref="panel" :claim-id="activeId" @close="close" @changed="onChanged" />
  </div>
</template>

<style scoped>
.queue { display: grid; grid-template-columns: 1fr; gap: 16px; align-items: start; }
.queue.split { grid-template-columns: minmax(0, 1fr) 420px; }
.list { padding: 0; overflow: hidden; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 18px; flex-wrap: wrap; }
.tabs { display: flex; gap: 4px; background: var(--surface-2); padding: 4px; border-radius: 999px; }
.tab { border: 0; background: none; height: 34px; padding: 0 14px; border-radius: 999px; font-weight: 600; font-size: 13.5px; color: var(--muted); }
.tab.on { background: var(--ink); color: var(--lime); }
.tools { display: flex; gap: 8px; align-items: center; }
.qbox { position: relative; }
.qbox svg { position: absolute; left: 12px; top: 10px; color: var(--muted); }
.qbox .input { height: 34px; padding-left: 32px; width: 200px; border-radius: 999px; }
.bulkbar { display: flex; justify-content: space-between; align-items: center; background: var(--lime-soft); color: var(--moss); padding: 10px 18px; font-size: 14px; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; padding: 10px 12px; border-bottom: 1px solid var(--line); white-space: nowrap; }
td { padding: 12px; border-bottom: 1px solid var(--line); vertical-align: middle; }
tbody tr { cursor: pointer; transition: background 120ms; }
tbody tr:hover { background: var(--surface-2); }
tbody tr.active { background: var(--lime-soft); }
tbody tr.flagged td:first-child { box-shadow: inset 3px 0 0 var(--coral); }
.cb { width: 36px; padding-left: 18px; }
.cb input { width: 16px; height: 16px; accent-color: var(--moss); }
.ref { font-size: 13px; font-weight: 600; white-space: nowrap; }
.sub { font-size: 12.5px; }
.pname { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 220px; display: inline-block; vertical-align: bottom; }
.right { text-align: right; }
.more { font-size: 12px; color: var(--muted); margin-left: 6px; font-weight: 600; }
.clean { font-size: 12.5px; font-weight: 600; color: var(--moss); }
.pager { display: flex; justify-content: space-between; align-items: center; padding: 12px 18px; font-size: 13px; }
.pager-btns { display: flex; gap: 6px; align-items: center; }
.kbd-hint { margin-right: 10px; font-size: 12px; }
kbd { font-family: var(--mono); font-size: 11px; border: 1px solid var(--line); border-bottom-width: 2px; padding: 0 5px; border-radius: 5px; }
.split-only { display: none; }
.split .split-only { display: inline; }
.split .col-product, .split .col-status { display: none; }
.split .pname { max-width: 170px; }
.split .sub { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; display: block; }
.split .tools .qbox .input { width: 150px; }
@media (max-width: 1280px) {
  .queue.split { grid-template-columns: 1fr; }
}
</style>

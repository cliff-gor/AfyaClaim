<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle2, CircleDollarSign, Clock3, Eye, XCircle } from 'lucide-vue-next'
import type { ClaimStatus } from '@/api/types'

const props = defineProps<{ status: ClaimStatus }>()

const map = {
  submitted: { label: 'Submitted', icon: Clock3, cls: 'submitted' },
  under_review: { label: 'Under review', icon: Eye, cls: 'review' },
  approved: { label: 'Approved', icon: CheckCircle2, cls: 'approved' },
  rejected: { label: 'Rejected', icon: XCircle, cls: 'rejected' },
  paid: { label: 'Paid', icon: CircleDollarSign, cls: 'paid' },
} as const

const conf = computed(() => map[props.status])
</script>

<template>
  <span class="chip pill" :class="conf.cls"><component :is="conf.icon" :size="13" :stroke-width="2.4" />{{ conf.label }}</span>
</template>

<style scoped>
.submitted { background: var(--lavender); color: var(--indigo); }
.review { background: var(--amber-soft); color: var(--amber); }
.approved { background: var(--lime-soft); color: var(--moss); }
.rejected { background: var(--coral-soft); color: var(--coral); }
.paid { background: var(--ink); color: var(--lime); }
</style>

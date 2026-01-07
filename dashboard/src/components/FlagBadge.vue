<script setup lang="ts">
import { AlertTriangle, Info, ShieldAlert } from 'lucide-vue-next'
import { RULE_LABELS } from '@/utils/format'
import type { Flag } from '@/api/types'

defineProps<{ flag: Flag; showMessage?: boolean }>()
const icons = { high: ShieldAlert, medium: AlertTriangle, low: Info }
</script>

<template>
  <span class="flag" :class="flag.severity" :title="flag.message">
    <component :is="icons[flag.severity]" :size="13" :stroke-width="2.4" />
    <span>{{ RULE_LABELS[flag.rule] ?? flag.rule }}</span>
    <span v-if="showMessage" class="msg">· {{ flag.message }}</span>
  </span>
</template>

<style scoped>
.flag { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px; border-radius: 999px; font-size: 12.5px; font-weight: 600; max-width: 100%; }
.flag span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.high { background: var(--coral-soft); color: var(--coral); }
.medium { background: var(--amber-soft); color: var(--amber); }
.low { background: var(--sky-soft); color: var(--sky); }
.msg { font-weight: 500; opacity: 0.85; }
</style>

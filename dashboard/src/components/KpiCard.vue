<script setup lang="ts">
defineProps<{ label: string; value: string; hint?: string; tone?: 'ink' | 'lime' | 'plain'; dot?: boolean }>()
</script>

<template>
  <div class="kpi card" :class="tone ?? 'plain'">
    <div class="kpi-head">
      <span class="label">{{ label }}</span>
      <slot name="icon" />
    </div>
    <div class="value" :class="dot ? 'dot' : 'num'">{{ value }}</div>
    <div v-if="hint || $slots.hint" class="hint"><slot name="hint">{{ hint }}</slot></div>
  </div>
</template>

<style scoped>
.kpi { display: flex; flex-direction: column; gap: 10px; min-height: 148px; }
.kpi-head { display: flex; justify-content: space-between; align-items: center; }
.label { font-size: 13.5px; font-weight: 500; color: var(--muted); }
.value { font-size: 34px; font-weight: 700; letter-spacing: -0.03em; line-height: 1; margin-top: auto; }
.value.dot { font-size: 46px; font-weight: 900; letter-spacing: 0.02em; }
.hint { font-size: 12.5px; color: var(--muted); display: flex; align-items: center; gap: 6px; }
.ink { background: var(--ink); color: #f2f3ee; }
.ink .label, .ink .hint { color: #9a9e95; }
.ink .value { color: var(--lime); }
.lime { background: var(--lime); color: var(--ink); }
.lime .label, .lime .hint { color: #3f4a1a; }
</style>

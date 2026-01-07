<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ score: number; compact?: boolean }>()
const segments = 10
const level = computed(() => (props.score >= 70 ? 'high' : props.score >= 35 ? 'medium' : 'low'))
const filled = computed(() => Math.max(props.score > 0 ? 1 : 0, Math.round((props.score / 100) * segments)))
</script>

<template>
  <div class="risk" :class="level" role="meter" :aria-valuenow="score" aria-valuemin="0" aria-valuemax="100" :aria-label="`Risk score ${score}`">
    <div class="bars">
      <span v-for="i in segments" :key="i" :class="{ on: i <= filled }" />
    </div>
    <span v-if="!compact" class="num value">{{ score }}</span>
  </div>
</template>

<style scoped>
.risk { display: inline-flex; align-items: center; gap: 10px; }
.bars { display: flex; gap: 3px; }
.bars span { width: 5px; height: 16px; border-radius: 3px; background: var(--line); }
.low .on { background: var(--sage); }
.medium .on { background: #f2b33d; }
.high .on { background: var(--coral); }
.value { font-size: 13px; font-weight: 600; min-width: 22px; }
.high .value { color: var(--coral); }
</style>

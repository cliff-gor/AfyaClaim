<script setup lang="ts">
defineProps<{ height?: number; radius?: number }>()
</script>

<template>
  <div class="sk" :style="{ height: `${height ?? 16}px`, borderRadius: `${radius ?? 10}px` }" aria-hidden="true" />
</template>

<style scoped>
.sk { background: linear-gradient(90deg, var(--surface-2), var(--line), var(--surface-2)); background-size: 200% 100%; animation: shimmer 1.2s infinite; }
@keyframes shimmer { to { background-position: -200% 0; } }
</style>

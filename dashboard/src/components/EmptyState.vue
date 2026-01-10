<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'
defineProps<{ title: string; body?: string }>()
</script>

<template>
  <div class="empty">
    <div class="icon"><Inbox :size="22" /></div>
    <strong>{{ title }}</strong>
    <p v-if="body" class="muted">{{ body }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; padding: 48px 16px; }
.icon { width: 48px; height: 48px; border-radius: 16px; background: var(--sage-soft); color: var(--moss); display: grid; place-items: center; }
</style>

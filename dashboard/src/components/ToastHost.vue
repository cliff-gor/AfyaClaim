<script setup lang="ts">
import { CheckCircle2, Info, XCircle } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'

const store = useToastStore()
const icons = { success: CheckCircle2, error: XCircle, info: Info }
</script>

<template>
  <div class="toasts" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in store.toasts" :key="t.id" class="toast" :class="t.kind" role="status" @click="store.dismiss(t.id)">
        <component :is="icons[t.kind]" :size="18" />
        <span>{{ t.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toasts { position: fixed; bottom: 24px; right: 24px; display: flex; flex-direction: column; gap: 8px; z-index: 50; }
.toast { display: flex; gap: 10px; align-items: center; background: var(--ink); color: #f2f3ee; padding: 12px 16px; border-radius: 16px; font-size: 14px; box-shadow: 0 12px 32px rgba(0,0,0,.18); cursor: pointer; max-width: 380px; }
.success svg { color: var(--lime); }
.error svg { color: #ff7a76; }
.toast-enter-active { transition: all 220ms var(--ease); }
.toast-leave-active { transition: all 150ms ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }
</style>

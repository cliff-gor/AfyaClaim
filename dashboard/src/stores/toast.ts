import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast { id: number; kind: 'success' | 'error' | 'info'; message: string }

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])
  let seq = 0

  function push(kind: Toast['kind'], message: string) {
    const id = ++seq
    toasts.value.push({ id, kind, message })
    setTimeout(() => dismiss(id), 4000)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return { toasts, push, dismiss, success: (m: string) => push('success', m), error: (m: string) => push('error', m) }
})

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Activity, ArrowRight, Eye, EyeOff, ShieldCheck } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const email = ref('reviewer@afyaclaim.test')
const password = ref('')
const show = ref(false)
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value, password.value)
    router.push((route.query.next as string) || '/')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="hero">
      <div class="brand"><span class="mark"><Activity :size="20" :stroke-width="2.5" /></span>AfyaClaim</div>
      <div class="hero-copy">
        <h1>Subsidy claims,<br /><span>reviewed in minutes.</span></h1>
        <p>Every malaria test and family-planning sale from 40 pharmacies across Western Kenya and the coast, checked for fraud before it reaches you.</p>
      </div>
      <div class="hero-stats">
        <div><span class="dot">48h</span><small>target time to payout</small></div>
        <div><span class="dot">7</span><small>fraud &amp; quality rules</small></div>
        <div><span class="dot">0</span><small>patient identifiers stored</small></div>
      </div>
      <div class="pulse" aria-hidden="true">
        <svg viewBox="0 0 600 120" preserveAspectRatio="none"><path d="M0 70h170l22-44 30 88 26-62 18 26h334" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /></svg>
      </div>
    </section>

    <section class="form-col">
      <form class="form card" novalidate @submit.prevent="submit">
        <h2>Sign in to the back office</h2>
        <p class="muted">For claims reviewers and programme admins.</p>

        <label for="email">Work email</label>
        <input id="email" v-model="email" class="input" type="email" autocomplete="username" required />

        <label for="password">Password</label>
        <div class="pw">
          <input id="password" v-model="password" class="input" :type="show ? 'text' : 'password'" autocomplete="current-password" required />
          <button type="button" class="eye" :aria-label="show ? 'Hide password' : 'Show password'" @click="show = !show">
            <component :is="show ? EyeOff : Eye" :size="18" />
          </button>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button class="btn btn--ink submit" :disabled="loading || !password">
          {{ loading ? 'Signing in…' : 'Sign in' }} <ArrowRight :size="16" />
        </button>
        <p class="secure muted"><ShieldCheck :size="14" /> Sessions expire after 24 hours. Every review action is written to a tamper-evident audit trail.</p>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login { min-height: 100vh; display: grid; grid-template-columns: 1.15fr 1fr; }
.hero { background: var(--ink); color: #f2f3ee; padding: 40px 56px; display: flex; flex-direction: column; position: relative; overflow: hidden; }
.brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 20px; }
.mark { width: 38px; height: 38px; background: var(--lime); color: var(--ink); border-radius: 12px; display: grid; place-items: center; }
.hero-copy { margin-top: auto; max-width: 520px; }
.hero h1 { font-size: 52px; font-weight: 700; letter-spacing: -0.04em; line-height: 1.02; }
.hero h1 span { color: var(--lime); }
.hero-copy p { margin-top: 18px; color: #a7aba1; font-size: 16px; max-width: 440px; }
.hero-stats { display: flex; gap: 40px; margin: 48px 0 8px; }
.hero-stats div { display: flex; flex-direction: column; }
.hero-stats .dot { font-size: 44px; color: var(--lime); line-height: 1; }
.hero-stats small { color: #8d9188; margin-top: 6px; }
.pulse { position: absolute; left: 0; right: 0; top: 34%; color: #22261f; height: 120px; }
.pulse svg { width: 100%; height: 100%; }
.form-col { display: grid; place-items: center; padding: 32px; }
.form { width: min(420px, 100%); display: flex; flex-direction: column; padding: 32px; gap: 6px; }
.form h2 { font-size: 24px; }
.form > .muted { margin-bottom: 16px; }
label { font-size: 13px; font-weight: 600; margin-top: 10px; }
.pw { position: relative; }
.eye { position: absolute; right: 4px; top: 4px; width: 34px; height: 34px; border: 0; background: none; color: var(--muted); border-radius: 10px; display: grid; place-items: center; }
.submit { margin-top: 20px; height: 48px; }
.error { color: var(--coral); background: var(--coral-soft); padding: 10px 12px; border-radius: 12px; font-size: 13.5px; margin-top: 10px; }
.secure { display: flex; gap: 8px; font-size: 12.5px; margin-top: 14px; align-items: flex-start; }
.secure svg { flex-shrink: 0; margin-top: 2px; }
@media (max-width: 900px) {
  .login { grid-template-columns: 1fr; }
  .hero { padding: 28px 20px; min-height: 380px; }
  .hero h1 { font-size: 36px; }
  .hero-stats { gap: 20px; }
}
</style>

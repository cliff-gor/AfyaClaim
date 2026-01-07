const kes = new Intl.NumberFormat('en-KE', { maximumFractionDigits: 0 })
const compact = new Intl.NumberFormat('en-KE', { notation: 'compact', maximumFractionDigits: 1 })

export const money = (cents: number) => `KES ${kes.format(Math.round(cents / 100))}`
export const moneyCompact = (cents: number) => `KES ${compact.format(cents / 100)}`
export const number = (n: number) => kes.format(n)
export const percent = (ratio: number, digits = 1) => `${(ratio * 100).toFixed(digits)}%`

export function dateTime(iso: string) {
  return new Date(iso).toLocaleString('en-KE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
}

export function date(iso: string) {
  return new Date(iso).toLocaleDateString('en-KE', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function relative(iso: string, now = Date.now()) {
  const diff = Math.round((now - new Date(iso).getTime()) / 1000)
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`
  const days = Math.floor(diff / 86400)
  return days === 1 ? 'yesterday' : `${days}d ago`
}

export const humanize = (s: string) => s.replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())

export const RULE_LABELS: Record<string, string> = {
  velocity: 'Velocity spike',
  no_matching_sale: 'No matching sale',
  duplicate_patient: 'Repeat patient',
  amount_mismatch: 'Amount mismatch',
  stock_inconsistency: 'Stock inconsistency',
  off_hours: 'Off-hours sale',
  positivity_outlier: 'Positivity outlier',
}

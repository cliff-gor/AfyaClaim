export type Role = 'pharmacist' | 'reviewer' | 'admin'
export type ClaimStatus = 'submitted' | 'under_review' | 'approved' | 'rejected' | 'paid'
export type Severity = 'low' | 'medium' | 'high'

export interface User {
  id: number
  email: string
  name: string
  role: Role
  pharmacy: { id: number; name: string; town: string } | null
}

export interface Meta { page: number; per_page: number; total: number; pages: number }
export interface Paginated<T> { data: T[]; meta: Meta }

export interface Flag {
  id?: number
  rule: string
  severity: Severity
  message: string
  resolved?: boolean
  created_at?: string
}

export interface ClaimRow {
  id: number
  reference: string
  status: ClaimStatus
  quantity: number
  amount_cents: number
  test_result: 'positive' | 'negative' | null
  submitted_at: string
  reviewed_at: string | null
  rejection_reason: string | null
  flags_count: number
  has_sale: boolean
  product: { id: number; name: string; category: string }
  pharmacy: { id: number; name: string; town: string; risk_score: number }
  top_flag: Flag | null
}

export interface ClaimEvent {
  id: number
  from_status: ClaimStatus | null
  to_status: ClaimStatus
  note: string | null
  metadata: Record<string, unknown>
  created_at: string
  digest: string
  actor: string
}

export interface ClaimDetail extends ClaimRow {
  reviewed_by: { id: number; name: string } | null
  sale: {
    id: number
    client_uuid: string
    sold_at: string
    total_cents: number
    patient: { age_band: string; sex: string; pseudonym: string } | null
    items: { product_id: number; quantity: number; unit_price_cents: number; product_name: string }[]
  } | null
  patient: { age_band: string; sex: string } | null
  flags: Flag[]
  events: ClaimEvent[]
  audit_intact: boolean
  reimbursement: { id: number; status: string; mpesa_receipt: string | null; paid_at: string | null } | null
}

export interface PharmacyStats { total: number; pending: number; flagged: number; rejected: number; approved_value_cents: number }

export interface Pharmacy {
  id: number
  name: string
  license_number: string
  town: string
  status: 'active' | 'suspended'
  risk_score: number
  high_risk: boolean
  mpesa_phone: string | null
  latitude: string | null
  longitude: string | null
  region: { id: number; name: string }
  stats: PharmacyStats
}

export interface InventoryItem {
  id: number
  product_id: number
  quantity_on_hand: number
  reorder_level: number
  low_stock: boolean
  product?: { id: number; name: string; category: string; subsidised: boolean }
}

export interface PharmacyDetail extends Pharmacy {
  inventory: InventoryItem[]
  recent_flags: (Flag & { claim_id: number; claim_reference: string })[]
  monthly: { month: string; claims: number; flagged: number }[]
}

export interface Overview {
  pending_review: number
  flagged_pending: number
  approved_today: number
  rejected_today: number
  awaiting_payout_cents: number
  paid_cents: number
  claims_in_period: number
  claims_value_cents: number
  flag_rate: number
  approval_rate: number
  active_pharmacies: number
  high_risk_pharmacies: number
  stockouts: number
  median_review_hours: number | null
}

export interface RegionRow { region_id: number; region: string; claims: number; value_cents: number; flagged: number }
export interface ProductRow { product_id: number; product: string; category: string; claims: number; units: number; value_cents: number }
export interface TrendPoint { date: string; claims: number; approved: number; positivity: number | null }
export interface Stockout {
  pharmacy_id: number
  pharmacy: string
  region: string
  product: string
  category: string
  quantity_on_hand: number
  reorder_level: number
  stocked_out: boolean
  updated_at: string
}

export interface Reimbursement {
  id: number
  amount_cents: number
  status: 'pending' | 'processing' | 'paid' | 'failed'
  mpesa_conversation_id: string | null
  mpesa_receipt: string | null
  attempts: number
  failure_reason: string | null
  paid_at: string | null
  created_at: string
  pharmacy: { id: number; name: string; mpesa_phone: string | null }
  claims_count: number
}

import { api } from './client'
import type {
  ClaimDetail, ClaimRow, Overview, Paginated, Pharmacy, PharmacyDetail, ProductRow,
  RegionRow, Reimbursement, Stockout, TrendPoint, User,
} from './types'

export interface ClaimFilters {
  status?: string
  flagged?: string
  q?: string
  region_id?: number | string
  pharmacy_id?: number | string
  sort?: string
  page?: number
  per_page?: number
}

export const AuthApi = {
  login: (email: string, password: string) => api.post<{ token: string; user: User }>('/auth/login', { email, password }),
  me: () => api.get<{ user: User }>('/me'),
}

export const ClaimsApi = {
  list: (filters: ClaimFilters) => api.get<Paginated<ClaimRow>>('/claims', { ...filters }),
  get: (id: number) => api.get<{ data: ClaimDetail }>(`/claims/${id}`),
  startReview: (id: number) => api.post<{ data: ClaimDetail }>(`/claims/${id}/start_review`),
  approve: (id: number, body: { note?: string; override?: boolean } = {}) => api.post<{ data: ClaimDetail }>(`/claims/${id}/approve`, body),
  reject: (id: number, reason: string) => api.post<{ data: ClaimDetail }>(`/claims/${id}/reject`, { reason }),
  bulkApprove: (ids: number[]) =>
    api.post<{ results: { id: number; status: 'approved' | 'skipped'; message?: string }[] }>('/claims/bulk_approve', { ids }),
}

export const PharmaciesApi = {
  list: (query: { q?: string; region_id?: number | string; high_risk?: boolean; page?: number; per_page?: number } = {}) =>
    api.get<Paginated<Pharmacy>>('/pharmacies', query),
  get: (id: number) => api.get<{ data: PharmacyDetail }>(`/pharmacies/${id}`),
}

export const AnalyticsApi = {
  overview: (days = 30) => api.get<{ data: Overview }>('/analytics/overview', { days }),
  byRegion: (days = 30) => api.get<{ data: RegionRow[] }>('/analytics/claims_by_region', { days }),
  byProduct: (days = 30) => api.get<{ data: ProductRow[] }>('/analytics/claims_by_product', { days }),
  trend: (days = 30) => api.get<{ data: TrendPoint[] }>('/analytics/trend', { days }),
  stockouts: () => api.get<{ data: Stockout[] }>('/analytics/stockouts'),
}

export const ReimbursementsApi = {
  list: (query: { status?: string; page?: number } = {}) => api.get<Paginated<Reimbursement>>('/reimbursements', query),
  create: () => api.post<{ data: Reimbursement[] }>('/reimbursements'),
}

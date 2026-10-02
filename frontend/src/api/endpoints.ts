import { api } from './client'
import type { AuditEvent, Decision, Me, MeetingDetail, MeetingSummary, Page, Policy } from './types'

export const Api = {
  me: () => api<Me>('/api/me'),
  meetings: () => api<MeetingSummary[]>('/api/meetings'),
  meeting: (id: number) => api<MeetingDetail>(`/api/meetings/${id}`),
  vote: (proposalId: number, decision: Decision, version?: number) =>
    api<{ proposalId: number; decision: Decision; submittedBy: string; submittedAt: string; version: number }>(
      `/api/proposals/${proposalId}/vote`,
      { method: 'PUT', body: JSON.stringify({ decision, version }) },
    ),
  summarize: (proposalId: number, refresh = false) =>
    api<{ summary: string; source: string }>(`/api/proposals/${proposalId}/summary?refresh=${refresh}`, { method: 'POST' }),
  policy: () => api<Policy>('/api/policy'),
  savePolicy: (body: { name: string; version: number; rules: Policy['rules'] }) => api<Policy>('/api/policy', { method: 'PUT', body: JSON.stringify(body) }),
  recalculate: () => api<{ recommendations: number }>('/api/policy/recalculate', { method: 'POST' }),
  audit: (page = 0) => api<Page<AuditEvent>>(`/api/audit?page=${page}&size=20`),
  uploadUrl: (fileName: string) => api<{ url: string; key: string }>('/api/ingest/upload-url', { method: 'POST', body: JSON.stringify({ fileName }) }),
}

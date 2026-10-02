export type Role = 'ANALYST' | 'VOTER' | 'POLICY_ADMIN' | 'OPS'
export type Decision = 'FOR' | 'AGAINST' | 'ABSTAIN'
export type DeadlineStatus = 'OPEN' | 'CLOSING_SOON' | 'CLOSED'
export type Category = 'DIRECTOR_ELECTION' | 'SAY_ON_PAY' | 'AUDITOR' | 'MERGER' | 'SHAREHOLDER_ENV' | 'SHAREHOLDER_SOCIAL' | 'OTHER'
export type ConditionType = 'PAY_SCORE_BELOW' | 'BOARD_INDEPENDENCE_BELOW' | 'BOARD_RECOMMENDS_AGAINST'

export interface Me { username: string; name: string; org: string; orgName: string; roles: Role[] }
export interface MeetingSummary {
  id: number; externalId: string; ticker: string; companyName: string; country: string; meetingDate: string
  voteDeadline: string; marketTimeZone: string; meetingType: string; deadlineStatus: DeadlineStatus
  proposalCount: number; votedCount: number
}
export interface Proposal {
  id: number; seq: number; category: Category; title: string; description: string | null
  boardRecommendation: Decision; payScore: number | null; boardIndependencePct: number | null
  aiSummary: string | null; aiSummarySource: string | null
  recommendation: { decision: Decision; rationale: string; rulePriority: number | null } | null
  vote: { decision: Decision; submittedBy: string; submittedAt: string; version: number } | null
}
export interface MeetingDetail { meeting: MeetingSummary; proposals: Proposal[] }
export interface Rule {
  priority: number; category: Category | null; conditionType: ConditionType | null
  threshold: number | null; decision: Decision; rationale: string
}
export interface Policy { name: string; version: number; updatedAt: string; updatedBy: string; rules: Rule[] }
export interface AuditEvent { id: number; actor: string; action: string; entityType: string; entityId: string; details: Record<string, unknown>; occurredAt: string }
export interface Page<T> { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }

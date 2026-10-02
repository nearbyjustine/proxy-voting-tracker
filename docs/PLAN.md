# App 2: Proxy Voting Tracker: Plan

## 1. Goal

A multi-tenant platform for institutional investors (e.g. pension funds) to track upcoming **shareholder meetings**, read **proposals** (director elections, executive pay, mergers, shareholder proposals), get **vote recommendations generated from their own custom voting policy**, and **submit votes before the deadline**, with a full audit trail.

It's a learning/portfolio project in the corporate-governance domain (the space proxy advisory firms work in). It does **not** use any real company's brand, data, or methodology; all data is synthetic.

**Production-ready definition of done:**

- [ ] OIDC auth (Keycloak), with tenant isolation by organisation claim
- [ ] PostgreSQL + Flyway; audit log is append-only
- [ ] Event-driven ingestion: S3 upload → Lambda → SQS → API consumer, idempotent with a dead-letter queue
- [ ] Policy rules engine (Specification + Strategy patterns) that's fully unit-tested
- [ ] Deadline and time-zone correctness (meetings in different markets)
- [ ] i18n (EN, FR) for UI and error messages
- [ ] AI proposal summaries behind an interface, with a deterministic offline fallback
- [ ] Unit, slice, and Testcontainers tests (Postgres + LocalStack); CI
- [ ] Docker Compose one-command stack; README + handbook

## 2. Architecture

```
 Ops user uploads meetings.csv
        │
        ▼
  S3 bucket "proxyvote-ingest"  ──(ObjectCreated notification)──►  Lambda "meeting-ingest" (Java 21)
                                                                     │  parse + validate CSV rows
                                                                     ▼
                                                SQS "meeting-events"  ──(after 3 failed receives)──► SQS DLQ
                                                                     │
 Vue 3 SPA ──OIDC PKCE──► Keycloak                                   ▼
     │                                         Spring Boot API: MeetingEventConsumer (poller)
     │  Bearer JWT                               ├── upsert Company / Meeting / Proposal (idempotent by externalId)
     ▼                                           ├── RecommendationService → PolicyEngine (rules per org)
 Spring Boot API (resource server) ─────────────► PostgreSQL
     ├── meetings   (Company, Meeting, Proposal)
     ├── policy     (VotingPolicy, PolicyRule → Specification + Strategy)
     ├── voting     (Recommendation, Vote, deadline rules)
     ├── audit      (AuditEvent, append-only)
     └── ai         (ProposalSummarizer → Claude API | offline extractive fallback)
```

## 3. Domain model

| Entity | Key fields | Notes |
|---|---|---|
| `Organization` | id, code, name | the tenant (an investor) |
| `Company` | id, ticker, name, country | the company holding the meeting |
| `Meeting` | id, externalId (unique), company, meetingDate, voteDeadline (`timestamptz`), market time zone, type AGM/EGM | |
| `Proposal` | id, meeting, seq, category (`DIRECTOR_ELECTION`, `SAY_ON_PAY`, `AUDITOR`, `MERGER`, `SHAREHOLDER_ENV`, `SHAREHOLDER_SOCIAL`, `OTHER`), title, description, boardRecommendation FOR/AGAINST, payScore (0–100, nullable), boardIndependencePct (nullable), aiSummary | |
| `VotingPolicy` | id, organization, name, active | one active policy per organisation |
| `PolicyRule` | id, policy, priority, category, condition type + parameters, decision FOR/AGAINST/ABSTAIN, rationale | |
| `Recommendation` | id, organization, proposal, decision, rationale, ruleId, generatedAt | unique (organization, proposal) |
| `Vote` | id, organization, proposal, decision, submittedBy, submittedAt, version | unique (organization, proposal); editable until the deadline |
| `AuditEvent` | id, organization, actor, action, entityType, entityId, details JSON, at | insert-only |
| `ProcessedMessage` | messageId, processedAt | idempotency for SQS consumers |

## 4. Policy engine (core design)

- **Rule conditions are Specifications:** `ProposalCondition` (`isSatisfiedBy(Proposal)`), composable with `and/or/not`. Built-in types: `CATEGORY_IS`, `PAY_SCORE_BELOW`, `BOARD_INDEPENDENCE_BELOW`, `BOARD_RECOMMENDS`.
- **Rules are evaluated in priority order; first match wins.** If no rule matches, the fallback is "follow the board recommendation".
- **The output is a decision plus a rationale**, so the UI can explain *why* ("Against: pay score 42 < 50 (rule #2)").
- Example policy "Stewardship Fund":
  1. `SAY_ON_PAY` and `payScore < 50` → AGAINST
  2. `DIRECTOR_ELECTION` and `boardIndependence < 50%` → AGAINST
  3. `SHAREHOLDER_ENV` → FOR
  4. otherwise → follow the board

## 5. API

| Method & path | Role | Notes |
|---|---|---|
| `GET /api/meetings?from=&to=&status=upcoming&page=` | ANALYST, VOTER | with proposal counts, deadline status |
| `GET /api/meetings/{id}` | ANALYST, VOTER | proposals + this org's recommendations + votes |
| `POST /api/proposals/{id}/summary` | ANALYST | generate/refresh the AI summary |
| `GET/PUT /api/policy` | ANALYST (PUT: POLICY_ADMIN) | the active policy + rules |
| `POST /api/policy/recalculate` | POLICY_ADMIN | regenerate recommendations for upcoming meetings |
| `PUT /api/proposals/{id}/vote` `{decision, version?}` | VOTER | 409 if past the deadline (or a stale version) |
| `GET /api/audit?entityType=&page=` | POLICY_ADMIN | audit trail |
| `POST /api/ingest/upload-url` | OPS | pre-signed S3 PUT URL for a CSV |

Tenant isolation: every query filters on the `org` claim from the JWT. It's never taken from the request body.

## 6. Milestones & tickets

### M1: Foundation
- **V-01** Project, Compose (Postgres, Keycloak realm `proxyvote` with an `org` claim mapper, LocalStack with bucket, queues + DLQ, Lambda deploy script).
- **V-02** Flyway schema + seed (2 orgs, 5 companies, 4 meetings in different markets).
- **V-03** Security: JWT → roles + `TenantContext` from the `org` claim; ProblemDetail errors; i18n (en, fr).

### M2: Meetings & policies
- **V-04** Meetings/proposals read API with filters and deadline status (`OPEN`, `CLOSING_SOON` < 48h, `CLOSED`), computed with an injectable `Clock`.
- **V-05** Policy engine (Specification + Strategy) with exhaustive unit tests.
- **V-06** Recommendations: generated on ingest and on policy change; idempotent upsert.

### M3: Voting & audit
- **V-07** Vote submission: deadline check, optimistic locking, one vote per org per proposal; audit event on every change.
- **V-08** Audit API (read-only), append-only at the DB level (no UPDATE/DELETE grants in production; documented).

### M4: Event-driven ingestion
- **V-09** Lambda `meeting-ingest` (Java 21, plain handler, AWS SDK v2): S3 event → parse CSV → one SQS message per meeting (JSON).
- **V-10** API consumer: long-poll SQS, process idempotently (`ProcessedMessage`), delete on success; poison messages go to the DLQ after `maxReceiveCount=3`.
- **V-11** Pre-signed upload URL endpoint + upload UI.

### M5: AI summaries
- **V-12** `ProposalSummarizer` interface; `ClaudeProposalSummarizer` (Anthropic Messages API over `RestClient`, key from env) and `ExtractiveSummarizer` fallback when there's no key; timeout + error fallback. Summaries are cached on the proposal.

### M6: Frontend
- **V-13** Vue shell, OIDC, i18n EN/FR, role guards.
- **V-14** Meetings dashboard (upcoming, deadline badges), meeting detail with recommendation rationale, vote buttons.
- **V-15** Policy editor (rules list, add/remove, recalculate) and audit view.

### M7: Hardening
- **V-16** Tests: engine unit tests, service tests, `@WebMvcTest` with JWT, Testcontainers (Postgres + LocalStack) for the ingestion flow.
- **V-17** Dockerfiles, Compose profile, CI workflow, README.

## 7. Out of scope (and the production approach)

| Not built | Production approach |
|---|---|
| Real market data feeds | Vendor feeds via SFTP/API into the same S3 → Lambda → SQS pipeline |
| Vote delivery to custodians | Outbox pattern + integration service with retries and reconciliation |
| Fine-grained DB-level tenancy | Postgres Row-Level Security, or schema-per-tenant for large clients |
| Search | OpenSearch for full-text proposal search |

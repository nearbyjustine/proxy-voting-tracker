# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Confirmed: design for real daily users first, while it must still demo well in a screen share.
Users work at institutional investors (organisations/tenants such as a pension fund or an asset manager):
- **Analysts** read upcoming shareholder meetings and proposals, request summaries, and review recommendations.
- **Voters** cast the organisation's vote on each proposal before the deadline.
- **Policy admins** maintain the organisation's custom voting policy (ordered rules) and review the audit log.
- **Ops** upload meeting data files (CSV) for ingestion.

## Product Purpose
A proxy-voting tracker: see every upcoming meeting and its deadline, get a recommendation per proposal generated from the organisation's own voting policy with a readable rationale, and vote on time, with a full audit trail. Success: no missed deadlines, every vote explainable against policy, policy changes immediately reflected.

## Positioning
Recommendations come from the investor's own rules, first-match-wins, and every recommendation states which rule fired and why ("pay score 31 < 50"). Deadlines are exact instants shown in both the user's time and the market's time.

## Operating Context
Proxy season is deadline-driven; meetings span markets and time zones (New York, London, Tokyo, Singapore, Manila, Sydney...). Desktop use at an investment firm. Multi-tenant: an organisation only ever sees its own votes, policy and audit. Sign-in through Keycloak (OIDC). Meeting data arrives as CSV → S3 → Lambda → SQS → API.

## Capabilities and Constraints
- Meetings list: company, market, deadline (local + market time + relative), status OPEN / CLOSING_SOON (<48h) / CLOSED, voted count.
- Meeting detail: proposals with category, description, pay score / board independence, board recommendation, policy recommendation + rationale, organisation vote (FOR / AGAINST / ABSTAIN) with optimistic locking, voting disabled after deadline, flag when a vote differs from policy, AI/extractive summary on request.
- Policy editor: ordered rules (category + optional condition + threshold → decision + rationale), add/remove/reorder, save (409 on stale version), recalculate.
- Audit log: append-only events with actor, action and JSON details.
- Import: CSV upload via pre-signed S3 PUT; meetings appear seconds later.
- Locales: English, French. Errors are ProblemDetail with a visible correlation ID.
- Stack: Vue 3 + TypeScript + Vite; adding Tailwind CSS and Motion for Vue (confirmed by user request).

## Brand Commitments
Product name "Proxy Voting". No logo or brand assets. Must not use any real proxy-advisory firm's name, brand or methodology; all data is synthetic.

## Evidence on Hand
Synthetic meetings/companies only (Orchard Foods, Poseidon Shipping, Northwind Energy, ...); demo users sam/vic/gina. No customers or real data; none may be invented.

## Product Principles
- The deadline is the most important fact on every screen.
- Every recommendation is explained; the rationale is never hidden.
- Tenant boundaries are visible: users always know which organisation they act for.
- Motion confirms consequential actions (a vote recorded, a policy saved), never decorates.

## Accessibility & Inclusion
Must support light and dark mode (confirmed). Keyboard-operable voting and rule editing; respect reduced-motion; EN/FR text lengths; decisions never conveyed by color alone.

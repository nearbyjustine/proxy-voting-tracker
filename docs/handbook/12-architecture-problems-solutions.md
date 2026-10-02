# 12 · Architecture: Problems & Solutions

This is the centrepiece of the handbook. Each entry is a problem that real systems like these run into, written as:

> **Problem → Symptom → Root cause → Solution → Where in this repo → Trade-offs**

Entries marked **(hit)** happened while building these apps. They make the best interview stories, because they're true.

Contents
1. [Double submission / duplicate payroll runs](#1-double-submission--duplicate-payroll-runs)
2. [Long-running work inside an HTTP request](#2-long-running-work-inside-an-http-request)
3. [Worker reads data that isn't committed yet](#3-worker-reads-data-that-isnt-committed-yet)
4. [At-least-once delivery (duplicate messages)](#4-at-least-once-delivery-duplicate-messages)
5. [Poison messages](#5-poison-messages)
6. [Lost updates](#6-lost-updates)
7. [N+1 queries](#7-n1-queries)
8. [Leaking entities and sensitive fields](#8-leaking-entities-and-sensitive-fields)
9. [Broken object-level authorization (IDOR)](#9-broken-object-level-authorization-idor)
10. [Multi-tenant data leakage](#10-multi-tenant-data-leakage)
11. [Deadlines and time zones](#11-deadlines-and-time-zones)
12. [Money precision](#12-money-precision)
13. [Schema drift](#13-schema-drift)
14. [Large file uploads through the API](#14-large-file-uploads-through-the-api)
15. [Third-party AI outage or refusal](#15-third-party-ai-outage-or-refusal)
16. [Observability across threads and services](#16-observability-across-threads-and-services)
17. [Audit trail integrity](#17-audit-trail-integrity)
18. [Config and secrets per environment](#18-config-and-secrets-per-environment)
19. [Hostname mismatches across Docker networks](#19-hostname-mismatches-across-docker-networks)
20. [Dependency and infrastructure drift](#20-dependency-and-infrastructure-drift)
21. [Wrong status codes from a catch-all handler](#21-wrong-status-codes-from-a-catch-all-handler)
22. [Side effects that aren't part of the transaction](#22-side-effects-that-arent-part-of-the-transaction)
23. [Resources outside your lifecycle manager](#23-resources-outside-your-lifecycle-manager)

Then: [Modular monolith vs microservices](#modular-monolith-vs-microservices) · [What breaks at 10x](#what-breaks-at-10x-and-the-fix)

---

## 1. Double submission / duplicate payroll runs

- **Problem:** two payroll admins (or one impatient double-click, or a retry after a timeout) request payroll for the same month.
- **Symptom:** employees get paid twice; totals are doubled.
- **Root cause:** "check if it exists, then insert" is a race. Both requests pass the check before either inserts.
- **Solution, three layers:**
  1. A **unique constraint** on `payroll_run.period`. The database is the final guarantee.
  2. `saveAndFlush` inside the request, so the constraint fires *now* and is mapped to a clean **409**, not at commit time.
  3. The run is a **resource with a status** (PENDING → PROCESSING → COMPLETED/FAILED). The client gets **202 + Location** and polls, so it never has a reason to resubmit.
- **Where:** [`V1__init.sql`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/db/migration/V1__init.sql), [`PayrollRunService.request`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRunService.java), [`PayrollRun`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRun.java) (state machine).
- **Trade-offs:** a generic API would use an `Idempotency-Key` header with stored responses. Here the business key (the month) *is* the idempotency key, which is simpler.

## 2. Long-running work inside an HTTP request

- **Problem:** computing 60 (or 60,000) payslips, rendering PDFs and uploading them takes too long for one request.
- **Symptom:** gateway timeouts; the user retries (see #1); a request thread and a DB connection are held for minutes.
- **Root cause:** synchronous processing of an unbounded batch.
- **Solution:**
  - Accept and return **202**, then process asynchronously on a **bounded thread pool**.
  - Process employees in **chunks of 50, each in its own transaction**, so progress is visible (`processedCount`) and a failure doesn't throw away everything done so far.
  - Make it **idempotent**: `(run, employee)` is unique and already-done employees are skipped, so a retry resumes rather than duplicates.
- **Where:** [`PayrollProcessor`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java), [`AsyncConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AsyncConfig.java), polling UI in [`PayrollView.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/PayrollView.vue).
- **Trade-offs:** `@Async` is **in-memory**. If the app restarts mid-run, the run stays PROCESSING. Production fix: put chunks on a durable queue (SQS) with a DLQ, or use Spring Batch with a job repository, plus a "resume stuck runs" job.

## 3. Worker reads data that isn't committed yet

- **Problem:** the request saves the run and publishes an event; the async worker immediately looks for the run.
- **Symptom:** intermittent "run not found", or the worker processing a run whose transaction later rolled back.
- **Root cause:** the event fired *before* the transaction committed.
- **Solution:** `@TransactionalEventListener` (default phase **AFTER_COMMIT**) plus `@Async`. The worker starts only once the row is visible. If the request rolled back, the event is dropped.
- **Where:** [`PayrollProcessor.onRunRequested`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java). This was also demonstrated in the 5-hour course, where a rollback produced no email.
- **Trade-offs:** if the app crashes *between* commit and handling, the event is lost. The **transactional outbox** pattern (write the event to a table in the same transaction; a relay publishes it) closes that gap.

## 4. At-least-once delivery (duplicate messages)

- **Problem:** SQS (like most brokers) delivers **at least once**. A message can arrive twice, for example when the consumer was slow and the visibility timeout expired.
- **Symptom:** duplicate meetings or proposals; double side effects.
- **Root cause:** exactly-once delivery is not something the network can guarantee. You need exactly-once *effect*.
- **Solution, two layers:**
  1. An **idempotent consumer**: store the SQS `messageId` in `processed_message` **in the same transaction** as the import, and skip it if already present.
  2. **Business-key upserts**: meetings by `externalId`, proposals by `(meeting, seq)`. Even the same file re-sent as *new* messages updates rather than duplicates.

  Delete the message only *after* a successful commit.
- **Where:** [`MeetingImportService.importOnce`](../../backend/src/main/java/dev/justine/proxyvote/ingest/MeetingImportService.java), [`SqsMeetingConsumer`](../../backend/src/main/java/dev/justine/proxyvote/ingest/SqsMeetingConsumer.java), test `duplicateDeliveryIsIgnoredAndReimportDoesNotDuplicateProposals` in [`IngestIntegrationTest`](../../backend/src/test/java/dev/justine/proxyvote/ingest/IngestIntegrationTest.java).
- **Trade-offs:** the `processed_message` table grows, so purge rows older than the queue's retention. FIFO queues offer deduplication but have lower throughput.

## 5. Poison messages

- **Problem:** a message that can never succeed (malformed JSON, an unknown enum, a bad time zone).
- **Symptom:** it is retried forever, burns CPU, floods the logs, and can block or delay other work.
- **Root cause:** no limit on retries.
- **Solution:** a **dead-letter queue** with `maxReceiveCount` (3 locally). The consumer *doesn't delete* on failure; SQS redelivers after the visibility timeout, and after N receives moves the message to the DLQ for a human to inspect. Validate early (the time zone is checked before anything is written).
- **Where:** redrive policy in [`infra/localstack/init.sh`](../../infra/localstack/init.sh), test `poisonMessageEndsUpInTheDeadLetterQueue`. The Lambda also skips bad CSV rows and reports them, instead of failing the whole file ([`MeetingCsvMapper`](../../lambda/src/main/java/dev/justine/ingest/MeetingCsvMapper.java)).
- **Trade-offs:** you need alerting on DLQ depth and a "redrive" runbook. Otherwise the DLQ becomes a silent graveyard.

## 6. Lost updates

- **Problem:** two HR users edit the same employee; two voters at the same fund change the same vote.
- **Symptom:** the second save silently overwrites the first ("last write wins").
- **Root cause:** each user saved a copy that was stale by the time they hit Save.
- **Solution:** **optimistic locking**. A `@Version` column, with the `version` included in the DTO the client sends back. A mismatch gives `ObjectOptimisticLockingFailureException`, mapped to **409** with "changed by someone else, reload".
- **Where:** [`Employee`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/Employee.java) + [`EmployeeService.update`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeService.java), [`VoteService.cast`](../../backend/src/main/java/dev/justine/proxyvote/voting/VoteService.java), [`PolicyService.update`](../../backend/src/main/java/dev/justine/proxyvote/policy/PolicyService.java).
- **Trade-offs:** use **pessimistic locking** (`SELECT … FOR UPDATE`) for hot rows with frequent conflicts (balances, counters). Optimistic locking is better when conflicts are rare.

## 7. N+1 queries

- **Problem:** list 60 employees, then touch `employee.getDepartment().getName()` for each one.
- **Symptom:** 1 query becomes 61; pages slow down as data grows; it's invisible until you read the SQL log.
- **Root cause:** a LAZY association loaded one row at a time.
- **Solution:** fetch what each use case needs in one query: `@EntityGraph(attributePaths = "department")` or `JOIN FETCH`. Keep `open-in-view: false` so lazy loading can't sneak into JSON serialization.
- **Where:** [`EmployeeRepository`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeRepository.java), [`MeetingRepository`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingRepository.java). [`MeetingService.list`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingService.java) loads votes for *all* proposals in one `IN (...)` query instead of one per meeting.
- **Trade-offs:** fetch-joining a collection with pagination makes Hibernate paginate in memory. For big pages use two queries (IDs first) or DTO projections.

## 8. Leaking entities and sensitive fields

- **Problem:** returning JPA entities directly from controllers.
- **Symptom:** salary or internal fields exposed; infinite JSON recursion; `LazyInitializationException`; the API changes every time the table changes.
- **Root cause:** the persistence model being used as the API contract.
- **Solution:** **DTO records** for every request and response, with explicit mapping (`EmployeeResponse.from(e)`). Entities never leave the service layer.
- **Where:** [`EmployeeDtos`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeDtos.java), [`PayrollDtos`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollDtos.java), [`MeetingDtos`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingDtos.java).
- **Trade-offs:** more mapping code. MapStruct can generate it.

## 9. Broken object-level authorization (IDOR)

- **Problem:** `GET /api/payslips/7/download-url`. Is payslip 7 *yours*?
- **Symptom:** any logged-in employee can download anyone's payslip by changing the ID. This is OWASP API Security risk #1.
- **Root cause:** URL rules can say "employees may call this endpoint", but they can't say "only for their own records".
- **Solution:** an **ownership check in the service**: owner username == JWT `preferred_username`, or the caller has PAYROLL_ADMIN. Otherwise 403. Pre-signed URLs are short-lived (5 min) and cover one object only.
- **Where:** [`PayslipService.downloadUrl`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayslipService.java), test `employeeCannotDownloadSomeoneElsesPayslip`; verified in the browser E2E (ana → hana's payslip = 403).
- **Trade-offs:** returning 404 instead of 403 hides whether the record exists. That's a choice between privacy and debuggability.

## 10. Multi-tenant data leakage

- **Problem:** two investment funds share one database (App 2).
- **Symptom:** fund A sees fund B's votes or policy, which is a serious breach.
- **Root cause:** the tenant taken from the request (body, query string, header), or a query that forgot the tenant filter.
- **Solution:** the tenant comes **only from the signed JWT** (`org` claim, set by Keycloak from a user attribute). A single `Tenant.current()` resolves it, and every org-scoped query filters by `organization_id`. Shared market data (meetings, proposals) is separate from per-tenant data (recommendations, votes, policy, audit).
- **Where:** [`Tenant`](../../backend/src/main/java/dev/justine/proxyvote/config/Tenant.java), [`MeetingService`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingService.java); E2E check "gina sees STEWARD vote? false".
- **Trade-offs / production:** add **PostgreSQL Row-Level Security** as a second wall, so a forgotten `WHERE` can't leak. Big clients may need schema- or database-per-tenant.

## 11. Deadlines and time zones

- **Problem:** a vote deadline for a Tokyo meeting, viewed from Manila, checked by a server in UTC.
- **Symptom:** off-by-hours deadlines, votes accepted after the close, "closing soon" wrong around daylight-saving changes.
- **Root cause:** storing local times, mixing zones, trusting the client's clock, or calling `Instant.now()` directly (which makes it untestable).
- **Solution:**
  - Store an **`Instant` in `timestamptz`**, plus the market's zone *for display only*.
  - The server checks the deadline with an **injected `Clock`** (`!now.isBefore(deadline)` → 409).
  - The UI shows the same instant in your zone *and* the market's zone.
  - The payroll "current month" uses **Asia/Manila** explicitly.
- **Where:** [`Meeting`](../../backend/src/main/java/dev/justine/proxyvote/meeting/Meeting.java), [`DeadlineStatus.of`](../../backend/src/main/java/dev/justine/proxyvote/meeting/DeadlineStatus.java) (a pure function with boundary tests), [`VoteService`](../../backend/src/main/java/dev/justine/proxyvote/voting/VoteService.java) + `VoteServiceTest` (a fixed clock 1 s before/at the deadline), [`time.ts`](../../frontend/src/utils/time.ts), [`PayrollRunService`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRunService.java).
- **(hit)** On the frontend, `Intl` refused `dateStyle` + `timeZoneName` together (TypeError). See [10](10-frontend-vue.md).

## 12. Money precision

- **Problem:** `0.1 + 0.2 = 0.30000000000000004`.
- **Symptom:** payslip totals off by a cent, and reconciliation failures.
- **Root cause:** binary floating point (`double`) can't represent most decimal fractions exactly.
- **Solution:** `BigDecimal` everywhere, `NUMERIC(12,2)` in the database, and one rounding rule (`HALF_UP`, 2 dp) in one place (`Money.round`). A test checks the invariant `gross − deductions == net` over many salaries.
- **Where:** [`Money`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/Money.java), [`DeductionEngineTest.grossMinusDeductionsAlwaysEqualsNet`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/DeductionEngineTest.java).
- **(hit)** Two expected tax values in a test were wrong. It was *my* arithmetic, not the code; working the brackets out by hand settled it. Tests catch misunderstandings of the spec too.
- **(hit)** The `₱` sign silently vanished from PDFs because the built-in Helvetica font has no glyph for it. **Fix:** plain numbers plus "Amounts in PHP" ([`PayslipPdfRenderer`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/pdf/PayslipPdfRenderer.java)).

## 13. Schema drift

- **Problem:** the database schema and the entity mappings disagree.
- **Symptom:** errors at runtime on some code path, or silent data truncation.
- **Root cause:** schemas changed by hand or by `ddl-auto=update`, with nothing comparing the two.
- **Solution:** **Flyway** owns the schema (versioned SQL in git), and Hibernate runs with `ddl-auto: validate`, so the app **refuses to start** on a mismatch.
- **(hit)** App 2's V1 declared `country CHAR(2)`; the entity maps a `String` (`varchar`). Startup failed with "wrong column type… found bpchar, expecting varchar(2)". V1 had already run, and editing it would change its checksum, so Flyway would refuse to start. **Fix-forward:** a new [`V3__company_country_varchar.sql`](../../backend/src/main/resources/db/migration/V3__company_country_varchar.sql) with `ALTER COLUMN`.
- **Trade-offs:** for zero-downtime deploys, use **expand → migrate → contract** (add the new column, backfill, switch code, drop the old column later), so old and new app versions can run side by side.

## 14. Large file uploads through the API

- **Problem:** users upload CSVs (or PDFs) through the API.
- **Symptom:** big request bodies tie up threads and memory; timeouts; the API has to scale just to move bytes.
- **Root cause:** the API acting as a file proxy.
- **Solution:** a **pre-signed PUT URL**. The API authorises and signs a URL valid for 10 minutes and one key; the browser uploads **directly to S3**. S3 then emits an event (→ Lambda → SQS) for processing. Downloads work the same way with pre-signed GET URLs.
- **Where:** [`IngestController`](../../backend/src/main/java/dev/justine/proxyvote/ingest/IngestController.java), [`IngestView.vue`](../../frontend/src/views/IngestView.vue), [`S3PayslipStorage`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/storage/S3PayslipStorage.java).
- **Trade-offs:** the bucket needs CORS; you can't inspect the file before it lands (validate it in the Lambda instead); size limits need a POST policy rather than PUT.

## 15. Third-party AI outage or refusal

- **Problem:** the AI summary depends on an external API (Claude).
- **Symptom:** the page breaks or hangs when the provider is slow, rate-limited or down, or declines a request.
- **Root cause:** a hard dependency on a remote service inside the user's request.
- **Solution:**
  - A **port** (`ProposalSummarizer`) with two adapters: Claude, and an **offline extractive summarizer**. Every failure falls back to the offline one.
  - SDK **timeout** and **retries** (`maxRetries(2)`, which retries 429/5xx).
  - **Server-side refusal fallbacks** (`fallbacks: "default"`), so a declined request is re-run on Anthropic's recommended fallback model.
  - **Low effort** for a short summary, to keep latency and cost down.
  - Summaries are **cached** on the proposal.
  - With no `ANTHROPIC_API_KEY`, the app uses the offline adapter automatically.
- **Where:** [`AiConfig`](../../backend/src/main/java/dev/justine/proxyvote/ai/AiConfig.java), [`ClaudeProposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ClaudeProposalSummarizer.java), [`ExtractiveSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ExtractiveSummarizer.java).
- **Trade-offs:** for many proposals, generate summaries asynchronously (or with the Batch API) instead of on click. A circuit breaker (Resilience4j) avoids waiting on timeouts during an outage.

## 16. Observability across threads and services

- **Problem:** a user reports "something went wrong" at 3 pm.
- **Symptom:** you can't find their request among thousands of log lines, and async work has no link back to the request that started it.
- **Root cause:** no request identity, and thread-locals (MDC) don't cross thread pools.
- **Solution:**
  - A **correlation ID** filter (reuses `X-Correlation-Id` or creates one), put in the logging **MDC** and echoed in the response.
  - Every **ProblemDetail error includes `correlationId`**, and the UI shows it.
  - A **TaskDecorator copies the MDC** onto `@Async` threads, so the payroll worker's logs carry the same ID.
  - Actuator health probes are included.
- **Where:** [`CorrelationIdFilter`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/CorrelationIdFilter.java), [`AsyncConfig.mdcPropagation`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AsyncConfig.java), [`ErrorBanner.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/components/ErrorBanner.vue).
- **Production:** OpenTelemetry tracing across services, JSON logs, CloudWatch or Grafana dashboards, and alerts on 5xx rate and DLQ depth.

## 17. Audit trail integrity

- **Problem:** regulators and clients need proof of who voted, what, and when.
- **Symptom:** an audit row missing for a vote (or present for a rolled-back one), or rows edited after the fact.
- **Root cause:** audit written in a separate transaction or asynchronously; a mutable table.
- **Solution:**
  - Write the audit row **in the same transaction** as the change (`Propagation.MANDATORY` forces callers to have one).
  - Mark the entity **`@Immutable`** with no setters.
  - In production, give the app's DB role **INSERT + SELECT only** on `audit_event`.
  - Store details as `jsonb`, including whether the vote went against the fund's own recommendation.
- **Where:** [`AuditService`](../../backend/src/main/java/dev/justine/proxyvote/audit/AuditService.java), [`AuditEvent`](../../backend/src/main/java/dev/justine/proxyvote/audit/AuditEvent.java), [`VoteService`](../../backend/src/main/java/dev/justine/proxyvote/voting/VoteService.java).
- **Trade-offs:** synchronous audit adds a write per change. For tamper evidence, hash-chain the rows or ship them to WORM storage (S3 Object Lock).

## 18. Config and secrets per environment

- **Problem:** URLs, credentials and feature flags differ between local, staging and production.
- **Symptom:** secrets committed to git, "works locally", configuration scattered across `@Value` strings.
- **Solution:**
  - Typed **`@ConfigurationProperties` records** (they fail fast at startup), with environment-variable overrides (`${DB_URL:default}`).
  - LocalStack gets dummy `test` credentials *only* when an endpoint override is set; otherwise the AWS **default credential chain** is used (which means an IAM role in AWS).
  - The Claude key comes only from `ANTHROPIC_API_KEY`.
- **Where:** [`AppProperties`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AppProperties.java), [`application.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/application.yml), [`AwsConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AwsConfig.java).
- **Production:** Secrets Manager or SSM injected at deploy time, never baked into images; rotate keys regularly.

## 19. Hostname mismatches across Docker networks

- **Problem:** the same service has two addresses: one for the browser (`localhost:8180`) and one inside the Docker network (`keycloak:8080`).
- **Symptoms (both hit while designing):**
  - JWT validation fails with "invalid issuer", because the token says `iss=http://localhost:8180/...` while the API fetched config from `keycloak:8080`.
  - Pre-signed URLs point at `http://localstack:4566/...`, which the browser can't resolve.
- **Root cause:** the issuer and signed URLs include a hostname, and "localhost" means something different inside each container.
- **Solution:**
  - Keycloak gets `KC_HOSTNAME=http://localhost:8180`, so tokens always carry the public issuer, plus `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`.
  - The API validates `issuer-uri` = the public URL but downloads keys from `jwk-set-uri` = the internal URL.
  - S3 uses two clients: an internal endpoint for uploads, and a **public endpoint for the presigner**.
- **Where:** [`docker-compose.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/docker-compose.yml), [`application.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/application.yml), [`AwsConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AwsConfig.java).
- **Production:** one public DNS name per service (behind an ALB), so the split mostly disappears.

## 20. Dependency and infrastructure drift

- **Problem:** the world changes under you while your code stays the same.
- **(hit)** `localstack/localstack:latest` started requiring an auth token, and the container exited immediately. **Fix:** pin `4.9`.
- **(hit)** npm installed TypeScript 7; `vue-tsc` couldn't load it. **Fix:** pin `typescript@~5.9`.
- **(hit)** Spring Boot 4 changed module and test-annotation package names (`spring-boot-starter-webmvc`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`), Testcontainers 2 renamed artifacts (`testcontainers-postgresql`), and Spring Data JPA 4 is stricter about `null` Specifications. **Fix:** combine only the non-null filters, and start from start.spring.io-generated poms.
- **(hit)** The registries were unreachable from this network. **Fix:** mirrors (see [11](11-devops-docker-ci.md)).
- **Solution in general:**
  - Pin versions (lockfiles, explicit tags, BOMs).
  - Let Renovate or Dependabot propose upgrades *as PRs that run the tests*.
  - Never use `:latest` in builds.
  - Keep a fallback path (mirrors, cached artifacts).

## 21. Wrong status codes from a catch-all handler

- **Problem (hit):** `PUT /api/proposals/1/vote` with `{"decision":"MAYBE"}` returned **500**.
- **Symptom:** clients and monitoring treat a user's typo as a server failure, and alerts fire for nothing.
- **Root cause:** `HttpMessageNotReadableException` (malformed JSON or an unknown enum) had no specific handler, so the `@ExceptionHandler(Exception.class)` catch-all turned it into a 500.
- **Solution:** map it explicitly to **400** in both apps' `ApiExceptionHandler`. A web-slice test (`unknownDecisionIsRejected`) caught it.
- **Lesson:** a catch-all handler is necessary, but every *client* error type needs its own mapping. Test the unhappy paths of your API, not just the happy ones.

## 22. Side effects that aren't part of the transaction

- **Problem:** the payroll chunk uploads a PDF to S3 and then saves the payslip row in the same DB transaction.
- **Symptom:** if the DB transaction rolls back, the S3 object stays behind (an orphan). If S3 succeeds but the app crashes before commit, a retry uploads again.
- **Root cause:** S3 and the database don't share a transaction.
- **Solution here:** **deterministic keys** (`payslips/{period}/{employeeNo}.pdf`), so a retry *overwrites* rather than duplicates, and the DB row is the source of truth.
- **Production options:** the outbox pattern (record "upload needed" in the DB, and a worker performs it), or an orphan-cleanup job that compares bucket keys with DB rows.

## 23. Resources outside your lifecycle manager

- **Problem (hit):** after `docker compose down` for App 2, LocalStack's Lambda execution container (`public.ecr.aws/lambda/java:21`, named `proxyvote-localstack-1-lambda-meeting-ingest-…`) was still running.
- **Symptom:** the `proxyvote_default` network couldn't be removed ("in use").
- **Root cause:** LocalStack starts Lambdas as **sibling containers through the mounted Docker socket**. Compose never created them, so it doesn't stop them.
- **Fix:** `docker ps -aq --filter network=proxyvote_default | xargs docker rm -f`.
- **Lesson:** anything started *on your behalf* (LocalStack Lambdas, Testcontainers, child processes, thread pools) needs its own cleanup. Testcontainers ships **Ryuk** for exactly this; in Spring we call `setWaitForTasksToCompleteOnShutdown` on the executor.

---

## Modular monolith vs microservices

Both apps are **modular monoliths**: one deployable, packages split by business module (`employee`, `payroll`; `meeting`, `policy`, `voting`, `audit`, `ingest`, `ai`), and modules talking through services and events.

| | Modular monolith (here) | Microservices |
|---|---|---|
| Transactions | Local ACID (payslip + run totals; vote + audit row together) | Distributed: sagas, outbox, eventual consistency |
| Deploy | One artifact | Many pipelines; independent releases |
| Failure modes | In-process calls | Network timeouts, retries, partial failure, versioned contracts |
| Team fit | 1–3 teams | Many teams with clear service ownership |
| When to split | A module needs independent scaling (e.g. payroll workers) or a separate team owns it | — |

The pieces that *are* separate here are the ones that benefit from it: the **Lambda** (event-driven, scales to zero, isolated from the API) and the **queue** between ingestion and the API (absorbs bursts, retries, DLQ).

## What breaks at 10x and the fix

| At 10x… | What breaks | Fix |
|---|---|---|
| Payroll for 60,000 employees | In-memory `@Async` loses work on restart; one node does it all | Put chunks on **SQS** consumed by N worker instances (or Spring Batch partitioning); a resume job for stuck runs |
| Read traffic (dashboards) | The DB primary is CPU-bound | **Read replicas** for queries; cache reference data (`@Cacheable` departments, tax tables) in **Redis**; HTTP caching/ETags |
| Several API instances | `@Scheduled` jobs (the SQS poller is fine; future cron jobs aren't) run on every node | **ShedLock** or a leader lock; or EventBridge Scheduler → one target |
| Event reliability | Events published after commit can be lost on a crash | **Transactional outbox** + relay (or Debezium CDC) |
| Ingestion bursts | One poller thread | More consumers (SQS scales horizontally), batch receives, back-pressure via visibility timeout |
| Audit / log volume | Big tables, slow queries | Partition `audit_event` by month; archive to S3 |
| Search across proposals | `LIKE` queries | OpenSearch index fed by events |
| Tenants | One noisy client affects others | Rate limits per tenant; RLS; schema-per-tenant for the largest clients |

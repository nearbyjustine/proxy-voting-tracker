# 06 · Async work, events and messaging

> **One-line summary:** slow work (payroll for every employee, importing a CSV) must not run inside the HTTP request. App 1 does it **in-process** (`@Async` after commit, in chunks). App 2 does it **across processes** with a **queue** (S3 → Lambda → SQS → API consumer), designed for *at-least-once* delivery: every step is idempotent, and failures end in a dead-letter queue.

---

## 1. What it is

| Concept | Plain meaning | Laravel equivalent |
|---|---|---|
| **`@Async`** | Run this method on another thread from a pool; the caller returns immediately | `dispatch(new Job)` with the `sync`→`database` queue driver, but in memory |
| **`ThreadPoolTaskExecutor`** | The pool of worker threads (core/max size, queue capacity) | Number of `queue:work` processes |
| **Application events** (`ApplicationEventPublisher`, `@EventListener`) | In-process publish/subscribe: the publisher doesn't know who listens | `event(new X)` + Listeners |
| **`@TransactionalEventListener`** | A listener that runs at a transaction phase, by default **AFTER_COMMIT** | `ShouldDispatchAfterCommit` / `afterCommit()` |
| **`@Scheduled`** | Run a method on a timer or cron | `$schedule->call(...)->everyMinute()` |
| **Message queue (SQS)** | Durable store of messages between processes. Survives restarts and supports many consumers | Redis/SQS queue driver |
| **At-least-once delivery** | A message may be delivered **more than once**, never zero times (if you don't delete it) | Same with Laravel + SQS |
| **Idempotent consumer** | Processing the same message twice has the same effect as once | You write this yourself in Laravel too |
| **Visibility timeout** | After a consumer receives a message, it's hidden for N seconds; if not deleted by then, it reappears | `retry_after` |
| **Dead-letter queue (DLQ)** | Where a message goes after failing `maxReceiveCount` times | `failed_jobs` table |
| **Poison message** | A message that can never succeed (bad JSON). Without a DLQ it would retry forever | A job that always throws |
| **MDC** | Per-thread key/value map the logger prints (our `correlationId`) | `Log::withContext()` |

---

## 2. Where we used it

### App 1: in-process async payroll run
- [PayrollRunService.request()](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRunService.java): saves a `PENDING` run (`saveAndFlush`, to hit the unique constraint now) and publishes `PayrollRunRequested`. The controller returns **202 Accepted + `Location: /api/payroll-runs/{id}`** ([PayrollController](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollController.java)).
- [PayrollProcessor](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java):
  - `@Async("payrollExecutor") @TransactionalEventListener`: runs only **after the request transaction commits**, on a worker thread.
  - "Claims" the run: in its own transaction it checks `status == PENDING`, moves it to `PROCESSING`, and makes it an idempotent no-op if already picked up.
  - Processes employees in **chunks of 50, each chunk in its own transaction** (`TransactionTemplate`), skipping any employee whose payslip already exists. The `(run, employee)` unique constraint backs this up.
  - On success → `COMPLETED`; on exception → `FAILED` with a reason, in a separate transaction.
- [AsyncConfig](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AsyncConfig.java): **bounded** pool (core 2, max 4, queue 20), named threads `payroll-*`, graceful shutdown, and a **`TaskDecorator` that copies the MDC** (correlation ID) onto the worker thread.
- [CorrelationIdFilter](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/CorrelationIdFilter.java): puts `correlationId` in the MDC per request and **always clears it** in `finally` (threads are pooled).
- Frontend [PayrollView.vue](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/PayrollView.vue) **polls** every 1.5 s only while a run is `PENDING`/`PROCESSING`.

### App 2: event-driven ingestion across processes
- [init.sh](../../infra/localstack/init.sh): creates `meeting-events` (visibility timeout 30 s) with a **redrive policy** → `meeting-events-dlq` after `maxReceiveCount=3`.
- [MeetingIngestHandler](../../lambda/src/main/java/dev/justine/ingest/MeetingIngestHandler.java) (Lambda): one SQS message per meeting, sent in batches of 10.
- [SqsMeetingConsumer](../../backend/src/main/java/dev/justine/proxyvote/ingest/SqsMeetingConsumer.java): `@Scheduled(fixedDelay = 500)` + **long polling** (`waitTimeSeconds`). **Deletes a message only after a successful import.** On failure it does nothing, so SQS redelivers it and eventually dead-letters it. It's toggled by `app.ingest.consumer-enabled` (`@ConditionalOnProperty`).
- [MeetingImportService.importOnce()](../../backend/src/main/java/dev/justine/proxyvote/ingest/MeetingImportService.java): two layers of idempotency.
  1. [`ProcessedMessage`](../../backend/src/main/java/dev/justine/proxyvote/ingest/ProcessedMessage.java) (message ID as primary key) is written **in the same transaction** as the import.
  2. **Business-key upserts:** meeting by `externalId`, proposal by `(meeting, seq)`. Even a re-uploaded file (new message IDs) doesn't duplicate.
- After import it regenerates recommendations in the same transaction.
- Proof: [IngestIntegrationTest](../../backend/src/test/java/dev/justine/proxyvote/ingest/IngestIntegrationTest.java) covers a real message imported, a duplicate ignored, and **`{not json` ending in the DLQ**.
- Scheduling is switched on with `@EnableScheduling` in [AppConfig](../../backend/src/main/java/dev/justine/proxyvote/config/AppConfig.java); the consumer's `@Scheduled` poll is the only scheduled job in App 2.

---

## 3. Why we chose this approach

1. **Requests must be fast and bounded.** Payroll for 60 employees (or 60,000) can't fit in an HTTP timeout. 202 + polling is the standard REST answer for long work.
2. **AFTER_COMMIT prevents phantom work.** If the worker started *inside* the request transaction, it might query the run before it's committed (not found) or process a run whose transaction later rolls back.
3. **Chunked transactions** keep locks and memory small, make progress visible (`processedCount` 23/61), and let a retry resume where it stopped.
4. **App 1 vs App 2 show both ends of the spectrum on purpose.** `@Async` is simple and fine when losing in-flight work on a crash is tolerable (an admin can re-run). Ingestion data from outside must not be lost, so it goes through a durable queue with retries and a DLQ.
5. **Idempotency is the price of reliable queues.** SQS standard queues are at-least-once, so the consumer must tolerate duplicates.

---

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| **Synchronous processing in the request** | Tiny jobs (< 1 s) | Payroll grows with headcount and would hit timeouts |
| **Spring Batch** | Big ETL-style jobs: restartable steps, skip/retry policies, job repository | Heavier framework. Our chunk loop shows the same ideas in 80 lines; mention Spring Batch as the production upgrade |
| **SQS for payroll too** (one message per chunk) | Multi-instance deployments; must survive restarts | `@Async` is enough for the demo; App 2 already shows the queue pattern |
| **Spring Cloud AWS `@SqsListener`** | Production SQS consumers: concurrency, ack modes, back-pressure built in | We hand-wrote the consumer so every concept is visible (long poll, delete-after-success). Swap later |
| **RabbitMQ** | Complex routing (topics, fan-out), on-prem | We're AWS-focused; SQS is serverless, with no broker to run |
| **Kafka / Kinesis** | Event streaming, replay, ordered partitions, many consumers of the same event | Overkill for "process each meeting once" |
| **SQS FIFO queue** | Strict ordering + exactly-once *within* the dedup window | Lower throughput; idempotent consumers make standard queues safe enough |
| **Outbox pattern** | You must update the DB **and** publish a message atomically | Not built; see pitfalls. Our App 1 event is in-process, so `AFTER_COMMIT` covers it |
| **Virtual threads (Java 21)** | Lots of blocking I/O tasks | A bounded pool is easier to reason about for CPU + DB work; `spring.threads.virtual.enabled` is ready to flip |

---

## 5. Problems & pitfalls

### Real problems/decisions from this build

**P1. Logs from the async worker lost the request's correlation ID**
- *Symptom:* worker log lines printed `[cid=]`, so you can't connect "payroll failed" to the request that started it.
- *Cause:* MDC is a `ThreadLocal`; a pool thread doesn't inherit the caller's.
- *Fix:* `TaskDecorator` in `AsyncConfig` copies the MDC map into the worker and clears it after. Verified in the container logs: `[cid=b603e7b8-…] [payroll-1] Payroll run 2 completed in 667 ms`.
- *Lesson:* anything thread-local (MDC, `SecurityContextHolder`, `LocaleContextHolder`) does **not** cross `@Async` boundaries. That's also why the async payroll run is audited as `system`, not `paolo`.

**P2. The worker must not start before the commit**
- A plain `@EventListener` runs *synchronously inside* the publishing transaction. With `@Async` it'd race the commit. `@TransactionalEventListener` (default `AFTER_COMMIT`) fixes both: if the request rolls back (e.g. duplicate period → 409), no work is done.

**P3. Double-click / two admins start the same month**
- Service check `existsByPeriod` **plus** DB unique constraint `payroll_run.period` **plus** `saveAndFlush` inside a try/catch → 409. The service check is for a friendly message; the constraint is the real guarantee under a race.

**P4. Proving DLQ behaviour quickly in tests**
- With a 30 s visibility timeout, a DLQ test would take minutes. The test queue uses `VisibilityTimeout=1` and `maxReceiveCount=2` ([TestcontainersConfiguration](../../backend/src/test/java/dev/justine/proxyvote/TestcontainersConfiguration.java)). Lesson: make timing *configuration*, not constants.

### Classic pitfalls

| Pitfall | Consequence | Mitigation |
|---|---|---|
| **Self-invocation** (`this.asyncMethod()`) | Runs synchronously: `@Async`, `@Transactional` and `@Cacheable` are proxy-based | Call through another bean (we put the listener in `PayrollProcessor`, separate from `PayrollRunService`) |
| **Unbounded executor / `new Thread()`** | Out-of-memory under load | Bounded `ThreadPoolTaskExecutor`; when the queue is full, callers get an error |
| **Exceptions in `void @Async` methods vanish** | Silent failures | We catch inside `process()` and mark the run `FAILED` with the reason (visible in the UI) |
| **Deleting the SQS message before processing** | Data loss on crash | Delete **after** a successful commit |
| **Processing longer than the visibility timeout** | A second consumer gets the same message → duplicate work | Set the timeout above the worst-case processing time, or extend it; stay idempotent anyway |
| **Non-idempotent consumer** | Duplicate rows, double emails | `ProcessedMessage` + business-key upserts |
| **The dual-write problem** | Commit DB, then crash before sending the message (or vice versa) → inconsistent | **Outbox pattern:** insert the event into an `outbox` table in the same transaction; a relay publishes and marks it sent. Debezium/CDC can do the relay |
| **`@Scheduled` on N instances** | Job runs N times | **ShedLock** (DB lock) or move the trigger to a queue / EventBridge Scheduler |
| **Polling too aggressively** | Wasted requests ($ on real SQS) | Long polling (`waitTimeSeconds` up to 20) returns as soon as a message arrives |
| **Losing in-flight `@Async` work on deploy** | Run stuck in `PROCESSING` | Graceful shutdown (`waitForTasksToCompleteOnShutdown`); in production add a sweeper that resets stale `PROCESSING` runs, or use a queue |

---

## 6. Interview questions

**Q1 (basic). Why return 202 instead of 200 when starting payroll?**
202 means "accepted for processing, not done yet". The body has the run (status `PENDING`) and `Location` points to the status resource the client polls. 200/201 would imply the work is complete.

**Q2. How does `@Async` work and what are the gotchas?**
Spring wraps the bean in a proxy; calls through the proxy are submitted to a `TaskExecutor`. Gotchas: self-invocation bypasses the proxy, thread-locals (security context, MDC) don't propagate, `void` methods swallow exceptions, and you must configure a bounded pool.

**Q3. Why `@TransactionalEventListener` and not `@EventListener`?**
I want the payroll worker to run only if the run row actually committed. AFTER_COMMIT guarantees the worker can read it and that a rolled-back request triggers nothing. It's the same reason you'd send emails after commit.

**Q4. Why process in chunks with separate transactions?**
One giant transaction holds locks for the whole run, can blow memory, and a failure at employee 9,999 rolls back everything. Per-chunk commits give progress, bounded memory, and resumability. Idempotency (`existsByRunIdAndEmployeeId` + unique constraint) makes a re-run safe.

**Q5. SQS is at-least-once. How do you avoid duplicates?**
Idempotent consumer: record the message ID in the same DB transaction as the effect, and design writes as upserts by business key. Delete the message only after commit. A duplicate delivery then becomes a no-op: our test calls `importOnce` twice with the same ID and gets `true`, then `false`.

**Q6. What is a DLQ and how did you prove it works?**
A queue that receives messages after `maxReceiveCount` failed receives, so a poison message doesn't loop forever and someone can inspect it. Our integration test sends `{not json`; the consumer fails to parse it and never deletes it, and after 2 receives SQS moves it to `test-events-dlq`. The test waits until the DLQ count > 0.

**Q7 (advanced). You need to save an order and publish an "OrderPlaced" event reliably. How?**
Transactional outbox: write the order and an outbox row in one DB transaction, and have a separate relay (poller or CDC) publish outbox rows to the broker and mark them sent. Consumers must still be idempotent, because the relay can publish twice.

**Q8 (advanced). Your app scales to 3 instances. What breaks?**
`@Scheduled` jobs run 3×: use ShedLock or an external scheduler. In-memory `@Async` work is lost if an instance dies: move to a queue. Caches diverge: use a shared cache (Redis) or accept TTL staleness. The SQS consumer is already fine with 3 instances (competing consumers + idempotency).

---

## 7. Exercise: rebuild it yourself

Move App 1's payroll processing **onto a queue**:
1. Add an SQS queue `payroll-chunks` (+ DLQ) to App 1's LocalStack init script.
2. On `PayrollRunRequested` (AFTER_COMMIT), send one message per chunk: `{runId, page}`.
3. Write a consumer like `SqsMeetingConsumer` that processes one chunk per message, idempotently, and deletes after commit.
4. Mark the run `COMPLETED` when `processedCount == employeeCount` (watch the race: two chunks finishing at once; use `@Version` on `PayrollRun` and retry on conflict).
5. Write an integration test proving a poison chunk message lands in the DLQ and the run ends `FAILED`.

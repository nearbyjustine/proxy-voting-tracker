# 08 · Design patterns and SOLID, as used in this code

## 1. What it is

Design patterns are named, reusable solutions to recurring design problems. You don't need to memorise the "Gang of Four" catalogue for an interview. You need to **recognise a problem, name the pattern, and point to where you used it**. Every pattern below exists in this repo for a concrete reason.

| Pattern | One-line idea | Where |
|---|---|---|
| Strategy | Swap interchangeable algorithms behind an interface | `DeductionRule` (App 1) |
| Specification | Business rules as small, composable predicate objects | `ProposalCondition` (App 2), `EmployeeSpecifications` (App 1) |
| Observer / domain events | Publish "something happened"; listeners react independently | `PayrollRunRequested` (App 1) |
| Repository | Collection-like access to aggregates, hiding SQL | All `*Repository` interfaces |
| Template Method | A fixed algorithm skeleton with a pluggable step | `TransactionTemplate` in `PayrollProcessor` |
| Ports & Adapters (hexagonal) | Domain depends on interfaces; infrastructure plugs in | `PayslipStorage`, `ProposalSummarizer` |
| State machine | Explicit states and legal transitions | `PayrollRun` |
| Builder / Factory | Construct complex objects step by step / choose an implementation | AWS SDK builders, `AiConfig` |
| Fallback / Null object | A safe default when the real thing is missing or fails | `ExtractiveSummarizer` |
| Decorator | Wrap behaviour around a call | `TaskDecorator` (MDC propagation), Spring proxies |

## 2. Where we used it

### Strategy: pluggable deductions
- Interface: [`DeductionRule`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/DeductionRule.java) (`code`, `label`, `preTax`, `appliesTo`, `compute`).
- Strategies: [`SssContribution`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/SssContribution.java), [`PhilHealthContribution`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/PhilHealthContribution.java), [`PagIbigContribution`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/PagIbigContribution.java), [`WithholdingTax`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/WithholdingTax.java).
- Context: [`DeductionEngine`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/DeductionEngine.java) receives `List<DeductionRule>` (ordered by `@Order`) and runs them.

```java
for (DeductionRule rule : rules) {
    DeductionContext ctx = new DeductionContext(employee, gross, contributions);
    if (!rule.appliesTo(ctx)) continue;
    BigDecimal amount = rule.compute(ctx);
    ...
    if (rule.preTax()) contributions = contributions.add(amount);
}
```

`WithholdingTax` is also **table-driven**: brackets are a `List<Bracket>` record list, not an if/else chain. Changing a bracket is a data change.

### Specification: composable rules
- [`ProposalCondition`](../../backend/src/main/java/dev/justine/proxyvote/policy/ProposalCondition.java): `isSatisfiedBy(proposal)` with default `and/or/not`.
- [`ConditionType`](../../backend/src/main/java/dev/justine/proxyvote/policy/ConditionType.java) creates conditions from a threshold.
- [`PolicyRule.condition()`](../../backend/src/main/java/dev/justine/proxyvote/policy/PolicyRule.java) composes "category is X" **and** "pay score below N".
- [`PolicyEngine`](../../backend/src/main/java/dev/justine/proxyvote/policy/PolicyEngine.java): first matching rule wins (like firewall rules), falling back to the board.
- In App 1, [`EmployeeSpecifications`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeSpecifications.java) uses Spring Data's `Specification<Employee>`, the same idea compiled to SQL `WHERE` clauses.

### Observer / domain events
- [`PayrollRunService.request()`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRunService.java) publishes `PayrollRunRequested(runId)`. It doesn't know who listens.
- [`PayrollProcessor.onRunRequested`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java) is `@Async` + `@TransactionalEventListener`, so it fires **after commit** on a worker thread.

### Repository
Spring Data interfaces (`EmployeeRepository`, `MeetingRepository`, …) expose domain-shaped methods (`findByActiveTrueOrderByIdAsc`, `findDeadlineAfter`) and hide SQL from services.

### Template Method
`TransactionTemplate.execute(status -> ...)`: Spring owns the skeleton (begin, commit or rollback on exception, close); we pass only the middle step. `PayrollProcessor` runs claim, chunk and complete as separate templated transactions.

### Ports & adapters
- Port [`PayslipStorage`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/storage/PayslipStorage.java), adapter [`S3PayslipStorage`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/storage/S3PayslipStorage.java). The payroll logic never imports an AWS class, and [`PayslipServiceTest`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayslipServiceTest.java) mocks the port.
- Port [`ProposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ProposalSummarizer.java), with adapters [`ClaudeProposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ClaudeProposalSummarizer.java) and [`ExtractiveSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ExtractiveSummarizer.java).

### State machine
[`PayrollRun`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollRun.java): `PENDING → PROCESSING → COMPLETED | FAILED`. Transitions are methods (`start`, `recordPayslip`, `complete`, `fail`) that guard their preconditions with `requireStatus(...)`. [`PayrollRunTest`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayrollRunTest.java) proves you can't complete a run that never started.

### Builder & Factory
- Builders: `S3Client.builder().region(...).endpointOverride(...).build()` in [`AwsConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AwsConfig.java); `MessageCreateParams.builder()` for Claude; `ThreadPoolTaskExecutor` setup.
- Factory: [`AiConfig.proposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/AiConfig.java) decides at startup which implementation to create.
- Static factories: `EmployeeResponse.from(e)`, `Proposal.of(...)`, `DeadlineStatus.of(...)`.

### Fallback
The Claude adapter catches rate limits, API errors and refusals and returns `fallback.summarize(p)`. With no API key, the extractive summarizer is used directly. A third-party outage degrades the feature instead of breaking the page.

### Decorator
`AsyncConfig.mdcPropagation()` wraps each `Runnable` to copy the logging context onto the worker thread. Spring's own `@Transactional`/`@Async` proxies are decorators around your beans.

## 3. Why we chose these patterns

- **Strategy + Specification**: these are the two places where business rules change most often (government rates; investor policies). Patterns that make change a matter of *adding* code rather than *editing* tested code reduce regressions.
- **Events**: the HTTP request must finish fast (202). Decoupling "request accepted" from "do the work" is what makes the async design possible.
- **Ports & adapters**: S3 and AI vendors are infrastructure details; tests and future vendor swaps shouldn't touch business logic.
- **State machine**: a payroll run must never be "completed" twice or from the wrong state. Making transitions explicit puts that rule in one place.

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| `switch` on a type field | 2–3 stable cases | Rules change yearly; each change would edit and retest one big method. |
| Rules engine (Drools, Easy Rules) | Business users author hundreds of rules | Overkill for a handful; plain Java is testable and debuggable. |
| Scripted rules (SpEL/JS stored in DB) | Rules must change without deploys | Security risk (code injection) and harder to validate; our rules are *data* (category, condition, threshold) instead. |
| Direct method call instead of an event | Same transaction, same thread, simple flow | We need after-commit and async semantics. |
| Message broker (SQS/Kafka) instead of in-memory events | Durability across restarts, multiple instances | App 2 uses SQS for ingestion; App 1's in-memory event is fine for one instance (see ch. 06/12). |
| Status as free-form string column | Never | No compile-time safety, no guarded transitions. |
| Spring StateMachine library | Complex workflows with many states/guards | Four states don't justify a framework. |

## 5. Problems & pitfalls

### Ordering dependencies between strategies
- **Symptom:** withholding tax was too high in an early mental model.
- **Cause:** tax is calculated on income **after** mandatory contributions; if it runs first, `contributionsSoFar` is 0.
- **Fix:** `@Order(100)` on `WithholdingTax` (contributions use 10/20/30), and `DeductionContext.taxableIncome()` subtracts contributions.
- **Lesson:** strategies aren't always independent; make the dependency explicit (ordering + context object) and test it (`taxIsComputedAfterContributions`).

### "First match wins" surprises
- **Symptom:** a broad rule placed above a specific one hides it.
- **Fix:** priority order is visible and editable in the UI (↑/↓), and every recommendation includes *which* rule fired ("rule 2: board independence 44% < 50%").
- **Lesson:** explainability is a feature of rule systems; always return the reason.

### Missing data in specifications
- **Symptom risk:** a proposal without a pay score treated as "below 50".
- **Fix:** `p.getPayScore() != null && p.getPayScore() < threshold`; missing data never matches (`missingDataNeverMatchesAThreshold` test).

### Events published but transaction rolled back
- **Symptom:** a worker processes a run whose row doesn't exist.
- **Cause:** a plain `@EventListener` fires immediately, before commit.
- **Fix:** `@TransactionalEventListener` (default phase `AFTER_COMMIT`).
- **Lesson:** side effects (emails, jobs) belong after commit; for cross-service guarantees use the outbox pattern (ch. 12).

### Over-patterning
Not every class needs an interface. `PolicyEngine` and `DeductionEngine` are concrete classes; we only introduce interfaces where there are **real alternative implementations** (storage, summarizer, rules).

## 6. SOLID mapped to this code

| Principle | Concrete example |
|---|---|
| **S**ingle responsibility | `PayrollRunService` accepts requests; `PayrollProcessor` does the work; `PayslipPdfRenderer` only renders; `S3PayslipStorage` only stores. |
| **O**pen/closed | New deduction = new `@Component DeductionRule`; new condition = new `ConditionType` constant. Engines unchanged. |
| **L**iskov substitution | Any `ProposalSummarizer` (Claude or extractive) can be used by `SummaryController` without it noticing. |
| **I**nterface segregation | `PayslipStorage` has only the three operations payroll needs, not the whole S3 API. |
| **D**ependency inversion | `PayslipService` depends on the `PayslipStorage` abstraction; the S3 adapter depends on that too, injected by Spring. |

## 7. Interview questions

1. **Give an example of the Strategy pattern from your work.** Deduction rules: one interface, one class per government contribution and tax, injected as a list into an engine. A new rule is a new class plus a test; the engine is untouched.

2. **What's the Specification pattern and how is it different from Strategy?** A Specification answers a yes/no question about an object and composes with and/or/not; a Strategy is an interchangeable *algorithm*. In App 2, conditions are Specifications; each `PolicyRule` combines them.

3. **How do you decouple the HTTP request from slow work?** Publish a domain event in the request transaction; handle it `@Async` with `@TransactionalEventListener(AFTER_COMMIT)`. Return 202 plus a status URL.

4. **Why put an interface in front of S3 or an AI API?** Testability (mock the port), vendor independence (swap the adapter), and graceful degradation (fallback adapter).

5. **How do you stop invalid state changes?** Encapsulate transitions in entity methods that check the current state (`PayrollRun.requireStatus`), with no public setters for status.

6. **Name a pattern Spring uses internally.** Proxy/Decorator (`@Transactional`), Template Method (`JdbcTemplate`, `TransactionTemplate`), Factory (`BeanFactory`), Singleton (default scope), Observer (`ApplicationEvent`).

7. **When is a pattern overkill?** When there's one implementation and no expected variation. Adding interfaces "just in case" adds indirection without benefit.

## 8. Exercise: rebuild it yourself

In App 2, add a new condition type `CATEGORY_IN_SET` (e.g. "SHAREHOLDER_ENV or SHAREHOLDER_SOCIAL") using `ProposalCondition.or(...)`, without editing `PolicyEngine`. Add unit tests in the style of [`PolicyEngineTest`](../../backend/src/test/java/dev/justine/proxyvote/policy/PolicyEngineTest.java), including one proving that rule priority still decides when two rules match. Then explain which SOLID principle the change demonstrates.

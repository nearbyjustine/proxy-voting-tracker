# 07 · Testing

> **One-line summary:** lots of fast tests with no Spring and no Docker at the bottom, a few slice tests in the middle, a handful of real-infrastructure integration tests, and a couple of browser E2E flows at the top. Constructor injection is what makes the bottom layer possible. In this build the tests caught **real bugs and my own wrong assumptions**.

---

## 1. What it is

| Tool / idea | Plain meaning | Laravel / TS equivalent |
|---|---|---|
| **JUnit 5** | The test runner + assertions (`@Test`, `@ParameterizedTest`) | PHPUnit / Vitest |
| **AssertJ** | Fluent assertions: `assertThat(x).isEqualByComparingTo("26669.95")` | `expect(x).toBe(...)` |
| **Mockito** | Fake collaborators: `when(repo.find(1)).thenReturn(...)`, `verify(events).publishEvent(any())` | Mockery / `vi.fn()` |
| **`@WebMvcTest`** | Starts **only the web layer** (controllers, advice, security filters) with `MockMvc`; services are mocks | `$this->getJson()` with mocked services |
| **`@DataJpaTest`** | Starts **only JPA** (entities, repositories, Flyway); each test rolls back | `RefreshDatabase` + model tests |
| **`@SpringBootTest`** | The **whole application context** | A full feature test |
| **Testcontainers** | Starts real Docker containers (Postgres, LocalStack) for the test, then throws them away | Laravel Sail/Docker in CI, but per test run |
| **Awaitility** | "Wait until X becomes true (max N s)" for async code | Polling helpers in E2E tests |
| **Vitest** | Unit tests for the Vue app | same |
| **puppeteer-core** | Drives your real Chrome for browser E2E | Playwright / Laravel Dusk |

### The pyramid as actually built

```
            ▲  Browser E2E (puppeteer-core): real Keycloak login, 3 users per app, CSV upload through S3→Lambda→SQS
           ▲▲  Integration (@SpringBootTest + Testcontainers): 1 test App 1, 3 tests App 2       (slow: ~2 min each)
         ▲▲▲▲  Slices: @WebMvcTest (6 App 1 + 3 App 2), @DataJpaTest on real Postgres (3)       (seconds)
     ▲▲▲▲▲▲▲▲  Unit (no Spring): rules, engine, state machine, services with Mockito,           (milliseconds)
               policy engine, deadlines, CSV parser, Vue utils
```

| Suite | Tests | Runs with |
|---|---|---|
| App 1 backend | **36** (16 rule cases, 3 engine, 2 run state machine, 3 run service, 2 payslip service, 6 web, 3 repository, 1 full flow) | `./mvnw test` |
| App 2 backend | **17** (6 policy engine, 1 deadline, 1 summarizer, 3 vote service, 3 web security, 3 ingest integration) | `./mvnw test` |
| Lambda | **6** (3 CSV parser, 3 CSV→message mapper) | `./mvnw package` |
| App 1 Vue | **8** (money/period/Manila-month format, route guard, ProblemDetail parsing) | `npm test` |
| App 2 Vue | **3** (time-zone formatting, relative time, guard) | `npm test` |
| Browser E2E | App 1 [login-flows.mjs](https://github.com/nearbyjustine/payroll-platform/blob/main/e2e/login-flows.mjs), App 2 [browser-flows.mjs](../../e2e/browser-flows.mjs) | `cd e2e && npm test` with the stack up |

---

## 2. Where we used it

### Pure unit tests (no Spring at all)
- [DeductionRulesTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/DeductionRulesTest.java): `@ParameterizedTest` + `@CsvSource` at **bracket boundaries** (20,833 vs 20,834, caps, floors).
- [DeductionEngineTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/DeductionEngineTest.java): builds the engine with `new` (constructor injection), checks rule order, and a **property-style loop**: for 40 salaries, `gross − deductions == net` and lines sum to the total.
- [PolicyEngineTest](../../backend/src/test/java/dev/justine/proxyvote/policy/PolicyEngineTest.java): first match wins, missing data never matches, priority order, Specification composition.
- [DeadlineStatusTest](../../backend/src/test/java/dev/justine/proxyvote/meeting/DeadlineStatusTest.java): "at the deadline = closed" boundary.
- Lambda [CsvParserTest](../../lambda/src/test/java/dev/justine/ingest/CsvParserTest.java): quoted commas, `""` escapes, CRLF, Excel BOM.

### Mockito service tests
- [PayrollRunServiceTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayrollRunServiceTest.java): **fixed `Clock`** (`Clock.fixed(2026-09-15…)`), so "too far in the future" is deterministic; `verifyNoInteractions(events)` when a duplicate is rejected.
- [PayslipServiceTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayslipServiceTest.java): sets the `SecurityContextHolder` by hand (`TestingAuthenticationToken`) to test the **IDOR ownership rule**.
- [VoteServiceTest](../../backend/src/test/java/dev/justine/proxyvote/voting/VoteServiceTest.java): vote at the deadline → conflict, one second before → saved + audited, stale version → optimistic-lock exception.

### Web slices: security + validation + errors
- [EmployeeControllerTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/employee/EmployeeControllerTest.java) and [VoteApiSecurityTest](../../backend/src/test/java/dev/justine/proxyvote/voting/VoteApiSecurityTest.java):
  - `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`, so the **real** URL rules are tested.
  - `@MockitoBean JwtDecoder`: otherwise the resource server would try to reach Keycloak.
  - `.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HR")))` fakes an authenticated JWT.
  - Asserts 401 / 403 / 400 with `$.errors.email`, 201 + `Location`, and the `X-Correlation-Id` header.

### JPA slice on real Postgres
- [EmployeeRepositoryTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/employee/EmployeeRepositoryTest.java): `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(TestcontainersConfiguration.class)`. The real Flyway migrations and seed data run, then it tests the `Specification` search and `Slice` paging.

### Full integration with real infrastructure
- [App 1 TestcontainersConfiguration](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/TestcontainersConfiguration.java): `@ServiceConnection` Postgres + a LocalStack container. The S3 endpoint is injected with a **`DynamicPropertyRegistrar`** bean.
- [PayrollFlowIntegrationTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayrollFlowIntegrationTest.java): request a run → **Awaitility** waits until `COMPLETED` → asserts 60 payslips, totals consistent, and **60 PDFs in S3**.
- [App 2 TestcontainersConfiguration](../../backend/src/test/java/dev/justine/proxyvote/TestcontainersConfiguration.java) creates the queue + DLQ inside the container (`execInContainer("awslocal", ...)`), with a 1 s visibility timeout so dead-lettering takes seconds.
- [IngestIntegrationTest](../../backend/src/test/java/dev/justine/proxyvote/ingest/IngestIntegrationTest.java): consumer import + recommendations, duplicate delivery ignored, poison message ends in the DLQ.

### Frontend
- App 1 [format.test.ts](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/__tests__/format.test.ts): "30 Sep 17:00 UTC is already October in Manila".
- App 2 [time.test.ts](../../frontend/src/__tests__/time.test.ts): one instant shown in New York and Tokyo time.

---

## 3. Why we chose this approach

1. **Speed buys feedback.** The 16 tax-rule cases run in ~0.03 s. The two full integration tests take ~2 minutes because they start containers. Most logic is pushed into the cheap layer.
2. **Test the real database and real AWS behaviour where it matters.** Postgres-specific things (unique constraints, `LOWER()` search, `NUMERIC`, `jsonb`, Flyway SQL) and SQS semantics (redrive to DLQ) can't be faked convincingly. Testcontainers gives us the real thing, disposable.
3. **Security is tested with the real filter chain,** not by calling controller methods directly. A wrong `requestMatchers` rule is caught.
4. **Time is injected** (`Clock` bean), so deadline and "future period" logic is deterministic.
5. **E2E only for what nothing else can prove:** the real OIDC redirect dance, role-based navigation, and the S3 → Lambda → SQS → API → UI round-trip.

---

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| **H2 in-memory DB** for repository tests | Very fast; no Docker available | Differs from Postgres (types, SQL dialect, `jsonb`, constraint behaviour). Bugs hide until production |
| **Mocking S3/SQS clients with Mockito** | Unit-testing *your* code around the SDK | Wouldn't prove redrive/DLQ or pre-signed URL behaviour. We mock at the **port** (`PayslipStorage`) for unit tests and use LocalStack for integration |
| **`@SpringBootTest` for everything** | Very small apps | Slow, so people stop running tests. Slices + pure units are 100–1000× faster |
| **Playwright** (vs puppeteer-core) | Cross-browser, auto-waits, tracing, CI-friendly | Needs to download browsers; on this network that was risky. `puppeteer-core` drives the installed Chrome. Playwright is the recommended upgrade |
| **WireMock** | Testing calls to third-party HTTP APIs (e.g. the Claude API) | The summarizer degrades to an offline fallback when no key is present; add WireMock to test the Claude path and error handling |
| **Contract tests (Spring Cloud Contract / Pact)** | Separate teams own API and UI | Single repo, typed DTOs; worth it at team scale |
| **Mutation testing (PIT)** | Checking that tests would catch changed logic | Nice next step for the rule engines |

---

## 5. Problems & pitfalls

### Real problems the tests surfaced (or that we hit while testing)

**P1. My expected values were wrong, not the code**
- *Symptom:* 2 of 16 `withholdingTaxBrackets` cases failed: 100,000 expected `16874.30`, got `16875.05`; 700,000 expected `195208.25`, got `195208.35`.
- *Cause:* hand arithmetic. 8,541.80 + (100,000 − 66,667) × 25% = **16,875.05**.
- *Fix:* corrected the test data and added the formula as a comment next to each value.
- *Lesson:* a failing test means "code and spec disagree". Check which one is wrong before changing the code. Writing the formula into the test makes it reviewable.

**P2. Malformed request body returned 500 instead of 400** (found by `VoteApiSecurityTest.unknownDecisionIsRejected`)
- *Cause:* `{"decision":"MAYBE"}` throws `HttpMessageNotReadableException`, which our catch-all `@ExceptionHandler(Exception.class)` turned into a 500 (and logged as an error).
- *Fix:* explicit handler → 400 in **both** apps' [ApiExceptionHandler](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/ApiExceptionHandler.java).
- *Lesson:* a catch-all handler hides client errors as server errors. Test the unhappy paths (bad JSON, unknown enum, wrong type).

**P3. A Vue page would have crashed in the browser** (found by `time.test.ts`)
- *Cause:* `Intl.DateTimeFormat` **throws `TypeError`** when `dateStyle`/`timeStyle` are combined with `timeZoneName`.
- *Fix:* explicit fields (`year, month, day, hour, minute, timeZoneName`) in [time.ts](../../frontend/src/utils/time.ts).
- *Lesson:* even "formatting helpers" deserve a test, because a runtime exception in a helper blanks the whole page.

**P4. Integration tests hung for 6 minutes, then failed**
- *Cause:* Testcontainers starts a helper container, **`testcontainers/ryuk`** (it cleans up containers after the run). Docker Hub was unreachable on this network, so the pull timed out ("Can't get Docker image: testcontainers/ryuk:0.14.0").
- *Fix:* pulled it via Google's mirror and retagged: `docker pull mirror.gcr.io/testcontainers/ryuk:0.14.0 && docker tag … testcontainers/ryuk:0.14.0`. Same for `postgres:17-alpine` and `localstack/localstack:4.9`.
- *Lesson:* integration tests have infrastructure dependencies too. In CI, pre-pull or use a registry mirror (`hub.image.name.prefix` in `~/.testcontainers.properties`).

**P5. Flaky browser test: the 3rd login timed out**
- *Cause:* a test-design bug, not an app bug. Clearing cookies between users didn't clear Keycloak's SSO session cookie for the other origin, so the next "Sign in" behaved differently.
- *Fix:* each user gets a fresh **isolated browser context** (`browser.createBrowserContext()`), like a new incognito window.
- *Lesson:* E2E tests must not share state; isolate per scenario.

### Classic pitfalls

| Pitfall | Fix |
|---|---|
| Tests depend on the current date/time | Inject `Clock`; use `Clock.fixed` in tests |
| `Thread.sleep(5000)` for async code (slow *and* flaky) | Awaitility: `await().atMost(60s).until(...)` |
| Over-mocking (mocking the class under test's internals) | Mock only collaborators at boundaries (repositories, ports, publishers) |
| Comparing `BigDecimal` with `equals` (`1350.0` ≠ `1350.00`) | `isEqualByComparingTo` |
| Lazy-loading exceptions in tests outside a transaction | Use `TransactionTemplate` in the test (as in `IngestIntegrationTest`) or fetch what you need |
| Shared mutable DB state between integration tests | Unique business keys per test (`IT-PAY-1`, `IT-DUP-1`) or clean up; `@DataJpaTest` rolls back automatically |
| Testing security by calling controller methods directly | Go through `MockMvc` with the real `SecurityFilterChain` |

---

## 6. Interview questions

**Q1 (basic). Unit vs integration vs E2E: how did you balance them?**
A pyramid: dozens of pure unit tests for business rules (milliseconds), slice tests for the web and JPA layers, a few `@SpringBootTest` tests with Testcontainers for full flows, and two browser E2E suites for login and the upload pipeline.

**Q2. How does constructor injection help testing?**
I can `new DeductionEngine(List.of(new SssContribution(), …))` or `new PayrollRunService(mockRepo, …, fixedClock)` with no Spring context at all. Those tests run in milliseconds and break only when logic changes.

**Q3. `@WebMvcTest` vs `@SpringBootTest`?**
`@WebMvcTest` loads only MVC components: controllers, advice, filters (and the security config I import). Services are `@MockitoBean`s. It's for status codes, validation, JSON shape and security rules. `@SpringBootTest` loads everything and is for real end-to-end flows.

**Q4. How do you test a secured endpoint without a running Keycloak?**
Spring Security Test's `jwt()` request post-processor injects an authenticated JWT with chosen authorities. I also mock the `JwtDecoder` bean so the resource server doesn't try to fetch keys.

**Q5. Why Testcontainers instead of H2?**
H2 isn't Postgres: SQL dialect, types like `jsonb` and `NUMERIC`, and constraint behaviour differ. Testcontainers runs the real Postgres version with the real Flyway migrations, so a passing test means something.

**Q6. How do you test asynchronous code?**
Trigger it, then poll the observable result with Awaitility under a timeout, e.g. wait until the payroll run is `COMPLETED`, then assert totals and the S3 objects. Never fixed sleeps.

**Q7 (advanced). Tell me about a bug your tests caught.**
Two: (1) a malformed JSON body returned 500 because the catch-all exception handler swallowed `HttpMessageNotReadableException`; a security slice test expecting 400 caught it, and the fix went into both apps. (2) A Vue time-formatting helper threw a `TypeError` because `Intl` forbids `timeStyle` with `timeZoneName`; that would have blanked the meetings page.

**Q8 (advanced). How did you prove dead-lettering works?**
An integration test against real SQS in LocalStack. The test queue has a 1 s visibility timeout and `maxReceiveCount=2`. I send invalid JSON; the consumer fails and doesn't delete it; Awaitility waits until the DLQ's `ApproximateNumberOfMessages > 0`.

---

## 7. Exercise: rebuild it yourself

Write a **new `@WebMvcTest` for App 1's `PayrollController`** from scratch:
1. `POST /api/payroll-runs` as `ROLE_PAYROLL_ADMIN` with `{"period":"2026-09"}` → 202, `Location` header, body `status = PENDING` (mock `PayrollRunService.request`).
2. Same request as `ROLE_HR` → 403. Anonymous → 401.
3. `{"period":"2026-13"}` → 400 with `$.errors.period`.
4. Service throws `ConflictException("error.conflict.payrollExists", ...)` → 409 with `$.code == "error.conflict.payrollExists"`.
5. Bonus: add `Accept-Language: fil` and assert the Filipino `detail` text.

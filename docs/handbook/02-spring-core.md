# 02 · Spring core: IoC, dependency injection, configuration, Boot 4

## 1. What it is

Spring is a **container**. At startup it scans your packages, creates one instance (a *bean*) of each class annotated `@Component`, `@Service`, `@Repository`, `@RestController` or `@Configuration`, and **passes beans into each other's constructors**. That's *Inversion of Control* (the framework creates objects, not you) and *Dependency Injection* (dependencies are handed in, not looked up).

Spring Boot adds **auto-configuration**. If a library is on the classpath (PostgreSQL driver, Flyway, Spring Security…), Boot configures sensible beans for it unless you define your own.

| Laravel | Spring |
|---|---|
| Service container, constructor type-hint resolution | IoC container, constructor injection |
| Service provider `register()` / `$this->app->bind()` | `@Configuration` class with `@Bean` methods |
| `config('app.x')`, `.env` | `application.yml` + env vars, bound to `@ConfigurationProperties` |
| Package auto-discovery | Auto-configuration (conditional beans) |
| Middleware, macros | Filters, AOP proxies |
| Per-request PHP process | **One long-lived JVM**: beans are shared by all request threads |

## 2. Where we used it

### Constructor injection everywhere
Every service takes its dependencies in the constructor and stores them in `final` fields. No `@Autowired` is needed with a single constructor.
- [`PayrollProcessor`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java) gets 9 collaborators (repositories, engine, renderer, storage, `PlatformTransactionManager`, properties, `Clock`).
- Because of this, [`DeductionEngineTest`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/DeductionEngineTest.java) and [`PolicyEngineTest`](../../backend/src/test/java/dev/justine/proxyvote/policy/PolicyEngineTest.java) just call `new` with no Spring context and run in milliseconds.

### `@Configuration` + `@Bean` for things we don't own
- [`AwsConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AwsConfig.java) builds `S3Client` and `S3Presigner` (AWS SDK classes can't be annotated).
- [`AsyncConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AsyncConfig.java) defines a named bounded `ThreadPoolTaskExecutor` (`payrollExecutor`) with a `TaskDecorator`.
- [`TimeConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/TimeConfig.java) exposes a `Clock` bean.
- [`AiConfig`](../../backend/src/main/java/dev/justine/proxyvote/ai/AiConfig.java) **chooses an implementation at startup**: `ClaudeProposalSummarizer` if `ANTHROPIC_API_KEY` is set, otherwise `ExtractiveSummarizer`. It's marked `@Primary` so `SummaryController` gets the chosen one.

### Typed configuration
- [`AppProperties`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AppProperties.java) is a record bound to the `app:` block of [`application.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/application.yml), enabled via `@EnableConfigurationProperties`.
- Values come from env vars with defaults: `url: ${DB_URL:jdbc:postgresql://localhost:5432/payroll}`. The same jar runs locally and in Docker Compose (where `DB_URL` points at `postgres:5432`).
- `presign-ttl: 5m` binds straight into a `java.time.Duration`.

### Injecting *all* implementations of an interface
```java
public DeductionEngine(List<DeductionRule> rules) { this.rules = List.copyOf(rules); }
```
Spring injects every `DeductionRule` bean, **sorted by `@Order`** (SSS = 10, PhilHealth = 20, Pag-IBIG = 30, withholding tax = 100). Tax must run last because it uses income *after* contributions. See [`DeductionEngine`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/DeductionEngine.java).

### Conditional beans
- [`SqsMeetingConsumer`](../../backend/src/main/java/dev/justine/proxyvote/ingest/SqsMeetingConsumer.java) has `@ConditionalOnProperty(name = "app.ingest.consumer-enabled", havingValue = "true")`, so the poller can be switched off without code changes.

### Proxies (AOP) behind the annotations
`@Transactional`, `@Async`, `@Cacheable`, `@PreAuthorize` and `@TransactionalEventListener` all work by Spring wrapping your bean in a **proxy** that runs extra logic before and after the call.
- `@Cacheable("departments")` on `EmployeeService.departments()`, enabled by `@EnableCaching`.
- `@Async("payrollExecutor")` on `PayrollProcessor.onRunRequested`, enabled by `@EnableAsync`.
- `@EnableScheduling` + `@Scheduled` on `SqsMeetingConsumer.poll()` in App 2.

### Spring Boot 4 specifics we hit
- Starters were split: **`spring-boot-starter-webmvc`** (not `-web`), `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-flyway`, and a **matching `-test` starter per module** (`spring-boot-starter-webmvc-test`, `-data-jpa-test`…). See [`pom.xml`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/pom.xml).
- Test annotations moved packages: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.
- **Jackson 3** is the default JSON library, in the new `tools.jackson` package. [`SqsMeetingConsumer`](../../backend/src/main/java/dev/justine/proxyvote/ingest/SqsMeetingConsumer.java) injects `tools.jackson.databind.json.JsonMapper`. Jackson 3 handles `java.time` types without extra modules. The Lambda still uses Jackson 2 (`com.fasterxml`), and the two live side by side because the packages differ.
- `@MockitoBean` (from `spring-test`) replaces the old `@MockBean`.

## 3. Why we chose this approach

- **Constructor injection** makes dependencies explicit, lets fields be `final`, fails fast at startup if a bean is missing, and makes classes unit-testable without Spring.
- **Typed `@ConfigurationProperties` records**: one place to see every setting, validated and typed at startup, instead of `@Value("${...}")` strings scattered across classes.
- **Env vars with defaults**: the twelve-factor approach. No rebuild per environment, and secrets (like `ANTHROPIC_API_KEY`) never live in git.
- **`List<Interface>` + `@Order`**: new deductions plug in without touching the engine (open/closed principle).

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| Field injection (`@Autowired private X x;`) | Quick prototypes | Hidden dependencies, can't be `final`, needs reflection or Spring in tests. |
| Setter injection | Truly optional dependencies | All our dependencies are required. |
| `@Value("${x}")` per field | One or two isolated settings | Untyped strings, no single overview, no IDE completion. |
| Spring profiles (`application-prod.yml`) | Many settings differing per environment | Env vars cover our differences (URLs, keys); profiles can be added later. |
| `@ConditionalOnProperty` on two beans | Static on/off switches | `AiConfig` needs logic (key present?) and a fallback, so a factory method is clearer. |
| Jakarta CDI / Quarkus / Micronaut | Faster startup, native images, serverless | The target role (Glass Lewis) uses Spring Boot. |
| Guice / manual wiring | Tiny libraries | We want the Boot ecosystem (Security, Data, Actuator). |

## 5. Problems & pitfalls

### Mutable state in a singleton (classic)
- **Symptom:** data from one request appears in another under load.
- **Cause:** beans are **singletons by default**, shared across all request threads. Unlike PHP, there's no per-request process.
- **Fix:** keep services stateless. Only `final` dependencies, no per-request fields.
- **Lesson:** "stateless services" is the first thing to say when asked about bean scopes.

### Self-invocation bypasses the proxy
- **Symptom:** `@Transactional` or `@Async` "does nothing".
- **Cause:** calling `this.otherMethod()` inside the same class skips the proxy that implements the annotation.
- **Fix:** call through another bean. In `PayrollProcessor` we don't rely on annotations for the inner steps; we use an explicit `TransactionTemplate`.
- **Lesson:** annotations = proxies = only apply to calls coming from *outside* the bean.

### Missing bean fails at startup (good!)
- **Symptom:** `required a bean of type ... that could not be found`.
- **Cause:** a class without a stereotype annotation, or outside the scanned package (`dev.justine.payroll` and below).
- **Fix:** annotate it, or define a `@Bean`.
- **Lesson:** DI fails fast at boot, not at 3 a.m. on the first request.

### Thread-locals don't cross `@Async`
- **Symptom:** async payroll logs had no correlation ID; `SecurityContext` was empty in the worker.
- **Cause:** MDC and the security context are thread-local.
- **Fix:** a `TaskDecorator` in `AsyncConfig` copies the MDC to the worker thread and clears it afterwards. The JPA auditor falls back to `"system"` when there's no user.
- **Lesson:** anything thread-local must be explicitly propagated, or deliberately not.

### Boot 4 / Jackson 3 surprises (real, this session)
- **Symptom:** old tutorials' imports (`com.fasterxml.jackson.databind.ObjectMapper`, `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`) don't match.
- **Cause:** Boot 4 modularised starters and moved to Jackson 3 (`tools.jackson`).
- **Fix:** generate the project at start.spring.io, then let the IDE resolve imports. We inject `JsonMapper` from `tools.jackson`.
- **Lesson:** check the framework's major version before copying tutorial code.

### Unbounded thread pools
- **Symptom:** under a burst, memory climbs until the JVM dies.
- **Cause:** `new Thread()` per task, or an unbounded queue.
- **Fix:** `payrollExecutor` has core 2, max 4, queue 20, and waits for tasks on shutdown.
- **Lesson:** every pool needs bounds and a plan for when it's full.

## 6. Interview questions

1. **What is dependency injection and why does Spring use constructor injection?**
   Objects receive their dependencies instead of creating them. Constructor injection gives immutable `final` fields and explicit, required dependencies, and lets you unit-test with `new` and mocks. Our engines are tested exactly that way.

2. **What's the default bean scope, and what does that imply?**
   Singleton: one instance per container, shared by all threads. Services must be stateless. Other scopes are prototype, request, session.

3. **How does Spring choose between two beans of the same type?**
   `@Primary`, `@Qualifier("name")`, or inject them all as a `List`/`Map`. We use `List<DeductionRule>` with `@Order` in App 1, and `@Primary` on the chosen `ProposalSummarizer` in App 2.

4. **How does auto-configuration work?**
   Boot ships configuration classes guarded by conditions such as `@ConditionalOnClass` and `@ConditionalOnMissingBean`. With the Postgres driver and a URL present you get a `DataSource`; if you define your own bean, Boot's backs off.

5. **`@Configuration`/`@Bean` vs `@Component`?**
   `@Component` is for your own classes. `@Bean` methods are for third-party classes or construction logic, like `S3Client.builder()...` in `AwsConfig`.

6. **How do you externalise config and keep secrets out of git?**
   `application.yml` with `${ENV_VAR:default}` placeholders, bound to a typed `@ConfigurationProperties` record. Real secrets (DB password, API keys) come from env vars or a secrets manager. `ANTHROPIC_API_KEY` is read that way.

7. **Why might `@Transactional` not work on a method?**
   Self-invocation, a non-public method, an exception swallowed inside, or a checked exception (no rollback by default). All are proxy-related.

8. **What changed in Spring Boot 4 that affected you?**
   Modular starters (`starter-webmvc` plus a matching `-test` starter per module), new test-annotation packages, Jackson 3 under `tools.jackson`, and Java 17+ (we use 21).

## 7. Exercise: rebuild it yourself

Add a fifth deduction, "Union dues: 1% capped at 300, regular employees only", **without editing `DeductionEngine`**. Give it the right `@Order` so it counts as a pre-tax contribution, add a parameterized test, and check that `grossMinusDeductionsAlwaysEqualsNet` in `DeductionEngineTest` still passes. Then explain out loud why you didn't need to touch the engine.

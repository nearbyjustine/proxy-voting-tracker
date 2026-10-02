# 13 · Tech Choices & Alternatives

For every technology in this repo: **what it is → why we chose it → alternatives (and when they'd be better) → a one-liner you can say in an interview.** Decision records for the big calls are at the end.

---

## Backend

### Java 21 (LTS)
- **What:** the language and runtime (JVM). 21 is a long-term-support release with records, sealed types, pattern matching for `switch`, and virtual threads.
- **Why:** the job description asks for Java. LTS is what enterprises run; records make DTOs one-liners.
- **Alternatives:**
  - **Java 25** (the next LTS) once your platform supports it.
  - **Kotlin** (null-safety, less boilerplate, same Spring).
  - **C#/.NET** (the JD mentions it): an equivalent ecosystem; choose it in Microsoft/Azure shops.
- **One-liner:** "Java 21 LTS: records for DTOs, `java.time` for deadlines, `BigDecimal` for money, and virtual threads available when we need high-concurrency I/O."

### Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, Actuator)
- **What:** an opinionated Spring setup with auto-configuration, starters, an embedded Tomcat and production endpoints.
- **Why:** it's the standard for Java web backends, and it's in the JD. Boot 4 is what start.spring.io generates today.
- **Alternatives:**
  - **Quarkus / Micronaut:** faster startup and lower memory, great for serverless or containers at scale.
  - **Spring WebFlux:** reactive, for very high-concurrency streaming. Not needed for CRUD plus batch.
  - **Plain Jakarta EE.**
- **One-liner:** "Spring Boot for DI, MVC and Security; I keep services stateless and constructor-injected so they're testable without the container."

### Maven (with the wrapper `./mvnw`)
- **What:** build tool and dependency manager (like Composer). The wrapper pins the Maven version per project.
- **Why:** it's the default for Spring projects, declarative, and the most common choice in enterprises. BOM imports (AWS SDK) keep versions aligned.
- **Alternatives:** **Gradle** (faster incremental builds, Kotlin DSL, better for big multi-module builds).
- **One-liner:** "Maven with BOMs for consistent versions; the wrapper so every machine and CI uses the same Maven."

### PostgreSQL 17
- **What:** a relational database.
- **Why:** strong SQL, `NUMERIC` for money, `timestamptz`, `jsonb` (audit details), partial and functional indexes (`LOWER(full_name)`), and row-level security for multi-tenancy later. RDS supports it.
- **Alternatives:**
  - **MySQL** (common at PH companies, fine for this).
  - **Aurora PostgreSQL** (managed scale-out reads, faster failover).
  - **DynamoDB** (key-value at massive scale, but no joins or ad-hoc queries; a poor fit for relational payroll data).
- **One-liner:** "Postgres: transactions and constraints are my last line of defence: unique keys stop duplicate payroll runs even under races."

### Flyway
- **What:** versioned SQL migrations (`V1__init.sql`, `V2…`) applied at startup and tracked in `flyway_schema_history`.
- **Why:** the schema lives in git and is reviewed like code; with `ddl-auto: validate`, the app refuses to start if entities and schema drift apart.
- **Alternatives:**
  - **Liquibase:** XML/YAML changelogs, rollbacks, database-agnostic; better when you support several databases.
  - **Hibernate `ddl-auto=update`:** fine for throwaway demos, dangerous in production (no review, no rollback, can't rename).
- **One-liner:** "Flyway owns the schema; I never edit an applied migration. I fix forward with a new one, like V3 when a CHAR vs VARCHAR mismatch broke validation."

### Hibernate / JPA via Spring Data JPA
- **What:** ORM (JPA is the spec, Hibernate the implementation) plus generated repositories, derived queries, `@EntityGraph`, Specifications and auditing.
- **Why:** less boilerplate for CRUD, dirty checking, optimistic locking with `@Version`, and auditing annotations.
- **Alternatives:**
  - **jOOQ:** type-safe SQL, great for reporting and complex queries.
  - **Spring JDBC / JdbcClient:** simple and explicit, no ORM magic.
  - **MyBatis:** SQL-mapper style.
- **One-liner:** "JPA for the domain model, but I'm deliberate about fetching: LAZY by default, `@EntityGraph` per use case, open-in-view off."

### Keycloak 26
- **What:** an open-source identity provider (OIDC/OAuth2/SAML): login pages, users, roles, token issuing.
- **Why:** real OIDC locally for free, the realm is imported from JSON, and it maps cleanly to Cognito, Okta or Entra ID in production. LocalStack's Cognito needs the paid tier.
- **Alternatives:**
  - **AWS Cognito** (managed, AWS-native).
  - **Okta / Auth0 / Entra ID** (enterprise SSO, managed).
  - **Spring Authorization Server** (embed your own; more code to own).
- **One-liner:** "Keycloak locally as the OIDC provider; the API is just a resource server, so swapping to Cognito or Entra is configuration, not code."

### AWS SDK for Java v2
- **What:** the official AWS client library (S3, S3Presigner, SQS).
- **Why:** it's the current SDK (v1 is in maintenance), it's modular (only `s3` + `sqs`), and it supports endpoint overrides for LocalStack.
- **Alternatives:**
  - **Spring Cloud AWS** (`@SqsListener`, S3 templates): less code, but another framework layer whose version must track Spring Boot. Boot 4 support lagged when this was built.
  - The **AWS CLI**, for scripts only.
- **One-liner:** "AWS SDK v2 directly. The SQS consumer is 60 lines and makes the delete-after-commit and DLQ behaviour explicit."

### OpenPDF
- **What:** a pure-Java PDF library (an LGPL fork of iText 4).
- **Why:** no external process or headless browser, it's small, and payslips are simple tables.
- **Alternatives:**
  - **iText 7+** (more features, AGPL/commercial licence).
  - **Flying Saucer / openhtmltopdf** (HTML/CSS templates → PDF; nicer for designers).
  - **JasperReports** (report designer, pixel-perfect).
- **One-liner:** "OpenPDF for simple generated documents; for designed templates I'd render HTML with Thymeleaf and convert with openhtmltopdf."

### anthropic-java SDK (Claude, `claude-opus-5`, low effort)
- **What:** the official SDK for the Claude API. It summarizes shareholder proposals in two sentences.
- **Why:**
  - The official SDK gives typed requests, retries and timeouts.
  - `claude-opus-5` is the current recommended model.
  - `effort: low` suits short summaries (lower latency and cost).
  - Server-side refusal fallbacks (`fallbacks: "default"`) keep the feature working if a request is declined.
  - It sits behind a `ProposalSummarizer` port with an offline fallback.
- **Alternatives:**
  - Raw HTTP with `RestClient` (fewer dependencies, but you rebuild retries and types).
  - A cheaper model tier for high-volume summarization (a cost/quality decision to measure).
  - Batch API for bulk overnight summaries.
- **One-liner:** "AI is behind an interface with a deterministic fallback, so an outage or refusal degrades one feature instead of breaking the page."

## Testing

### Testcontainers (Postgres + LocalStack)
- **What:** starts real Docker containers from JUnit; `@ServiceConnection` wires Spring to them.
- **Why:** tests run against the *same* Postgres and the same S3/SQS behaviour as production: NUMERIC, constraint errors, Flyway migrations, DLQ redrive.
- **Alternatives:**
  - **H2 in-memory** (fast, but a different SQL dialect, which hides bugs).
  - **Embedded fakes** (e.g. ElasticMQ for SQS, MinIO for S3).
  - Shared test databases (flaky, order-dependent).
- **One-liner:** "Testcontainers instead of H2, because the CHAR vs VARCHAR and constraint bugs only show up on the real database."

### JUnit 5, Mockito, AssertJ, Awaitility, MockMvc
- **What:**
  - **JUnit 5:** the test runner; `@ParameterizedTest` for tax brackets.
  - **Mockito:** mocks.
  - **AssertJ:** fluent assertions, e.g. `isEqualByComparingTo` for `BigDecimal`.
  - **Awaitility:** waits for async results without `sleep`.
  - **MockMvc** + `jwt()`: web-slice tests with the real security rules.
- **Alternatives:**
  - **Spock** (Groovy, very readable).
  - **WireMock** (fake external HTTP APIs, e.g. to test the Claude adapter).
  - **REST Assured** (HTTP-level API tests).
- **One-liner:** "A pyramid: millisecond unit tests for rules, Mockito for services, `@WebMvcTest` for HTTP and security, and a few Testcontainers integration tests for the real flows."

### puppeteer-core (browser E2E)
- **What:** drives your installed Chrome; logs in through Keycloak's real login page.
- **Why:** it needs no extra browser download (the network was restricted) and is enough for a few smoke flows.
- **Alternatives:** **Playwright** (better test runner, auto-waits, multi-browser; the default choice for a real E2E suite); **Cypress**.
- **One-liner:** "A handful of browser smoke tests for the critical flows; everything else lower in the pyramid."

## Frontend

### Vue 3 + Vite + TypeScript + Pinia + vue-router + vue-i18n
- **Why:** Vue is in the JD and was used at RSB (Nuxt 3) and Savorite. The Composition API plus TypeScript gives typed, reusable logic. Vite gives an instant dev server. Pinia is the official store. vue-i18n is needed because the brief asks for globalisation.
- **Alternatives:**
  - **Nuxt 3** (SSR, file routing; useful for public or SEO pages).
  - **React + Vite** (bigger ecosystem).
  - **TanStack Query** for server-state caching.
  - **PrimeVue** for enterprise components.
- **One-liner:** "Vue 3 Composition API with typed composables and a single API client that turns ProblemDetail into form field errors."

### oidc-client-ts
- **What:** a browser OIDC client: Authorization Code + PKCE, silent renew.
- **Alternatives:**
  - **keycloak-js** (Keycloak-specific).
  - **A BFF** (tokens stay server-side; the most secure option for SPAs).
  - **@auth0/auth0-vue** (Auth0-specific).
- **One-liner:** "Standards-based OIDC, so the SPA works with any provider, and the API never trusts the UI for authorization."

### nginx
- **What:** a web server serving the built SPA, with SPA fallback and cache headers.
- **Alternatives:**
  - **S3 + CloudFront** (the production default for static sites on AWS).
  - **Caddy** (automatic HTTPS).
  - Serving `dist/` from Spring Boot (one deployable, but it couples the release cycles).
- **One-liner:** "nginx in the container for parity; in AWS I'd host the SPA on S3 + CloudFront."

## Infrastructure & delivery

### LocalStack 4.9
- **What:** emulates AWS services (S3, SQS, Lambda) in one Docker container.
- **Why:** you can develop and test the S3 → Lambda → SQS pipeline with no AWS account, no cost and no internet. Pinned to 4.9 because newer images require an auth token.
- **Alternatives:**
  - **Moto server** (open-source AWS mocks).
  - **MinIO** (S3) + **ElasticMQ** (SQS) as separate emulators.
  - A real AWS sandbox account, for the highest fidelity at real cost.
- **One-liner:** "LocalStack locally and in Testcontainers; the code only differs by an endpoint override, and in AWS it uses the default credential chain and IAM roles."

### Docker & Docker Compose
- **Why:** a one-command local stack, healthchecks to order startup, and profiles for infra-only vs full.
- **Alternatives:** **Kubernetes** (kind/minikube) when production is k8s; **Testcontainers dev mode** (start the app from the IDE with containers, no compose file).
- **One-liner:** "Compose with healthchecks and profiles; everything provisioned from files in git: the realm JSON and LocalStack init scripts."

### GitHub Actions
- **Why:** CI next to the code, with free Ubuntu runners that have Docker (so Testcontainers works).
- **Alternatives:** **Jenkins** (used at RSB; self-hosted, flexible), **GitLab CI**, **AWS CodePipeline/CodeBuild**.
- **One-liner:** "Every PR builds and runs unit, slice and Testcontainers tests for both apps; merging builds the images."

---

## Decision records (short ADRs)

Format: **Context → Decision → Consequences.**

### ADR-1: Modular monolith
- **Context:** two small teams' worth of features, with strong consistency needs (payslips and totals, votes and audit).
- **Decision:** one Spring Boot app per product, packaged by business module, with modules talking through services and events. Only the ingestion step is a separate Lambda.
- **Consequences:** local transactions, simple deploys and simple debugging. If payroll processing needs independent scaling, extract the worker behind SQS first; module boundaries make that cheap.

### ADR-2: Keycloak as the OIDC provider; the API is a pure resource server
- **Context:** we need realistic login, roles and a tenant claim locally; the production provider may be Cognito or Entra ID.
- **Decision:** Keycloak with an imported realm. Spring validates JWTs (`issuer-uri` + `jwk-set-uri`); a custom converter maps `realm_access.roles`; App 2 gets a declared `org` user attribute mapped to a claim.
- **Consequences:** swapping providers is configuration. The trade-off is one more container and some realm JSON to maintain.

### ADR-3: Flyway + `ddl-auto: validate` instead of Hibernate generating the schema
- **Context:** the schema must be reviewable, repeatable and safe to evolve.
- **Decision:** versioned SQL migrations; Hibernate only validates.
- **Consequences:** drift fails fast at startup (it caught the CHAR vs VARCHAR bug). Never edit applied migrations; fix forward (V3).

### ADR-4: Testcontainers instead of H2
- **Context:** H2 behaves differently from Postgres (types, constraints, functions, `jsonb`).
- **Decision:** repository and integration tests run against real Postgres (and LocalStack) containers.
- **Consequences:** slower tests (seconds, not milliseconds) and Docker required, but far fewer "passes in CI, fails in prod" bugs. Keep most tests as fast unit tests.

### ADR-5: Pre-signed URLs for file transfer
- **Context:** payslip PDFs (download) and meeting CSVs (upload) shouldn't flow through the API.
- **Decision:** the API authorises, then returns a short-lived pre-signed GET or PUT for one object key.
- **Consequences:** no big bodies on API threads, and S3 scales the transfer. Requires bucket CORS for uploads; validation moves to the event consumer (the Lambda).

### ADR-6: Hand-written SQS consumer instead of Spring Cloud AWS `@SqsListener`
- **Context:** we need explicit control over delete-after-commit, idempotency and DLQ behaviour; Spring Cloud AWS support for Boot 4 lagged.
- **Decision:** a `@Scheduled` long-poll loop using the AWS SDK v2; delete only after a successful transactional import.
- **Consequences:** ~60 lines we own, easy to explain and test. At scale, switch to Spring Cloud AWS or a dedicated worker service for concurrency and visibility-extension features.

### ADR-7: Runtime-only backend Dockerfile
- **Context:** the Docker build VM couldn't reach Maven Central, and CI already builds and tests the jar.
- **Decision:** the image only copies `target/*.jar` onto a JRE base, runs as non-root, with `MaxRAMPercentage=75`.
- **Consequences:** fast image builds that reuse the tested artifact, but `docker build` needs the jar built first. Where the build can reach Maven, a multi-stage Dockerfile or `spring-boot:build-image` is the self-contained alternative.

### ADR-8: AI behind a port with an offline fallback
- **Context:** proposal summaries are helpful but non-critical, and the provider can be slow, down, rate-limited or decline a request.
- **Decision:** a `ProposalSummarizer` interface. The Claude adapter (official SDK, timeouts, retries, refusal fallbacks, low effort) is used when a key is set; otherwise, or on any failure, an extractive summarizer is used.
- **Consequences:** the UI never breaks because of the AI, and tests and demos run offline. The quality difference between the two summarizers is visible via the `aiSummarySource` field.

# The Handbook

> This handbook is shared by two repos: [payroll-platform](https://github.com/nearbyjustine/payroll-platform) (App 1) and [proxy-voting-tracker](https://github.com/nearbyjustine/proxy-voting-tracker) (App 2). Paths like `app1-payroll/` below refer to those repos.

This handbook explains **everything that was built** in this repo: every concept, pattern and technology, why it was chosen, what the alternatives are, and the real problems we hit while building. It's written for someone who knows Laravel/PHP, TypeScript/Vue and Python and is learning Java and Spring Boot for interviews.

Every chapter points at real code. Read the chapter, open the linked file, and check that the explanation matches what you see.

## Chapters

| # | Chapter | One line |
|---|---|---|
| 01 | [Java](01-java.md) | The language features the apps rely on: records, enums, interfaces, streams, `Optional`, `BigDecimal`, `java.time`. |
| 02 | [Spring core](02-spring-core.md) | IoC/DI, beans, configuration properties, profiles, auto-configuration, proxies. |
| 03 | [Data & JPA](03-data-jpa.md) | Entities, repositories, transactions, N+1, optimistic locking, Flyway migrations. |
| 04 | [REST API](04-rest-api.md) | Controllers, DTOs, validation, ProblemDetail errors, status codes, pagination. |
| 05 | [Security, OAuth2 & OIDC](05-security-oauth2-oidc.md) | Keycloak, JWT resource server, roles, object-level authorization, multi-tenancy. |
| 06 | [Async, events & messaging](06-async-events-messaging.md) | `@Async`, `@TransactionalEventListener`, `@Scheduled`, SQS, idempotency, DLQ. |
| 07 | [Testing](07-testing.md) | The test pyramid: unit, Mockito, `@WebMvcTest`, `@DataJpaTest`, Testcontainers, browser E2E. |
| 08 | [Design patterns](08-design-patterns.md) | Strategy, Specification, Observer, Repository, State machine, Port/Adapter, Template. |
| 09 | [AWS & LocalStack](09-aws-localstack.md) | S3, pre-signed URLs, SQS, Lambda, IAM, and running all of it locally. |
| 10 | [Frontend (Vue)](10-frontend-vue.md) | Vue 3, Pinia, OIDC with PKCE, router guards, i18n, the typed API client. |
| 11 | [DevOps, Docker & CI](11-devops-docker-ci.md) | Compose profiles, healthchecks, Dockerfiles, GitHub Actions, real AWS pipelines. |
| 12 | [Architecture problems & solutions](12-architecture-problems-solutions.md) | **The centrepiece.** Real problems in systems like these and the architectural fix for each. |
| 13 | [Tech choices & alternatives](13-tech-choices-alternatives.md) | Every technology: why it was chosen, what else you could use, plus decision records. |

## The learning loop

Use this for every feature you rebuild yourself. It's how you stop blanking when you have to write code:

1. **Ticket.** Write one sentence plus acceptance criteria, for example: "POST /payroll-runs creates payslips; same month twice returns 409".
2. **25 minutes alone.** No AI, no copying. Getting stuck is the point.
3. **Narrow lookup.** One question, one source (spring.io guides, Baeldung, this handbook). Close the tab, then write it yourself.
4. **Test.** If you can't write a test for it, you don't understand it yet.
5. **Review.** Compare with the code in this repo, or ask for a review (hints, not answers).
6. **LEARNINGS note.** Three lines: what broke, what fixed it, and the interview sentence.
7. **Explain it out loud** in 60 seconds. If you can't, go back to step 4.

## Reading order for interview prep

| Interview stage | Read | Then practise |
|---|---|---|
| "Tell me about yourself" / HR | [12](12-architecture-problems-solutions.md) (skim the problem titles) | Pick 3 problems and tell them as STAR stories |
| Java fundamentals | [01](01-java.md) | Rewrite `DeductionEngine` from memory |
| Spring basics (DI, beans, config) | [02](02-spring-core.md), [04](04-rest-api.md) | Build a CRUD controller + service + test |
| Data layer (JPA, transactions, N+1) | [03](03-data-jpa.md) | Explain `@Transactional` pitfalls, then show the N+1 fix |
| Security (OAuth2/OIDC, roles) | [05](05-security-oauth2-oidc.md) | Draw the PKCE login flow on paper |
| Async / messaging / AWS | [06](06-async-events-messaging.md), [09](09-aws-localstack.md) | Explain the S3 → Lambda → SQS → API pipeline end to end |
| Testing | [07](07-testing.md) | Write one Mockito test and one `@WebMvcTest` from memory |
| Design / system design | [08](08-design-patterns.md), [12](12-architecture-problems-solutions.md), [13](13-tech-choices-alternatives.md) | "Design a payroll run for 10,000 employees" |
| Frontend (Vue is core in the JD) | [10](10-frontend-vue.md) | Explain how the SPA gets and uses a token |
| DevOps / CI | [11](11-devops-docker-ci.md) | Describe a pipeline from PR to production |

## Running the apps

Prerequisites: JDK 21+ (the Homebrew `openjdk` works), Docker Desktop, Node 20+.

### App 1: HR & Payroll

```bash
cd app1-payroll
docker compose up -d                      # Postgres, Keycloak, LocalStack only
# run the API from your IDE (PayrollApiApplication) and the web app with: cd frontend && npm run dev
# or run everything in containers:
cd backend && ./mvnw -DskipTests package && cd ..
docker compose --profile full up -d --build
```

### App 2: Proxy Voting Tracker

```bash
cd app2-proxyvote
cd lambda && ./mvnw package && cd ..      # the Lambda jar must exist BEFORE LocalStack starts
docker compose up -d                      # Postgres, Keycloak, LocalStack (+ Lambda, S3 trigger, SQS + DLQ)
cd backend && ./mvnw -DskipTests package && cd ..
docker compose --profile full up -d --build
```

Stopping App 2 takes one extra step. LocalStack starts the Lambda's execution container itself, through the mounted Docker socket, so Compose doesn't know about it and won't remove it. Clean it up too, or the network stays "in use":

```bash
docker compose --profile full down
docker ps -aq --filter network=proxyvote_default | xargs docker rm -f   # leftover Lambda container(s)
docker network rm proxyvote_default 2>/dev/null || true
```

### Ports

| Service | App 1 | App 2 |
|---|---|---|
| Web (nginx container) | http://localhost:8081 | http://localhost:8091 |
| Web (Vite dev server) | http://localhost:5173 | http://localhost:5174 |
| API | http://localhost:8080 | http://localhost:8090 (8080 when run from the IDE) |
| Keycloak | http://localhost:8180 (admin/admin) | http://localhost:8280 (admin/admin) |
| LocalStack | http://localhost:4566 | http://localhost:4567 |
| PostgreSQL | localhost:5432 (payroll/payroll) | localhost:5433 (proxyvote/proxyvote) |

### Demo users (password = username + `123`)

| App 1 | Roles | App 2 | Org | Roles |
|---|---|---|---|---|
| `ana` | EMPLOYEE | `sam` | STEWARD | ANALYST, VOTER, POLICY_ADMIN, OPS |
| `hana` | EMPLOYEE, HR | `vic` | STEWARD | VOTER |
| `paolo` | EMPLOYEE, PAYROLL_ADMIN | `gina` | GROWTH | ANALYST, VOTER, POLICY_ADMIN |

### Tests

```bash
./mvnw test          # in app1-payroll/backend, app2-proxyvote/backend, app2-proxyvote/lambda (Docker must be running for Testcontainers)
npm test             # in each frontend/
cd e2e && npm install && npm test   # browser E2E against the full stack
```

## Network workarounds used on this machine

While building, the network couldn't reach Maven Central, Docker Hub or Quay directly, though Google and GitHub worked. Two workarounds were used. Neither is part of the apps, but they're worth knowing:

- **Maven:** a settings file mirroring `central` to Google's copy, passed with `-s`:
  ```xml
  <settings><mirrors><mirror>
    <id>google-central</id><mirrorOf>central</mirrorOf>
    <url>https://maven-central.storage-download.googleapis.com/maven2/</url>
  </mirror></mirrors></settings>
  ```
  `./mvnw -s mvn-mirror.xml test`
- **Docker images:** pulled from Google's Docker Hub mirror, then re-tagged so the Compose files and Testcontainers can use the normal names:
  `docker pull mirror.gcr.io/library/postgres:17-alpine && docker tag mirror.gcr.io/library/postgres:17-alpine postgres:17-alpine`
  (Testcontainers also needs `testcontainers/ryuk:0.14.0`, pulled the same way.)
- **LocalStack is pinned to 4.9**, because newer images require an auth token. See [09](09-aws-localstack.md) and [12](12-architecture-problems-solutions.md#20-dependency-and-infrastructure-drift).

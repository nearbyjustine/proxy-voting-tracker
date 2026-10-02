# 11 · DevOps: Docker, Compose, CI/CD

How the apps are packaged, run locally as a full stack, and tested on every push, and how that maps to a real AWS pipeline.

---

## 1. What it is

| Piece | Job |
|---|---|
| **Docker image** | A frozen, runnable filesystem plus a start command. "Works on my machine" becomes "works everywhere". |
| **Docker Compose** | Runs several containers together on one private network, using a YAML file. |
| **Compose profiles** | Optional groups of services, so one file can run "infra only" or "everything". |
| **Healthchecks + `depends_on: condition: service_healthy`** | Start a service only after its dependencies are actually *ready*, not just started. |
| **Init hooks** | Scripts that provision things on startup: Keycloak `--import-realm`, LocalStack `ready.d` scripts. |
| **GitHub Actions** | CI: build and test on every push or PR, on GitHub's Ubuntu runners. |

## 2. Where it's used

- **Compose files:** [`app1-payroll/docker-compose.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/docker-compose.yml), [`app2-proxyvote/docker-compose.yml`](../../docker-compose.yml).
  - `docker compose up -d` starts the infra only (Postgres, Keycloak, LocalStack); you run the API and web from your IDE.
  - `docker compose --profile full up -d` also starts the `api` and `web` containers.
  - The `api` service has `depends_on` with `condition: service_healthy` for all three infra services.
- **Healthchecks:**
  - Postgres: `pg_isready`.
  - Keycloak: an HTTP probe of `/health/ready` on management port 9000 using bash's `/dev/tcp`, because the image has no curl.
  - LocalStack (App 1): `awslocal s3 ls s3://payslips`.
  - LocalStack (App 2): checks for a marker file that the init script writes last (`/tmp/proxyvote-ready`).
- **Keycloak realm import:** `start-dev --import-realm` with [`infra/keycloak/realm-payroll.json`](https://github.com/nearbyjustine/payroll-platform/blob/main/infra/keycloak/realm-payroll.json) mounted at `/opt/keycloak/data/import`. Realm, client, roles and users all come from a file in git.
- **LocalStack init hooks:**
  - [`app1-payroll/infra/localstack/init-s3.sh`](https://github.com/nearbyjustine/payroll-platform/blob/main/infra/localstack/init-s3.sh) creates the bucket.
  - [`app2-proxyvote/infra/localstack/init.sh`](../../infra/localstack/init.sh) creates the bucket with CORS, a queue with a DLQ redrive policy, and the Lambda (from the mounted jar), and wires the S3 → Lambda notification. That's what Terraform/CDK would create in real AWS.
- **Backend Dockerfile (runtime-only):** [`app1-payroll/backend/Dockerfile`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/Dockerfile). `eclipse-temurin:21-jre-alpine`, non-root user, `COPY target/*.jar`, `-XX:MaxRAMPercentage=75`.
- **Frontend Dockerfile (multi-stage):** [`app1-payroll/frontend/Dockerfile`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/Dockerfile). A `node:22-alpine` build stage, then `nginx:1.29-alpine` serving `dist/` with [`nginx.conf`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/nginx.conf).
- **CI:** `.github/workflows/` at the repo root, one workflow per app. The jobs build and test the backend with Maven (Testcontainers runs fine on `ubuntu-latest` because Docker is available there), and build and test the frontend with npm.

## 3. Why it's built this way

- **Two profiles:** day to day you want the API running in your IDE (debugger, hot reload) against real infra. Before a demo you want the whole thing containerised. One file serves both.
- **Healthchecks, not just `depends_on`:** a started Postgres container can't accept connections for a few seconds. Without `service_healthy`, the API boots, fails Flyway, and exits.
- **Everything provisioned from files in git (realm JSON, init scripts):** a new developer gets an identical environment with one command. It's infrastructure-as-code in miniature.
- **Runtime-only backend image:** on this machine the Docker VM could not reach Maven Central, so `mvn package` inside a Dockerfile would fail. Building the jar on the host (or in the CI job that already compiled and tested it) and copying it in is faster and reuses exactly the artifact that passed the tests.
- **Non-root user:** if the app is compromised, the attacker isn't root inside the container.
- **`-XX:MaxRAMPercentage=75`:** the JVM sizes its heap from the *container's* memory limit, leaving room for metaspace, threads and native memory. Without a cap, a JVM in a small container can be killed by the OOM killer.
- **Multi-stage frontend image:** the final image contains only nginx and static files. No Node and no `node_modules` at runtime, so it's small with a minimal attack surface.

## 4. Alternatives

| Choice here | Alternative | When better |
|---|---|---|
| Runtime-only Dockerfile (jar from host/CI) | **Multi-stage** (`FROM maven ... RUN mvn package` then JRE stage) | When the build must be fully self-contained (`docker build` anywhere, no JDK on the host) and the Docker build can reach Maven. |
| Plain Dockerfile | **Spring Boot buildpacks** (`./mvnw spring-boot:build-image`) or **Jib** | No Dockerfile to maintain; layered images (dependencies cached separately from your code); Jib doesn't even need a Docker daemon. |
| Fat jar | **Layered jar** (`java -Djarmode=tools extract`) | Faster rebuilds and pushes: dependency layers rarely change. |
| Docker Compose | **Kubernetes** (kind/minikube locally) or **Testcontainers dev mode** (`TestPayrollApiApplication`) | k8s when production is k8s and you need to test manifests. Testcontainers dev mode to start the app plus containers from the IDE with no compose file. |
| GitHub Actions | GitLab CI, Jenkins (used at RSB), AWS CodePipeline | Jenkins for self-hosted or legacy setups; CodePipeline when everything is AWS-native. |
| Init scripts in LocalStack | **Terraform / CDK** applied to LocalStack (`tflocal`, `cdklocal`) | When you want the *same* IaC to create local and real AWS resources. |

## 5. Problems & pitfalls (including ones we hit)

1. **Docker Hub / Quay / Maven Central unreachable (real).** Pulls timed out with "context deadline exceeded" or "no such host", while Google and GitHub worked. **Workarounds:**
   - Maven through Google's mirror, using a settings file passed with `-s`.
   - Images from `mirror.gcr.io/<image>`, re-tagged to the normal names so the Compose files stay standard.
   - Testcontainers also needed `testcontainers/ryuk:0.14.0` pre-pulled the same way. Its first run failed after 6 minutes trying to pull Ryuk.
2. **LocalStack now requires an auth token (real).** `localstack/localstack:latest` exited with "License activation failed… set LOCALSTACK_AUTH_TOKEN". **Fix:** pin `localstack/localstack:4.9`, the last tokenless community build. **Lesson:** never use `:latest` for anything your build depends on.
3. **Orphaned Lambda container after `docker compose down` (real).**
   - **What happened:** after stopping App 2, LocalStack's Lambda execution container (image `public.ecr.aws/lambda/java:21`, named like `proxyvote-localstack-1-lambda-meeting-ingest-…`) was still running.
   - **Why:** LocalStack starts it as a *sibling* container through the mounted `/var/run/docker.sock`, outside Compose's control. It kept the `proxyvote_default` network "in use", so the network couldn't be removed.
   - **Fix:** `docker ps -aq --filter network=proxyvote_default | xargs docker rm -f`, then `docker network rm proxyvote_default`.
   - **Lesson:** anything started through the Docker socket (LocalStack Lambdas, Testcontainers) is outside Compose's lifecycle and needs its own cleanup. That's exactly what Testcontainers' **Ryuk** sidecar does for tests: it removes the containers when the test JVM exits.
4. **The Lambda jar must exist before LocalStack starts.** It's bind-mounted, so build `lambda/` first. Otherwise Docker creates an empty *directory* at that path and `create-function` fails.
5. **Lambda containers need the Compose network.** `LAMBDA_DOCKER_NETWORK=proxyvote_default`; otherwise the function can't reach LocalStack to read S3 or send to SQS.
6. **`localhost` means different things in different places.** Inside a container, `localhost` is that container. The API reaches `keycloak:8080` and `localstack:4566`, but the *browser* uses `localhost:8180` and `localhost:4566`. This is why there are separate `OIDC_ISSUER` vs `OIDC_JWKS` and `S3_ENDPOINT` vs `S3_PUBLIC_ENDPOINT` settings (see [12 §19](12-architecture-problems-solutions.md#19-hostname-mismatches-across-docker-networks)).
7. **Docker Desktop not running.** Testcontainers and Compose fail immediately. Start it first (`open -a Docker`).
8. **Volumes keep old data.** `pgdata` survives `down`; use `docker compose down -v` to reset the database (Flyway seeds again on the next start).
9. **Memory.** Postgres + Keycloak + LocalStack (+ Lambda) + API is several GB. Run one app's stack at a time on an 8 GB Docker VM.

## 6. How this maps to a real AWS pipeline

```
PR opened ──► CI: compile, unit + slice + Testcontainers tests, frontend tests, build both images
merge to main ──► push images to ECR (tagged with the git SHA)
              ──► deploy to STAGING (ECS Fargate service or Elastic Beanstalk env)
                     api: ALB health check on /actuator/health/readiness
                     db : RDS PostgreSQL (private subnet), Flyway migrates on startup
                     secrets: Secrets Manager / SSM → env vars; IAM task role for S3/SQS (no keys)
              ──► smoke tests (the e2e/ scripts, or a few curl checks) against staging
              ──► manual approval ──► PRODUCTION (rolling or blue/green)
                     auto-rollback if health checks fail; CloudWatch alarms on 5xx + DLQ depth
web ──► `vite build` per environment ──► S3 + CloudFront (invalidate index.html only)
infra ──► Terraform/CDK defines buckets, queues, DLQ, Lambda, IAM, the same resources init.sh creates locally
```

## 7. Interview Q&A

- **Walk me through your CI/CD.**
  On a PR: build, run unit, web-slice and Testcontainers integration tests, and build the images. On merge: push to ECR, deploy to staging, smoke test, approve, then roll out to production with health-check-based rollback. Migrations run via Flyway on startup and are backward-compatible, so a rollback is safe.
- **Why multi-stage builds?**
  The build tools (JDK/Maven, Node) stay in the build stage. The runtime image has only the JRE or nginx, so it's smaller, faster to pull, and has less attack surface.
- **What's the difference between `depends_on` and a healthcheck?**
  `depends_on` alone only orders container *start*. With `condition: service_healthy`, Compose waits until the dependency's healthcheck passes.
- **How do you size the JVM in a container?**
  Set the container memory limit and use `-XX:MaxRAMPercentage` (e.g. 75) so the heap follows the limit. Leave headroom for non-heap memory.
- **How do you handle secrets?**
  Never in git or images. Inject them as environment variables from Secrets Manager or SSM at deploy time. AWS access uses IAM roles, not keys.
- **Something you learned the hard way?**
  Containers started through the Docker socket outlive `docker compose down`. LocalStack's Lambda container blocked network removal; Testcontainers solves the same problem with Ryuk.

## 8. Exercise

1. Convert App 1's backend Dockerfile to a **multi-stage** build, and compare build time and image size (`docker images`).
2. Try `./mvnw spring-boot:build-image` and compare the result with your Dockerfile.
3. Add a `smoke` job to the CI workflow that starts `docker compose --profile full` and curls `/actuator/health`.

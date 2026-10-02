# 09 · AWS services and LocalStack

> **One-line summary:** we use **S3** (files), **SQS** (queue + dead-letter queue) and **Lambda** (a Java function triggered by S3 uploads), running locally in **LocalStack**, a Docker container that pretends to be AWS. The code is the *real* AWS SDK; only the endpoint URL and credentials change between laptop and cloud.

---

## 1. What it is

| AWS service | Plain meaning | Laravel / everyday equivalent |
|---|---|---|
| **S3** | Object storage: files ("objects") in "buckets", addressed by key (`payslips/2026-09/E-0001.pdf`) | `Storage::disk('s3')` |
| **Pre-signed URL** | A URL signed with your credentials that grants **one operation on one object for N minutes**, with no AWS credentials in the browser | `Storage::temporaryUrl()` |
| **SQS** | A managed message queue. Standard queues are at-least-once, roughly ordered, and nearly unlimited throughput | Laravel's SQS queue driver |
| **DLQ / redrive policy** | After `maxReceiveCount` failed receives, SQS moves the message to another queue | `failed_jobs` |
| **Lambda** | Run a function on an event with no server to manage; pay per invocation; max 15 min | A queued job, but AWS runs the worker |
| **S3 event notification** | "When an object is created in this bucket (suffix `.csv`), invoke this Lambda" | A filesystem watcher that dispatches a job |
| **IAM** | Who may do what. **Roles** give temporary credentials to compute (EC2/ECS/Lambda); users/access keys are long-lived (avoid them for apps) | DB grants + `.env` keys, but central |
| **LocalStack** | Emulates AWS APIs on `localhost:4566` in Docker | MinIO/Mailhog-style local fakes, but for many AWS services at once |

### The flows we built

```
App 1 (payroll)
  PayrollProcessor ──putObject──► S3 "payslips"  (private)
  Browser ──GET /api/payslips/{id}/download-url──► API ──presign GET (5 min)──► browser downloads directly from S3

App 2 (proxy voting)
  Browser ──POST /api/ingest/upload-url──► API ──presign PUT (10 min)──► browser PUTs CSV directly to S3 "proxyvote-ingest"
  S3 ObjectCreated(*.csv) ──► Lambda "meeting-ingest" (Java 21) ──SendMessageBatch──► SQS "meeting-events"
                                                                        │ after 3 failed receives
                                                                        └──► SQS "meeting-events-dlq"
  API SqsMeetingConsumer ──long poll──► SQS ──► import into Postgres ──► delete message
```

---

## 2. Where we used it

### Provisioning (what Terraform/CDK would do in the cloud)
- App 1 [init-s3.sh](https://github.com/nearbyjustine/payroll-platform/blob/main/infra/localstack/init-s3.sh): creates bucket `payslips`. Mounted into LocalStack's `/etc/localstack/init/ready.d/`, so it runs once LocalStack is ready.
- App 2 [init.sh](../../infra/localstack/init.sh): bucket + **CORS** (browser PUT), queue + DLQ with **redrive policy** (`maxReceiveCount: 3`, visibility timeout 30 s), `lambda create-function --runtime java21 --zip-file fileb:///opt/lambda/meeting-ingest.jar`, `lambda wait function-active-v2`, and the **S3 → Lambda notification** filtered on `.csv`.
- Compose: [app1](https://github.com/nearbyjustine/payroll-platform/blob/main/docker-compose.yml) (`SERVICES: s3`), [app2](../../docker-compose.yml) (`SERVICES: s3,sqs,lambda`, mounts `/var/run/docker.sock` and the Lambda jar, `LAMBDA_DOCKER_NETWORK: proxyvote_default`).

### SDK clients
- App 1 [AwsConfig](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AwsConfig.java): `S3Client` + `S3Presigner`, `endpointOverride` when `app.storage.endpoint` is set, **path-style** addressing, static `test/test` credentials **only** when pointing at LocalStack, otherwise `DefaultCredentialsProvider` (env vars, `~/.aws`, or the IAM role).
- App 2 [AppConfig](../../backend/src/main/java/dev/justine/proxyvote/config/AppConfig.java): `SqsClient` + `S3Presigner` with the same rules.

### S3
- [S3PayslipStorage](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/storage/S3PayslipStorage.java): `putObject` with a **deterministic key** (retries overwrite instead of duplicating), and `presignGetObject` with `response-content-disposition: attachment; filename=...` so the browser downloads with a nice name. It sits behind the `PayslipStorage` **port** interface.
- [PayslipService.downloadUrl](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayslipService.java): checks ownership **before** signing.
- App 2 [IngestController](../../backend/src/main/java/dev/justine/proxyvote/ingest/IngestController.java): `presignPutObject` with a random key prefix (`uploads/<uuid>-<name>.csv`), file-name validation, and `contentType text/csv`.

### Lambda
- [MeetingIngestHandler](../../lambda/src/main/java/dev/justine/ingest/MeetingIngestHandler.java): `RequestHandler<S3Event, String>`. **Static `S3Client`/`SqsClient`** are created once per container and reused across warm invocations. It **URL-decodes the object key**, batches `SendMessageBatch` in groups of 10, and fails loudly if SQS rejects entries.
- [lambda pom.xml](../../lambda/pom.xml): no Spring (faster cold start), only the SDK modules needed, `url-connection-client` instead of Apache/Netty (smaller jar), **maven-shade** fat jar.

### SQS
- [SqsMeetingConsumer](../../backend/src/main/java/dev/justine/proxyvote/ingest/SqsMeetingConsumer.java): long poll, delete-after-success (details in chapter 06).

### Tests against emulated AWS
- App 1 [PayrollFlowIntegrationTest](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayrollFlowIntegrationTest.java) asserts 60 PDFs in the bucket. App 2 [IngestIntegrationTest](../../backend/src/test/java/dev/justine/proxyvote/ingest/IngestIntegrationTest.java) proves the DLQ redrive.

---

## 3. Why we chose this approach

1. **Real SDK code, fake cloud.** The same `S3Client` calls run against LocalStack and AWS. That's far more convincing than mocks, and costs $0 with no AWS account.
2. **Pre-signed URLs keep big files off our servers** and credentials out of the browser. The API only *authorizes* and *signs*; S3 serves the bytes.
3. **Event-driven ingestion decouples producers from the API.** Uploads keep working when the API is redeployed, and messages wait in SQS. Bad rows are reported and bad messages are dead-lettered instead of blocking everything.
4. **Lambda for spiky, short work** (parse a CSV when one arrives); the long-running API stays on containers.
5. **The job description lists S3, EC2, Lambda, RDS and IAM.** This build covers S3, Lambda and IAM concepts hands-on, and RDS/EC2 map directly (see section 4b).

---

## 4. Alternatives

### 4a. Local emulation alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| **Real AWS (free tier)** | Testing IAM policies, real networking, latency, quotas | Needs an account, a card, and internet; easy to leave things running. Use for a final deploy, with a **budget alarm** |
| **MinIO** (S3-compatible) | Only need S3; very fast and light | We also needed SQS + Lambda + S3 notifications in one place |
| **ElasticMQ** (SQS-compatible) | Only need SQS | Same reason |
| **Moto** (Python mock server) | Python test suites; lightweight | Lambda execution support is weaker; LocalStack runs the real Java runtime image |
| **LocalStack Pro / latest** | Cognito, RDS, ECS emulation, persistence | Requires an auth token (see problems). The community 4.9 image covers S3/SQS/Lambda |

### 4b. How each local piece maps to a real AWS deployment

| Local | Real AWS |
|---|---|
| `localstack` container | AWS account + region (e.g. `ap-southeast-1`) |
| `init.sh` / `init-s3.sh` | **Terraform** or **AWS CDK** (infrastructure as code, reviewed in PRs) |
| `postgres` container | **RDS for PostgreSQL** (Multi-AZ, automated backups) in private subnets |
| `api` container | **ECS Fargate** service behind an **ALB**, or **Elastic Beanstalk**, health check `/actuator/health` |
| `web` container (nginx) | **S3 + CloudFront** static hosting (no server at all) |
| `keycloak` container | **Cognito** user pool, or a managed IdP (Okta / Entra ID) |
| `test/test` static credentials | **IAM task role** for ECS, **execution role** for Lambda, with least-privilege policies (`s3:PutObject` on `arn:…:payslips/*` only) |
| `DB_PASSWORD` env var | **Secrets Manager** / SSM Parameter Store, injected as env at task start |
| `docker logs` | **CloudWatch Logs** (+ metrics/alarms on DLQ depth and 5xx rate) |
| Lambda jar mounted from `target/` | CI uploads the jar to S3 and updates the function (or a SAM/CDK deploy) |

---

## 5. Problems & pitfalls

### Real problems we hit

**P1. The latest LocalStack refused to start**
- *Symptom:* container exits: *"License activation failed! … set the LOCALSTACK_AUTH_TOKEN"*.
- *Cause:* recent LocalStack images require an auth token even for basic use.
- *Fix:* pinned **`localstack/localstack:4.9`**, the last tokenless community build. Verified S3, SQS, Lambda and presigning work. (Alternative: sign up for a free token and set `LOCALSTACK_AUTH_TOKEN`.)
- *Lesson:* **pin image versions.** `:latest` changes under you; reproducible builds need fixed tags.

**P2. Docker Hub and Quay were unreachable from this network**
- *Symptom:* `docker pull` timed out (`context deadline exceeded`, `lookup quay.io: no such host`), while Google/GitHub/npm worked.
- *Fix:* pulled through **Google's Docker Hub mirror** and retagged so Compose files stay standard: `docker pull mirror.gcr.io/localstack/localstack:4.9 && docker tag … localstack/localstack:4.9` (same for Postgres, Keycloak, Temurin, Node, nginx, Testcontainers' ryuk). Maven used `maven-central.storage-download.googleapis.com` via a `-s settings.xml` mirror.
- *Lesson:* know your registry mirrors. Companies run their own (ECR pull-through cache, Artifactory, Nexus) for exactly this reason.

**P3. Pre-signed URLs pointed at a host the browser can't reach**
- *Symptom (by design, avoided):* inside Docker the API talks to `http://localstack:4566`. A URL signed for that host is useless to the browser, and you can't just rewrite the host afterwards, because **the host is part of the signature**.
- *Fix:* a **separate `S3Presigner` configured with the public endpoint** (`S3_PUBLIC_ENDPOINT=http://localhost:4566` in App 1, `AWS_PUBLIC_ENDPOINT=http://localhost:4567` in App 2), while the `S3Client`/`SqsClient` use the internal one.
- *Lesson:* this is the same "internal vs public hostname" problem as the Keycloak issuer (chapter 05). In real AWS both are the same regional endpoint, so it disappears.

**P4. Lambda inside LocalStack must reach LocalStack**
- LocalStack runs each Lambda as a **sibling Docker container** (hence the `docker.sock` mount). It must join the Compose network (`LAMBDA_DOCKER_NETWORK=proxyvote_default`). LocalStack injects **`AWS_ENDPOINT_URL`**, which the handler uses as `endpointOverride` (with `forcePathStyle`); in real AWS the variable is absent and the SDK uses the regional endpoint. The runtime image `public.ecr.aws/lambda/java:21` was pulled up-front.

**P5. S3 event keys arrive URL-encoded**
- `my meetings.csv` arrives as `my+meetings.csv`; `getObject` with the raw key returns 404. The handler does `URLDecoder.decode(key, UTF_8)`. Our upload endpoint also replaces spaces in file names.

### Classic pitfalls

| Pitfall | Consequence | Mitigation |
|---|---|---|
| **Public buckets** | Data leak headlines | Buckets private (default); share via short-lived pre-signed URLs only, after an authorization check |
| **Long pre-signed URL expiry** | A forwarded link works for days | 5 min (payslips), 10 min (uploads) |
| **Access keys in code or `.env` in git** | Credential theft | IAM roles for compute; LocalStack-only dummy keys guarded by "endpoint override is set" |
| **Over-broad IAM** (`s3:*` on `*`) | Blast radius | Least privilege per role and resource ARN |
| **Lambda cold starts** (Java + Spring ≈ seconds) | Latency spikes | No Spring in the Lambda, small jar, static clients; SnapStart or provisioned concurrency in production |
| **Creating SDK clients per invocation** | Slow, connection churn | Static fields, created once per container |
| **Lambda timeout < work** | Partial processing, retries | 60 s timeout; split work into messages; stay idempotent |
| **S3 event fired for objects you write yourself** | Infinite trigger loop | Separate input/output prefixes or buckets; suffix filter `.csv` |
| **No DLQ** | Poison messages retry forever and cost money | Redrive policy + a CloudWatch alarm on DLQ depth |
| **Virtual-hosted vs path-style URLs** | `bucket.localhost` DNS fails locally | Path-style when using an endpoint override |

---

## 6. Interview questions

**Q1 (basic). What are S3, SQS and Lambda, and where did you use each?**
S3 stores the payslip PDFs (App 1) and uploaded CSVs (App 2). SQS carries one message per imported meeting between the Lambda and the API, with a DLQ for failures. Lambda parses each uploaded CSV, triggered by an S3 event.

**Q2. How do users download a private file securely?**
The API checks they own the payslip, then returns a pre-signed GET URL valid for 5 minutes for that single object. The browser downloads directly from S3. No AWS credentials reach the browser, and our servers don't stream the bytes.

**Q3. How does the browser upload a large file without going through your API?**
It asks the API for a pre-signed PUT URL (role-checked, random key, content type fixed), then PUTs the file straight to S3. The bucket needs a CORS rule allowing PUT from our origin.

**Q4. IAM user access keys vs IAM roles?**
Keys are long-lived secrets that leak. Roles give compute (ECS tasks, Lambda, EC2) temporary credentials automatically. Our code uses the SDK's default credential chain, so in AWS it picks up the role with no code change. Only LocalStack gets dummy static keys.

**Q5. What is a cold start and how did you reduce it?**
The first invocation on a new container must start the JVM and load classes. I kept the Lambda free of Spring, trimmed SDK dependencies, used the lightweight URL-connection HTTP client, and created clients once in static fields. In production: SnapStart or provisioned concurrency.

**Q6. How do you run all this locally without AWS?**
LocalStack in Docker emulates S3, SQS and Lambda. An init script provisions the buckets, queues, function and trigger, like Terraform would. The app points its SDK clients at `localhost:4566` through config; in AWS that override is empty.

**Q7 (advanced). Your API in Docker generates pre-signed URLs that don't work in the browser. Why?**
The signature covers the host. The API signs for `localstack:4566`, which only exists inside the Docker network. Use a separate presigner configured with the browser-facing endpoint; rewriting the host afterwards would break the signature.

**Q8 (advanced). How would you deploy App 2 to real AWS?**
Terraform/CDK provisions: RDS Postgres in private subnets, ECS Fargate for the API behind an ALB, S3 + CloudFront for the Vue build, Cognito (or the corporate IdP) for OIDC, an S3 bucket + SQS queue + DLQ + Lambda with an S3 notification, Secrets Manager for DB credentials and the Anthropic key, least-privilege IAM roles, and CloudWatch alarms on DLQ depth, 5xx and latency. CI builds and tests, pushes images to ECR, and rolls the services.

---

## 7. Exercise: rebuild it yourself

Add **payslip notifications through SQS + Lambda to App 1**:
1. In App 1's LocalStack init, create queue `payslip-ready` (+ DLQ, `maxReceiveCount: 3`) and enable `sqs,lambda` in `SERVICES`.
2. After each chunk commits in `PayrollProcessor`, send `{employeeNo, period, pdfKey}` to the queue.
3. Write a small Java 21 Lambda (no Spring, static `SqsClient`/`S3Client`, shaded jar) that consumes from the queue via an **SQS event source mapping** and logs "would email {employeeNo}" (in real AWS: SES).
4. Prove with an integration test that a message with a missing `pdfKey` ends up in the DLQ.
5. Write down the IAM policy the Lambda's execution role would need in real AWS (only `sqs:ReceiveMessage/DeleteMessage` on that queue and `s3:GetObject` on `payslips/*`).

# 03 · Data: JPA, Hibernate, Spring Data, transactions, Flyway

## 1. What it is

- **JPA** is the *specification* for mapping Java objects to tables (`@Entity`, `@ManyToOne`…).
- **Hibernate** is the *implementation* (the ORM that generates SQL).
- **Spring Data JPA** is the layer on top: you write a repository **interface**, and Spring generates the implementation, including queries derived from method names.
- **Flyway** runs versioned SQL migrations at startup.

| Laravel / Eloquent | Spring / JPA |
|---|---|
| Active Record: `$employee->save()` | **Data Mapper**: `employeeRepository.save(employee)`; entities are plain objects |
| `belongsTo` / `hasMany` | `@ManyToOne` / `@OneToMany(mappedBy = ...)` |
| `->with('department')` | `@EntityGraph(attributePaths = "department")` or `JOIN FETCH` |
| `DB::transaction(fn)` | `@Transactional` or `TransactionTemplate` |
| Migrations (`php artisan migrate`) | Flyway `V1__init.sql`, `V2__...` (plain SQL) |
| Model scopes / query builder | Derived queries, `@Query` (JPQL), `Specification` |
| `$model->isDirty()` + save | **Dirty checking**: changes to a managed entity are flushed at commit, no `save()` needed |

## 2. Where we used it

### Schema owned by Flyway, validated by Hibernate
- Migrations: [`V1__init.sql`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/db/migration/V1__init.sql), [`V2__seed_demo_data.sql`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/db/migration/V2__seed_demo_data.sql); App 2 [`V1`](../../backend/src/main/resources/db/migration/V1__init.sql), [`V2`](../../backend/src/main/resources/db/migration/V2__seed_demo_data.sql), [`V3__company_country_varchar.sql`](../../backend/src/main/resources/db/migration/V3__company_country_varchar.sql).
- [`application.yml`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/application.yml): `ddl-auto: validate`, so Hibernate only *checks* that entities match the schema, and `open-in-view: false`.

### Entities and relationships
- [`Employee`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/Employee.java): `@ManyToOne(fetch = LAZY)` to `Department`, `@Version long version`, `BigDecimal baseSalary` with precision/scale, `@Enumerated(STRING)`.
- [`Payslip`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/Payslip.java): an **aggregate** that owns its lines, via `@OneToMany(mappedBy = "payslip", cascade = ALL, orphanRemoval = true)`.
- [`Meeting`](../../backend/src/main/java/dev/justine/proxyvote/meeting/Meeting.java) owns `Proposal`s, and `upsertProposal(seq)` makes re-imports idempotent.
- Entities have a `protected` no-arg constructor (required by JPA) and **behaviour methods** (`Employee.update`, `PayrollRun.start/complete/fail`, `Vote.cast`) instead of public setters.

### Repositories and queries
- Derived queries: `existsByEmailIgnoreCase`, `findByUsername`, `findByActiveTrueOrderByIdAsc`, `findByEmployeeUsernameOrderByRunPeriodDesc` in [`EmployeeRepository`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeRepository.java) and [`PayslipRepository`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayslipRepository.java).
- `Slice` instead of `Page` for the payroll processor: no `COUNT(*)` query, just "is there a next chunk?".
- JPQL: `select distinct m from Meeting m where m.voteDeadline >= :since order by m.voteDeadline` in [`MeetingRepository`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingRepository.java).

### N+1 avoided
`@EntityGraph(attributePaths = "department")` on `EmployeeRepository.findAll(spec, pageable)` loads the department in the same SQL statement as the employees. Without it, `EmployeeResponse.from(e)` calls `e.getDepartment().getName()` and triggers one extra query per row. In App 2, `MeetingService.list()` loads meetings with company and proposals in one query, then votes for all proposals in one more `IN (...)` query: **2 queries total, no matter how many meetings**.

### Dynamic search with Specifications
[`EmployeeSpecifications`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeSpecifications.java) builds `name/number contains` and `in department` predicates and combines **only the ones supplied**, using `cb.conjunction()` as the neutral start.

### Transactions
- `@Transactional(readOnly = true)` at class level, overridden by a plain `@Transactional` on write methods: [`EmployeeService`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeService.java).
- **Per-chunk transactions** with `TransactionTemplate` in [`PayrollProcessor`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollProcessor.java). Claim the run (one transaction), then 50 employees per transaction, then complete or fail (a separate transaction).
- **`Propagation.MANDATORY`** in [`AuditService`](../../backend/src/main/java/dev/justine/proxyvote/audit/AuditService.java) and [`RecommendationService`](../../backend/src/main/java/dev/justine/proxyvote/voting/RecommendationService.java). They refuse to run without a caller's transaction, so an audit row is always committed or rolled back *together* with the change it describes.
- `saveAndFlush` in `PayrollRunService.request()` makes the unique constraint fire inside the method, where we can translate it into a 409.

### Optimistic locking
`@Version` on `Employee`, `PayrollRun`, `VotingPolicy` and `Vote`. The client sends the version it edited. If it differs, we throw `ObjectOptimisticLockingFailureException`, and `ApiExceptionHandler` maps it to **409** ("changed by someone else").

### Auditing, JSONB, immutable entities
- [`Auditable`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/Auditable.java) + `@EnableJpaAuditing` in [`JpaConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/JpaConfig.java). `@CreatedBy`/`@LastModifiedBy` come from an `AuditorAware` that reads the JWT username, or `"system"` for background jobs.
- [`AuditEvent`](../../backend/src/main/java/dev/justine/proxyvote/audit/AuditEvent.java): `@Immutable` (Hibernate ignores updates), `Map<String,Object> details` stored as **`jsonb`** via `@JdbcTypeCode(SqlTypes.JSON)`.

### Idempotency: unique constraints are the real guarantee
- `payroll_run.period UNIQUE`, `payslip (payroll_run_id, employee_id) UNIQUE`, `recommendation (organization_id, proposal_id) UNIQUE`, `vote (organization_id, proposal_id) UNIQUE`, `processed_message.message_id PRIMARY KEY`.
- Services check first (friendly error); the database decides under concurrency.

## 3. Why we chose this approach

- **Flyway + `validate`**: schema changes are reviewed SQL in git, applied in order, identical in every environment. Hibernate never silently alters production tables.
- **LAZY by default + explicit fetch per use case**: predictable SQL, no surprise N+1 queries.
- **Open-in-view off**: the web layer can't trigger lazy loading. Every query happens inside a service transaction, where we can see it.
- **Chunked transactions for batch work**: one 10,000-row transaction holds locks and memory and loses everything on one failure. Chunks show progress and can be retried.
- **MANDATORY propagation for audit**: there's no state where a vote exists without its audit row.

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| `ddl-auto=update` | Throwaway prototypes | Unreviewed schema changes in production, can't drop or rename safely. |
| Liquibase | XML/YAML changelogs, rollbacks, many DB vendors | Flyway's plain SQL is simpler for a single Postgres. |
| jOOQ / Spring JDBC `JdbcClient` | Reporting, complex SQL, bulk operations | Most of our work is aggregate CRUD where JPA shines; we'd add jOOQ for reports. |
| MyBatis | Hand-written SQL shops | Same as above. |
| Pessimistic locking (`SELECT … FOR UPDATE`) | Hot rows with frequent conflicts (balances) | Our edits rarely conflict; optimistic locking scales better. |
| One big transaction for payroll | Small, fast, all-or-nothing jobs | Long locks, no progress, total loss on failure. |
| `REQUIRES_NEW` for audit | Audit must survive even when the business change fails | We want audit to reflect *committed* changes only. |
| Spring Data JDBC | Simpler aggregates, no lazy loading | Fine choice; JPA is what most enterprise Java codebases (and interviews) use. |

## 5. Problems & pitfalls

### CHAR vs VARCHAR failed schema validation (real, App 2)
- **Symptom:** startup failed with `Schema validation: wrong column type encountered in column [country] in table [company]; found [bpchar], but expecting [varchar(2)]`.
- **Cause:** V1 declared `country CHAR(2)`, but a Java `String` maps to `VARCHAR`. (In App 1 we caught the same thing on `period` *before* the first run.)
- **Fix:** V1 had already been applied, so we added **[`V3__company_country_varchar.sql`](../../backend/src/main/resources/db/migration/V3__company_country_varchar.sql)** with `ALTER COLUMN ... TYPE VARCHAR(2)`.
- **Lesson:** **never edit an applied migration**. Flyway stores a checksum and refuses to start if it changes. Always fix forward. `ddl-auto=validate` turns a silent mismatch into a loud startup failure.

### `null` Specifications in Spring Data JPA 4 (real)
- **Symptom:** risk of errors combining optional filters with `Specification.allOf(...)` when a filter is absent.
- **Cause:** newer Spring Data is stricter about `null` specs.
- **Fix:** start from `(root, q, cb) -> cb.conjunction()` and `and()` only non-null specs.
- **Lesson:** build dynamic queries from a neutral element; don't rely on null-tolerance.

### N+1 queries
- **Symptom:** listing 20 employees runs 21 SQL statements (visible with SQL logging).
- **Cause:** lazy `department` accessed per row while mapping to DTOs.
- **Fix:** `@EntityGraph` / `JOIN FETCH` per query, or a DTO projection.
- **Lesson:** decide what each use case needs and fetch it in one go.

### `LazyInitializationException`
- **Symptom:** `could not initialize proxy - no Session` when touching a relation.
- **Cause:** accessing a LAZY association after the transaction closed (open-in-view is off).
- **Fix:** fetch it in the query, or map to a DTO inside the `@Transactional` service method.
- **Lesson:** entities never leave the service layer; DTOs do.

### Dirty checking surprises
- **Symptom (positive):** `EmployeeService.update` never calls `save()`, yet the change is stored.
- **Cause:** the entity is *managed* inside the transaction; Hibernate diffs it at flush/commit.
- **Pitfall:** mutating a managed entity "just for display" also gets saved. Map to DTOs instead.

### Optimistic lock conflicts
- **Symptom:** two HR users edit the same employee; one silently overwrites the other.
- **Fix:** `@Version` + the client sends `version`. Stale edits return 409 (`error.conflict.version`), and the UI reloads.

### Unique constraint races
- **Symptom:** two admins click "Run payroll" at the same moment; both pass `existsByPeriod`.
- **Fix:** the DB unique constraint fails the second insert. `saveAndFlush` + catching `DataIntegrityViolationException` → 409.
- **Lesson:** service checks are UX; constraints are correctness.

### Classic `@Transactional` traps
- Checked exceptions **don't** roll back by default (use `rollbackFor`); we only throw unchecked.
- Self-invocation skips the proxy, which is why `PayrollProcessor` uses `TransactionTemplate` explicitly.
- Long external calls (S3 uploads, AI calls) inside a transaction hold a DB connection. We accept it per 50-row chunk; the AI summary call happens inside `SummaryController`'s transaction only because it's a single row (see ch. 12 for the trade-off).

### `equals/hashCode` on entities
Don't use Lombok `@Data` or "all fields" equality. The ID is null before persist, and lazy proxies break field comparisons. Our entities rely on identity (default `Object` equality), which is safe within one persistence context.

## 6. Interview questions

1. **JPA vs Hibernate vs Spring Data JPA?** Spec, implementation, and repository abstraction respectively. We write interfaces like `EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee>`.

2. **What is the N+1 problem and how did you avoid it?** One query for the list, plus one per row for a lazy relation. We use `@EntityGraph` on list queries and batch-load votes and recommendations with `IN (...)` queries in `MeetingService`.

3. **How does `@Transactional` work and when doesn't it roll back?** It's a proxy that begins and commits or rolls back. It rolls back on unchecked exceptions and Errors, not checked ones. It doesn't apply on self-invocation or non-public methods.

4. **Explain propagation `MANDATORY` vs `REQUIRES_NEW`.** MANDATORY joins the caller's transaction and fails if there isn't one. Our `AuditService` uses it so audit rows commit together with the change. REQUIRES_NEW suspends the outer transaction and starts its own; use it for logs that must persist even if the main work fails.

5. **Optimistic vs pessimistic locking?** Optimistic: a `@Version` column checked at update; on conflict, return 409 and let the user retry. Best when conflicts are rare (our edits and votes). Pessimistic: `SELECT … FOR UPDATE`; best for hot rows, at the cost of throughput.

6. **How do you manage schema changes safely?** Flyway versioned SQL, `ddl-auto=validate`, never edit applied migrations (fix forward like our V3), and expand/contract for zero-downtime deploys.

7. **How did you make the payroll run safe to retry?** Unique (run, employee) on payslips, a per-employee existence check, PDF keys that are deterministic per employee and period (overwrite, not duplicate), and a `PENDING → PROCESSING` claim so a run is only picked up once.

8. **Why `Slice` instead of `Page` in the processor?** `Page` issues a `COUNT(*)` each time; the processor only needs `hasNext()`.

## 7. Exercise: rebuild it yourself

In App 1, add a `leave_request` table via a **new** Flyway migration (V3), an entity with `@ManyToOne(fetch = LAZY) Employee` and `@Version`, and a repository method that lists pending requests *with* the employee name in one query. Turn on `spring.jpa.show-sql=true`, call it, and prove there's exactly one SELECT. Then deliberately declare a column `CHAR(10)` mapped to a `String` and watch `ddl-auto=validate` stop the app. Fix it with V4, not by editing V3.

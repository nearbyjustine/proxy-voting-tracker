# 01 · Java essentials used in these apps

> Paths below are relative to this file. `P1` = `../../app1-payroll/backend/src/main/java/dev/justine/payroll`, `P2` = `../../app2-proxyvote/backend/src/main/java/dev/justine/proxyvote`.

## 1. What it is

Java is a **statically typed, compiled** language that runs on the JVM. Compared with what you know:

| You know | Java equivalent | Key difference |
|---|---|---|
| TypeScript `interface` | `interface` | Must be implemented explicitly (`implements X`), not by shape. |
| TS/PHP readonly DTO | `record` | Immutable; constructor, getters, `equals`/`hashCode`/`toString` generated. |
| PHP 8.1 `enum` | `enum` | Enums can have fields, methods, and even per-constant behaviour. |
| `array_map` / `.map().filter()` | Streams | Lazy, nothing runs until a terminal op (`toList()`, `reduce`). |
| `null` / `?->` | `Optional<T>` | Used as a *return type* that says "maybe absent". |
| PHP `float` for money (bad) | `BigDecimal` | Exact decimal arithmetic with explicit rounding. |
| Carbon / JS `Date` | `java.time` | Separate types for "an instant" vs "a calendar date" vs "a zone". |

The compiler checks types before anything runs. In PHP a typo in a method name fails at runtime. In Java it fails the build. That's why the codebases here can be refactored with confidence.

## 2. Where we used it

### Records (immutable data)
- DTOs: [`EmployeeDtos`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeDtos.java), [`PayrollDtos`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollDtos.java), [`MeetingDtos`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingDtos.java)
- Config: [`AppProperties`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/AppProperties.java) is a record with nested records.
- Engine results: `DeductionEngine.Line` and `Calculation` in [`DeductionEngine`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/DeductionEngine.java).
- Message contract: [`MeetingMessage`](../../lambda/src/main/java/dev/justine/ingest/MeetingMessage.java).

```java
public record Line(String code, String label, BigDecimal amount) {}
public record Calculation(BigDecimal gross, List<Line> lines, BigDecimal totalDeductions, BigDecimal net) {}
```

Records can have static factory methods and validation. `EmployeeResponse.from(Employee e)` is the mapping function from entity to DTO.

### Enums with behaviour
- Simple: `EmploymentType`, `RunStatus`, `VoteDecision`, `ProposalCategory`.
- With logic: [`DeadlineStatus.of(deadline, now, window)`](../../backend/src/main/java/dev/justine/proxyvote/meeting/DeadlineStatus.java) is a pure function on the enum.
- **Per-constant behaviour**: [`ConditionType`](../../backend/src/main/java/dev/justine/proxyvote/policy/ConditionType.java). Each constant overrides abstract methods `with(threshold)` and `describe(...)`, so adding a condition is adding a constant, not editing a `switch`.

### Interfaces
- [`DeductionRule`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/DeductionRule.java): Strategy contract.
- [`ProposalCondition`](../../backend/src/main/java/dev/justine/proxyvote/policy/ProposalCondition.java): a `@FunctionalInterface` with **default methods** `and/or/not`, so a lambda can be a condition *and* compose.
- [`PayslipStorage`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/storage/PayslipStorage.java), [`ProposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ProposalSummarizer.java): ports to the outside world.

### Collections & streams
- `DeductionEngine.calculate` uses a plain `for` loop because it carries running state (`contributions`). Use loops when state flows between steps.
- `MeetingService.list` uses streams + `Collectors.toMap(..., Function.identity())` to index votes by proposal ID, avoiding a query per row.
- `List.copyOf(rules)` in `DeductionEngine` gives an unmodifiable defensive copy.

### Optional
- Repository lookups: `employees.findById(id).orElseThrow(() -> new NotFoundException(...))` in [`EmployeeService`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeService.java).
- [`CurrentUser.username()`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/CurrentUser.java) returns `Optional<String>`. Callers decide: `.orElse("system")` for auditing, `.orElseThrow(ForbiddenException::new)` for self-service.

### BigDecimal and rounding
- One rounding rule for all money: [`Money.round`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/Money.java) → `setScale(2, RoundingMode.HALF_UP)`.
- Comparisons use `compareTo`, never `equals`: `taxable.compareTo(b.over()) > 0` in [`WithholdingTax`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/rules/WithholdingTax.java). Tests use AssertJ's `isEqualByComparingTo("1030.05")`.
- DB columns are `NUMERIC(12,2)`, entities use `precision = 12, scale = 2`.

### Exceptions
- All business errors extend `RuntimeException` via [`ApiException`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/ApiException.java) (unchecked). It carries an HTTP status and a message key.
- `PayrollRun.requireStatus` throws `IllegalStateException` on an illegal transition.

### java.time
- `Instant`: exact moments (`requestedAt`, `voteDeadline`). Stored as `TIMESTAMPTZ`.
- `LocalDate`: calendar day in the market (`meetingDate`).
- `YearMonth`: a payroll period, parsed from `"2026-09"` in [`PayrollController`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollController.java).
- `ZoneId`: only for display/validation (`ZoneId.of(msg.marketTimeZone())` in `MeetingImportService`).
- **`Clock` is injected** ([`TimeConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/TimeConfig.java)). Tests pass `Clock.fixed(...)` in [`PayrollRunServiceTest`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/PayrollRunServiceTest.java) and [`VoteServiceTest`](../../backend/src/test/java/dev/justine/proxyvote/voting/VoteServiceTest.java).

### Text blocks, generics, pattern matching
- Text blocks (`"""`): the Claude system prompt in [`ClaudeProposalSummarizer`](../../backend/src/main/java/dev/justine/proxyvote/ai/ClaudeProposalSummarizer.java), and JSON bodies in controller tests.
- Generics: `Converter<Jwt, Collection<GrantedAuthority>>` in `KeycloakRoleConverter`, `JpaRepository<Employee, Long>`, `Specification<Employee>`.
- Pattern matching for `instanceof`: `if (auth.getPrincipal() instanceof Jwt jwt)` in `CurrentUser`, and `a instanceof MessageArg m ? ... : a` in `ApiExceptionHandler`.

## 3. Why we chose this approach

- **Records for anything that is "just data"**: less code, immutable (thread-safe), and correct `equals/hashCode` for free.
- **Unchecked exceptions**: Spring's `@Transactional` rolls back on them by default. Checked exceptions would also force every caller to declare them.
- **BigDecimal**: payroll must be exact. `0.1 + 0.2` as `double` is `0.30000000000000004`.
- **Injected Clock**: the deadline logic ("is voting closed?") is tested at the exact boundary, deterministically.
- **Enums with behaviour** instead of string constants: the compiler catches typos, and `switch`/maps over enums are exhaustive.

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| Lombok `@Data`/`@Value` | Pre-Java-16 codebases, huge POJOs | Records cover DTOs; Lombok `@Data` on JPA entities generates a dangerous `equals/hashCode`. |
| `double` for money | Scientific values, approximations | Rounding errors on currency; auditors would reject it. |
| `long` cents for money | Very high-volume systems, simple currencies | Fine too, but percentages and rounding get awkward; BigDecimal is the common choice in enterprise Java. |
| Checked exceptions | Recoverable I/O errors the caller *must* handle | Pollutes signatures; doesn't roll back `@Transactional` by default. |
| `Date`/`Calendar` | Never in new code | Mutable and confusing; `java.time` replaced them in Java 8. |
| `Instant.now()` directly | Throwaway scripts | Not testable; tests would depend on the real date. |
| Returning `null` | Performance-critical internals | `Optional` makes "maybe absent" explicit in the API. |

## 5. Problems & pitfalls

### BigDecimal `equals` vs `compareTo`
- **Symptom:** `new BigDecimal("1350").equals(new BigDecimal("1350.00"))` is `false`.
- **Cause:** `equals` compares value *and* scale.
- **Fix:** use `compareTo(...) == 0` (or AssertJ `isEqualByComparingTo`).
- **Lesson:** decide one scale (`Money.round`) and compare numerically.

### Wrong expected values caught by tests (real, this session)
- **Symptom:** `DeductionRulesTest` failed for taxable 100,000 and 700,000.
- **Cause:** the *test* had arithmetic mistakes (16874.30 instead of 8,541.80 + 33,333 × 25% = **16,875.05**; 195,208.25 instead of **195,208.35**). The code was right.
- **Fix:** recompute by hand and correct the expectations, with the formula written as a comment next to each case.
- **Lesson:** parameterized tests at bracket boundaries check your *understanding of the spec* as well as the code. When a test fails, check the expectation too.

### Mutable state shared between threads
- **Symptom:** occasional wrong totals under load.
- **Cause:** a singleton service storing per-request data in a field.
- **Fix:** keep services stateless. Our services only hold `final` dependencies.
- **Lesson:** immutability (records, `final`, `List.copyOf`) removes whole classes of concurrency bugs.

### Instant vs LocalDateTime
- **Symptom:** a deadline is off by hours depending on the server's time zone.
- **Cause:** storing a `LocalDateTime` (no zone) for a global deadline.
- **Fix:** store `Instant` in a `TIMESTAMPTZ`, and convert to `ZoneId` only for display (App 2 shows "your time" and "market time").
- **Lesson:** instants for "when exactly", local dates for "which calendar day", zones for presentation.

### Records are shallowly immutable
- A record holding a `List` can still have its list mutated by someone else. Our `DeductionEngine` returns `List.copyOf(lines)` so the calculation can't be altered after the fact.

## 6. Interview questions

1. **What's a record, and when would you not use one?**
   An immutable data carrier with generated constructor, accessors, `equals/hashCode/toString`. Don't use it for JPA entities: Hibernate needs a no-arg constructor, mutable fields and proxies. Our entities are classes; our DTOs are records.

2. **Why `BigDecimal` for money? How do you round?**
   `double` is binary floating point and can't represent 0.1 exactly. We use `BigDecimal` with `setScale(2, RoundingMode.HALF_UP)` in one helper (`Money.round`), so every rule rounds the same way.

3. **Checked vs unchecked exceptions?**
   Checked must be declared or caught (compiler-enforced). Unchecked (`RuntimeException`) need not be. Our `ApiException` is unchecked, so it rolls back transactions and flows to `@RestControllerAdvice` without polluting signatures.

4. **Why override `equals` and `hashCode` together?**
   Hash-based collections look up by `hashCode` first, then `equals`. If equal objects have different hashes, `HashMap`/`HashSet` lookups fail. Records do this correctly. For entities, base equality on the ID or a business key, never on all fields.

5. **`Instant` vs `LocalDate` vs `ZonedDateTime`?**
   `Instant` is a point on the global timeline (vote deadline). `LocalDate` is a calendar date with no zone (meeting day in its market). `ZonedDateTime` combines them for display. We store instants and inject a `Clock` for testability.

6. **What does `Optional` solve? Where shouldn't you use it?**
   It makes "may be absent" explicit in return types (`findById`). Don't use it for fields, parameters or collections (return an empty list instead).

7. **Explain the enum in `ConditionType`.**
   Each constant implements abstract methods differently: constant-specific behaviour. It's a compact Strategy. Adding a condition type is adding one constant, with the compiler forcing you to implement every method.

8. **Streams vs loops?**
   Streams for transformations without shared state (filter/map/collect). Loops when each step depends on the previous one. `DeductionEngine` needs running contributions for the tax, so it uses a loop.

## 7. Exercise: rebuild it yourself

Without looking at the code, write `Money`, `DeductionRule` and three rules (SSS capped at 1,350, PhilHealth 2.5% with floor 10,000 and ceiling 100,000, and a bracket-table withholding tax). Then write a `@ParameterizedTest` with `@CsvSource` covering every bracket boundary. Compare with [`DeductionRulesTest`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/test/java/dev/justine/payroll/payroll/DeductionRulesTest.java). Did you compute 16,875.05 correctly on the first try?

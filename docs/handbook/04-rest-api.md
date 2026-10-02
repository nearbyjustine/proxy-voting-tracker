# 04 · REST APIs: controllers, DTOs, validation, errors, status codes

## 1. What it is

A REST controller maps HTTP requests to Java methods and returns JSON. In Spring MVC:

| Laravel | Spring MVC |
|---|---|
| `Route::get('/employees/{id}', ...)` + controller | `@RestController` + `@GetMapping("/employees/{id}")` |
| `FormRequest` rules | Bean Validation annotations on a DTO + `@Valid` |
| API Resource (`toArray`) | DTO **record** with a `from(entity)` factory |
| `App\Exceptions\Handler` | `@RestControllerAdvice` with `@ExceptionHandler` methods |
| `abort(404)` | throw a typed exception; the advice maps it to a status |
| `paginate()` JSON | `Pageable` parameter + `PagedModel` response |
| `trans('errors.x')` | `MessageSource` + `messages_xx.properties`, chosen by `Accept-Language` |

## 2. Where we used it

### Thin controllers
- [`EmployeeController`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeController.java), [`PayrollController`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayrollController.java), [`MeController`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/me/MeController.java)
- [`MeetingController`](../../backend/src/main/java/dev/justine/proxyvote/meeting/MeetingController.java), [`VoteController`](../../backend/src/main/java/dev/justine/proxyvote/voting/VoteController.java), [`PolicyController`](../../backend/src/main/java/dev/justine/proxyvote/policy/PolicyController.java), [`IngestController`](../../backend/src/main/java/dev/justine/proxyvote/ingest/IngestController.java), [`SummaryController`](../../backend/src/main/java/dev/justine/proxyvote/ai/SummaryController.java)

Controllers do only HTTP: parse, validate (`@Valid`), call one service method, choose the status. Business rules live in services.

### DTO records, never entities
- Request records carry validation: `CreateEmployeeRequest(@NotBlank @Pattern(regexp = "E-\\d{4}") String employeeNo, @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal baseSalary, ...)` in [`EmployeeDtos`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/employee/EmployeeDtos.java).
- Responses are built from entities inside the transaction: `EmployeeResponse.from(e)`.
- **Cross-field validation** with `@AssertTrue`: in [`PolicyDtos.RuleDto`](../../backend/src/main/java/dev/justine/proxyvote/policy/PolicyDtos.java), `isThresholdPresentWhenNeeded()` rejects "pay score below ___" without a number. The error appears as `rules[0].thresholdPresentWhenNeeded`.
- Validation messages can be message keys: `@Pattern(..., message = "{payroll.period.format}")` resolves from [`messages.properties`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/resources/i18n/messages.properties).

### One error format: ProblemDetail + i18n + correlation ID
[`ApiExceptionHandler`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/ApiExceptionHandler.java) returns RFC 9457 `ProblemDetail` for everything:

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Payroll for 2026-09 has already been requested.",
 "instance":"/api/payroll-runs","code":"error.conflict.payrollExists","correlationId":"783f7603-..."}
```

- Services throw typed exceptions carrying a **message key**, not text: `new ConflictException("error.conflict.payrollExists", period)`.
- The handler resolves the key for the request's `Accept-Language` (`fil` in App 1, `fr` in App 2). For example, App 2 returns *"Le vote a été clôturé le …"*.
- [`CorrelationIdFilter`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/CorrelationIdFilter.java) gives each request an ID (or reuses `X-Correlation-Id`), puts it in the logging MDC, and returns it in the header and the error body. A user's screenshot then maps directly to the log lines.
- Validation errors include a field map (`"errors": {"email": "must be a well-formed email address"}`), which the Vue forms show next to each field.

### Status codes, deliberately chosen

| Status | Where | Meaning |
|---|---|---|
| **201 Created + Location** | `POST /api/employees` | New resource; `Location: /api/employees/61` |
| **202 Accepted + Location** | `POST /api/payroll-runs` | Work queued; the client polls `/api/payroll-runs/{id}` (the UI polls every 1.5 s while a run is active) |
| 200 | reads, `PUT /api/proposals/{id}/vote` | Success |
| 400 | invalid DTO, bad period format, malformed JSON | Client error with field details |
| 401 | missing or invalid token | Not authenticated |
| 403 | wrong role, someone else's payslip, no tenant | Authenticated but not allowed |
| 404 | unknown ID | Not found (localised entity name) |
| 409 | duplicate email or period, stale `version`, vote after deadline | Conflicts with current state |

### Idempotent PUT for votes
`PUT /api/proposals/{id}/vote {"decision":"AGAINST","version":0}` sets the organisation's vote to a value. Repeating it yields the same state, so a retry after a timeout is safe. See [`VoteService.cast`](../../backend/src/main/java/dev/justine/proxyvote/voting/VoteService.java).

### Pagination
`@PageableDefault(size = 20, sort = "fullName")` on the employee list, returned as `new PagedModel<>(page)`:
```json
{"content":[...],"page":{"size":15,"number":0,"totalElements":61,"totalPages":5}}
```
`PagedModel` gives a stable JSON shape (serialising Spring's `PageImpl` directly is discouraged because its structure isn't a contract).

### CORS
The SPA runs on another origin (`localhost:5173`/`8081`), so [`SecurityConfig`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/SecurityConfig.java) registers allowed origins from config and **exposes** `Location` and `X-Correlation-Id` so JavaScript can read them.

## 3. Why we chose this approach

- **DTOs**: a stable contract that doesn't change when the table changes. No accidental leakage (salary, internal flags), no lazy-loading or JSON recursion problems.
- **Message keys + ProblemDetail**: a single machine-readable format (`code`) for the UI, human text in the user's language, a correlation ID for support.
- **202 for payroll**: a 61-employee run takes ~0.6 s here, but 10,000 would exceed HTTP timeouts. Async plus polling is the honest contract.
- **409 for conflicts**: the client knows to reload, not to fix its input.

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| Return entities directly | Internal admin tools, quick demos | Leaks fields, lazy-loading exceptions, couples API to schema. |
| MapStruct for mapping | Many large DTOs | Our `from()` factories are short and explicit. |
| Custom error JSON | Legacy contracts | ProblemDetail is a standard and built into Spring 6+. |
| Return 200 + `{"success":false}` | Never | Breaks HTTP semantics, caching and client libraries. |
| WebSocket/SSE for payroll progress | Real-time dashboards | Polling every 1.5 s is simpler and good enough here. |
| GraphQL | Many client-specific shapes | Two SPAs with known screens; REST is simpler. |
| springdoc OpenAPI | Public or partner APIs | Worth adding next; types are hand-mirrored in `frontend/src/api/types.ts` today. |
| POST for votes | Non-idempotent "create" | A vote is "set my decision", so PUT is the idempotent fit. |

## 5. Problems & pitfalls

### Malformed JSON returned 500 (real, this session)
- **Symptom:** `PUT /vote {"decision":"MAYBE"}` returned **500 Internal Server Error**. Found by `VoteApiSecurityTest.unknownDecisionIsRejected`.
- **Cause:** Jackson couldn't map `MAYBE` to the enum and threw `HttpMessageNotReadableException`; our catch-all `@ExceptionHandler(Exception.class)` turned it into a 500.
- **Fix:** a dedicated handler mapping `HttpMessageNotReadableException` → **400** `error.badRequest`, added to both apps.
- **Lesson:** a catch-all handler is necessary, but every *client* error type needs an explicit mapping first. Write a test for malformed input.

### Validation messages stay in English
- **Symptom:** with `Accept-Language: fil`, the top-level `detail` is Filipino but field messages ("must be greater than 0") are English.
- **Cause:** Hibernate Validator ships default messages for some languages only; we didn't override each constraint for Filipino.
- **Fix (if needed):** custom `message = "{key}"` on each constraint, as we did for the period format.
- **Lesson:** i18n is per message; verify both levels.

### Leaking existence through 403 vs 404
`GET /api/payslips/{id}/download-url` returns 403 for someone else's payslip, which reveals that the ID exists. That's acceptable for internal IDs, but for sensitive resources many APIs return 404 instead. Make it a conscious choice.

### Forgetting to expose headers to CORS
Without `addExposedHeader("Location")`, browser code can't read the Location of a 201/202, even though curl can.

### Treating UI checks as security
The Vue router hides `/payroll` from HR users, but the API **independently** returns 403. UI guards are UX; the server enforces.

## 6. Interview questions

1. **Why DTOs instead of returning entities?** A stable contract, no sensitive-field leakage (salaries), no lazy-loading or recursion during serialisation, and freedom to change the schema. Our `EmployeeResponse.from(e)` runs inside the service transaction.

2. **How do you handle errors consistently?** One `@RestControllerAdvice` producing `ProblemDetail` with a stable `code`, a localised `detail`, field `errors` for validation, and a `correlationId` matching the logs.

3. **Which status code for: duplicate email? stale edit? payroll started? not your payslip?** 409, 409, 202 + Location, 403.

4. **What's the difference between 401 and 403?** 401: we don't know who you are (no or invalid token). 403: we know, and you're not allowed.

5. **PUT vs POST vs PATCH; which is idempotent?** GET, PUT and DELETE are idempotent; POST isn't. Our vote is a PUT because repeating it leaves the same state.

6. **How do you validate a rule that spans two fields?** `@AssertTrue` on a boolean method in the record (threshold required for some condition types), or a custom class-level constraint.

7. **How would you design an endpoint for a long job?** Return 202 with a Location for a status resource, make the job idempotent, and let the client poll (or push via SSE/WebSocket). That's our payroll run.

8. **How does `Accept-Language` reach your messages?** Spring's `LocaleResolver` reads the header into `LocaleContextHolder`; our handler calls `messageSource.getMessage(key, args, locale)`.

## 7. Exercise: rebuild it yourself

Add `DELETE /api/employees/{id}` to App 1 that **deactivates** the employee (soft delete), restricted to HR. It should return 204, 404 for an unknown ID, and 409 if the employee has a payslip in a PROCESSING run. Write a `@WebMvcTest` covering 204, 403 (PAYROLL_ADMIN), 404 and 409, and check that every error body contains `code` and `correlationId`.

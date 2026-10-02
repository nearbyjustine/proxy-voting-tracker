# 10 · Frontend: Vue 3, Pinia, OIDC, i18n

Both apps share the same frontend shape: a Vue 3 single-page app (SPA) built with Vite and TypeScript. It logs in through Keycloak, calls the Spring API with a bearer token, and is served by nginx in production.

---

## 1. What it is

| Piece | Job |
|---|---|
| **Vue 3 Composition API** (`<script setup>`, `ref`, `computed`, `watch`) | Components grouped by feature, not by option type; logic is reusable as plain functions. |
| **Pinia** | Shared state. Here: the logged-in user and token (`useAuthStore`). |
| **oidc-client-ts** | Implements OpenID Connect in the browser: Authorization Code + PKCE, token storage, silent renew. |
| **vue-router** | URL → view, plus a global guard that checks login and roles. |
| **vue-i18n** | Translations (EN/FIL in App 1, EN/FR in App 2). |
| **`Intl.*`** | Locale-aware money, dates, time zones and relative time, built into the browser. |
| **Vite** | Dev server with hot reload; production build into hashed static files. |
| **nginx** | Serves the built files in the container, with SPA fallback and caching rules. |

## 2. Where it's used

- **Auth store (OIDC + PKCE):** [`app1-payroll/frontend/src/stores/auth.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/stores/auth.ts). `UserManager` with `response_type: 'code'`, tokens in `sessionStorage`, `automaticSilentRenew`. Roles are read from the access token's `realm_access.roles`.
- **Login callback:** [`src/views/CallbackView.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/CallbackView.vue). Exchanges the `?code=` for tokens, then returns to the page you started on (passed through the OIDC `state`).
- **Typed API client:** [`src/api/client.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/api/client.ts). Adds `Authorization: Bearer`, adds `Accept-Language` from the current locale, re-logs-in on 401, and turns any non-2xx response into an `ApiError` with `fieldErrors` and `correlationId` parsed from the backend's ProblemDetail.
- **Endpoints + types:** [`src/api/endpoints.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/api/endpoints.ts), [`src/api/types.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/api/types.ts). These mirror the Java DTO records.
- **Router guard:** [`src/router/index.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/router/index.ts). `canAccess(meta, authenticated, roles)` is a pure function (unit-tested in [`src/__tests__/guard.test.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/__tests__/guard.test.ts)); `beforeEach` either redirects to Keycloak or sends you home.
- **Server field errors in a form:** [`src/views/EmployeeFormView.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/EmployeeFormView.vue). Shows `fieldErrors.email` etc. under each input.
- **Debounced search:** [`src/views/EmployeesView.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/EmployeesView.vue). Waits 300 ms after typing stops, then resets to page 0 and loads.
- **Polling a 202 job:** [`src/views/PayrollView.vue`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/views/PayrollView.vue). After `POST /payroll-runs` returns 202, it polls every 1.5 s **only while** a run is PENDING or PROCESSING, and clears the interval on unmount.
- **Money / period formatting:** [`src/utils/format.ts`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/utils/format.ts). `currentPeriod()` uses the **Manila** month, not the browser's (test: 30 Sep 17:00 UTC is already October in Manila).
- **Time zones (App 2):** [`app2-proxyvote/frontend/src/utils/time.ts`](../../frontend/src/utils/time.ts). One deadline instant shown in your time **and** in the market's time, plus "in 24 hours".
- **Direct-to-S3 upload (App 2):** [`app2-proxyvote/frontend/src/views/IngestView.vue`](../../frontend/src/views/IngestView.vue). Asks the API for a pre-signed URL, then `fetch(url, { method: 'PUT', body: file })`.
- **Voting UI with optimistic locking (App 2):** [`app2-proxyvote/frontend/src/views/MeetingDetailView.vue`](../../frontend/src/views/MeetingDetailView.vue). Sends the `version` it last saw, so a colleague's concurrent change returns 409.
- **nginx:** [`app1-payroll/frontend/nginx.conf`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/nginx.conf). `try_files $uri $uri/ /index.html` plus cache headers.
- **Build image:** [`app1-payroll/frontend/Dockerfile`](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/Dockerfile). `VITE_*` values are build `ARG`s.

## 3. Why it's built this way

- **Authorization Code + PKCE** is the current standard for SPAs. The SPA never sees a password or a client secret, and PKCE stops a stolen `code` from being redeemed by someone else. The old *implicit flow* put tokens in the URL and is deprecated.
- **Tokens in `sessionStorage`, not `localStorage`:** they disappear when the tab closes, which shrinks the window for theft. Either is readable by injected JavaScript (XSS), so the real defences are short token lifetimes (5 min) and avoiding XSS. The BFF alternative is below.
- **The router guard is UX, not security.** It hides pages you can't use; the **API enforces every rule**. Anyone can edit JavaScript in their own browser, so the guard is never what protects data.
- **The API client parses ProblemDetail in one place,** so every form shows server-side validation the same way, and every error banner can show a correlation ID that support can find in the logs.
- **`Accept-Language` comes from the UI locale,** so backend error messages come back in the same language as the UI (FIL/FR).
- **Polling instead of WebSockets** for payroll progress: one endpoint, no extra infrastructure, and it only runs while a job is active.
- **Pre-signed PUT:** big files go browser → S3 directly and never pass through, or tie up, the API server.

## 4. Alternatives

| Choice here | Alternative | When the alternative is better |
|---|---|---|
| oidc-client-ts in the SPA (public client) | **BFF (Backend-for-Frontend):** the server does the OIDC dance and keeps tokens; the browser only has an HttpOnly session cookie | Higher-security apps (banking, HR in production): no tokens in JavaScript at all. Costs a server-side session and CSRF protection. |
| Pinia | Plain `reactive()` module, or **TanStack Query** (Vue Query) for server state | Vue Query when much of the state is cached API data (it handles refetch, staleness and dedup for you). |
| Hand-written fetch client | **axios** with interceptors; **OpenAPI-generated client** (openapi-typescript) | Generated clients once the API is large: the types can't drift from the Java DTOs. |
| Polling a 202 job | **Server-Sent Events** or WebSocket (STOMP) | Many concurrent watchers, or near-instant updates needed. |
| vue-i18n with TS message files | JSON files loaded lazily per locale; a translation platform (Crowdin, Lokalise) | Many languages or non-developer translators. |
| Vite build + nginx | Static hosting (S3 + CloudFront); SSR (Nuxt) | CloudFront for production static hosting; Nuxt when SEO/first paint matters (it doesn't for an internal tool). |
| Hand-made CSS | PrimeVue / Vuetify / Tailwind | A design system when there are many screens or many developers. |

## 5. Problems & pitfalls (including the ones we actually hit)

1. **`Intl.DateTimeFormat` TypeError (real bug).** `{ dateStyle: 'medium', timeStyle: 'short', timeZoneName: 'short' }` throws `TypeError: Invalid option`. `dateStyle`/`timeStyle` can't be combined with individual fields like `timeZoneName`. It would have crashed App 2's meetings page; a unit test caught it. **Fix:** list the fields explicitly: `{ year, month, day, hour, minute, timeZoneName }` ([`time.ts`](../../frontend/src/utils/time.ts)).
2. **TypeScript 7 vs `vue-tsc` (real).** `npm install` picked TypeScript 7 (the new native compiler), and `vue-tsc` crashed (`ERR_PACKAGE_PATH_NOT_EXPORTED ./lib/tsc`). **Fix:** pin `typescript@~5.9` until the Vue tooling supports 7. Lesson: a major version can break your toolchain even when your own code is fine.
3. **Arrow function returning an assignment (real, caught by the type-checker).** `addUserLoaded((u) => (this.user = u))` returns `User`, but the callback type expects `void`. **Fix:** `(u) => { this.user = u }`.
4. **`VITE_*` values are baked in at build time.** They're replaced inside the JS bundle during `vite build`, so you can't change them with runtime environment variables. You need one build per environment, or a runtime `config.json` that the app fetches at startup.
5. **Deep links 404 without SPA fallback.** `/employees/5` isn't a file on disk. nginx must fall back to `index.html` and let vue-router handle it.
6. **Stale bundles after a deploy.** If `index.html` is cached, users keep loading old JS. **Rule:** hashed `/assets/*` → cache for a year (`immutable`); `index.html` → `no-cache`.
7. **The browser's time zone ≠ the business's time zone.** "Current payroll period" must use Asia/Manila; a browser set to UTC would be a month off on the last evening of the month.
8. **Polling leaks.** Always `clearInterval` in `onUnmounted`, and stop polling when nothing is in progress.
9. **Debounce or you DDoS yourself.** One request per keystroke is wasteful and causes out-of-order responses.
10. **CORS for direct S3 uploads.** The bucket needs a CORS rule allowing `PUT` from the web origin (App 2's [`infra/localstack/init.sh`](../../infra/localstack/init.sh)).
11. **Login state leaking between users in tests (real).** The browser E2E test first "logged out" by clearing storage and cookies, and the next login hung. **Fix:** a fresh browser context (like a new incognito window) per user ([`e2e/login-flows.mjs`](https://github.com/nearbyjustine/payroll-platform/blob/main/e2e/login-flows.mjs)).

## 6. Interview Q&A

- **How does your Vue app authenticate against a Spring Boot API?**
  The SPA uses Authorization Code + PKCE with Keycloak via oidc-client-ts. It gets a short-lived JWT access token and sends it as a bearer token. Spring is an OAuth2 resource server that validates the signature against Keycloak's JWKS, plus issuer and expiry, and maps `realm_access.roles` to `ROLE_*` authorities.
- **If the router guard blocks a page, is the data protected?**
  No. Guards are UX only. The API's `SecurityFilterChain` and service-level ownership checks are the real protection.
- **ref vs reactive? computed vs watch?**
  `ref` holds any value and you access it via `.value`; `reactive` is for objects and loses reactivity if you destructure it. `computed` is a cached derived value; `watch` is for side effects, like the debounced search reload.
- **How do you show server validation errors in a form?**
  The backend returns ProblemDetail with an `errors` map; the client parses it into `ApiError.fieldErrors`, and the form binds `fieldErrors.email` under the email input.
- **How do you handle a long-running backend job in the UI?**
  The API returns 202 with a Location header. The UI polls the status endpoint while the job is active and stops when it's done (or uses SSE).
- **Why not keep tokens in localStorage?**
  They'd survive closing the tab, which makes them easier to steal and keep. sessionStorage plus short lifetimes reduces exposure, and a BFF removes tokens from JavaScript entirely.

## 7. Exercise

Rebuild **App 1's employees screen** from scratch in a new view:
1. A typed `Api.employees({ q, department, page })` call.
2. A debounced search box and a department filter that resets to page 0.
3. Pagination using `page.totalPages`.
4. A create form that shows server field errors from a 400.

Then write a Vitest test for a pure helper you extracted, as was done with `canAccess`.

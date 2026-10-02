# 05 · Security: OAuth2, OpenID Connect, Keycloak, Spring Security

> **One-line summary:** the browser logs in at **Keycloak** (not our app), gets a signed **JWT access token**, and sends it to our **stateless Spring Boot API**. The API checks the signature and reads the roles and tenant out of the token. We never see a password.

---

## 1. What it is

| Term | Plain meaning | Laravel equivalent you know |
|---|---|---|
| **Authentication (authN)** | *Who are you?* | `Auth::attempt()`, the login form |
| **Authorization (authZ)** | *What may you do?* | Gates, Policies, `can()` |
| **OAuth 2.0** | A standard for an app to get an **access token** to call an API on a user's behalf. It's about *authorization*. | Laravel Passport (an OAuth2 server) |
| **OpenID Connect (OIDC)** | A layer on top of OAuth2 that adds **identity**: an **ID token** (who logged in), a `/userinfo` endpoint, and standard claims (`name`, `email`, `preferred_username`). It's about *authentication*. | Socialite "Login with Google" uses OIDC |
| **Identity Provider (IdP)** | The server that shows the login page and issues tokens. We use **Keycloak**. In real companies: Okta, Entra ID (Azure AD), Auth0, AWS Cognito. | Passport/Sanctum, but as a separate service |
| **JWT** | A token in 3 base64 parts, `header.payload.signature`. Anyone can *read* the payload; only the IdP can *sign* it. | Sanctum tokens are opaque strings stored in DB. A JWT is self-contained, so no DB lookup is needed |
| **Resource server** | An API that accepts access tokens and validates them. **Our Spring Boot apps are resource servers.** | `auth:sanctum` middleware |
| **Client** | The app that obtains tokens. **Our Vue SPAs are public clients** (they can't keep a secret). | your SPA calling Sanctum |

### The four OAuth2 roles in our system

```
Resource owner  = the human (ana, hana, paolo / sam, vic, gina)
Client          = Vue SPA  (payroll-web / proxyvote-web), a "public" client with no secret
Auth server     = Keycloak  (realm "payroll" / "proxyvote")
Resource server = Spring Boot API (payroll-api / proxyvote-api)
```

### Authorization Code flow + PKCE (what actually happens when you click "Sign in")

```
1. SPA creates a random code_verifier, hashes it -> code_challenge (S256)
2. SPA redirects browser to Keycloak /auth?client_id=...&code_challenge=...&redirect_uri=/callback
3. User types password ON KEYCLOAK'S PAGE (our app never sees it)
4. Keycloak redirects back to /callback?code=XYZ
5. SPA POSTs code + code_verifier to Keycloak /token
   -> Keycloak checks hash(code_verifier) == code_challenge  (this is PKCE)
   -> returns access_token (JWT, 5 min), id_token, refresh_token
6. SPA calls API with  Authorization: Bearer <access_token>
7. API validates signature with Keycloak's public keys (JWKS), checks exp + iss, reads roles
```

**Why PKCE:** a public client has no client secret, so an attacker who intercepts the `code` (via a malicious app or browser extension) could swap it for tokens. PKCE ties the code to a secret only the original tab knows. The old **Implicit flow** returned tokens directly in the URL. It's deprecated; never use it.

### What's inside our tokens (decoded during this session)

```json
// App 1
{ "iss": "http://localhost:8180/realms/payroll", "preferred_username": "paolo",
  "realm_access": { "roles": ["PAYROLL_ADMIN", "EMPLOYEE"] }, "email": "paolo@demo.ph", "name": "Paolo Cruz" }
// App 2: note the custom "org" claim (the tenant)
{ "iss": "http://localhost:8280/realms/proxyvote", "preferred_username": "gina", "org": "GROWTH",
  "realm_access": { "roles": ["POLICY_ADMIN", "ANALYST", "VOTER"] } }
```

---

## 2. Where we used it

### Keycloak (the IdP)
- Realm definitions, imported at container start (`start-dev --import-realm`):
  - [app1 realm-payroll.json](https://github.com/nearbyjustine/payroll-platform/blob/main/infra/keycloak/realm-payroll.json): 3 roles, public client `payroll-web` with `pkce.code.challenge.method: S256`, demo users.
  - [app2 realm-proxyvote.json](../../infra/keycloak/realm-proxyvote.json): 4 roles, a **declarative user profile** that declares the `org` attribute, and an `oidc-usermodel-attribute-mapper` that copies it into the token as the `org` claim.
- Keycloak settings in [app1 docker-compose.yml](https://github.com/nearbyjustine/payroll-platform/blob/main/docker-compose.yml) and [app2 docker-compose.yml](../../docker-compose.yml): `KC_HOSTNAME`, `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`.

### The SPA (OIDC client)
- [app1 stores/auth.ts](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/stores/auth.ts): `oidc-client-ts` `UserManager` with `response_type: 'code'` (Auth Code + PKCE is automatic), tokens in **sessionStorage**, `automaticSilentRenew`, roles read from the token for **UI only**. App 2 reuses the same file.
- [app1 router/index.ts](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/router/index.ts): `canAccess()` route guard (login / forbidden / ok). The comment says it: *UI checks are UX, not security*.
- [app1 api/client.ts](https://github.com/nearbyjustine/payroll-platform/blob/main/frontend/src/api/client.ts): adds `Authorization: Bearer`, re-logs in on 401.

### The API (resource server)
- [app1 SecurityConfig.java](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/SecurityConfig.java): `SecurityFilterChain` with `oauth2ResourceServer().jwt()`, `STATELESS`, CSRF off, CORS, URL rules, and **`.anyRequest().denyAll()`** (secure by default).
- [app2 SecurityConfig.java](../../backend/src/main/java/dev/justine/proxyvote/config/SecurityConfig.java): same pattern with method + path rules (`PUT /api/proposals/*/vote` needs `VOTER`).
- [KeycloakRoleConverter.java](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/KeycloakRoleConverter.java): maps `realm_access.roles` → `ROLE_*` authorities.
- `application.yml` → `spring.security.oauth2.resourceserver.jwt.issuer-uri` (public URL) + `jwk-set-uri` (can be internal).
- [CurrentUser.java](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/common/CurrentUser.java): username from `preferred_username`; App 2's version adds `orgCode()` from the `org` claim.

### Object-level authorization and tenancy
- [PayslipService.downloadUrl](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/payroll/PayslipService.java): *only the owner or a PAYROLL_ADMIN* may get a payslip URL. This can't be expressed as a URL rule.
- [Tenant.java](../../backend/src/main/java/dev/justine/proxyvote/config/Tenant.java): resolves the organisation from the JWT `org` claim. Every org-scoped query (votes, recommendations, policy, audit) goes through it.

### Secrets
- [AiConfig.java](../../backend/src/main/java/dev/justine/proxyvote/ai/AiConfig.java): `ANTHROPIC_API_KEY` comes from the environment; with no key, the app falls back to the offline summarizer. Nothing secret is in git.

---

## 3. Why we chose this approach

1. **Stateless JWT validation scales horizontally.** Any API instance can validate any token with only Keycloak's public key, with no shared session store and no DB lookup per request.
2. **Login is not our code.** Password hashing, MFA, brute-force protection, password reset and SSO all live in Keycloak. Writing login yourself is the #1 way to ship a security bug.
3. **Matches the job description.** "Experience with OAuth and OpenID" plus a Vue + Spring stack is exactly *SPA (OIDC client) → Spring resource server*.
4. **Keycloak is free and runs in Docker**, like Cognito/Okta locally. The Spring side is identical for any OIDC provider; only `issuer-uri` changes.
5. **Two layers of authorization on purpose:** coarse URL rules in the filter chain (cheap, central, deny-by-default) **plus** ownership/tenant rules in services, where the data is.

---

## 4. Alternatives

| Option | When it's better | Why we didn't use it here |
|---|---|---|
| **Session cookie + server-side login** (Spring Security form login, Laravel's default) | Server-rendered apps (Thymeleaf), one backend, no SSO needs | We have a separate SPA and want stateless, horizontally scalable APIs and SSO-ready auth |
| **BFF (Backend-for-Frontend)**: a server component does the OIDC flow and gives the browser only an **httpOnly cookie** | Highest-security SPAs (banking). Tokens never touch JavaScript, so XSS can't steal them | More moving parts (a BFF service, CSRF protection back on). Great next step; mention it in interviews |
| **AWS Cognito / Okta / Entra ID** | Production. Managed, compliant, no servers to patch | Costs money and needs internet; Keycloak gives the same OIDC contract locally |
| **Spring Authorization Server** (embed your own IdP in Spring) | You must *be* the IdP and want it in Java | Building an IdP is out of scope; Keycloak is battle-tested |
| **Opaque tokens + introspection** (`/introspect` call per request) | Need instant revocation | Extra network hop per request; short-lived JWTs (5 min) + refresh are good enough here |
| **API keys** | Machine-to-machine, simple integrations | No user identity, no roles per user. For service-to-service OAuth, use the **Client Credentials** flow |
| **`@PreAuthorize` on every method** instead of URL rules | Fine-grained rules that depend on method args | We use both: URL rules for coverage, service code for data-dependent rules. `@EnableMethodSecurity` is on, ready for `@PreAuthorize` |

---

## 5. Problems & pitfalls

### Real problems we hit while building

**P1. Roles were ignored: everyone got 403**
- *Symptom:* a valid token for `paolo` (PAYROLL_ADMIN) still got 403 on `/api/payroll-runs`.
- *Cause:* Spring's default `JwtGrantedAuthoritiesConverter` only reads the `scope`/`scp` claim and makes `SCOPE_*` authorities. Keycloak puts roles in `realm_access.roles`.
- *Fix:* [`KeycloakRoleConverter`](https://github.com/nearbyjustine/payroll-platform/blob/main/backend/src/main/java/dev/justine/payroll/config/KeycloakRoleConverter.java) maps them to `ROLE_PAYROLL_ADMIN`, so `hasRole("PAYROLL_ADMIN")` works. We also set `principalClaimName = preferred_username`, so `auth.getName()` is the username, not a UUID.
- *Lesson:* every IdP puts roles somewhere different (Cognito: `cognito:groups`; Entra: `roles`). Isolate that mapping in one converter.

**P2. Issuer mismatch: "the iss claim is not valid"**
- *Symptom (classic Docker trap):* the browser gets tokens from `http://localhost:8180`, so `iss = http://localhost:8180/realms/payroll`. The API runs *inside* Docker and must call Keycloak at `http://keycloak:8080`. If you set `issuer-uri: http://keycloak:8080/...`, every token is rejected; if you set `localhost:8180`, the container can't reach it.
- *Fix (two parts):*
  1. Keycloak: `KC_HOSTNAME=http://localhost:8180` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`, so tokens always say `iss=localhost:8180` even when requested via the internal host.
  2. API: `issuer-uri` = **public** URL (what's *in* the token), `jwk-set-uri` = **internal** URL (where to *download keys*). With `jwk-set-uri` set, Spring doesn't call the issuer URL at startup; it only checks the `iss` string.
- *Lesson:* separate "identity of the issuer" from "network address to reach it". In AWS you'd use one public HTTPS hostname for both.

**P3. The custom `org` attribute silently disappeared (Keycloak 26)**
- *Symptom:* users imported with `attributes: { org: [...] }` had no `org` claim.
- *Cause:* since Keycloak 24, the **declarative user profile** is always on and *unmanaged* attributes are dropped by default.
- *Fix:* declare `org` in the realm's `UserProfileProvider` component (`kc.user.profile.config`), and add an `oidc-usermodel-attribute-mapper` on the client to emit it as a claim. We verified by decoding the token: `'org': 'GROWTH'`.
- *Lesson:* treat the IdP config as code (realm JSON in git) and **test the token you actually get**, not the config you think you wrote.

**P4. Two ways to break tenant isolation (designed out)**
- Taking `orgId` from the request body or a header would let any user act for another org. [`Tenant.current()`](../../backend/src/main/java/dev/justine/proxyvote/config/Tenant.java) reads it **only from the signed token**. The smoke test proved it: Gina (GROWTH) sees `vote: None` for a proposal Vic (STEWARD) had voted on.

### Classic pitfalls

| Pitfall | Why it hurts | What we do |
|---|---|---|
| **IDOR / Broken Object Level Authorization** (#1 in OWASP API Top 10): `GET /payslips/7` checks "logged in" but not "is it yours" | Ana downloads Hana's payslip by changing the ID | Ownership check in `PayslipService`; returns **403**. Smoke test: Ana → Hana's payslip = 403 |
| **Trusting the UI** | Hiding a button doesn't stop `curl` | Route guards are UX only; the API enforces everything. Ana typing `/payroll` gets redirected, *and* the API would 403 her anyway |
| **401 vs 403 confusion** | Wrong client behaviour | **401** = no/invalid token, so re-login (our client calls `auth.login()`). **403** = valid token, not allowed, so show an error |
| **CSRF on a bearer API** | Unnecessary friction or a false sense of security | CSRF abuses cookies the browser sends *automatically*. Bearer tokens are added by JS, never automatically, so CSRF is disabled. **If you move to cookies (BFF), turn CSRF back on** |
| **CORS misunderstood** | People `allow *` to "fix" errors | CORS is a *browser* rule that protects users, not an API firewall. We allow only the two SPA origins and expose `Location` + `X-Correlation-Id` headers |
| **Token storage** | XSS can read `localStorage` | We use `sessionStorage` (cleared on tab close) and short-lived 5-min access tokens with refresh. Best: BFF + httpOnly cookie |
| **Long-lived access tokens** | A stolen token works for days | `accessTokenLifespan: 300` in the realm; silent renew via refresh token |
| **Secrets in git** | Leaked keys get abused within minutes | `ANTHROPIC_API_KEY` from env; DB passwords via env in Compose. In AWS: Secrets Manager / SSM, injected as env vars, with IAM roles instead of access keys |
| **Not validating `aud`** | A token issued for another app is accepted | Fine for a demo with one client per realm. In production, add an audience validator |

---

## 6. Interview questions

**Q1 (basic). OAuth2 vs OpenID Connect?**
OAuth2 is *authorization*: it gets an access token to call an API. OIDC adds *authentication* on top: an ID token with who the user is, standard claims, and `/userinfo`. Our SPA uses OIDC to log in; our API only cares about the OAuth2 access token.

**Q2. Walk me through what happens when a user logs in to your app.**
Authorization Code + PKCE: the SPA redirects to Keycloak with a code challenge, the user authenticates on Keycloak's page, Keycloak returns a code, and the SPA exchanges code + verifier for tokens. Then every API call sends `Authorization: Bearer <jwt>`. Spring's `BearerTokenAuthenticationFilter` validates the signature against Keycloak's JWKS, checks `exp` and `iss`, converts `realm_access.roles` to authorities, and stores an `Authentication` in the `SecurityContextHolder`.

**Q3. How does the API validate a JWT without calling Keycloak every time?**
It downloads Keycloak's public keys (JWKS) once and caches them. Validation is local: verify the RSA signature, `exp`, and `iss`. That's why it's stateless and scales. The trade-off is revocation: a token stays valid until it expires, so we keep access tokens short (5 min).

**Q4. 401 or 403? Give examples from your app.**
No token → 401. Hana (HR) calling `POST /api/payroll-runs` → 403 (valid token, wrong role). Ana requesting Hana's payslip → 403 (object-level rule).

**Q5. URL rules aren't enough. Why?**
`/api/payslips/{id}` is allowed for any authenticated user, but which payslip you may see depends on data. That's object-level authorization, done in `PayslipService`. Missing it is IDOR, the most common API vulnerability.

**Q6. How do you do multi-tenancy securely?**
The tenant comes from the signed token (`org` claim), never from client input. One component (`Tenant`) resolves it, and every org-scoped query filters on it. At larger scale, Postgres Row-Level Security adds a DB-level guarantee.

**Q7. Why is CSRF disabled? Isn't that insecure?**
CSRF works because browsers send cookies automatically on cross-site requests. Our API uses bearer tokens attached by JavaScript, which a malicious site can't do on our behalf. With cookie sessions (or a BFF), CSRF protection must be on.

**Q8 (advanced). Your API runs in Docker and token validation fails with an issuer error. Why?**
The `iss` in the token is the browser-facing URL, while the container reaches Keycloak by its service name. Configure the IdP's hostname so `iss` is stable, set `issuer-uri` to the public value, and point `jwk-set-uri` at the internal address.

---

## 7. Exercise: rebuild it yourself

On a branch of App 1, add a new role **AUDITOR** that can *read* payroll runs but not start them:
1. Add the role to `realm-payroll.json` and give it to a new user `aldo`; recreate the Keycloak container.
2. Decode `aldo`'s token (`curl .../token`, then base64-decode the middle part) and confirm the role is there.
3. Change `SecurityConfig` so `GET /api/payroll-runs/**` allows `PAYROLL_ADMIN` or `AUDITOR`, but `POST` stays admin-only.
4. Add two `@WebMvcTest` cases using `jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))`: GET → 200, POST → 403.
5. Bonus: make an auditor able to download *any* payslip, by updating the ownership check in `PayslipService` and its unit test.

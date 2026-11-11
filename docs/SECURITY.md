# Security

TrafonDart is a small private app, but it stores passwords and is reachable from the internet, so it is built defensively. This page lists what is protected and how.

## Authentication & sessions

| Control | Implementation |
|---|---|
| Password hashing | BCrypt with cost 12 (`SecurityConfig.passwordEncoder`) |
| Password policy | 10–72 characters. 72 is BCrypt's byte limit, enforced in bytes so long passwords are never silently truncated |
| Session token | HS256 JWT (Nimbus), audience `session`, 14-day expiry, random `jti`. Verified: algorithm pinned to HS256, signature, issuer, audience, expiry |
| Token storage | `td_session` cookie: **HttpOnly**, **Secure** (production), **SameSite=Lax**, `Path=/`. JavaScript can't read it, so an XSS bug can't steal it |
| Revocation | each user has a `token_version`. Changing your password, "log out everywhere", or an admin changing your role or disabling you bumps it, and every existing token stops working at once (checked on every request) |
| Signing secret | `JWT_SECRET` env var, at least 32 bytes. The app **refuses to start** without it outside the dev/test profiles |
| User enumeration | login returns the same `AUTH_INVALID_CREDENTIALS` for an unknown user and a wrong password, and runs BCrypt against a dummy hash for unknown users so both take the same time |
| First admin | created from `BOOTSTRAP_ADMIN_*` env vars only while the user table is empty. No default credentials exist in code |

## Registration: invite codes

- Only admins can create codes. Each code has **max uses (1–100)**, an **expiry (≤ 90 days)**, an optional note, and can be **revoked**.
- Codes have 60 bits of randomness (`K7QM-2XRP-9WDF`, no look-alike characters) and come from `SecureRandom`.
- Only the **SHA-256 hash** is stored. The plain code is shown to the admin once.
- Redemption is a single atomic `UPDATE … WHERE uses < max_uses AND NOT revoked AND expires_at > now()`, so parallel sign-ups can't exceed the limit. Registration runs in one transaction: if the claim fails, the new user is rolled back.
- The "is this code valid?" check is rate-limited per IP.

## Forgotten passwords: admin reset links

There is no email sending. Instead an admin creates a **one-time link** under *Admin → Members → Password link* and sends it to the person (e.g. on Messenger).

- 256 random bits (`SecureRandom`, base64url). Only the **SHA-256 hash** is stored (`password_reset_tokens`).
- Valid for **24 hours** and **one use**. Creating a new link invalidates any older unused link for that person.
- The token travels in the request **body** (`POST /api/auth/password-reset/check` and `/password-reset`), not in API URLs, so it never lands in access logs. `Referrer-Policy` keeps the page URL from leaking to other sites.
- Redeeming it sets the new password, bumps `token_version` (every other session ends), lifts the per-username login lock-out, and logs the person in on that device. Disabled accounts can't use a link.
- The admin never sees or chooses the password. Link checks and redemptions are rate-limited per IP.

## Comments

Comments are plain text (≤ 1000 characters) rendered by React as text, never as HTML. Authors can edit their own comments, and authors or admins can delete them. Kudos and likes are one per person (composite primary keys).

## CSRF

Spring Security's CSRF protection is on for every state-changing request. The SPA reads the `XSRF-TOKEN` cookie and sends it back in the `X-XSRF-TOKEN` header (double-submit cookie). Together with `SameSite=Lax` on the session cookie, this blocks cross-site form posts and fetches. Even the WebSocket ticket endpoint is a CSRF-protected `POST`.

## Brute force & abuse: rate limits

In-memory token buckets (`common/RateLimiter`). Exceeding a limit gives `429 RATE_LIMITED` with `Retry-After`.

| Action | Limit |
|---|---|
| Login, per client IP | 20 per 5 minutes |
| Login, per username | 6 per 5 minutes (reset after a successful login) |
| Registration, per IP | 10 per hour |
| Invite-code check, per IP | 30 per hour |
| Password change, per user | 10 per hour |
| Reset-link check or use, per IP | 20 per hour |

Client IPs are read from `X-Forwarded-For` **counting from the right**, skipping exactly `TRUSTED_PROXY_HOPS` proxies we control (Vercel + Render = 2). The left-most entries are attacker-controlled and never trusted. Someone who bypasses Vercel and calls Render directly can spoof the per-IP key, but the per-username limit still applies. If you scale to more than one backend instance, move the buckets to a shared store.

## Injection & XSS

- **SQL injection:** all queries are JPA/JPQL with bound parameters, and no SQL is built from strings. Database check constraints reject impossible data.
- **Input validation:** Bean Validation on every request DTO (usernames `^[A-Za-z0-9_.-]{3,24}$`, real names restricted to letters, spaces and `.'-`, lengths and ranges everywhere). Validation errors return field codes, never echoed input.
- **XSS:** React escapes all output. ESLint forbids `dangerouslySetInnerHTML`. User-supplied text (names, notes, dispute reasons) is only ever rendered as text.
- **Content Security Policy** (Vercel): `default-src 'self'`, no inline scripts, `connect-src` limited to the app and the backend socket, `frame-ancestors 'none'`, `object-src 'none'`. Fonts are self-hosted, so no third-party origins are needed.
- **API responses** carry `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff` and a restrictive `Permissions-Policy`.

## Authorisation

- `/api/admin/**` requires `ROLE_ADMIN` at the filter-chain level. Everything else under `/api/**` requires login, and anything not explicitly routed is denied.
- Scoring is allowed only for the match's players, its creator or an admin. Rated matches must include the creator (admins excepted), which stops people inventing results for others.
- Admins cannot demote or disable themselves, and the last active admin can't be removed.
- Tournament management is limited to the organiser or an admin. Fixtures can be started by their players, the organiser or an admin.

## WebSocket

- The handshake at `/ws` is restricted to `WS_ALLOWED_ORIGINS`.
- `CONNECT` requires a **60-second ticket**: a JWT with audience `ws`, minted by the CSRF-protected `POST /api/ws/ticket`. A session token is rejected on the socket, and a ticket is rejected as a session.
- Clients may only `SUBSCRIBE` to an allow-list of topics (`/topic/live`, `/topic/matches/{uuid}`, `/topic/tournaments/{uuid}`). **`SEND` is always rejected**, because all writes go through the REST API.
- Messages are broadcast only after the database transaction commits.

## Errors & logging

API errors return a stable code (`{"code":"MATCH_OUT_OF_SYNC","status":409}`) and never stack traces, SQL or exception messages (`server.error.include-*: never`). Unexpected exceptions are logged on the server only.

## Secrets & the repository

- No secrets in Git, ever. Only `.env.example` templates are committed, and `.gitignore` covers `.env*`, key stores and `application-local.*`.
- **gitleaks** runs in CI on the full history for every push and pull request.
- Production secrets live in Render (env vars, `JWT_SECRET` generated by Render) and Vercel. `VITE_*` variables end up in the public bundle, so they must never hold secrets.
- The test configuration uses throwaway credentials that only exist on CI's temporary database container.
- **Dependabot** opens weekly update PRs for Maven, npm and GitHub Actions.

### If a secret leaks

1. **Rotate it immediately.** For `JWT_SECRET`, generate a new value in Render; this logs everyone out. For the database password, rotate it in Neon and update Render.
2. Remove it from the Git history (`git filter-repo`) and force-push. Treat the old value as compromised regardless.

## Reporting a vulnerability

Please don't open a public issue. Contact the repository owner privately.

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
- Valid for **24 hours** and **one use**, claimed with a single conditional update so two simultaneous requests can't both use it. Creating a new link invalidates any older unused link for that person.
- The token travels in the request **body** (`POST /api/auth/password-reset/check` and `/password-reset`), not in API URLs, so it never lands in access logs. `Referrer-Policy` keeps the page URL from leaking to other sites.
- Redeeming it sets the new password, bumps `token_version` (every other session ends), lifts the per-username login lock-out, and logs the person in on that device. Disabled accounts can't use a link.
- The admin never sees or chooses the password. Link checks and redemptions are rate-limited per IP.

## Passwords

BCrypt (cost 12) only reads the first 72 bytes of a password. Sign-up, password changes and reset links refuse longer passwords, and a login attempt with more than 72 bytes is treated as a wrong password. Otherwise *the real password followed by anything* would also log in.

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
| Data export, per user | 5 per hour |
| Account deletion, per user | 10 per hour (shares the password-change budget) |
| Browser error reports (`/api/client-errors`), per IP | 10 per minute |

Client IPs are read from `X-Forwarded-For` **counting from the right**, skipping exactly `TRUSTED_PROXY_HOPS` proxies we control (Vercel + Render = 2). The left-most entries are attacker-controlled and never trusted, and an entry that isn't an IP address is never used as a key.

Someone who bypasses Vercel and calls the Render URL directly can still write anything in the client part of the header. That's why every per-IP limit has a second, shared budget for the **edge** address: the right-most entry, written by Render itself, which can't be forged. Through Vercel the edge is one of Vercel's servers, and the budget is 5× the per-IP limit (plenty for the whole group). A direct caller rotating fake addresses runs out after 5× the normal limit. The per-username login limit applies regardless.

If you scale to more than one backend instance, move the buckets to a shared store.

## Injection & XSS

- **SQL injection:** all queries are JPA/JPQL with bound parameters, and no SQL is built from strings. Database check constraints reject impossible data.
- **Input validation:** Bean Validation on every request DTO (usernames `^[A-Za-z0-9_.-]{3,24}$`, real names restricted to letters, spaces and `.'-`, lengths and ranges everywhere). Validation errors return field codes, never echoed input.
- **Free text** (comments, dispute reasons, notes, tournament names, challenge messages) goes through `common/TextInput`: trimmed, length-checked, and refused if it contains NUL bytes, other control characters, bidirectional overrides, byte-order marks or broken surrogate pairs. Emoji and accents are fine.
- **Races** between requests are decided by the database (unique indexes, row locks, conditional updates). A constraint violation is answered with `409 CONFLICT`, never a 500.
- **XSS:** React escapes all output. ESLint forbids `dangerouslySetInnerHTML`. User-supplied text (names, notes, dispute reasons) is only ever rendered as text.
- **Content Security Policy** (Vercel): `default-src 'self'`, no inline scripts, `connect-src` limited to the app and the backend socket, `manifest-src 'self'`, `worker-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`. Fonts are self-hosted and error reports go through our own backend (no Sentry script), so no third-party origins are needed.
- **Client-side path traversal:** ids from the address bar never go into API URLs as typed. The frontend builds every dynamic path with `apiPath` (each segment URL-encoded, `.`/`..`/empty refused), and the fetch wrapper refuses any path outside `/api/` or containing a dot segment, also when percent-encoded. Otherwise a crafted link like `/matches/..%2Fadmin%2F…` could aim the app's own CSRF-protected requests at another endpoint.
- **API responses** carry `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff` and a restrictive `Permissions-Policy`.

## Authorisation

- `/api/admin/**` requires `ROLE_ADMIN` at the filter-chain level. Everything else under `/api/**` requires login, and anything not explicitly routed is denied.
- Scoring is allowed only for the match's players, its creator or an admin. Rated matches must include the creator (admins excepted), which stops people inventing results for others.
- Rated tournament fixtures can only be started by their players or an admin, so an organiser can't start and score a rated match between two others. Casual tournament fixtures can also be started by the organiser.
- Casual (unrated) matches can be set up by anyone, but they never count towards ratings or Player of the Month. They do appear in profile statistics; a player who didn't play one can dispute it.
- Admins cannot demote or disable themselves, and the last active admin can't be removed (nor delete their own account).
- **Party games:** only players, the creator or an admin can score; the creator must play (admins excepted). Games are never rated.
- **Challenges:** only the two players can see or act on a challenge (anyone else gets 404, so ids reveal nothing). Only the opponent accepts or declines. Limits: one open challenge per pair, five open per sender, 48 h expiry.
- **Your own data:** `GET /api/users/me/export` and `DELETE /api/users/me` only ever act on the logged-in user; there is no id in the path. Deletion needs the password.
- Tournament management is limited to the organiser or an admin.

## WebSocket

- The handshake at `/ws` is restricted to `WS_ALLOWED_ORIGINS`.
- `CONNECT` requires a **60-second, single-use ticket**: a JWT with audience `ws`, minted by the CSRF-protected `POST /api/ws/ticket`. Each ticket opens one connection (its id is remembered until it expires). A session token is rejected on the socket, and a ticket is rejected as a session. Logging out everywhere invalidates unused tickets too.
- Clients may only `SUBSCRIBE` to an allow-list of topics (`/topic/live`, `/topic/matches/{uuid}`, `/topic/matches/{uuid}/social`, `/topic/tournaments/{uuid}`, `/topic/games/{uuid}`). **`SEND` is always rejected**, because all writes go through the REST API.
- Messages are broadcast only after the database transaction commits.

## Errors & logging

API errors return a stable code (`{"code":"MATCH_OUT_OF_SYNC","status":409}`) and never stack traces, SQL or exception messages (`server.error.include-*: never`). Unexpected exceptions are logged on the server, and sent to Sentry if `SENTRY_DSN` is set.

**Error alerts** ([OPERATIONS.md](OPERATIONS.md)) never carry a user, username, IP, cookie or request body. Messages, stack traces and paths are scrubbed of e-mail addresses, JWTs, `Bearer` tokens, `password=`/`token=`-style values, query strings and fragments (so a reset link's token can't leak). `POST /api/client-errors` is public so errors on the login page can be reported, but it needs the CSRF token, limits every field's size and is rate-limited. At most 30 events a minute leave the server.

## Secrets & the repository

- No secrets in Git, ever. Only `.env.example` templates are committed, and `.gitignore` covers `.env*`, key stores and `application-local.*`.
- **gitleaks** runs in CI on the full history for every push and pull request.
- Production secrets live in Render (env vars, `JWT_SECRET` generated by Render) and Vercel. `VITE_*` variables end up in the public bundle, so they must never hold secrets.
- The test configuration uses throwaway credentials that only exist on CI's temporary database container.
- **Dependabot** opens weekly update PRs for Maven, npm and GitHub Actions.

### If a secret leaks

1. **Rotate it immediately.** For `JWT_SECRET`, generate a new value in Render; this logs everyone out. For the database password, rotate it in Neon and update Render.
2. Remove it from the Git history (`git filter-repo`) and force-push. Treat the old value as compromised regardless.

## Personal data and backups

The database holds usernames, real names, BCrypt password hashes, match data and comments. Nothing else: no email addresses, phone numbers or IP addresses are stored (the rate limiter keeps IPs in memory only, for a few minutes).

- Members can download everything stored about them and delete their account themselves ([PRIVACY.md](PRIVACY.md)). Deletion anonymises the row (`deleted~<id>`, "Slettet spiller", unusable password, sessions and reset links revoked), deletes their comments, kudos and likes, and keeps the matches for their opponents. A deleted account can't be re-enabled or sent a reset link.
- Everything, including the Player of the Month pages and TV mode, requires login. Only `/api/admin/**` (including `POST /api/admin/awards/recompute`) needs the admin role.
- `scripts/backup-db.sh` writes dumps to `backups/`, which is git-ignored together with `*.dump`. The folder is created with `700` permissions and the dump with `600`. Store copies somewhere private and encrypted.
- Screenshots in `docs/images` come from demo data only. Never commit screenshots of the production app.

## Installable app

The service worker (`public/sw.js`) caches only the app shell and hashed static files. It never caches or answers `/api`, `/ws`, other origins or non-`GET` requests (`scripts/sw.test.mjs`), so no session, score or personal data is stored by it. Darts queued while offline are kept in `localStorage` as segment/multiplier pairs only, under `td.pending.*`, and removed once sent. The session stays in its HttpOnly cookie.

## Tests

Every protection on this page has a test that tries to break it: forged and replayed tokens, CSRF on every endpoint, admin endpoints as a member, races, hostile input, forged proxy headers, socket abuse. See [TESTING.md](TESTING.md).

## Reporting a vulnerability

Please don't open a public issue. Contact the repository owner privately.

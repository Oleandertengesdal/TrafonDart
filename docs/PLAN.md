# TrafonDart: project plan

This is the plan the code follows. Decisions that came from you are marked **(decided)**. Everything else is my recommendation, and the reasoning is written down so you can change it later.

## 1. What we are building

TrafonDart is a private web app for one friend group. It covers four jobs:

1. **Scoring a physical darts match at the board.** A tablet or phone next to the board runs a touch keypad. The server does the X01 maths, bust detection and checkout hints.
2. **Watching live.** Anyone in the group can open the match link and see the scoreboard update in real time over WebSockets.
3. **Running tournaments.** Round-robin and single-elimination brackets that fill themselves in as matches finish.
4. **Bragging rights.** Glicko-2 ratings, rating history, averages, 180s, checkouts and head-to-head records.

The whole UI is available in **Norwegian (bokmål)** and **English** **(decided)**. It uses a dark "Trafon at night" theme built on the building's red brick. UKA is not named or shown anywhere **(decided)**.

## 2. Key decisions

| Topic | Decision | Why |
|---|---|---|
| Repo layout | Monorepo: `backend/` (Spring Boot), `frontend/` (React), `docs/` | One GitHub repo **(decided)**. Each half deploys separately |
| Backend | Java 21, Spring Boot 4.1, Spring Security 7, Spring Data JPA (Hibernate 7), Flyway, STOMP over WebSocket | Current supported Spring line. Flyway keeps the schema versioned and reviewable |
| Frontend | React 19 + TypeScript, Vite, Tailwind CSS 4, Lucide, Recharts, TanStack Query, `@stomp/stompjs`, react-i18next | Type safety on the API contract. TanStack Query handles caching and refetching |
| Database | PostgreSQL 16+ | Requested. Works the same locally (Docker) and on Neon |
| Ratings | **Glicko-2 (decided)**: one match = one rating period, RD grows with inactivity | Suits a small group that plays irregularly |
| Sign-up | **Invite codes only (decided)**. Admins create codes with a max number of uses and an expiry date, and can revoke them | Strangers cannot register |
| First admin | Created at first start-up from `BOOTSTRAP_ADMIN_*` env vars, only if the user table is empty | No hard-coded passwords in the repo |
| Players per match | 1 vs 1 | Ratings, head-to-head and brackets are all pairwise. Since 1.4.0, games for more players (X01 for 3–4 and 2 v 2, Cricket, Killer, Around the Clock) are a separate, never-rated "party games" subsystem ([GAME-RULES.md](GAME-RULES.md#party-games-selskapsspill)) |
| Score input | REST `POST` per dart, results pushed over WebSocket | Every write gets CSRF protection, validation and auth on the normal HTTP path. The socket is read-only, so it has a much smaller attack surface |
| Source of truth | The list of darts thrown. Every live number (remaining, busts, legs, averages) is recomputed by a pure engine | Undo is trivial and correct, even across a leg boundary. The engine is unit-testable without a database |
| Hosting | Frontend on **Vercel**, backend Docker image on **Render** (free web service), Postgres on **Neon** (free) | Render's free Postgres is deleted after 30 days. Neon's free plan has no expiry |

## 3. Architecture

```
 Tablet at the board ─┐                    ┌─────────── Vercel ───────────┐
 Phones of spectators ┼── https://<app>.vercel.app ──► static React build  │
                      │        /api/*  ──── rewrite (same-origin proxy) ───┼──► Render: Spring Boot ──► Neon Postgres
                      └── wss://<backend>.onrender.com/ws (STOMP, read-only)┘        ▲
                                                                           └─────────┘
```

**Why the `/api` proxy matters.** The login cookie is `HttpOnly` and comes from the same origin as the page. If the browser talked to `onrender.com` directly, the cookie would be third-party, and Safari (iPad/iPhone, the most likely devices at the board) blocks those. Vercel rewrites forward `/api/*` to Render, so the browser only ever sees the Vercel origin.

**Why the WebSocket goes direct.** Vercel cannot proxy WebSocket connections. The client therefore asks `/api/ws/ticket` (same origin, cookie-authenticated) for a ticket that is valid for 60 seconds and works only for the socket. It then sends the ticket in the STOMP `CONNECT` frame to Render. No cookie is needed on the socket.

### 3.1 Backend modules (package by feature)

```
no.trafondart
├── config/        security, CORS, WebSocket, Jackson, properties
├── auth/          login, logout, register, me, JWT cookie, invite codes, rate limiting
├── user/          users, roles, admin user management
├── game/          pure X01 engine, checkout calculator (no Spring, no DB)
├── match/         match lifecycle, darts, live state, disputes, completion pipeline
├── rating/        Glicko-2 maths, rating service, rating history, full recompute
├── stats/         profile stats, head-to-head, leaderboard
├── tournament/    round-robin and knockout generators, standings, progression
├── realtime/      STOMP auth interceptor, ws tickets, broadcaster
└── common/        error model (stable error codes), validation, client IP resolver
```

### 3.2 Match completion pipeline

When the final dart of a match is recorded, one transaction runs:

1. The engine confirms the winner. The match is marked `COMPLETED`.
2. Per-player aggregates are written to `match_player_stats`, and one row per leg to `legs`.
3. Glicko-2 updates both players if the match is rated, and writes `rating_history`.
4. If the match belongs to a tournament fixture, the fixture's winner is recorded. The winner advances, standings are recomputed, and the tournament is closed if it was the last fixture.
5. After the commit, events go to `/topic/matches/{id}`, `/topic/tournaments/{id}` and `/topic/live`.

## 4. Database schema (summary)

The full ER diagram and column list are in [`DATABASE.md`](DATABASE.md). Tables:

- `users`: username (unique, case-insensitive), real name, BCrypt hash, role, enabled, `token_version`, Glicko-2 rating / RD / volatility, last rated time
- `invite_codes`: SHA-256 hash of the code (never the plain code), a short hint, max uses, use count, expiry, revoked flag, creator, note
- `invite_code_redemptions`: which user used which code
- `matches`: config (start score, in-rule, out-rule, legs to win), players, starting player, status, winner, rated flag, tournament fixture, timestamps, optimistic-lock version
- `darts`: match, sequence number, player, segment (0–20, 25), multiplier (0–3). **Source of truth**
- `legs`: leg number, starter, winner, darts used, checkout value. Written at completion
- `match_player_stats`: per-player aggregates for one match (points, darts, first-9, 180/140+/100+, checkout attempts/hits, highest checkout)
- `rating_history`: before/after rating and RD per player per rated match
- `match_disputes`: raised by a player, resolved by an admin (upheld = match voided, ratings recomputed)
- `tournaments`, `tournament_participants`, `tournament_fixtures`: format, config, seeds, bracket links (`next_fixture_id`, `next_slot`)

## 5. Game rules implemented

Full definitions are in [`GAME-RULES.md`](GAME-RULES.md).

- **Start scores:** 301, 501, 701.
- **In-rules:** Straight in, Double in (bullseye counts as a double).
- **Out-rules:** Straight out, Double out, Master out (double or treble).
- **Bust:** below zero; or a remainder the out-rule cannot finish (1 for double/master out); or reaching zero on a dart the out-rule does not allow. A bust ends the turn and resets the score to the start of the turn.
- **Checkout assistant:** for the remaining score, the out-rule and the darts left this turn, suggest the best route (`120 → T20 · 20 · D20`), or say none exists. Routes come from a precomputed table and are ranked: fewest darts, then preferred finishing doubles (D20, D16, D8 …), then easier set-up darts.
- **Stats:** 3-dart average = points ÷ darts × 3, with bust darts counted at zero. First-9 average uses the first 9 darts of each leg. A checkout attempt is a dart thrown while the remainder can be finished with that one dart.

## 6. Ratings (Glicko-2)

- New player: rating 1500, RD 350, volatility 0.06, τ = 0.5.
- Each rated match is one rating period for both players. Before the update, each player's RD grows with the number of whole weeks since their last rated match, capped at 350.
- Shown as `rating` with a "provisional" badge while RD > 110.
- When an admin voids a match, every rating is **recomputed from the start** in match order. That is cheap at this scale and always consistent.
- Details and the reference test values (from Glickman's paper) are in [`RATINGS.md`](RATINGS.md).

## 7. Tournaments

- **Round-robin:** fixtures come from the circle method. Every fixture can be played as soon as the tournament starts. Standings: wins, then leg difference, then legs won, then head-to-head.
- **Single elimination:** players are seeded by rating, padded with byes to a power of two, and placed so the top seeds meet last. Byes advance automatically. When both slots of a fixture are filled it becomes `READY`, which unlocks the next round with no manual step.
- Whoever creates a tournament organises it: adds participants and starts it. Admins can cancel any tournament.

## 8. Security

| Threat | Mitigation |
|---|---|
| Stolen session | JWT signed with HS256 in an `HttpOnly; Secure; SameSite=Lax` cookie. JavaScript never sees it. `token_version` lets "log out everywhere", password changes and admin disables revoke all tokens |
| CSRF | Spring Security CSRF on every state-changing request (double-submit `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header), plus `SameSite=Lax` |
| Brute force | Token-bucket rate limits on login (per IP and per username), registration and invite redemption. Responses are `429` with `Retry-After` |
| Weak passwords | At least 10 characters, at most 72 bytes (the BCrypt limit). BCrypt cost 12 |
| SQL injection | Only JPA/JPQL with bound parameters. No string-built SQL |
| XSS | React escapes output. No `dangerouslySetInnerHTML`. Strict CSP and security headers from both Vercel and Spring |
| Invite code leaks | Codes are stored only as SHA-256 hashes, have limited uses and expire. Redemption is atomic, so concurrent sign-ups cannot exceed the limit |
| WebSocket abuse | 60-second single-purpose tickets. Clients may only `SUBSCRIBE`, and every `SEND` is rejected. The allowed origin is set from config |
| Secret leaks in Git | No secrets in the repo, only `.env.example` files. `.gitignore` covers env files. CI runs gitleaks on every push. The app refuses to start in prod without a strong `JWT_SECRET` |
| User enumeration | Login gives the same error for an unknown user and a wrong password, and takes the same time either way |

Details are in [`SECURITY.md`](SECURITY.md).

## 9. Internationalisation

- `react-i18next`, with `frontend/src/i18n/locales/nb.json` and `en.json` holding the same keys.
- Default language: the browser's language if it is Norwegian (`nb`, `nn`, `no`), otherwise English. Your choice is saved, and the NO/EN switch is always in the header.
- The backend never returns display text, only stable **error codes** (`AUTH_INVALID_CREDENTIALS`, `INVITE_EXPIRED`, …) which the frontend translates.
- Numbers and dates are formatted with `Intl` for the active locale (`3,5` vs `3.5`).
- Spoken callouts ("Hundreåtti!" / "One hundred and eighty!") use the browser's speech engine in the active language.
- CI runs a script that fails if the two locale files have different keys.

## 10. Visual design: "Trafon after dark"

The idea: a red-brick transformer station lit up at night. Its arched windows frame the scores, and its round gable window doubles as a dartboard.

| Token | Hex | Use |
|---|---|---|
| `night` | `#170D0C` | Page background: a warm, nearly black brick, not a neutral grey |
| `masonry` | `#24140F` / `#321C17` | Raised surfaces |
| `trafon` | `#C8231F` | Brand red: primary actions, the active player, the logo |
| `salmon` | `#E58A73` | The building's old colour: secondary data, chart lines |
| `trim` | `#F3E9DC` | The white window trim: main text and big numbers |
| `board` | `#1F8A5B` | Dartboard green: wins, success, checkout-possible |
| `brass` | `#CDA349` | Achievements: 180s, high checkouts, tournament winners |

- **Type:** *Big Shoulders Display* (industrial, condensed signage) for scores and headings. *Schibsted Grotesk* (designed for the Norwegian media house) for body text. Both are self-hosted through Fontsource, so no Google Fonts requests are made.
- **The signature element:** the scorer's remaining-score panel is an **arched window**, rounded on top and square at the bottom, with a huge cream number. Everything around it stays quiet.
- **Motion:** only where it means something. The score ticks down when a dart lands, the panel shakes on a bust, and a brass flash plus a spoken callout marks a 180 or a checkout of 100+. `prefers-reduced-motion` turns animations off.
- **Layouts:** tablet landscape puts the scores on the left and the keypad on the right. A phone stacks the scores above a full-width keypad. Desktop gets a wide stats dashboard.

## 11. Hosting and deployment

The step-by-step guide is in [`DEPLOYMENT.md`](DEPLOYMENT.md).

- **Neon** (free: 100 CU-hours and 1 GB per project, no expiry) holds Postgres.
- **Render** free web service runs `backend/Dockerfile`. The free tier sleeps after 15 minutes without traffic and takes about a minute to wake up. Live WebSocket messages count as traffic, so it stays awake during a match. A paid instance (from $7/month) removes the sleep.
- **Vercel** builds `frontend/` and rewrites `/api/*` to Render.
- **GitHub Actions** runs backend tests against a Postgres service container, the frontend build, lint, unit tests, the i18n key check and a gitleaks secret scan.

## 12. Delivery order

1. Docs skeleton and this plan.
2. Backend: schema → engine and checkout (with tests) → Glicko-2 (with tests) → auth and invites → matches and live socket → stats → tournaments → admin and disputes.
3. Frontend: theme and i18n → auth → scorer → spectator → profile/stats → tournaments → admin.
4. Verification: unit and integration tests, a full local run against Postgres, screenshots in both languages, a secret scan.
5. Docs: README, local setup, deployment, API reference.

## 13. Added after v1

- **1.1:** kudos, comments with replies and likes, match analysis tabs, the darts nerd corner, admin password links, the bug-report page.
- **1.2:** Player of the Month with five monthly awards, badges and a hall of fame ([AWARDS.md](AWARDS.md)); user guide, admin guide, deployment plan and screenshot gallery.

See [CHANGELOG.md](../CHANGELOG.md) for details.

## 14. Roadmap

Done in 1.4.0: matches for 3–4 players and doubles (as unrated party games), Cricket, Killer and Around the Clock, the installable app with an offline scoring queue, Player of the Year and the year recap, lifetime achievements, challenges, TV mode, data download and account deletion, error alerts.

Still open:

- Sets (best of N sets of M legs)
- Push notifications when it is your turn in a tournament, or when someone challenges you

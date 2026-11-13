# Changelog

All notable changes to TrafonDart. Versions follow [semantic versioning](https://semver.org/). Database migrations are listed, because they are the one thing that can't simply be rolled back.

## 1.5.0: Trafon at dusk

### Changed

- **New look, drawn from the real building.** A redrawn illustration of Trafon's front, from photos: the low arched gable with its oval window, the pilasters, the banded ground floor, the black doors with their lamps and the ornamented plaques (with a dartboard where the old power company's monogram is), at dusk in the snow. It's on the login page, the waking screen, the quiet home page and the 404 pages.
- **Colour scales**: five scales (brick, stone, lamp, board, dusk) from 50 to 950, with every colour in the app a step on one of them. Warm charcoal surfaces instead of the old maroon-black, and no gradients, glows or purple. Every text colour passes WCAG AA contrast.
- **Type**: Young Serif for headings (sentence case, no bold), Schibsted Grotesk for text, Big Shoulders Display for the scoreboard only. No more letter-spaced capitals.
- **Spacing scale** (4-point) and fixed radii, used everywhere.
- **Real icons, no emoji**: Cricket marks are drawn in SVG, a finished Around the Clock shows a check icon.
- **New logo, favicon and app icons**: the building with a dartboard for a window, as SVG with PNG fallbacks.
- A live match on the home page is the lit facade: a red arch with a white top edge.

### Added

- **Tab titles** on every page ("Spillere – TrafonDart"); a match shows its live score in the tab.
- **Better error handling.** Every error says what happened and what to do, with its own icon: offline, server waking up, logged out, no access, not found, too many tries, server error, changed meanwhile. Retry only where it can help; log in, go home or report the bug where that's the way out.
- **Session expiry**: when a session runs out, the next request sends you to the login page, which says why and brings you back.
- **404 pages** for unknown addresses and for a match, game, player or tournament that doesn't exist (also for a link with a broken id), with a way home.
- An **offline banner** under the header, and a "new version is out" page with a reload button instead of a crash when an open tab misses code after a deploy.
- 29 new tests (428 in all), among them `design.test.mjs`, which keeps the colours, contrast, spacing, fonts, icons and tab titles honest. [DESIGN.md](docs/DESIGN.md) is rewritten, and all screenshots are retaken.

## 1.4.0: Party games, Player of the Year and the installable app

### Added

- **Installable app (PWA)** for the tablet: web app manifest and icons, full screen with no browser bar, a service worker that caches the app shell and static files (never `/api`), and a "waking up the server" screen instead of a bare spinner while a sleeping server starts. The screen stays awake during matches, games and in TV mode (Screen Wake Lock).
- **Offline scoring.** Darts typed while the connection is down are kept on the tablet (also across a reload) and sent in order when it comes back, never twice. Up to 30 can wait.
- **Party games** (never rated): X01 for 3–4 players and 2 v 2 doubles, Cricket with its own scoreboard, Killer and Around the Clock. Live scoreboards, own statistics on the profile. New API `/api/games`, socket topic `/topic/games/{id}`.
- **Player of the Year**: the year's month points added up (≥ 10 rated matches, `AWARDS_MIN_MATCHES_YEAR`), crowned on 1 January with a badge; race and hall of fame on the awards page.
- **Year recap** ("Ditt 2026"): darts thrown, best month, favourite finish, nemesis, biggest win and more, for any year; promoted on the home page in December and January.
- **Lifetime achievements** (23) in the trophy cabinet, with progress and links to the match where they happened. Computed from the matches, so voiding a match updates them. See [docs/ACHIEVEMENTS.md](docs/ACHIEVEMENTS.md).
- **Challenge a friend**: invite, accept or decline, then press Start at the board. Expires after 48 hours.
- **TV mode** (`/tv`): a read-only, full-screen scoreboard that follows live matches and games and shows the leaderboard in between.
- **Your data**: download everything stored about you as JSON, or delete your account (anonymised, matches kept). See [docs/PRIVACY.md](docs/PRIVACY.md).
- **Error alerts** to Sentry with `SENTRY_DSN` (optional `SENTRY_ENVIRONMENT`): server errors and browser crashes (through `POST /api/client-errors`, no third-party script), scrubbed of personal data and capped. A friendly "something broke" page replaces blank screens. See [docs/OPERATIONS.md](docs/OPERATIONS.md).
- 118 new tests (399 in all). New docs: ACHIEVEMENTS, PRIVACY, OPERATIONS.

### Fixed

- Month names crashed on a year-long period; the recap's win rate; deleted accounts could be sent a reset link; a flaky socket test; the new-match form and the profile's head-to-head table were wider than a phone screen. See [docs/TESTING.md](docs/TESTING.md#flaws-the-tests-found).

### Changed

- Players in every response have a `deleted` flag; deleted accounts show as "Deleted player" and can't be found by name.
- `POST /api/admin/awards/recompute` returns `{months, years}`.
- `GET /api/auth/me` is retried while the server wakes up, instead of sending you to the login page.
- `vercel.json`: serves `/sw.js` uncached and the manifest with its content type; CSP adds `manifest-src` and `worker-src 'self'`; `Permissions-Policy` allows the wake lock and full screen for the app itself.

### Database

- `V4__games_challenges_years.sql`: `users.deleted_at`, `games`, `game_players`, `game_darts`, `game_results`, `challenges`, `award_years`, `yearly_awards`.

## 1.3.0: Tests and hardening

### Added

- **281 automated tests** (from 67): unit, property and fuzz tests for the game engine, checkouts, ratings, brackets and month points; end-to-end tests for login, sessions, CSRF on every endpoint, admin access, races between requests, hostile input, the live socket and every feature; frontend tests for the API client, formatting, translations and static security rules. See [docs/TESTING.md](docs/TESTING.md).
- `AccessControlIT` walks every endpoint the server exposes, so a new endpoint without login, CSRF or admin protection fails the build.
- Frontend tests run TypeScript directly on Node 22.18+ (`npm test`, `npm run typecheck:test`), both in CI.

### Fixed (found by the new tests)

- **Security:** a password followed by extra characters also logged in (BCrypt reads only 72 bytes). Logins with more than 72 bytes are now wrong passwords.
- **Security:** a password reset link could be used twice by two simultaneous requests. It is now claimed atomically.
- **Security:** a WebSocket ticket could open many connections within its 60 seconds. Each ticket now works once.
- **Security:** calling the backend directly with a forged `X-Forwarded-For` bypassed the per-IP rate limits. A second budget per edge address now applies.
- **Security:** an organiser of a rated tournament could start and score a fixture between two other players alone. Rated fixtures are now started by their players or an admin.
- **Security:** ids from the address bar went unencoded into API URLs (client-side path traversal). All dynamic API paths are now built with `apiPath`, and the client refuses anything outside `/api/`.
- **Integrity:** casual matches set up by someone else counted towards Player of the Month. The monthly awards now count rated matches only.
- **Integrity:** an upheld dispute stayed open after the match was voided and could be decided again.
- NUL bytes and other control characters in comments, dispute reasons, invite notes or tournament names caused a 500. Free text is now validated (`TextInput`).
- An unsupported `Content-Type` and two simultaneous sign-ups with the same username caused a 500.
- Sign-up without a language stored an empty locale (now Norwegian), and names with a stray space around them were refused (now trimmed).
- A reset link for a disabled account was reported as valid.
- A change that rounds to zero was shown as "−0,0".

### Changed

- Monthly awards count **rated matches only** (see [docs/AWARDS.md](docs/AWARDS.md)).
- Free text is refused if it contains control characters or bidirectional overrides.

## 1.2.0: Player of the Month

### Added

- **Player of the Month** (*Månedens spiller*). Every month crowns five winners: Player of the Month, Climber of the Month, Most active, 180 master and Best finish. Player of the Month goes to whoever had the best month, not the highest rating: month points come from rating gained, matches played, wins, improvement against your own earlier average, 180s, 100+ checkouts and kudos. See [docs/AWARDS.md](docs/AWARDS.md).
- New **Awards** page (*Kåringer*): last month's champion, the live race with a points breakdown per player, category leaders, hall of fame and the rules.
- **Trophy cabinet** on profiles, a "Player of the Month" chip under the reigning player's name, and a crown next to them in the rating tables.
- Home page card with the reigning Player of the Month and the top three in this month's race.
- Admin button to recompute the monthly awards. Voiding a match re-decides the affected months automatically.
- Settings `AWARDS_ZONE` (default `Europe/Oslo`) and `AWARDS_MIN_MATCHES` (default `3`).
- API: `GET /api/awards/current`, `GET /api/awards`, `GET /api/users/{username}/awards`, `POST /api/admin/awards/recompute`.
- Documentation: Norwegian user guide and admin guide, a full deployment plan (phases, checklists, costs, risks, monitoring, backups, rollback), a screenshot gallery with 30 screens in Norwegian and English, and a documentation index.
- `scripts/backup-db.sh` for `pg_dump` backups into the git-ignored `backups/` folder.
- `scripts/backdate-demo.sql` to spread local demo data over three months.
- `scripts/check-docs.py`, run in CI: every endpoint and environment variable must be documented, and every link and image in the docs must exist.

### Changed

- The phone tab bar fits six tabs (Awards was added).
- The chart library now loads only with the match analysis tabs and profiles. The first page load is about 40 % smaller (main script 973 kB → 560 kB before compression).

### Database

- `V3__monthly_awards.sql`: tables `award_periods` and `monthly_awards`.

## 1.1.0: Social and analysis

### Added

- Kudos on matches, comment threads with replies and likes, all live.
- Match page tabs: overview, turn by turn, analysis (score ranges, race to zero, dartboard heatmaps, win probability) and head-to-head.
- Darts nerd corner on profiles: darts thrown, hit rates, best leg, hold and break, favourite numbers and doubles, best checkouts, heatmap.
- Password reset without email: admins create a one-time link (24 hours) under *Admin → Members → Password link*. Using it ends all other sessions and lifts any login lock-out.
- *Report a bug* page that opens an email to `VITE_CONTACT_EMAIL` (default `adminsjef@uka.no`).

### Database

- `V2__social_and_password_reset.sql`: `match_kudos`, `match_comments`, `comment_likes`, `password_reset_tokens`.

## 1.0.0: First version

### Added

- Touch scorepad for X01 (301, 501, 701) with straight/double in and straight/double/master out, automatic bust detection, undo and a checkout assistant.
- Live spectating over STOMP WebSockets, with callouts for 180s, big finishes and game shots.
- Glicko-2 ratings with history, statistics dashboards and head-to-head records.
- Round-robin and knockout tournaments seeded by rating.
- Invite-only sign-up with codes that have a maximum number of uses and an expiry.
- Score disputes handled by admins, with full rating recalculation when a match is voided.
- Norwegian and English throughout. "Trafon after dark" design.
- Security: HttpOnly session cookie, CSRF protection, BCrypt, rate limiting, strict CSP, gitleaks in CI.
- Deployment on Vercel, Render and Neon free tiers.

### Database

- `V1__initial_schema.sql`

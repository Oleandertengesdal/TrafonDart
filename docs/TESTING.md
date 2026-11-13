# Testing

TrafonDart has **428 automated tests**: 305 in the backend (JUnit 5, against a real PostgreSQL) and 123 in the frontend (Node's built-in test runner). CI runs all of them on every push, together with ESLint, a type check, the translation checks, the documentation check and a secret scan.

The suite was written to **find flaws**, not just to confirm the happy path: it forges tokens, races requests against each other, sends hostile input, walks every endpoint without permission, and plays hundreds of random matches to check that the statistics add up. The flaws it found are listed at the end.

## Running the tests

```bash
# backend: needs the Docker database (docker compose up -d)
cd backend
set -a; source ../.env; set +a
mvn verify                    # everything (about 2 minutes)
mvn verify -Punit-only        # only the tests that don't need a database (a few seconds)
mvn verify -Dtest=AuthAndSessionIT   # one class

# frontend
cd frontend
npm test                      # unit, translation and static security tests
npm run typecheck:test        # type-checks the test files
npm run lint && npm run build

# documentation
python3 scripts/check-docs.py
```

The integration tests wipe and re-migrate the **`trafondart_test`** database when they start. They never touch `trafondart`.

## How the integration tests work

- `IntegrationTestBase` starts the real application on a random port with the `test` profile, against PostgreSQL. All `*IT` classes share one Spring context, so the server starts once.
- Every class creates its own members (`member("prefix")` registers a unique user through a fresh invite) and never assumes an empty database. Classes can run in any order.
- `ApiClient` behaves like the browser: it keeps cookies, fetches the CSRF cookie and sends `X-XSRF-TOKEN`. The test profile trusts two proxy hops like production (Vercel → Render), and every client sends its own made-up `X-Forwarded-For` chain, so one test's rate limits never affect another. Security tests forge that header on purpose.
- `StompProbe` is a minimal STOMP client over Java's WebSocket, for the live socket.
- `playToEnd(...)` finishes any match by following the app's own checkout suggestions.
- `app.awards.min-matches` and `min-matches-year` are 1 in tests, so a single match puts someone in the Player of the Month and Player of the Year races.

## What is covered

### Backend unit tests (no database)

| Class | Tests | What it proves |
|---|---|---|
| `X01EngineTest` | 11 | turns, busts (below zero, dead 1, wrong finishing dart), double in, master out, first-9, undo |
| `X01EngineFuzzTest` | 6 | **300 random matches** under every rule combination: remaining never negative, legs alternate the starter, the winner ends on exactly zero with a legal finishing dart, and every statistic (darts, points, first 9, 180s/140+/100+, highest turn, highest checkout, checkouts hit) can be re-derived from the turns. Nothing can be thrown after the match ends. Invalid darts and settings are refused |
| `CheckoutCalculatorTest` | 23 | classic routes (170, 167, 120 …), bogey numbers, every route adds up and ends legally |
| `CheckoutExhaustiveTest` | 3 | **brute force over every 1-, 2- and 3-dart combination**: the checkout table never misses a finish, never suggests a longer route than needed, and the engine accepts every suggested route as a leg win |
| `Glicko2Test` | 4 | Glickman's worked example to 2 decimals, symmetry, inactivity |
| `Glicko2PropertiesTest` | 6 | 20 000 random pairs: winners never lose rating, losers never gain, nothing becomes NaN even at 3500 vs 100, upsets move ratings more than expected results, win probabilities add up to 1 |
| `BracketGeneratorTest` | 11 | round-robin pairs everyone once, seeding order, byes |
| `BracketGeneratorPropertiesTest` | 3 | **every field size 2–64**: everyone in round one exactly once, byes go to the top seeds, seeds 1 and 2 in opposite halves, no empty first-round match; round robin has every pair once and nobody twice in a round |
| `MonthlyScoreTest` | 4 | the month-points formula: an improving, active player beats a stronger but stagnant one; caps and floors |
| `MonthlyScorePropertiesTest` | 4 | 20 000 random months: more wins, 180s, big finishes, kudos or rating never lower your points; components stay within their caps; ties go to the higher-ranked player; ineligible players never win the main awards |
| `DartAnalyticsTest` | 2 | hold/break, best leg, finishing doubles, score ranges, hit rates |
| `JwtServiceTest` | 13 | forged tokens: tampered payload, wrong secret, `alg: none`, HS512 with the right key, wrong issuer, missing expiry, non-numeric subject, expired, garbage, 5 kB input; session and socket tokens aren't interchangeable; secrets shorter than 32 bytes are refused at start-up |
| `RateLimiterTest` | 9 | capacity, refill over time (with a fake clock), keys independent, idle eviction, **exactly 10 of 200 concurrent requests get through a limit of 10**, forged client addresses behind one edge still run out |
| `ClientIpResolverTest` | 9 | `X-Forwarded-For` counted from the right; prepended fake addresses ignored; direct calls to Render; blank, absurdly long and non-address headers |
| `TextInputTest` | 13 | NUL bytes, control characters, ANSI escapes, bidirectional overrides, byte-order marks and broken surrogates are refused; emoji, accents and HTML-looking text are kept as typed |
| `InviteCodeFormatTest` | 5 | 20 000 codes without repeats, only unambiguous characters, sloppy typing normalised, Turkish locale doesn't break codes, only a hash is stored |
| `StompTopicsTest` | 18 | the topic allow-list, including traversal, wildcards, case and trailing characters; socket tickets work once |

### Backend integration tests (real HTTP and PostgreSQL)

| Class | Tests | What it proves |
|---|---|---|
| `AccessControlIT` | 11 | reads **every endpoint from Spring's handler mapping**, so new endpoints are covered automatically: all non-public ones need login, all state-changing ones need the CSRF token, all `/api/admin/**` ones are forbidden for members. CSRF tokens from another browser are refused. Unknown paths and methods, actuator internals. Members can't change their role or rating through their profile. Outsiders can't score, undo, abandon or dispute other people's matches. Rated matches and rated tournament fixtures need a player or an admin |
| `AuthAndSessionIT` | 13 | cookie flags (`HttpOnly`, `SameSite=Lax`, no `Domain`), no secrets in responses, identical answers for wrong password and unknown user, **passwords over 72 bytes**, logout and log-out-everywhere (a copied cookie dies too), password change, forged and wrong-audience cookies, disabling a member, username lock-out with `Retry-After` (and its case variants), forged `X-Forwarded-For` can't dodge per-IP limits, role changes end sessions, admins can't demote or disable themselves |
| `RegistrationIT` | 10 | unknown, revoked and used-up invites; a failed sign-up doesn't use the invite; **8 parallel sign-ups on a 2-person invite create exactly 2 accounts**; the same username registered 4 times at once gives one account and no 500; username and name validation (HTML, SQL, control characters); sign-up can't set role, rating or id; locale; invite settings bounds; the invite list never shows a usable code |
| `PasswordResetIT` | 5 | single use, a new link cancels the old one, **6 parallel uses of one link: exactly one succeeds**, bad input, disabled accounts, tokens never listed |
| `MatchFlowIT` | 9 | match settings, impossible darts, **6 tablets sending the same dart at once record it once**, undo across a leg win, statistics that add up from the darts, abandoned matches leave no trace, **voiding gives exactly the ratings as if the match never happened**, dispute rules, 404 vs 400 |
| `SocialIT` | 7 | kudos rules, replies stay one level deep and can't cross matches, only the author edits, author or admin deletes, deleted text disappears from responses, likes are idempotent, HTML stored as text, NUL bytes and bidi overrides refused |
| `TournamentIT` | 5 | a 4-player league plays 6 matches and ranks correctly, a 5-player cup gives 3 byes and crowns a champion, participant rules, names and settings, starting a fixture twice, abandoning reopens it |
| `AwardsIT` | 5 | **a casual match made up by someone else can't win anybody an award**, voids leave the race, disabled members leave the race, endpoint validation, finalising a month twice changes nothing |
| `StatsIT` | 5 | a brand-new member's statistics contain no `NaN`, an empty match breakdown, the leaderboard order, **months follow Oslo time (including summer time), not UTC**, a cup result can't be voided once the winner has played on, and nothing changes when that is refused |
| `LiveSocketIT` | 9 | every dart is pushed, missing or wrong tickets, **a ticket opens one connection only**, tickets die with the session, only allow-listed topics, clients can never `SEND`, no subscribing before `CONNECT`, foreign websites can't open the socket |
| `RobustnessIT` | 6 | security headers on every response, malformed JSON, wrong content types, a 3 MB body, injection-looking input, error bodies have a fixed shape and never leak stack traces, SQL or class names |
| `GamesIT` | 8 | create and play every party game type over HTTP; doubles lineups; only players, the creator or an admin can score; `MATCH_OUT_OF_SYNC` with the current state; undo; abandon; games never touch ratings; game statistics; live pushes on `/topic/games/{id}` |
| `AchievementsIT` | 6 | a nine-darter unlocks first win, first 180, 100+ checkout, short leg, nine-darter and the averages, each with its match; progress counts; a 170 + bull finish; **a casual match set up by an outsider unlocks nothing**, one set up by the player does; **voiding takes an achievement away**; a solo Around the Clock under 40 darts; unknown players |
| `YearIT` | 3 | the year race sums the month points; **a year is decided once** and gives a `PLAYER_OF_YEAR` badge; voiding re-decides it; the recap adds up (matches, darts, nemesis, favourite opponent, streak, best match, favourite finish, weekday) with no `NaN`; empty years; years out of range; unknown players |
| `ChallengesIT` | 5 | invite → accept → start creates exactly one match with the agreed settings; outsiders get 404 and can't act; you can't accept your own; decline, cancel and who may do which; bad settings, long or hostile messages, disabled opponents; **one open challenge per pair, five per sender**; pending and accepted challenges expire |
| `AccountIT` | 4 | **the export contains everything about you and nothing secret or about others**, is a download and isn't cached; exports are rate-limited; **deleting needs the password**, ends every session, anonymises the account everywhere, removes comments, kudos and likes, calls off challenges, keeps the matches, can't be undone by an admin (enable, role or reset link), frees the username for a newcomer who inherits nothing; the last admin can't delete themselves |
| `ClientErrorsIT` | 4 | browser error reports work before login, need the CSRF token and JSON, refuse oversized or odd reports and are rate-limited per client |
| `TrafonDartApiIntegrationTest` | 12 | the original end-to-end story: sign-up, a full match over the socket, ratings, stats, disputes, tournaments, social, password links, Player of the Month |

### Frontend

| File | Tests | What it proves |
|---|---|---|
| `src/api/client.test.ts` | 16 | CSRF cookie fetched once before the first call, token on every state-changing method but never on `GET`, same-origin credentials only, **one retry after `CSRF_INVALID` and no endless loop**, error mapping (JSON, HTML 502, empty 404, network down), **no request leaves the same-origin API: absolute URLs, `//host`, `..`, `%2e%2e` and backslashes are refused**, `apiPath` encodes every segment and refuses `.`, `..` and empty values, and a bad link is never retried (it shows a message instead of loading forever); deleted players are shown as "Deleted player" in every response; `DELETE` can carry a body |
| `src/lib/format.test.ts` | 9 | Norwegian and English numbers, percentages, signed numbers (a value that rounds to zero has no minus sign), month names (and a whole year for Player of the Year), relative dates, dart labels, rule summaries |
| `src/features/scoring/dartQueue.test.ts` | 16 | the offline queue: labels match the server's, only real darts (a miss is `0,0`) are kept, network and server errors keep the queue while conflicts and refusals drop it, backoff never above 15 s, **a lost answer is recognised as a dart that landed but anything else is a conflict**, the saved queue survives a reload, **tampered or oversized saved queues are ignored**, and only the darts the server hasn't seen are resent |
| `src/lib/wakeLock.test.ts` | 5 | the screen lock is taken and released, taken again after the lock screen, not when turned off, and harmless where unsupported or refused |
| `src/lib/errorReport.test.ts` | 7 | reports keep only the path (no query, no fragment), handle anything thrown, are trimmed to the server's limits, drop e-mail addresses and query strings, are sent once each and at most five per page, ignore noise, and never throw |
| `src/features/games/gameLogic.test.ts` | 8 | the lineup rules match the server's, doubles are sent as A then B, side names, Cricket marks (a level 0–3, drawn as SVG) and targets, Around the Clock labels |
| `src/lib/errors.test.ts` | 10 | every error gets a kind with its own explanation and way out: **offline is told apart from a sleeping server**, 502–504 count as waking up, a retry is offered only where it can help (network, 5xx, 429, never 4xx), 401 sends you to log in, 403 and 404 offer the way home, **a malformed id on a detail page means "not found"**, business errors keep their own sentence; **any request refused as "not logged in" ends the session**, except the "who am I" check itself |
| `src/lib/pageTitle.test.ts` | 4 | tab titles put the page first and the app name last, fall back to the app name, are trimmed and shortened, and show a match's live score |
| `src/features/recap/recapMath.test.ts` | 4 | kilometres walked, win rate without dividing by zero, the years on offer, the December/January promotion |
| `scripts/i18n-usage.test.mjs` | 5 | the same `{{placeholders}}` in both languages, complete plural forms, no English left in the Norwegian file, **every key the code asks for exists**, every API error code has a translation |
| `scripts/security.test.mjs` | 15 | no HTML injection sinks, no `eval`, no secrets in browser storage, `target="_blank"` links have `rel="noopener"`, **every API URL with dynamic parts goes through `apiPath`**, the app talks only to its own origin; `vercel.json` has a strict CSP whose socket host matches the `/api` rewrite, HSTS and the other headers; `index.html` loads no third-party code; no secrets in `VITE_*` variables; **the service worker, manifest and icons are never rewritten to the app shell, `/sw.js` isn't cached by the CDN**, and the app is installable (manifest, icons, CSP for manifest and worker) |
| `scripts/sw.test.mjs` | 7 | runs the service worker in a sandbox: **it never handles `/api`, `/ws`, other sites or anything but `GET`**, serves the shell for page loads, caches hashed assets, never serves itself from cache |
| `scripts/check-i18n.test.mjs` | 2 | the key-parity check itself |
| `scripts/design.test.mjs` | 15 | the design system holds ([DESIGN.md](DESIGN.md)): five full colour scales that get darker step by step, every role colour points at a scale step, **WCAG contrast of 4.5:1 for every text colour on every surface**, no purple anywhere, **no colour values outside `index.css` and `palette.ts`**, no gradients, spacing only from the scale, no one-off font sizes, exactly three typefaces, no letter-spaced caps, **no emoji or dingbat characters in code, translations or the manifest** (real icons only), every page sets its tab title, the favicon set is complete and uses only scale colours |

`npm test` runs the app's TypeScript directly with Node (it strips the types itself). `scripts/test-support` adds the extension-less imports Vite allows and swaps the browser-only i18n setup for a stub.

### Documentation

`scripts/check-docs.py` (in CI) fails if an endpoint is missing from [API.md](API.md), an environment variable is undocumented, a link or image in the docs is broken, an image is unused, or an API error code has no translation.

## Flaws the tests found

The tests were written first and run against the code as it was. These failed, and were then fixed:

| Flaw | Found by | Fix |
|---|---|---|
| **BCrypt only reads 72 bytes**, so *the real password + anything* also logged in | `AuthAndSessionIT` | logins and password changes with more than 72 bytes are simply wrong |
| An **upheld dispute stayed open**: voiding ran bulk queries that detached it, so it could later be "rejected" too | `MatchFlowIT` | the decision is saved before anything else runs; bulk award queries no longer clear the session |
| A **NUL byte** in a comment, dispute reason, invite note or tournament name gave a 500 | `SocialIT`, `MatchFlowIT`, `RegistrationIT`, `TournamentIT` | `TextInput` cleans all free text: no control characters, bidi overrides or broken surrogates |
| Wrong `Content-Type` gave a 500 | `RobustnessIT` | 415 / 406 / 400 handlers |
| Two sign-ups with the same username at the same moment gave a 500 | `RegistrationIT` | the unique index decides, the loser gets `USERNAME_TAKEN` |
| Sign-up without a language stored an empty locale | `RegistrationIT` | defaults to Norwegian |
| A name with a stray space before or after it was refused | `RegistrationIT` | names and usernames are trimmed before validation |
| A **password link could be used twice** by two requests at once | `PasswordResetIT` | the link is claimed with one atomic update |
| A link for a disabled account was reported as valid | `PasswordResetIT` | it's invalid |
| A **socket ticket could open many connections** within its 60 seconds | `LiveSocketIT` | each ticket works once |
| Calling Render directly with a **forged `X-Forwarded-For`** gave unlimited per-IP attempts | `AuthAndSessionIT` | a second, shared budget per edge address the caller can't forge |
| A **casual match made up by someone else** counted towards Player of the Month (fake 180s, wins, activity) | `AwardsIT` | the monthly awards count rated matches only |
| An organiser of a **rated tournament** could start a fixture between two other players and score it alone | `AccessControlIT` | rated fixtures are started by their players or an admin |
| **Client-side path traversal**: an id from the address bar went unencoded into API URLs, so a crafted link could aim the app's own CSRF-protected requests at another endpoint (`/matches/..%2Fadmin%2F…`) | `security.test.mjs`, `client.test.ts` | `apiPath` encodes every segment; the client refuses anything outside `/api/` |
| "−0,0" shown for a change that rounds to zero | `format.test.ts` | rounding happens before choosing the sign |
| *1.4.0:* the profile's trophy cabinet would **crash on a Player of the Year badge**: month names were built from `YYYY-MM`, and `"2025"` made an invalid date | `format.test.ts` | a year is shown as just the year; anything else malformed as "–" |
| *1.4.0:* the offline queue treated a **miss as `0,1`** while the keypad and server use `0,0`: a saved queue with a miss would have been thrown away after a reload | the browser smoke test (a seeded game with misses) → `dartQueue.test.ts` | misses are `0,0`; the test now covers it |
| *1.4.0:* a deleted account could have been **sent a password link** by an admin | `AccountIT` | refused with `ACCOUNT_DELETED` |
| *1.4.0:* `LiveSocketIT` failed now and then: the server may close the socket before the `ERROR` frame arrives | the full suite, run repeatedly | the test accepts either as "refused", and checks that an allowed topic keeps the socket open |
| *1.4.0:* the recap showed "1 % won" instead of 61 % | the browser smoke test | the win rate is passed to `pct()` as a percentage |
| *1.4.0:* on a phone, the Cricket board, the live party games, the profile (party games and head-to-head) and the new-match form were **wider than the screen**, cutting off the keypad on the Cricket scorer | a width scan of every page at 390 px | grid columns may shrink (`min-w-0`), the Cricket table has fixed columns with truncated names |

## Checked in a real browser

Some things only show up in a browser, so 1.4.0 was also driven end to end with Playwright against the running app (demo data): every new page in Norwegian and English, every page's width on a 390 px phone, a challenge from invite to started match, the data download, and the **offline queue**: the tablet goes offline mid-turn, the darts wait (and are saved), they are sent exactly once when it comes back; then the server "goes down", the page is reloaded, and the saved dart is still sent when it returns. The screenshots in [SCREENSHOTS.md](SCREENSHOTS.md) come from that run.

## Writing new tests

- New endpoint: `AccessControlIT` checks login, CSRF and admin access for it automatically. Add the behaviour to the matching `*IT` class, and document it in [API.md](API.md) (`check-docs.py` will remind you).
- New free-text field: pass it through `TextInput` and add a NUL-byte case to the IT test.
- New dynamic API call in the frontend: build the path with `apiPath`, or `security.test.mjs` fails.
- New translation key: add it to both locale files, with the same placeholders.
- Fake secrets in a test (a token that must be scrubbed, say): assemble them at runtime (`"eyJ" + "…"`), or the gitleaks scan in CI flags the test file as a leak.
- New achievement: add a case to `AchievementsIT` (see [ACHIEVEMENTS.md](ACHIEVEMENTS.md)).
- New party game rule: add an example to `PartyEngineTest`; the fuzz test checks the invariants for free.
- Prefer properties over examples where you can ("for every field size", "for 1 000 random months"): they find the cases nobody thought of.

# API reference

The base path is `/api`. All bodies are JSON and all timestamps are ISO-8601 UTC.

- **Auth:** the `td_session` HttpOnly cookie, set by login or register.
- **CSRF:** every `POST`, `PUT`, `PATCH` and `DELETE` needs the `X-XSRF-TOKEN` header, equal to the `XSRF-TOKEN` cookie. Call `GET /api/auth/csrf` once to get the cookie.
- **Errors:** `{"code": "INVITE_EXPIRED", "status": 400, "fieldErrors": [{"field": "username", "code": "FORMAT"}], "details": …}`. Codes are listed in `common/ErrorCode.java` and translated in the frontend. A few come from the framework instead: `METHOD_NOT_ALLOWED` (405), `NOT_ACCEPTABLE` (406) and `UNSUPPORTED_MEDIA_TYPE` (415). Two requests that race (the same username, the last use of an invite or reset link) never give a 500: the loser gets the matching error or `409 CONFLICT`.
- **Free text** (comments, dispute reasons, notes, tournament names) is trimmed and must not contain control characters, bidirectional overrides or NUL bytes (`VALIDATION_FAILED` otherwise).
- **Passwords** are 10–72 bytes. A login attempt with a longer password is a wrong password.
- **`PlayerSummary`** is `{id, username, realName, rating, rd, provisional, deleted}`. `deleted: true` is an account its owner deleted: it is shown as "Deleted player", its `username` is `deleted~<id>` and it has no profile (every `/api/users/{username}/…` call answers 404).

## Auth: `/api/auth`

| Method | Path | Auth | Body → Response |
|---|---|---|---|
| GET | `/csrf` | – | → 204, sets `XSRF-TOKEN` |
| POST | `/login` | – | `{username, password}` → `Me`, sets session |
| POST | `/register` | – | `{inviteCode, username, realName, password, preferredLocale?}` → `Me`, sets session. Spaces around the username and name are trimmed; `preferredLocale` defaults to `nb` |
| GET | `/invites/{code}` | – | → `{valid, reason, expiresAt}` (rate-limited) |
| POST | `/logout` | – | → 204, clears the cookie |
| POST | `/logout-all` | user | → 204, revokes every session |
| GET | `/me` | user | → `Me {id, username, realName, role, preferredLocale, rating, rd, provisional}` |
| POST | `/change-password` | user | `{currentPassword, newPassword}` → `Me` (other sessions revoked, new cookie) |
| POST | `/password-reset/check` | – | `{token}` → `{valid, username, realName, expiresAt}` (rate-limited) |
| POST | `/password-reset` | – | `{token, newPassword}` → `Me`, sets session. Revokes other sessions and lifts the login lock-out. `RESET_LINK_INVALID` if the link was used or has expired |

## Users: `/api/users`

| Method | Path | → |
|---|---|---|
| GET | `/` | active players `PlayerSummary[]` (for pickers) |
| GET | `/leaderboard` | `LeaderboardRow[]` by rating |
| PATCH | `/me` | `{realName?, preferredLocale?}` → `Me` |
| GET | `/me/export` | everything stored about you as a JSON download (`Content-Disposition: attachment`, `Cache-Control: no-store`): `{format, exportedAt, profile, matches (with your darts), games, comments, kudosGiven, commentLikes, ratingHistory, awards, achievements, challenges}`. 5 per hour |
| DELETE | `/me` | `{password}` → 204, clears the cookie. Deletes the account for good (see [PRIVACY.md](PRIVACY.md)). `PASSWORD_INCORRECT`; `LAST_ADMIN` for the only admin |
| GET | `/{username}/stats` | `ProfileStats` (record, averages, 180s, checkout %, form, rating history, average trend) |
| GET | `/{username}/head-to-head` | `HeadToHeadRow[]`, one per opponent |
| GET | `/{username}/head-to-head/{other}` | `HeadToHeadRow` |
| GET | `/{username}/achievements` | `{unlocked, total, items: [{code, unlocked, unlockedAt, matchId, gameId, progress, target}]}`. Codes and rules: [ACHIEVEMENTS.md](ACHIEVEMENTS.md) |
| GET | `/{username}/recap?year=` | the year recap (default: this year; 2000 up to this year, else `VALIDATION_FAILED`): `{year, soFar, player, matches: {played, won, lost}, legsWon, legsPlayed, dartsThrown, average, count180, count140, count100, highestCheckout, highestTurn, bestMatch, biggestWin, nemesis, favouriteVictim, favouriteFinish: {label, count}, favouriteNumber, longestWinStreak, busiestWeekday (1 = Monday), bestMonth: {period, points, rank}, monthsWon, yearRank, yearPoints, playerOfTheYear, games: {played, won, byType}, achievements: [code]}`. Empty years give zeros and `null`s |
| GET | `/{username}/game-stats` | `{player, types: [{type, played, won, darts, average, best}]}` for party games |

## Matches: `/api/matches`

| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{player1Id, player2Id, startScore: 301\|401\|501\|701, inRule: STRAIGHT\|DOUBLE, outRule: STRAIGHT\|DOUBLE\|MASTER, legsToWin: 1–11, startingPlayer: 0\|1\|null (random), rated}` → 201 `LiveMatch` |
| GET | `/live` | `MatchSummary[]` in progress |
| GET | `/recent?user=&limit=` | `MatchSummary[]` completed/voided, newest first |
| GET | `/{id}` | `LiveMatch` |
| POST | `/{id}/throws` | `{segment: 0–20\|25, multiplier: 0–3, expectedDartCount}` → `LiveMatch` |
| POST | `/{id}/undo` | `{expectedDartCount}` → `LiveMatch` |
| POST | `/{id}/abandon` | → `LiveMatch` |
| POST | `/{id}/disputes` | `{reason}` → 201 `DisputeView` (players of a completed match) |

Valid darts: miss `(0,0)`, `(1–20, 1–3)`, outer bull `(25,1)`, bull `(25,2)`.

`MATCH_OUT_OF_SYNC` (409) means `expectedDartCount` didn't match. `details` then contains the current `LiveMatch`.

### `LiveMatch` (also pushed on the socket)

```jsonc
{
  "id": "uuid", "status": "IN_PROGRESS", "rated": true,
  "config": {"startScore": 501, "inRule": "STRAIGHT", "outRule": "DOUBLE", "legsToWin": 3},
  "tournament": {"id": "uuid", "name": "Trafon Cup"} | null,
  "players": [PlayerSummary, PlayerSummary], "scorerIds": [1, 2],
  "currentLeg": 2, "currentPlayerIndex": 0,
  "legsWon": [1, 0], "remaining": [141, 301], "opened": [true, true],
  "currentTurn": {"playerIndex": 0, "darts": [{"segment": 20, "multiplier": 3, "label": "T20", "points": 60}], "total": 60, "bust": false, "checkout": false} | null,
  "lastCompletedTurn": TurnView | null, "turnJustEnded": false,
  "stats": [LivePlayerStats, LivePlayerStats],
  "checkout": ["T20", "S11", "D20"] | null, "dartsLeftInTurn": 2,
  "lastEvent": "NONE|DART|BUST|LEG_WON|MATCH_WON",
  "winnerIndex": null, "dartCount": 37,
  "legs": [{"legNo": 1, "starterIndex": 0, "winnerIndex": 0, "winnerDarts": 18, "checkout": 40}],
  "createdAt": "…", "completedAt": null, "disputed": false
}
```

### Analysis

| Method | Path | → |
|---|---|---|
| GET | `/api/matches/{id}/breakdown` | `{winProbability: [p1%, p2%], legs: [{legNo, starterIndex, winnerIndex, dartsUsed, averages, turns: [{playerIndex, turnInLeg, darts: ["T20", …], scored, remainingAfter, bust, checkout}]}], players: [{heatmap, bands, trebleRate, doubleRate, missRate, busts, bestLeg, finishes}]}` |
| GET | `/api/users/{username}/dart-stats` | `{dartsThrown, pointsScored, trebleRate, doubleRate, bullRate, missRate, busts, bustRate, bestLeg, avgDartsPerLegWon, hold: {won, of}, breaks: {won, of}, mostCommonTurn, favouriteSegment, bands, heatmap: [{segment, multiplier, count}], finishes, topCheckouts, kudosReceived}` |

### Kudos and comments

Every mutation returns the full `SocialView {matchId, kudos: {count, mine, canGive, givers}, commentCount, comments: [CommentView]}`.

| Method | Path | Body |
|---|---|---|
| GET | `/api/matches/{id}/social` | – |
| PUT / DELETE | `/api/matches/{id}/kudos` | – (players can't give kudos to their own match: `KUDOS_OWN_MATCH`) |
| POST | `/api/matches/{id}/comments` | `{body (≤ 1000), parentId?}`. A reply to a reply joins the same thread |
| PATCH | `/api/comments/{id}` | `{body}` (author only) |
| DELETE | `/api/comments/{id}` | author or admin. A comment that has replies is soft-deleted, so the thread stays intact |
| PUT / DELETE | `/api/comments/{id}/like` | – |

`CommentView`: `{id, author, body, deleted, createdAt, editedAt, likes, likedByMe, likedBy, canEdit, canDelete, replies}`.

## Monthly awards: `/api/awards`

How the points are calculated is explained in [AWARDS.md](AWARDS.md). All periods are `YYYY-MM` in the awards time zone (`AWARDS_ZONE`, default `Europe/Oslo`).

| Method | Path | → |
|---|---|---|
| GET | `/api/awards/current` | `AwardRace` for the month in progress (see below). Only rated matches count |
| GET | `/api/awards?limit=24` | `MonthResult[]`, decided months newest first (`limit` 1–120) |
| GET | `/api/users/{username}/awards` | `AwardView[]`, every award that player has won (the trophy cabinet) |

Finished months are decided lazily: any of these calls first finalises months that have ended but aren't stored yet. A scheduled job does the same at 00:05 on the 1st.

```jsonc
// AwardRace
{
  "period": "2026-11", "startsAt": "2026-10-31T23:00:00Z", "endsAt": "2026-11-30T23:00:00Z",
  "minMatches": 3,
  "standings": [{
    "rank": 1, "player": PlayerSummary, "eligible": true,
    "stats":  {"matches": 7, "wins": 2, "ratingChange": 74.1, "monthAverage": 78.2, "priorAverage": 71.3,
               "count180": 2, "tonCheckouts": 0, "highestCheckout": 96, "kudos": 3},
    "points": {"rating": 60, "activity": 28, "wins": 12, "improvement": 13.8, "highlights": 6, "kudos": 3, "total": 122.8}
  }],
  "leaders": [{"award": "PLAYER_OF_MONTH", "player": PlayerSummary, "value": 122.8}],
  "reigning": AwardView | null            // last month's Player of the Month
}
// AwardView
{"period": "2026-10", "award": "PLAYER_OF_MONTH|MOST_IMPROVED|MOST_ACTIVE|MOST_180S|HIGHEST_CHECKOUT", "player": PlayerSummary | null, "value": 149.4}
// MonthResult
{"period": "2026-10", "awards": [AwardView]}
```

## Player of the Year

| Method | Path | → |
|---|---|---|
| GET | `/api/awards/year/current` | `{year, minMatches, standings: [{rank, player, eligible, matches, points, monthsPlayed, monthsWon}], reigning: AwardView \| null}`. `points` is the sum of the year's month points; `minMatches` is `AWARDS_MIN_MATCHES_YEAR` |
| GET | `/api/awards/years` | `[{year, award: AwardView}]`, decided years newest first |

A Player of the Year award is an `AwardView` with `award: "PLAYER_OF_YEAR"` and `period: "YYYY"`; it also appears in `/api/users/{username}/awards`. Years are decided on 1 January (and lazily, like months).

## Party games: `/api/games`

X01 for 3–4 players or 2 v 2, Cricket, Killer and Around the Clock. Never rated. Rules: [GAME-RULES.md](GAME-RULES.md).

| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{type: X01\|CRICKET\|KILLER\|AROUND_THE_CLOCK, playerIds, teams, settings?}` → 201 `GameView`. For doubles send `[a1, a2, b1, b2]`; the throwing order becomes A1, B1, A2, B2. The creator must play (admins excepted). `settings` (all optional): X01 `{startScore, inRule, outRule, legsToWin}`, Cricket `{roundLimit: 0\|10–30}`, Killer `{lives: 1–5}` (numbers are drawn by the server), Around the Clock `{multiplierSkips}` |
| GET | `/live` | `GameSummary[]` in progress |
| GET | `/recent?user=&limit=` | `GameSummary[]` finished, newest first |
| GET | `/{id}` | `GameView` |
| POST | `/{id}/throws` | `{segment, multiplier, expectedDartCount}` → `GameView` (players, the creator or an admin) |
| POST | `/{id}/undo` | `{expectedDartCount}` → `GameView` |
| POST | `/{id}/abandon` | → `GameView` |

The `MATCH_*` error codes are reused (`MATCH_OUT_OF_SYNC` with the current `GameView` in `details`, `MATCH_NOT_SCORER`, `MATCH_NOT_IN_PROGRESS`).

```jsonc
// GameView (also pushed on /topic/games/{id})
{
  "id": "uuid", "type": "CRICKET", "teams": false, "rated": false, "status": "IN_PROGRESS",
  "settings": {"startScore": null, "inRule": null, "outRule": null, "legsToWin": 1, "lives": null, "multiplierSkips": null, "roundLimit": 20, "killerNumbers": null},
  "players": [{"slot": 0, "side": 0, "player": PlayerSummary}],
  "sides": [{"side": 0, "legsWon": 0, "remaining": null, "opened": null, "marks": {"20": 3, "19": 1}, "points": 40,
             "number": null, "lives": null, "killer": null, "out": null, "target": null, "finished": null}],
  "slots": [{"slot": 0, "side": 0, "darts": 9, "score": 6, "count180": 0, "highestCheckout": 0, "placement": 0}],
  "currentSlot": 1, "leg": 1, "round": 4, "turnDarts": ["T20"], "checkout": null, "lastDart": "T20",
  "winnerSide": null, "dartCount": 10, "createdBy": 1, "createdAt": "…", "completedAt": null
}
```

Fields a game type doesn't use are `null`. `score` means X01 points, Cricket marks, Killer lives taken and Around the Clock targets hit.

## Practice against Trafo: `/api/bot-matches`

Matches against Trafo, the bot. You score your own darts; when the turn passes to Trafo, it throws its turn in the same request. Stored apart from matches: never rated, and never in the leaderboard, head-to-head, match stats, awards, achievements or the recap. How the bot plays: [GAME-RULES.md](GAME-RULES.md#trafo-the-practice-bot).

| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{level: BEGINNER\|CASUAL\|CLUB\|LEAGUE\|PRO, startScore, inRule, outRule, legsToWin: 1–11, startingPlayer: 0 (you)\|1 (Trafo)\|null (random)}` → 201 `BotMatch`. One unfinished match per player: otherwise `BOT_MATCH_IN_PROGRESS` (409) with `{id}` in `details` |
| GET | `/{id}` | `BotMatch` (your own matches only; anyone else gets 404) |
| POST | `/{id}/throws` | `{segment, multiplier, expectedDartCount}` → `BotMatch`, with Trafo's answer already thrown |
| POST | `/{id}/undo` | `{expectedDartCount}` → `BotMatch`. Takes back your last dart and everything Trafo threw after it. Trafo's darts are drawn from the match's seed, so the same dart gets the same answer |
| POST | `/{id}/forfeit` | → 200 `BotMatch` (`FORFEITED`, a loss) once you've thrown; 204 and the match is removed if you haven't |
| GET | `/api/users/{username}/bot-record` | `BotRecord`: played, won, lost, `winRate`, `currentStreak`, `bestStreak`, `hardestBeaten`, `levels[]` `{level, typicalAverage, played, won}`, `throwing` (your average, first 9, 180s, checkout % and Trafo's average in these matches), `recent[]` (last 10), `openMatchId` (your own unfinished match; null on others' records) |

`BotMatch` has every field of `LiveMatch` (you are player 0, Trafo player 1 with id 0), and also `level`, `typicalAverage`, `playerTurn` (your latest turn) and `botTurns` (Trafo's turns since your last dart, for showing them dart by dart). `status` is `IN_PROGRESS`, `COMPLETED` or `FORFEITED`. The `MATCH_*` error codes are reused as for party games.

## Challenges: `/api/challenges`

| Method | Path | Body → Response |
|---|---|---|
| GET | `/` | `{incoming: [ChallengeView], outgoing: [ChallengeView]}`, open ones only (pending or accepted) |
| POST | `/` | `{opponentId, startScore, inRule, outRule, legsToWin, rated?, message? (≤ 200)}` → 201 `ChallengeView` |
| POST | `/{id}/accept` | opponent only, pending only |
| POST | `/{id}/decline` | opponent only, pending only |
| POST | `/{id}/cancel` | the challenger (pending), either player (accepted) |
| POST | `/{id}/start` | either player, accepted only: creates the match → `ChallengeView` with `matchId` |

`ChallengeView`: `{id, challenger, opponent, startScore, inRule, outRule, legsToWin, rated, message, status: PENDING|ACCEPTED|DECLINED|CANCELLED|EXPIRED|STARTED, matchId, createdAt, expiresAt, respondedAt}`. A pending challenge expires after 48 hours, an accepted one 48 hours after it was accepted. Errors: `CHALLENGE_NOT_OPEN` (409, answered, expired or already started), `CHALLENGE_EXISTS` (409, the two of you already have an open one), `CHALLENGE_LIMIT` (409, five open challenges sent), `MATCH_PLAYERS_SAME`, and 404 for anyone who isn't one of the two players.

## Error reports: `/api/client-errors`

| Method | Path | Auth | Body → Response |
|---|---|---|---|
| POST | `/api/client-errors` | – (CSRF required) | `{kind: error\|unhandledrejection\|boundary, message (≤ 500), stack? (≤ 8000), path? (≤ 300), release? (≤ 40)}` → 202. 10 per minute per client |

The frontend reports its own crashes here. With `SENTRY_DSN` set the backend forwards them to Sentry, scrubbed (see [OPERATIONS.md](OPERATIONS.md)); without it they are only logged.

## Tournaments: `/api/tournaments`

| Method | Path | Body → Response |
|---|---|---|
| GET | `/` | `TournamentSummary[]` |
| POST | `/` | `{name, format: ROUND_ROBIN\|SINGLE_ELIMINATION, startScore, inRule, outRule, legsToWin, rated, participantIds[]}` → 201 `TournamentDetail` (status DRAFT) |
| GET | `/{id}` | `TournamentDetail {summary, organiserId, participants, fixtures, standings, rounds}` |
| PUT | `/{id}/participants` | `{participantIds}` (DRAFT only, organiser/admin) |
| POST | `/{id}/start` | seeds by rating, generates fixtures and byes (organiser/admin) |
| POST | `/{id}/fixtures/{fixtureId}/start` | creates the match for a READY fixture → `{matchId}`. Rated tournaments: the fixture's players or an admin. Casual ones: also the organiser |
| POST | `/{id}/cancel` | organiser/admin |

## Live socket: `/ws` (STOMP 1.2)

1. `POST /api/ws/ticket` → `{ticket, expiresInSeconds: 60}`
2. Open a WebSocket to `/ws` and send `CONNECT` with the header `ticket:<ticket>`. A ticket opens **one** connection; get a new one to reconnect.
3. `SUBSCRIBE` to:

| Topic | Payload |
|---|---|
| `/topic/matches/{id}` | the full `LiveMatch` after every dart, undo or abandon |
| `/topic/matches/{id}/social` | `{matchId}` whenever kudos, comments or likes change (refetch) |
| `/topic/live` | `{matchId}` whenever a match or party game starts or ends (refetch the lists) |
| `/topic/tournaments/{id}` | `{tournamentId}` whenever the tournament changes (refetch it) |
| `/topic/games/{id}` | the full `GameView` after every dart, undo or abandon |

`SEND` frames and other destinations are rejected.

## Admin: `/api/admin` (role ADMIN)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/users` | `AdminUserView[]` `{id, username, realName, role, enabled, rating, createdAt, deleted}` |
| PATCH | `/users/{id}` | `{role?: USER\|ADMIN, enabled?}` (not yourself; the last admin is kept; `ACCOUNT_DELETED` for a deleted account) |
| POST | `/users/{id}/password-reset` | → 201 `{token, username, expiresAt}`. The frontend builds `/reset-password?token=…`. The token is only returned here. `ACCOUNT_DELETED` for a deleted account |
| GET | `/invites` | `InviteView[]` (hint, uses, status, usedBy) |
| POST | `/invites` | `{maxUses: 1–100, validHours: 1–2160, note?}` → 201 `{code, invite}`. **The code is only returned here.** |
| POST | `/invites/{id}/revoke` | → 204 |
| GET | `/disputes` | `DisputeView[]` |
| POST | `/disputes/{id}/resolve` | `{outcome: REJECT\|VOID_MATCH, note?}` |
| POST | `/ratings/recompute` | → `{matches}`. Also re-decides the monthly awards |
| POST | `/awards/recompute` | → `{months, years}`. Forgets every decided month and year and decides them again from the matches |

## Health

`GET /actuator/health` → `{"status":"UP"}` (public, no details).

# API reference

The base path is `/api`. All bodies are JSON and all timestamps are ISO-8601 UTC.

- **Auth:** the `td_session` HttpOnly cookie, set by login or register.
- **CSRF:** every `POST`, `PUT`, `PATCH` and `DELETE` needs the `X-XSRF-TOKEN` header, equal to the `XSRF-TOKEN` cookie. Call `GET /api/auth/csrf` once to get the cookie.
- **Errors:** `{"code": "INVITE_EXPIRED", "status": 400, "fieldErrors": [{"field": "username", "code": "FORMAT"}], "details": …}`. Codes are listed in `common/ErrorCode.java` and translated in the frontend.

## Auth: `/api/auth`

| Method | Path | Auth | Body → Response |
|---|---|---|---|
| GET | `/csrf` | – | → 204, sets `XSRF-TOKEN` |
| POST | `/login` | – | `{username, password}` → `Me`, sets session |
| POST | `/register` | – | `{inviteCode, username, realName, password, preferredLocale?}` → `Me`, sets session |
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
| GET | `/{username}/stats` | `ProfileStats` (record, averages, 180s, checkout %, form, rating history, average trend) |
| GET | `/{username}/head-to-head` | `HeadToHeadRow[]`, one per opponent |
| GET | `/{username}/head-to-head/{other}` | `HeadToHeadRow` |

## Matches: `/api/matches`

| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{player1Id, player2Id, startScore: 301\|501\|701, inRule: STRAIGHT\|DOUBLE, outRule: STRAIGHT\|DOUBLE\|MASTER, legsToWin: 1–11, startingPlayer: 0\|1\|null (random), rated}` → 201 `LiveMatch` |
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

## Tournaments: `/api/tournaments`

| Method | Path | Body → Response |
|---|---|---|
| GET | `/` | `TournamentSummary[]` |
| POST | `/` | `{name, format: ROUND_ROBIN\|SINGLE_ELIMINATION, startScore, inRule, outRule, legsToWin, rated, participantIds[]}` → 201 `TournamentDetail` (status DRAFT) |
| GET | `/{id}` | `TournamentDetail {summary, organiserId, participants, fixtures, standings, rounds}` |
| PUT | `/{id}/participants` | `{participantIds}` (DRAFT only, organiser/admin) |
| POST | `/{id}/start` | seeds by rating, generates fixtures and byes (organiser/admin) |
| POST | `/{id}/fixtures/{fixtureId}/start` | creates the match for a READY fixture → `{matchId}` (fixture players, organiser, admin) |
| POST | `/{id}/cancel` | organiser/admin |

## Live socket: `/ws` (STOMP 1.2)

1. `POST /api/ws/ticket` → `{ticket, expiresInSeconds: 60}`
2. Open a WebSocket to `/ws` and send `CONNECT` with the header `ticket:<ticket>`.
3. `SUBSCRIBE` to:

| Topic | Payload |
|---|---|
| `/topic/matches/{id}` | the full `LiveMatch` after every dart, undo or abandon |
| `/topic/matches/{id}/social` | `{matchId}` whenever kudos, comments or likes change (refetch) |
| `/topic/live` | `{matchId}` whenever a match starts or ends (refetch the lists) |
| `/topic/tournaments/{id}` | `{tournamentId}` whenever the tournament changes (refetch it) |

`SEND` frames and other destinations are rejected.

## Admin: `/api/admin` (role ADMIN)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/users` | `AdminUserView[]` |
| PATCH | `/users/{id}` | `{role?: USER\|ADMIN, enabled?}` (not yourself; the last admin is kept) |
| POST | `/users/{id}/password-reset` | → 201 `{token, username, expiresAt}`. The frontend builds `/reset-password?token=…`. The token is only returned here |
| GET | `/invites` | `InviteView[]` (hint, uses, status, usedBy) |
| POST | `/invites` | `{maxUses: 1–100, validHours: 1–2160, note?}` → 201 `{code, invite}`. **The code is only returned here.** |
| POST | `/invites/{id}/revoke` | → 204 |
| GET | `/disputes` | `DisputeView[]` |
| POST | `/disputes/{id}/resolve` | `{outcome: REJECT\|VOID_MATCH, note?}` |
| POST | `/ratings/recompute` | → `{matches}` |

## Health

`GET /actuator/health` → `{"status":"UP"}` (public, no details).

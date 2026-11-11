# Database

PostgreSQL 16. The schema is owned by Flyway (`backend/src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`, so it only checks the schema and never changes it.

## ER diagram

```mermaid
erDiagram
  users ||--o{ invite_codes : "creates"
  invite_codes ||--o{ invite_code_redemptions : "used by"
  users ||--o| invite_code_redemptions : "registered with"
  users ||--o{ matches : "player1 / player2 / created_by / winner"
  matches ||--o{ darts : "source of truth"
  matches ||--o{ legs : "written at completion"
  matches ||--o{ match_player_stats : "2 rows at completion"
  matches ||--o{ rating_history : "2 rows if rated"
  matches ||--o{ match_disputes : ""
  tournaments ||--o{ tournament_participants : ""
  tournaments ||--o{ tournament_fixtures : ""
  tournaments ||--o{ matches : "tournament_id"
  tournament_fixtures |o--o| matches : "match_id"
  tournament_fixtures |o--o| tournament_fixtures : "next_fixture_id"

  users {
    bigserial id PK
    varchar username "unique lower()"
    varchar real_name
    varchar password_hash "BCrypt(12)"
    varchar role "USER|ADMIN"
    boolean enabled
    int token_version "bump = revoke sessions"
    varchar preferred_locale "nb|en"
    float rating
    float rating_deviation
    float volatility
    timestamptz last_rated_at
  }
  invite_codes {
    bigserial id PK
    varchar code_hash "SHA-256, unique"
    varchar code_hint "first 4 chars"
    int max_uses
    int uses
    timestamptz expires_at
    boolean revoked
  }
  matches {
    uuid id PK
    int start_score "301|501|701"
    varchar in_rule
    varchar out_rule
    int legs_to_win
    bigint player1_id FK
    bigint player2_id FK
    smallint starting_player
    varchar status "IN_PROGRESS|COMPLETED|ABANDONED|VOIDED"
    boolean rated
    bigint winner_id FK
    uuid tournament_id FK
    bigint version
  }
  darts {
    bigserial id PK
    uuid match_id FK
    int seq "unique per match"
    bigint player_id FK
    smallint segment "0-20, 25"
    smallint multiplier "0-3"
  }
  legs {
    uuid match_id FK
    int leg_no
    bigint starter_id
    bigint winner_id
    int winner_darts
    int checkout
  }
  match_player_stats {
    uuid match_id PK
    bigint user_id PK
    int legs_won
    int darts_thrown
    int points_scored
    int first9_darts
    int first9_points
    int count_180
    int count_140
    int count_100
    int highest_turn
    int highest_checkout
    int checkout_attempts
    int checkouts_hit
  }
  rating_history {
    bigint user_id FK
    uuid match_id FK
    float rating_before
    float rating_after
    float rd_before
    float rd_after
    timestamptz rated_at
  }
  tournaments {
    uuid id PK
    varchar name
    varchar format "ROUND_ROBIN|SINGLE_ELIMINATION"
    varchar status
    bigint winner_id
  }
  tournament_fixtures {
    bigserial id PK
    uuid tournament_id FK
    int round
    int position
    bigint player1_id
    bigint player2_id
    uuid match_id "unique"
    bigint winner_id
    varchar status "PENDING|READY|IN_PROGRESS|COMPLETED|BYE"
    bigint next_fixture_id FK
    smallint next_slot "1|2"
  }
```

## Social & password reset (V2)

| Table | Purpose |
|---|---|
| `match_kudos` | `(match_id, user_id)` primary key: one kudos per person per match |
| `match_comments` | `id, match_id, author_id, parent_id → match_comments (one level of replies), body ≤ 1000, deleted (soft delete when replies exist), created_at, edited_at` |
| `comment_likes` | `(comment_id, user_id)` primary key: one like per person per comment |
| `password_reset_tokens` | `user_id, token_hash (SHA-256, unique), expires_at, used_at, created_by`: admin-issued one-time links |

## Design notes

- **`darts` is the only live game state.** Legs, stats and ratings are *derived* and written once, when the match completes. Live views replay the darts, which takes microseconds for a few hundred darts.
- **Check constraints mirror the domain rules.** Examples: valid segment/multiplier pairs, `uses <= max_uses`, distinct players, allowed start scores. A bug in the service layer still can't write nonsense.
- **Indexes** cover the hot paths: case-insensitive username lookup, matches by player and completion time, the live-match list, rating history per user, fixtures per tournament. A partial unique index allows only one *open* dispute per match.
- **UUIDs** are used for matches and tournaments, which appear in shareable URLs. Everything else uses `bigserial`.
- **Concurrency:**
  - Scoring locks the match row (`SELECT … FOR UPDATE`).
  - Rating updates lock both player rows, in id order, to avoid deadlocks.
  - Invite redemption is a single conditional `UPDATE … WHERE uses < max_uses`, so concurrent sign-ups can never exceed the limit.
- **Statistics bands:** `count_140` is turns scoring 140–179 and `count_100` is 100–139. A 180 is counted only in `count_180`.

## Changing the schema

1. Add `V{n}__short_description.sql` in `db/migration`. Never edit a migration that has already run anywhere.
2. Update the entity, then run `mvn verify`. The integration test migrates a fresh database and Hibernate validates against it.

# Achievements (Prestasjoner)

Lifetime achievements sit in the trophy cabinet on every profile, next to the monthly and yearly awards. They are
milestones you keep for good: your first 180, a 100+ checkout, a nine-darter, 100 matches played.

## Rules

| Code | Name (en / nb) | How to unlock |
|---|---|---|
| `FIRST_WIN` | First win / Første seier | Win a match |
| `MATCHES_10` | Regular / Stamgjest | Play 10 matches (progress shown) |
| `MATCHES_50` | Part of the furniture / En del av inventaret | Play 50 matches (progress shown) |
| `MATCHES_100` | Centurion / Hundre kamper | Play 100 matches (progress shown) |
| `WINS_25` | Winning habit / Vinnervaner | Win 25 matches (progress shown) |
| `STREAK_5` | On fire / Glohet | Win 5 matches in a row (progress = longest run) |
| `FIRST_180` | One hundred and eighty! / Hundreogåtti! | Throw a 180 |
| `MAX_COLLECTOR` | Maximum collector / Maksimumsamler | Throw ten 180s (progress shown) |
| `TON_PLUS_CHECKOUT` | Ton-plus finish / Hundre-pluss-utgang | Check out 100 or more |
| `BIG_FISH` | The Big Fish / Den store fisken | Check out 170 |
| `BULL_FINISH` | Bull finish / Bull-utgang | Win a leg with the bull as the last dart |
| `SHORT_LEG` | Short leg / Kort leg | Win a 501 leg in 15 darts or fewer |
| `NINE_DARTER` | Nine-darter / Nipiler | Win a 501 leg in 9 darts |
| `AVERAGE_50` | Fifty club / 50-klubben | Average 50+ in a match started from 301 or more |
| `AVERAGE_70` | Seventy club / 70-klubben | Average 70+ in a match started from 301 or more |
| `GIANT_KILLER` | Giant killer / Kjempedreper | Win a **rated** match against someone rated at least 150 above you (ratings before the match) |
| `TOURNAMENT_WIN` | Tournament winner / Turneringsvinner | Win a tournament |
| `PLAYER_OF_MONTH` | Player of the Month / Månedens spiller | Be crowned Player of the Month |
| `PLAYER_OF_YEAR` | Player of the Year / Årets spiller | Be crowned Player of the Year |
| `CRICKET_WIN` | Cricket champ / Cricket-mester | Win a game of Cricket |
| `KILLER_WIN` | Last one standing / Sistemann igjen | Win a game of Killer |
| `CLOCK_UNDER_40` | Clockwork / Som et urverk | Win Around the Clock in under 40 darts (solo counts) |
| `DARTS_10K` | Ten thousand darts / Ti tusen piler | Throw 10,000 darts in matches and party games (progress shown) |

## Which matches count

Achievements are worked out from the darts every time they are asked for (`GET /api/users/{username}/achievements`);
nothing is stored. So:

- **Only matches that are rated, or that one of the two players set up, count.** Nobody can unlock a nine-darter for a
  friend by inventing a casual match between other people.
- **A voided match takes its achievements with it.** If the first win was in a match an admin voids after a dispute,
  `FIRST_WIN` moves to the next win (or locks again).
- Party games count for the players in them (the creator must play, admins excepted).
- Each achievement remembers *when* it was first unlocked and *in which* match or game; the cabinet links there.

The year recap lists the achievements unlocked in that year.

## Adding one

1. Add the code to `AchievementService.Code` (with a target if it is count-based) and the rule in `cabinet()`.
2. Add the code to `AchievementCode` in `frontend/src/api/types.ts`, an icon in `features/achievements/Cabinet.tsx`,
   and `achievements.<CODE>.title` / `.how` to both `nb.json` and `en.json`.
3. Add it to this table and a test to `AchievementsIT`.

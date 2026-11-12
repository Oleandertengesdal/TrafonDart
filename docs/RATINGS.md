# Ratings: Glicko-2

TrafonDart uses **Glicko-2** (Mark Glickman), implemented in `rating/Glicko2.java`. The implementation is verified against the worked example in Glickman's paper: 1500/200/0.06 against three opponents gives **1464.06 / 151.52 / 0.05999**.

## Why Glicko-2 and not Elo

A friend group plays irregularly: some people every week, others twice a year. Glicko-2 tracks three numbers per player:

| | Meaning | Start |
|---|---|---|
| **Rating** | estimated strength | 1500 |
| **RD** (rating deviation) | uncertainty: high means "we're not sure yet" | 350 |
| **Volatility** | how erratic the player's results are | 0.06 |

New and returning players have a high RD, so their rating moves quickly. Regulars have a low RD and a stable rating. A win against a well-established player counts for more than a win against a newcomer.

## How it is applied

- **One rated match = one rating period** for the two players. The score is 1 for the winner and 0 for the loser (darts has no draws).
- **Inactivity:** before each update, a player's RD grows once for every whole **week** since their last rated match, using Glickman's step 6: φ' = √(φ² + n·σ²). It is capped at 350.
- **System constant τ = 0.5.**
- **Provisional:** the UI marks a rating as provisional (`~`) while RD > 110.
- Profiles show the rating ± 2·RD (roughly a 95% interval).
- Casual (unrated) matches still count for profile statistics, but not for ratings or Player of the Month.

## Corrections

When an admin **voids** a match after a dispute, the backend **recomputes every rating from scratch**. It resets everyone to 1500/350/0.06 and replays all completed rated matches in order (`RatingService.recomputeAll`). This takes milliseconds at friend-group scale and guarantees the ratings are always exactly what the remaining history implies. Admins can also trigger it by hand: *Admin → Members → Recalculate all ratings*.

## Tuning

Change `TAU`, `PROVISIONAL_RD` or `RatingService.IDLE_PERIOD`, then run a recompute. History is rebuilt with the new parameters.

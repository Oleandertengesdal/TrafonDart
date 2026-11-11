# Game rules & statistics

Implemented in `backend/src/main/java/no/trafondart/game/` and covered by `X01EngineTest` and `CheckoutCalculatorTest`.

## Darts

| Input | Points | Notes |
|---|---|---|
| Single 1–20 | n | |
| Double 1–20 | 2n | counts as a double for in/out rules |
| Treble 1–20 | 3n | counts as a treble for Master out |
| 25 (outer bull) | 25 | a single |
| Bull | 50 | **counts as a double** (Double in/out) |
| Miss | 0 | |

## X01

- Start at **301, 501 or 701**. The first player to reach exactly **0** wins the leg.
- A match is **first to N legs** (1–11).
- The starting player alternates every leg. The first leg starts with the chosen player, or a random one.

### In-rules

| Rule | Behaviour |
|---|---|
| Straight in | every dart counts from the start |
| Double in | darts score 0 until the player hits a double (or bull). That dart counts, and so does everything after it |

### Out-rules and busts

| Rule | Legal finishing dart | Bust when the remaining score would be… |
|---|---|---|
| Straight out | any | below 0 |
| Double out | double or bull | below 0, **1**, or 0 on a non-double |
| Master out | double, treble or bull | below 0, **1**, or 0 on a single |

On a **bust** the turn ends immediately and the score goes back to what it was at the start of the turn. Darts thrown in a busted turn still count as thrown (with 0 points) in the averages.

## Checkout assistant

For the current player's remaining score, out-rule and **darts left this turn**, the app shows the best route, or nothing if there is no finish.

All routes of 1–3 darts are enumerated once at start-up, and the best one per (out-rule, darts left, score) is stored in a lookup table. Routes are ranked by:

1. **Fewest darts.**
2. **Preferred finishing double:** D20, D16, D8, D18, D12, D10, D14, D4, D6, D2 … The doubles at the top split into another double when you hit the single (D16 → 8 → D4 …). The bull is a last resort.
3. **Easy set-up darts:** T20 and T19 are preferred, big singles are cheap, small trebles and set-up doubles are expensive.
4. Bigger set-up dart first.

Examples (double out, three darts): `170 → T20 T20 Bull`, `167 → T20 T19 Bull`, `120 → T20 20 D20`, `100 → T20 D20`, `61 → T15 D8`, `50 → Bull`, `3 → 1 D1`. **Bogey numbers** (169, 168, 166, 165, 163, 162, 159) show no checkout.

## Statistics definitions

| Stat | Definition |
|---|---|
| **3-dart average** | points scored ÷ darts thrown × 3. Bust turns score 0 but their darts count. |
| **First-9 average** | the same, using only each player's first three turns (9 darts) of every leg |
| **180s / 140+ / 100+** | turns scoring exactly 180 / 140–179 / 100–139 (non-overlapping bands) |
| **Highest turn** | best single-turn score |
| **Highest checkout** | best score of a turn that finished a leg |
| **Checkout attempt** | a dart thrown while the remaining score could be finished **with that single dart** under the out-rule (double out: 2–40 even, or 50) |
| **Checkout %** | checkouts hit ÷ checkout attempts |
| **Leg win rate** | legs won ÷ legs played, across completed matches |
| **Form / streak** | last 10 results / current run of wins or losses |
| **Head-to-head** | completed matches against one opponent: record, legs, both players' averages |

### Darts nerd statistics (profile and match analysis)

These are computed by replaying the dart log (`analysis/DartAnalytics.java`).

| Stat | Definition |
|---|---|
| Treble / double / bull / miss rate | share of all darts thrown that landed in a treble, a double (incl. bullseye), the 25/bull, or missed |
| Turn-score ranges | non-bust turns in 0–19, 20–39, …, 100–139, 140–179, 180 |
| Bust rate | busted turns ÷ completed turns |
| Best leg | fewest darts used to win a leg (9 = nine-darter) |
| Darts per leg won | average darts thrown in the legs the player won |
| Hold rate | legs won when the player threw first in the leg |
| Break rate | legs won when the opponent threw first |
| Most common turn / favourite number | the turn score thrown most often / the board number hit most often |
| Favourite finishes | the darts that finished legs, e.g. D20 ×4 |
| Heatmap | hits per bed (single, double, treble, 25, bull), tinted by frequency |
| Win probability | Glicko-2 expected score from both players' ratings *before* the match |

Only **completed** matches count. Abandoned and voided matches are excluded everywhere.

## Match lifecycle

```
IN_PROGRESS ──final dart──► COMPLETED ──admin upholds a dispute──► VOIDED
     └──────scorer abandons──► ABANDONED
```

- **Who can score:** both players, whoever set the match up, and admins.
- **Rated matches** must include the person creating them (admins excepted). Anyone can set up a *casual* match for friends.
- **Abandoned** matches don't count. If one was a tournament fixture, the fixture goes back to *ready*.

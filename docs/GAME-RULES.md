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

## Party games (selskapsspill)

For darts nights with more than two people. Party games are **never rated**: they don't touch ratings, Player of the
Month or Player of the Year. They have their own statistics on the profile, their own achievements
([ACHIEVEMENTS.md](ACHIEVEMENTS.md)) and a section in the year recap. Start one from **Spill → Flere enn to?**
(`/games/new`). The normal 1 v 1 match is unchanged.

The scorer uses the same keypad as a match. Darts are entered one at a time, sent with the dart count the tablet
expects (so nothing is ever recorded twice), queued while offline, and pushed live to everyone watching. Who may score:
the players, whoever created the game, and admins.

| Game | Players | Teams |
|---|---|---|
| X01 | 3–4 | or exactly 4 as 2 v 2 |
| Cricket | 2–4 | or exactly 4 as 2 v 2 |
| Killer | 2–8 | no |
| Around the Clock | 1–8 (solo practice works) | no |

Two players for X01? That's a normal match, so the form sends you there. Teams (doubles) are chosen as A1, A2, B1, B2;
the throwing order is A1, B1, A2, B2. Partners share one score.

### X01 for a group, and doubles

Exactly the X01 rules above (start 301/501/701, straight or double in, straight/double/master out, busts), with each
player or team in turn. Legs to win 1–3; the first leg's starter rotates each leg.

### Cricket

- The numbers are **20, 19, 18, 17, 16, 15 and the bull**. A single is one mark, a double two, a treble three; the
  outer bull is one mark and the bull two. Anything else scores nothing.
- Three marks **close** a number. Marks beyond that score the number's value (25 for the bull) as **points**, but only
  while at least one opponent hasn't closed it.
- You win when you have closed everything **and** have at least as many points as everyone else.
- **Round limit** (default 20, or 10–30, or none): when every side has thrown that many rounds, the most points wins,
  then the most marks, then whoever threw first.

### Killer

- Each player gets a **different number 1–20, drawn by the server** when the game starts, and **lives** (default 3,
  1–5).
- Hitting the **double of your own number** makes you a **killer**.
- As a killer, every double of an opponent's number takes one of their lives. Doubles on numbers of players who are
  out, the bull, singles and trebles do nothing.
- A player with no lives left is out and skipped. The last one standing wins; the others are placed by how long they
  lasted.

### Around the Clock

- Hit **1, 2, 3 … 20 and then the bull** (outer or inner) in order. Only the next number counts.
- With **"doubles and trebles skip ahead"** on, a double moves you two numbers on and a treble three (never past 20).
- The first player to hit the bull wins at once. Placements follow how far round everyone got, then fewest darts.

### Statistics

Per player and game type: games played and won, darts thrown, and a "best": X01 best average, Cricket best marks per
round, Killer most lives taken in a game, Around the Clock fewest darts in a win.

## Match lifecycle

```
IN_PROGRESS ──final dart──► COMPLETED ──admin upholds a dispute──► VOIDED
     └──────scorer abandons──► ABANDONED
```

- **Who can score:** both players, whoever set the match up, and admins.
- **Challenges:** a match can also start from an accepted challenge (see the user guide). It is created only when one of the two presses *Start*, with a random starting player.
- **Rated matches** must include the person creating them (admins excepted). Anyone can set up a *casual* match for friends; casual matches don't count for ratings or Player of the Month.
- **Tournament fixtures** are started by their players or an admin. In a casual tournament the organiser can start them too.
- **Abandoned** matches don't count. If one was a tournament fixture, the fixture goes back to *ready*.

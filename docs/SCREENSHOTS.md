# Screenshots

Every screen in TrafonDart, captured from a local install filled by `scripts/seed_demo.py` (fictional players, simulated matches) with some matches moved into earlier months by `scripts/backdate-demo.sql`. Desktop shots are 1440 px wide, the tablet is 1180 × 820 in landscape, and phones are 390 × 844 at 2× resolution.

How to retake them is described at the bottom.

## Playing

| Home page | Home on a phone |
|---|---|
| ![Home](images/home.png) | <img src="images/phone-home.png" alt="Home on a phone" width="300"> |

The home page shows challenges (one waiting for an answer, one accepted and ready to start), live matches and party games in progress.

**New match:** players, who throws first, game, in/out rules, legs and whether it is rated.

![New match](images/new-match.png)

| Scoring on the tablet | Scoring on a phone |
|---|---|
| ![Tablet scorer](images/scorer-tablet.png) | <img src="images/phone-scorer.png" alt="Phone scorer" width="300"> |

**Watching live:** someone who isn't playing sees every dart as it lands.

![Spectating](images/spectate.png)

**No connection?** The darts wait on the tablet, are saved (also across a reload) and are sent when the connection returns.

![Offline](images/offline.png)

## Practice against Trafo

**Setting up:** pick Trafo's level (each one says the average it plays at and how you've done against it), who throws first, the game and the rules. Your record against Trafo is underneath.

![Playing against Trafo](images/bot-setup.png)

**Trafo's turn:** you score your own darts as usual; Trafo throws at once, and its darts appear one by one.

![Trafo throwing](images/bot-match.png)

**The record:** on the profile, apart from the real statistics: won and lost per level, streaks, the hardest level beaten and the throwing numbers from these matches.

![The record against Trafo](images/profile-trafo.png)

## Party games

**New party game:** pick the game, tap the players in throwing order (or four for two against two), set the options.

![New party game](images/new-game.png)

| Cricket on the tablet | Cricket on a phone |
|---|---|
| ![Cricket scorer](images/game-cricket.png) | <img src="images/phone-cricket.png" alt="Cricket on a phone" width="300"> |

| X01, two against two | A finished game of Killer |
|---|---|
| ![X01 doubles](images/game-x01-doubles.png) | ![Killer](images/game-killer.png) |

## TV mode

A read-only scoreboard for a big screen. It follows whatever is live, and shows the leaderboard in between.

| A match on the TV | A party game on the TV |
|---|---|
| ![TV, match](images/tv.png) | ![TV, game](images/tv-game.png) |

## After the match

**Overview** with match statistics, legs, kudos and comment threads.

![Match overview](images/match-overview.png)

| Turn by turn | Analysis | Head-to-head |
|---|---|---|
| ![Turns](images/match-turns.png) | ![Analysis](images/match-analysis.png) | ![Head-to-head](images/match-h2h.png) |

**Match history** with every finished match.

![Match history](images/history.png)

## Players

**Profile** of the reigning Player of the Month: rating arch, form, the year recap and challenge buttons, stat tiles, rating and average charts, the darts nerd corner with a dartboard heatmap, the trophy cabinet, lifetime achievements (with progress), party games, head-to-head and recent matches.

![Profile](images/profile.png)

| Another profile | Profile on a phone |
|---|---|
| ![Kari's profile](images/profile-kari.png) | <img src="images/phone-profile.png" alt="Profile on a phone" width="300"> |

**Challenge a friend** from their profile.

![Challenge](images/challenge.png)

**The year recap**, "your year in darts".

| On a computer | On a phone |
|---|---|
| ![Recap](images/recap.png) | <img src="images/phone-recap.png" alt="Recap on a phone" width="300"> |

**Rating table**, with a crown next to the reigning Player of the Month.

![Players](images/players.png)

## Player of the Month and of the Year

Last month's winners, this month's race with a points breakdown per player, the category leaders, the race for Player of the Year, the hall of fame and the rules. See [AWARDS.md](AWARDS.md).

![Awards](images/awards.png)

| Awards on a phone | Category leaders on a phone |
|---|---|
| <img src="images/phone-awards.png" alt="Awards on a phone" width="300"> | <img src="images/phone-awards-race.png" alt="Category leaders on a phone" width="300"> |

## Tournaments

| Tournament list | A knockout cup in progress |
|---|---|
| ![Tournaments](images/tournaments.png) | ![Cup](images/tournament.png) |

## Account

Settings has a **Your data** card: download everything, or delete the account.

| Settings | Report a bug |
|---|---|
| ![Settings](images/settings.png) | ![Feedback](images/feedback.png) |

| Login | Choosing a new password from an admin's link |
|---|---|
| ![Login](images/login.png) | <img src="images/phone-reset.png" alt="Reset password" width="300"> |

## Admin

| Invite codes | Members |
|---|---|
| ![Invite codes](images/admin-invites.png) | ![Members](images/admin-members.png) |

**Password link** for someone who forgot their password.

![Password link](images/admin-reset-link.png)

## English

The whole interface is available in English. The language follows the account.

![Home in English](images/en-home.png)

| Player of the Month | Profile |
|---|---|
| ![Awards in English](images/en-awards.png) | ![Profile in English](images/en-profile.png) |

| Year recap | New party game |
|---|---|
| ![Recap in English](images/en-recap.png) | ![New party game in English](images/en-new-game.png) |

## Retaking the screenshots

1. Start Postgres, the backend and `npm run dev` ([LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md)) on an **empty** database.
2. Seed and spread the matches over three months:
   ```bash
   python3 scripts/seed_demo.py --admin <you> --password '<pw>' --matches 44
   psql "postgresql://trafondart:<pw>@localhost:5432/trafondart" -f scripts/backdate-demo.sql
   python3 scripts/seed_party.py --admin <you> --password '<pw>'   # party games and challenges (1.4.0)
   ```
3. As admin: *Admin → Members → Recompute all ratings*, then *Recompute the monthly awards*.
4. Take the screenshots (any browser at the sizes above, or Playwright), then shrink them: 256-colour PNGs are about a quarter of the size and look the same on this dark palette.
   ```bash
   python3 -c "from PIL import Image; import sys; [Image.open(f).convert('RGB').quantize(256, dither=Image.Dither.FLOYDSTEINBERG).save(f, optimize=True) for f in sys.argv[1:]]" docs/images/*.png
   ```

Never take screenshots of the production app for the repository: they would show real names.

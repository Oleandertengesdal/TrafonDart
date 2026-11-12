# TrafonDart documentation

Start with the guide that matches what you want to do.

## Using the app

| Document | For | Language |
|---|---|---|
| [USER-GUIDE.md](USER-GUIDE.md) | everyone in the group: installing the app, scoring (also offline), watching, statistics, awards, tournaments, party games, challenges, TV mode, the year recap, your data | Norwegian |
| [ADMIN-GUIDE.md](ADMIN-GUIDE.md) | admins: invite codes, members, password links, disputes, recomputing | Norwegian |
| [SCREENSHOTS.md](SCREENSHOTS.md) | a tour of every screen, in Norwegian and English | English |

## Running it

| Document | Contents |
|---|---|
| [DEPLOYMENT-PLAN.md](DEPLOYMENT-PLAN.md) | the plan: phases, checklists, costs, risks, monitoring, backups, rollback, routines after launch |
| [DEPLOYMENT.md](DEPLOYMENT.md) | step by step on Neon, Render and Vercel, plus troubleshooting |
| [SECURITY.md](SECURITY.md) | threat model and how each threat is handled |
| [OPERATIONS.md](OPERATIONS.md) | error alerts with Sentry |
| [PRIVACY.md](PRIVACY.md) | what is stored about members, data download and account deletion |

## How it works

| Document | Contents |
|---|---|
| [PLAN.md](PLAN.md) | the original plan and the key decisions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | components, request flow, live updates, packages, configuration |
| [DATABASE.md](DATABASE.md) | ER diagram and table reference |
| [API.md](API.md) | REST endpoints, socket topics, error codes |
| [GAME-RULES.md](GAME-RULES.md) | X01 rules, busts, checkout routing, party games, how statistics are defined |
| [RATINGS.md](RATINGS.md) | how Glicko-2 is applied |
| [AWARDS.md](AWARDS.md) | Player of the Month (the five awards, month points), Player of the Year, the year recap |
| [ACHIEVEMENTS.md](ACHIEVEMENTS.md) | the lifetime achievements and which matches count |
| [DESIGN.md](DESIGN.md) | the "Trafon after dark" visual language |
| [I18N.md](I18N.md) | adding or changing Norwegian and English text |

## Working on it

| Document | Contents |
|---|---|
| [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md) | running and seeding locally |
| [TESTING.md](TESTING.md) | the test suite: what is covered, how to run it, how to add tests, the flaws it found |
| [../CHANGELOG.md](../CHANGELOG.md) | what changed in each version |

`scripts/check-docs.py` checks that these documents stay in step with the code (every endpoint documented, every environment variable documented, no broken links or images). CI runs it on every push.

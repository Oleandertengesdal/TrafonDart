# Local development

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 22 LTS | `node -v` |
| Docker | any recent | `docker compose version` |

On macOS: `brew install openjdk@21 maven node@22`, plus Docker Desktop or OrbStack.

## First run

```bash
cp .env.example .env          # edit POSTGRES_PASSWORD / DATABASE_PASSWORD and BOOTSTRAP_ADMIN_PASSWORD
docker compose up -d          # PostgreSQL 16 on 127.0.0.1:5432 (+ an empty trafondart_test database)

cd backend
./run-dev.sh                  # loads ../.env, profile "dev", http://localhost:8080

cd ../frontend
npm install
npm run dev                   # http://localhost:5173 – proxies /api and /ws to :8080
```

Open http://localhost:5173 and log in with `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`.

> `JWT_SECRET` may stay empty locally. The dev profile then generates a random one on every start, which logs you out on every restart. Set one (`openssl rand -base64 48`) if that gets annoying.

### Demo data

```bash
pip install requests
python3 scripts/seed_demo.py --admin <admin-username> --password '<admin-password>'
```

This creates five friends (password `demo-password-123`), 14 simulated matches with some kudos and comments, a knockout cup with one result in, and a live match in progress. The players throw dart by dart through the real API and follow the server's checkout hints. The script refuses to run against anything except localhost.

### Testing on a real tablet

Run `npm run dev -- --host` and open `http://<your-laptop-ip>:5173` on the tablet (same Wi-Fi). Add that origin to `WS_ALLOWED_ORIGINS` in `.env` if you want live updates there too.

## Tests

### Backend

```bash
cd backend
set -a; source ../.env; set +a      # so the tests can reach the Docker database
mvn verify                          # all tests
mvn verify -Punit-only              # skip the database tests
```

| Suite | What it covers |
|---|---|
| `X01EngineTest` | turns, busts (below zero, dead 1, wrong finishing dart), double in, master out, first-9, undo |
| `CheckoutCalculatorTest` | classic routes (170, 167, 120 …), bogey numbers, every route adds up and ends legally |
| `Glicko2Test` | Glickman's worked example to 2 decimals, symmetry, inactivity |
| `BracketGeneratorTest` | round-robin pairs everyone once, seeding order, byes |
| `DartAnalyticsTest` | hold/break, best leg, finishing doubles, score ranges, hit rates |
| `TrafonDartApiIntegrationTest` | real HTTP + PostgreSQL: CSRF, login, invites, admin guard, scoring, out-of-sync, undo, the live STOMP socket, ratings, stats, head-to-head, disputes, tournaments, kudos/comments/replies/likes, match breakdown, admin password reset links (lock-out lifted, sessions revoked, single use) |

The integration test wipes and re-migrates the **`trafondart_test`** database on every run. It never touches `trafondart`.

### Frontend

```bash
cd frontend
npm run i18n:check   # nb.json and en.json have identical keys, none empty
npm test             # node:test unit tests for the scripts
npm run lint         # ESLint (also forbids dangerouslySetInnerHTML)
npm run build        # type-check + production build
```

## Useful URLs

| URL | |
|---|---|
| http://localhost:5173 | the app |
| http://localhost:8080/actuator/health | backend health |
| ws://localhost:8080/ws | STOMP endpoint (needs a ticket from `POST /api/ws/ticket`) |

## Project conventions

- **Backend:** package by feature (`match`, `tournament`, `rating` …). Pure game logic (`game/`, `rating/Glicko2`, `tournament/BracketGenerator`) has no Spring dependencies and is unit-tested.
- **Database changes:** always add a new Flyway migration (`V2__description.sql`). Hibernate only *validates* the schema.
- **Errors:** throw `ApiException(ErrorCode.X)`. Add the code to `ErrorCode.java` and a translation under `errors.X` in **both** locale files.
- **User-facing text:** never hard-code it. Add it to `nb.json` and `en.json` (see [I18N.md](I18N.md)).

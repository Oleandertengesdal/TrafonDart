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

### The installable app and offline scoring

The service worker is registered in **production builds only** (in development it would serve stale code). To try installing, run `npm run build && npm run preview` and open `http://localhost:4173` (browsers allow service workers on `localhost` without HTTPS; on the tablet you need HTTPS, i.e. a deployed copy). Offline scoring works in development too: in Chrome DevTools → Network, set *Offline*, tap some darts, and set it back. The queue is in `localStorage` under `td.pending.*`. If a service worker from a preview build gets in the way later, remove it under DevTools → Application → Service workers.

### Error alerts locally

Leave `SENTRY_DSN` empty: errors (including browser errors posted to `/api/client-errors`) are then only logged. To try Sentry, put a DSN from a test project in `.env`.

## Tests

```bash
cd backend
set -a; source ../.env; set +a      # so the tests can reach the Docker database
mvn verify                          # all 359 backend tests
mvn verify -Punit-only              # skip the database tests

cd ../frontend
npm test                            # 131 unit, translation, service worker and static security tests
npm run typecheck:test && npm run lint && npm run build
```

What each suite covers, how the integration tests are set up and how to add new ones: [TESTING.md](TESTING.md).

### Documentation

```bash
python3 scripts/check-docs.py   # every endpoint in API.md, every env var documented, no broken links/images, all error codes translated
```

CI runs this too. If it fails after you add an endpoint or a setting, document it in the same pull request.

## Useful URLs

| URL | |
|---|---|
| http://localhost:5173 | the app |
| http://localhost:8080/actuator/health | backend health |
| ws://localhost:8080/ws | STOMP endpoint (needs a ticket from `POST /api/ws/ticket`) |

## Project conventions

- **Backend:** package by feature (`match`, `tournament`, `rating` …). Pure game logic (`game/`, `rating/Glicko2`, `tournament/BracketGenerator`) has no Spring dependencies and is unit-tested.
- **Database changes:** always add a new Flyway migration with the next free number (`V4__description.sql`). Hibernate only *validates* the schema.
- **Errors:** throw `ApiException(ErrorCode.X)`. Add the code to `ErrorCode.java` and a translation under `errors.X` in **both** locale files.
- **User-facing text:** never hard-code it. Add it to `nb.json` and `en.json` (see [I18N.md](I18N.md)).

## Demo data across several months

`seed_demo.py` plays every match "now", so the Player of the Month race only has the current month. To see the hall of fame and trophy cabinets locally, move some matches back in time and let the server recompute:

```bash
python3 scripts/seed_demo.py --admin <you> --password '<pw>' --matches 44
psql "postgresql://trafondart:<pw>@localhost:5432/trafondart" -f scripts/backdate-demo.sql   # 32 oldest matches → two previous months
# then, as admin: Admin → Members → "Recompute all ratings" and "Recompute the monthly awards"
```

Never run `backdate-demo.sql` against a real database: it rewrites match timestamps.

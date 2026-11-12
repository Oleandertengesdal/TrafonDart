# Operations: error alerts

So you hear about crashes before your friends do, TrafonDart can send errors to [Sentry](https://sentry.io) (the free
plan is plenty). It is off until you set `SENTRY_DSN`.

## Set it up

1. Create a free Sentry account and a project of type **Java** (one project is enough: browser errors arrive in the
   same project, tagged `source: frontend`).
2. Copy the project's **DSN** (Settings → Client Keys). It looks like `https://abc123@o12345.ingest.sentry.io/678`.
3. On Render, add the environment variable `SENTRY_DSN` with that value. Optionally set `SENTRY_ENVIRONMENT`
   (default `production`) if you also run a staging copy.
4. Redeploy. The log says `Error alerts are sent to Sentry (…)` on startup.
5. In Sentry, set up an alert rule (Alerts → Create → "A new issue is created") to get an e-mail.

The DSN is not a password, but it is still kept out of Git: a fork of this repository shouldn't report into your
project. Without `SENTRY_DSN`, unexpected errors are only written to the server log.

## What is sent

| Source | When |
|---|---|
| Backend | Any unexpected exception that would give a 500 (`GlobalExceptionHandler`) |
| Browser | A page that crashes (the "Something broke" screen), uncaught errors and rejected promises |

Browser errors are posted to our own `POST /api/client-errors`, and the backend forwards them. So there is no Sentry
script in the page and the Content-Security-Policy stays `script-src 'self'`, `connect-src 'self'`.

Each event has the error type, message, stack trace, the request path (no query string), the app version and the
environment. **Never** a user, username, IP address, cookie or request body. Before sending, the backend also scrubs
e-mail addresses, JWTs, `Bearer` tokens, `password=`/`token=`-style values, query strings and URL fragments from every
message and stack trace (`ErrorReporter.scrub`, covered by `ErrorReporterTest`).

## Limits

- At most 30 events a minute leave the server; the rest are dropped (a crash loop can't eat the free quota).
- The browser sends each distinct error once per page load and at most five, and ignores known noise
  (`ResizeObserver loop`, aborted requests).
- `POST /api/client-errors` accepts 10 reports a minute per client, with size limits on every field.
- Sending happens in the background with a 10-second timeout. If Sentry is down, nothing else is affected.

## Without Sentry

Render keeps the logs of the last days (Dashboard → your service → Logs). Search for `ERROR` or `Browser error`.

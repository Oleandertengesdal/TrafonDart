#!/usr/bin/env bash
# Take a compressed backup of the TrafonDart database into ./backups (git-ignored).
#
#   PGURL='postgresql://user:password@host/db?sslmode=require' scripts/backup-db.sh
#
# Use Neon's *direct* (non-pooled) host. The dump contains password hashes and real names:
# keep it private and never commit it. Restore with:
#   pg_restore --no-owner --dbname "<target url>" backups/<file>.dump
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${PGURL:-}" ]]; then
  echo "Set PGURL to the database connection string (see docs/DEPLOYMENT-PLAN.md#8-backups)." >&2
  exit 1
fi
command -v pg_dump >/dev/null || { echo "pg_dump not found – install the PostgreSQL client tools." >&2; exit 1; }

mkdir -p backups
chmod 700 backups
out="backups/trafondart-$(date -u +%Y-%m-%dT%H%M).dump"
umask 077
pg_dump --format=custom --no-owner --no-privileges --dbname "$PGURL" --file "$out"
echo "Backup written to $out ($(du -h "$out" | cut -f1))"

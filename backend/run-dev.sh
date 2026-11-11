#!/usr/bin/env bash
# Start the backend locally with the dev profile, reading settings from ../.env (git-ignored).
#   ./run-dev.sh
set -euo pipefail
cd "$(dirname "$0")"

if [[ -f ../.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source ../.env
  set +a
else
  echo "No .env found – copy .env.example to .env in the repo root first." >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=dev
exec mvn -q spring-boot:run

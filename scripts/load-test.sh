#!/usr/bin/env bash
set -euo pipefail
job=${1:?job UUID}; seq 1 "${2:-1000}" | xargs -P 20 -I{} curl -fsS -X POST "${BASE_URL:-http://localhost:18080}/api/jobs/$job/trigger" >/dev/null

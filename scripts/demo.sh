#!/usr/bin/env bash
set -euo pipefail
base=${BASE_URL:-http://localhost:18080}
job=$(curl -fsS -X POST -H 'Content-Type: application/json' \
  -d '{"name":"demo-1000-runs","handlerType":"sleep-and-log","payloadJson":"{}","maxAttempts":1,"concurrencyLimit":100,"enabled":true}' \
  "$base/api/jobs" | jq -r '.id')
if [[ -z "$job" || "$job" == "null" ]]; then
  echo "FAIL: no non-flaky job is available for the normal-case demo" >&2
  exit 1
fi
before=$(docker compose exec -T postgres psql -U chronos -d chronos -Atc "select count(*) from job_run where job_definition_id='$job'")
printf 'Submitting 1000 runs for job %s\n' "$job"
seq 1 1000 | xargs -P 5 -I{} curl -fsS --connect-timeout 10 -X POST "$base/api/jobs/$job/trigger" >/dev/null
target=$((before + 1000))
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  counts=$(docker compose exec -T postgres psql -U chronos -d chronos -Atc "select count(*) filter (where status='SUCCEEDED' and attempt=0), count(*) from job_run where job_definition_id='$job'")
  read -r succeeded total <<<"${counts//|/ }"
  if (( succeeded == target && total == target )); then
    printf 'PASS: exactly %s runs succeeded at attempt 0\n' "$target"
    exit 0
  fi
  sleep 2
done
echo "FAIL: timeout waiting for all 1000 submitted runs (succeeded=$succeeded total=$total target=$target)" >&2
exit 1

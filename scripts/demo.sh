#!/usr/bin/env bash
set -euo pipefail
base=${BASE_URL:-http://localhost:18080}
job=$(curl -fsS "$base/api/jobs" | jq -r '.[0].id')
before=$(docker compose exec -T postgres psql -U chronos -d chronos -Atc "select count(*) from job_run where job_definition_id='$job'")
printf 'Submitting 1000 runs for job %s\n' "$job"
seq 1 1000 | xargs -P 20 -I{} curl -fsS -X POST "$base/api/jobs/$job/trigger" >/dev/null
target=$((before + 1000))
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  counts=$(docker compose exec -T postgres psql -U chronos -d chronos -Atc "select count(*) filter (where status='SUCCEEDED' and attempt=0), count(*) from job_run where job_definition_id='$job'")
  read -r succeeded total <<<"${counts//|/ }"
  if (( succeeded >= target && total >= target )); then
    printf 'PASS: %s runs succeeded at attempt 0 (at least 1000 new runs)\n' "$succeeded"
    exit 0
  fi
  sleep 2
done
echo "FAIL: timeout waiting for all 1000 submitted runs (succeeded=$succeeded total=$total target=$target)" >&2
exit 1

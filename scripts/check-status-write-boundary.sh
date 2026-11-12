#!/usr/bin/env bash
set -euo pipefail

allowed='core/src/main/java/io/chronos/core/run/repository/JdbcRunTransitionRepository.java'
pattern='update[[:space:]]+job_run'

matches=$(grep -RInE "$pattern" --include='*.java' api common core handlers scheduler worker || true)
violations=$(printf '%s\n' "$matches" | grep -v "$allowed:" || true)

if [[ -n "$violations" ]]; then
  printf 'Illegal job_run status writes found outside the run repository:\n%s\n' "$violations" >&2
  exit 1
fi

if ! printf '%s\n' "$matches" | grep -q "$allowed:"; then
  echo 'Expected status updates in JdbcRunTransitionRepository were not found.' >&2
  exit 1
fi

controller_sql=$(grep -RInE 'JdbcTemplate|JdbcClient|"[[:space:]]*(select|insert[[:space:]]+into|update[[:space:]]|delete[[:space:]]+from)' \
  --include='*Controller.java' api/src/main/java || true)
if [[ -n "$controller_sql" ]]; then
  printf 'Repository SQL or JDBC access found in a controller:\n%s\n' "$controller_sql" >&2
  exit 1
fi

layer_violations=$(grep -RInE '^import .*\.repository\.' \
  --include='*Controller.java' api/src/main/java || true)
if [[ -n "$layer_violations" ]]; then
  printf 'Controller imports a repository directly:\n%s\n' "$layer_violations" >&2
  exit 1
fi

core_api_imports=$(grep -RInE '^import io\.chronos\.api\.' \
  --include='*.java' core/src/main/java || true)
if [[ -n "$core_api_imports" ]]; then
  printf 'Core code depends on the API module:\n%s\n' "$core_api_imports" >&2
  exit 1
fi

echo 'PASS: job_run status SQL is confined to JdbcRunTransitionRepository behind RunTransitionService.'

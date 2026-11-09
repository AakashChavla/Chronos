#!/usr/bin/env bash
set -euo pipefail

allowed='core/src/main/java/io/chronos/core/run/repository/JdbcRunTransitionRepository.java'
pattern='update[[:space:]]+job_run([[:space:]]+[[:alpha:]_]+)?[[:space:]]+set.*status[[:space:]]*='

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

echo 'PASS: job_run status SQL is confined to JdbcRunTransitionRepository behind RunTransitionService.'

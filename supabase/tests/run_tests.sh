#!/usr/bin/env bash
# TEST ONLY — runs the migrations and all tests on a throwaway local PostgreSQL.
# Usage:  PGHOST=... PGPORT=... PGUSER=postgres ./run_tests.sh
# The database "mgp_test" (or $TEST_DB) is DROPPED and re-created.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../migrations"
export TEST_DB="${TEST_DB:-mgp_test}"

psql -X -q -d postgres -c "drop database if exists $TEST_DB" -c "create database $TEST_DB"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -d "$TEST_DB")

"${PSQL[@]}" -f "$HERE/00_supabase_stubs.sql"
for pass in 1 2; do                        # run twice: migrations must be re-runnable
  echo "== migrations, pass $pass"
  for f in "$MIG"/*.sql; do
    "${PSQL[@]}" -f "$f"
  done
done

echo "== scenario test"
"${PSQL[@]}" -f "$HERE/01_scenario_test.sql" 2>&1 | sed 's/^psql:[^ ]* NOTICE:  /  /'

echo "== progression test"
"${PSQL[@]}" -f "$HERE/03_progression_test.sql" 2>&1 | sed 's/^psql:[^ ]* NOTICE:  /  /'

echo "== concurrency test"
bash "$HERE/02_concurrency.sh" 2>&1 | sed 's/^psql:[^ ]* NOTICE:  /  /'

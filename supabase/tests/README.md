# Backend tests (local only)

These files are for testing on your own computer. **Never run them on the real Supabase project.**
`00_supabase_stubs.sql` creates fake versions of Supabase-only things (auth, vault, pg_net), and the
tests create throwaway users.

## Database tests

You need a local PostgreSQL 15 or newer (`psql` on your PATH). Then run:

```bash
PGHOST=localhost PGPORT=5432 PGUSER=postgres ./supabase/tests/run_tests.sh
```

The script:

1. Drops and re-creates a database called `mgp_test` (set `TEST_DB=...` to use a different name).
2. Loads the stubs, then runs all three migrations **twice**, to prove they can be re-run safely.
3. Runs `01_scenario_test.sql`. This covers security, players, a 5-player pool with 45/27/18 payouts,
   a duel, tie-breaks, error codes, rewards, the store, push tokens and the inbox. It also checks
   that no coins are created or lost.
4. Runs `02_concurrency.sh`. In this test, 40 players join one tournament at the same moment, and
   then every full pool submits its scores at the same moment. It checks that no pool gets too many
   players and that every pool pays out exactly once.

A passing run ends with `ALL SCENARIO TESTS PASSED` and `CONCURRENCY TESTS PASSED`.

## Edge function test

`send_push_test.ts` runs `functions/send-push` with a fake Supabase, Google sign-in server and
Firebase, so nothing is really sent. You need [Deno](https://deno.com) 2. Run it from a folder
that has **no** `node_modules`:

```bash
SUPABASE_URL=http://supabase.test SUPABASE_SERVICE_ROLE_KEY=srk \
  deno run --allow-env --allow-read --allow-net --node-modules-dir=none \
  /path/to/funpixelnest-mgp/supabase/tests/send_push_test.ts
```

A passing run ends with `SEND-PUSH TESTS PASSED`.

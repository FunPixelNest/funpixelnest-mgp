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
2. Loads the stubs, then runs all four migrations **twice**, to prove they can be re-run safely.
3. Runs `01_scenario_test.sql`. This covers security, players, a 5-player pool with 45/27/18 payouts,
   a duel, tie-breaks, error codes, rewards, the store, push tokens and the inbox. It also checks
   that no coins are created or lost.
4. Runs `03_progression_test.sql`: avatars, welcome flag, level math, XP and league points on pool
   and duel completion, the one-time promotion reward, the league top list, every challenge's
   progress and claim errors, the 7-day daily bonus with the day-7 epic chest, chests, the free-chest
   cooldown, the daily spin, and that coins and gems always match their history.
5. Runs `02_concurrency.sh`. In this test, 40 players join one tournament at the same moment, and
   then every full pool submits its scores at the same moment. It checks that no pool gets too many
   players and that every pool pays out exactly once.

A passing run prints `ALL SCENARIO TESTS PASSED`, `ALL PROGRESSION TESTS PASSED` and `CONCURRENCY TESTS PASSED`.

On a machine where `postgres` uses peer login, a quick way is to create a superuser for yourself
(`sudo -u postgres createuser -s $USER`) and run with `PGHOST=/var/run/postgresql`.

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

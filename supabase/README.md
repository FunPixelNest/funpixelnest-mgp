# FunPixelNest MGP — Supabase backend

This folder holds the whole server side of the app:

| Path | What it is |
|---|---|
| `migrations/20261017000001_core.sql` | Tables, security rules (RLS) and every function the app calls |
| `migrations/20261017000002_seed.sql` | The catalog: 13 games, 65 tournaments, 4 store packs, settings |
| `migrations/20261017000003_push.sql` | When a notification row is added, the database calls the push function |
| `migrations/20261020000001_new_native_games.sql` | Adds Queen Sudoku (+ its 5 tournaments) and renames Skill Slots to Solitaire Slots |
| `migrations/20261020000002_more_pool_tournaments.sql` | Adds Rookie (10 coins) and Champion (250 coins) pools to every game (5 pool tournaments per game) |
| `functions/send-push/index.ts` | Edge Function that sends a notification to the player's phones through Firebase |
| `tests/` | Tests you run on your own computer (see `tests/README.md`). **Never run these on the real project** |

**How the game works on the server:** players pay an entry fee (in coins) to get a seat in a *pool*.
Everyone in the pool gets the same random seed, so they all play the same board. When the last
player submits a score, the database ranks everyone, pays the winners and writes one notification
per player. Each notification is then sent as a push message. The app never decides who wins.

---

## 1. Create the Supabase project

1. Sign in at <https://supabase.com> and click **New project**.
2. Pick a name (e.g. `funpixelnest-mgp`) and a region close to your players (for the US, e.g. *East US*).
3. Choose a strong **database password** and save it in your password manager.
4. Wait a minute or two until the project is ready.

## 2. Run the migrations (create the database)

Use **one** of the two ways below. Run the files **in order** (`...001`, then `...002`, then `...003`, then `...004`).
You can safely run them again later. They only add what is missing and update the catalog.

### Option A: in the browser (SQL Editor)

1. In the dashboard, open **SQL Editor** → **New query**.
2. Open `migrations/20261017000001_core.sql` on your computer, copy **all** of it, paste it in and click **Run**.
   You should see "Success. No rows returned".
3. Do the same with `20261017000002_seed.sql`, then `20261017000003_push.sql`, then `20261017000004_progression.sql`, then `20261020000001_new_native_games.sql`, then `20261020000002_more_pool_tournaments.sql`.
4. To check it worked, open **Table Editor**. You should see `games` (13 rows) and `tournaments` (65 rows).

### Option B: with the Supabase CLI

1. Install the CLI. <https://supabase.com/docs/guides/cli> explains how, or use `npx supabase ...` instead of `supabase ...`.
2. In a terminal, at the repository root:
   ```bash
   supabase login
   supabase init          # only if supabase/config.toml does not exist yet; answer "N" to the IDE questions
   supabase link --project-ref <your-project-ref>   # the ref is the "abcdefghijkl" part of https://abcdefghijkl.supabase.co
   supabase db push       # runs every file in supabase/migrations that has not run yet
   ```
   `supabase link` asks for the database password from step 1.

> Pick one way and stick to it. The CLI keeps a list of which files it has already run, but files you
> run in the SQL Editor are not on that list. If you later switch from the SQL Editor to the CLI, first run
> `supabase migration repair --status applied 20261017000001 20261017000002 20261017000003`
> so the CLI does not try to run them again (running them again would be harmless anyway).

## 3. Turn on guest (anonymous) sign-in

The app signs players in as guests first.

1. Go to **Authentication** → **Sign In / Providers**. On older dashboards this is **Authentication** → **Providers** or **Settings**.
2. Switch on **Allow anonymous sign-ins** and click **Save**.

Before launch, also consider turning on **CAPTCHA protection** (Authentication → Attack Protection).
It stops scripts from creating thousands of guest accounts.

## 4. Connect the app

1. In the dashboard, click **Connect** (top bar), or go to **Project Settings** → **API Keys** / **Data API**.
2. Copy the **Project URL** (`https://xxxx.supabase.co`) and the **anon / public** key. The newer
   "publishable" key (`sb_publishable_...`) also works.
3. In the app repository root, open `local.properties` (create it if missing; it is not committed to git) and add:
   ```properties
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=your-anon-or-publishable-key
   ```
   The anon key is safe to ship inside the app. **Never** put the `service_role` / secret key in the app.

## 5. Set up push notifications (optional at first)

Everything works without push. Players still see results in the in-app inbox (`get_notifications`).
Until you finish this section, the database trigger simply does nothing.

### 5.1 Firebase

1. Go to <https://console.firebase.google.com> → **Add project**. You can turn off Google Analytics.
2. Add your apps to the Firebase project:
   - **Android**: package name `com.funpixelnest.mgp`. Download `google-services.json` for the app.
   - **iOS**: bundle ID `com.funpixelnest.mgp`. Upload an **APNs Authentication Key** (from your Apple
     Developer account) under *Project settings → Cloud Messaging*.
   - **Web** (optional): register a web app, and create a **Web Push certificate (VAPID key)** under *Cloud Messaging*.
3. Go to **Project settings → Service accounts → Generate new private key**. This downloads a JSON file.
   Keep it secret: anyone with this file can send pushes as you.

### 5.2 Give the Edge Function the Firebase key

With the CLI (from the repository root, after `supabase link`):
```bash
supabase secrets set FCM_SERVICE_ACCOUNT="$(cat path/to/your-firebase-service-account.json)"
```
Or in the dashboard: **Edge Functions → Secrets** → add `FCM_SERVICE_ACCOUNT` and paste the **whole** JSON file as the value.

### 5.3 Deploy the function

```bash
supabase functions deploy send-push --no-verify-jwt
```
`--no-verify-jwt` is intended. The function checks the caller's key itself, so the default gateway
check isn't needed, and it would reject the newer non-JWT secret keys.

### 5.4 Tell the database where the function is (Vault)

The trigger reads two secrets from the database's encrypted **Vault**. In the **SQL Editor**, run
(replace both values):

```sql
select vault.create_secret('https://xxxx.supabase.co', 'project_url');
select vault.create_secret('<your service_role key>', 'service_role_key');
```

You can find the `service_role` key under **Project Settings → API Keys**. It may be on the
**Legacy API keys** tab. To change a value later:
`select vault.update_secret((select id from vault.secrets where name = 'project_url'), 'https://new-url');`

> **If your project has no legacy service_role key:** make up a long random password, for example
> from `openssl rand -hex 32`. Then set it in two places:
> `supabase secrets set PUSH_FUNCTION_SECRET=<it>`, and as the vault secret `service_role_key`.
> The function accepts either value.

### 5.5 Test it

1. Install the app on a phone, sign in and allow notifications. The app calls `register_push_token`.
2. In the SQL Editor, send yourself a test notification (use your player id from the `profiles` table):
   ```sql
   insert into public.notifications (player_id, title, body, data)
   values ('<your-player-uuid>', 'Hello from FunPixelNest', 'Push works!', '{"type":"test"}');
   ```
3. To see what the function answered (a short summary like `"sent":1`), run:
   `select id, status_code, content from net._http_response order by id desc limit 5;`
   You can also check **Edge Functions → send-push → Logs**.

## 6. Before launch: turn off demo purchases

The Store currently gives coin packs **for free** (`purchase_pack_demo`), so you can test without
real payments. Before you go live, turn it off in the SQL Editor:

```sql
update public.app_settings set value = 'false' where key = 'demo_purchases_enabled';
```

After that, `purchase_pack_demo` fails with `PURCHASES_DISABLED`. Re-running the seed file will
**not** switch it back on. Real purchases (Google Play / App Store billing with server-side receipt
checks) are a separate piece of work. Also remember: free Supabase projects pause after 7 days without
activity, so switch to the Pro plan before launch.

## 7. Changing the catalog

You can edit `migrations/20261017000002_seed.sql` (or the tables directly) and run it again.
For example, set `active = false` to hide a game or tournament, or change entry fees or `featured`.
A running pool always keeps the fee and prize rules it started with, so edits never change the
payout of a match that is already in progress.

---

## Contract (what the app calls)

All functions are called as **RPCs** by a **signed-in** user (a guest counts as signed in). Names of
functions, parameters and JSON keys are exact.

- **Call** them with `POST /rest/v1/rpc/<name>` and a JSON body of named parameters, e.g.
  `{"p_tournament_id": "solitaire_starter"}`. In supabase-kt this is
  `supabase.postgrest.rpc("join_tournament", buildJsonObject { put("p_tournament_id", id) })`.
- **Errors** come back as HTTP 400 with a body like
  `{"code":"P0001","message":"INSUFFICIENT_COINS",...}`. The **`message`** field is the error code.
- **Timestamps** are ISO-8601 strings with an offset, e.g. `"2026-10-17T05:37:00.123456+00:00"`.
- **Coins** are JSON integers (bigint). **Seeds are strings** (`"seed": "8127391283712398123"`),
  because 63-bit numbers lose precision as JSON numbers. Parse them with `toLong()`.
- Functions that return `bigint` return a bare number (e.g. `50`). `void` functions return an empty body.
- Any function raises `NOT_AUTHENTICATED` if there is no signed-in user. The player profile and wallet
  are created automatically the first time any function runs, but call `ensure_player` first anyway.

### Player and wallet

| RPC | Params | Returns |
|---|---|---|
| `ensure_player` | `p_display_name text` (optional, default null) | `{"player_id": uuid, "display_name": text, "coins": bigint}` |
| `set_display_name` | `p_name text` | void |
| `get_wallet` | `p_limit int` (default 50) | `{"coins": bigint, "transactions": [{"id": text, "amount": bigint, "description": text, "created_at": timestamptz}]}` newest first |

- `ensure_player`: the first call creates the profile and wallet with **1000** coins (transaction
  "Welcome bonus"). If no name is given, the name is `Player` plus the first 4 characters of the id in
  upper case (e.g. `Player3FA2`). If the player already exists, the name argument is ignored.
  Errors: `INVALID_NAME` if a name is given on first call and it isn't 2–20 characters.
- `set_display_name`: trims spaces, must be 2–20 characters, else `INVALID_NAME`. It also updates
  the name shown on all the player's leaderboards.
- Transaction descriptions: `Welcome bonus`, `Entry: <Game> <Title>`, `Entry: <Game> 1v1 <Title>`,
  `Prize: #<rank> in <Game> <Title>`, `Prize: won 1v1 <Game> <Title>`, `Daily reward · Day N`,
  `Free coins (video)`, `Mission: <title>`, `Store: <name> (demo, no charge)`.
  Entries have a negative `amount`.

### Tournaments and pools

| RPC | Params | Returns |
|---|---|---|
| `join_tournament` | `p_tournament_id text` | `{"ticket_id": uuid, "pool_id": uuid, "tournament_id": text, "game_id": text, "seed": text}` |
| `submit_score` | `p_ticket_id uuid`, `p_score bigint`, `p_duration_ms bigint` | pool object (below) |
| `get_pool` | `p_pool_id uuid` | pool object, or `null` if you are not in that pool |
| `get_my_pools` | `p_limit int` (default 50) | `[pool object, ...]`, most recently joined first |

**Pool object:**
```jsonc
{
  "id": "uuid",
  "tournament_id": "solitaire_starter",
  "seed": "8127391283712398123",
  "status": "open | full | completed",
  "entries": [
    {
      "player_id": "uuid",
      "display_name": "Alice",
      "score": 1200,           // null until submitted
      "duration_ms": 93000,    // null until submitted
      "submit_sequence": 17,   // null until submitted
      "rank": 1,               // null until the pool is completed (1 = winner)
      "prize": 45              // 0 until completed; 0 for non-winners
    }
  ]
}
```
Entries are in the order players joined. To show the leaderboard, sort them yourself: score
highest first, then duration shortest first, then submit_sequence lowest first. Once
`status` is `completed`, just use `rank`.

- `join_tournament` takes the entry fee and seats you in the oldest open pool you aren't already in,
  or opens a new pool. You can be in several pools of the same tournament at once.
  Errors: `TOURNAMENT_NOT_FOUND` (unknown, or the tournament/game is inactive) and `INSUFFICIENT_COINS`.
- `submit_score`: each ticket works **once**. Errors: `TICKET_NOT_FOUND` (unknown ticket or someone
  else's), `TICKET_ALREADY_USED`, `INVALID_SCORE` (score or duration missing or negative). A player
  who quits should submit score `0`. When the last score arrives, the pool is ranked and paid
  immediately and the returned object already has `"status": "completed"`.
- Pool modes: `pool` has 5 players and 50/30/20 prizes. `duel` is async 1v1: 2 players, the winner
  takes everything. Prize pool = `entry_fee × pool_size × (100 − platform_fee_percent) / 100`.
  For example, Starter (fee 20) pays 45 / 27 / 18, and a Quick duel (fee 20) pays 36.

**Catalog (read the tables directly; no sign-in needed):**
`games` (`id, name, category, sort_order, active, webgl_base_url, webgl_version`),
`tournaments` (`id, game_id, title, mode, entry_fee, pool_size, platform_fee_percent, prize_shares, active, featured, sort_order`),
`store_packs` (`id, name, coins, bonus_coins, price_label, sort_order, active`).
Only active rows are visible. Tournament ids are `<game>_duel_quick`, `<game>_duel_high`,
`<game>_starter`, `<game>_pro` and `<game>_elite`.

**Live leaderboard (Realtime):** subscribe to Postgres changes on `public.pool_entries` with filter
`pool_id=eq.<pool id>`, and on `public.pools` with filter `id=eq.<pool id>`. You only receive changes
for pools you are in. When an event arrives, call `get_pool` to refresh. Don't read `seed` from the
raw realtime row, because there it is a plain number and can lose precision.

### Rewards

| RPC | Params | Returns |
|---|---|---|
| `get_rewards_state` | — | see below |
| `claim_daily_reward` | — | coins granted (bigint), `0` if already claimed today |
| `claim_free_coins` | — | `50`, or `0` if today's 3 claims are used up |
| `claim_mission` | `p_mission_id text` | reward coins, or `0` if not complete, unknown or already claimed |

```json
{
  "daily": { "day": 3, "claimed_today": false, "coins_per_day": [50, 75, 100, 125, 150, 200, 500] },
  "free_coins_amount": 50,
  "free_coins_left_today": 2,
  "missions": [
    { "id": "play_3",    "title": "Play 3 tournaments",       "target": 3, "progress": 1, "reward_coins": 100, "claimed": false },
    { "id": "variety_3", "title": "Play 3 different games",   "target": 3, "progress": 1, "reward_coins": 200, "claimed": false },
    { "id": "duel_1",    "title": "Play a 1v1 duel",          "target": 1, "progress": 0, "reward_coins": 100, "claimed": false },
    { "id": "top_3",     "title": "Finish in the top 3",      "target": 1, "progress": 0, "reward_coins": 150, "claimed": false },
    { "id": "win_1",     "title": "Win a tournament or duel", "target": 1, "progress": 0, "reward_coins": 250, "claimed": false }
  ]
}
```
- Days are counted in **UTC**. `day` is the streak day you can claim today (or have just claimed,
  if `claimed_today` is true). Missing a day restarts the streak at day 1, and after day 7 it starts
  again at day 1.
- Missions are one-time and their progress counts all time. `progress` never goes above `target`.

### Store, push, inbox

| RPC | Params | Returns |
|---|---|---|
| `purchase_pack_demo` | `p_pack_id text` | coins granted (coins + bonus) |
| `register_push_token` | `p_token text`, `p_platform text` (`android` / `ios` / `web`) | void |
| `get_notifications` | `p_limit int` (default 30) | `[{"id": text, "title": text, "body": text, "data": object, "created_at": timestamptz, "read": bool}]` newest first |
| `mark_notifications_read` | — | void (marks all as read) |

- `purchase_pack_demo` errors: `PURCHASES_DISABLED` and `PACK_NOT_FOUND`. Packs: `pack_small`
  Handful 500, `pack_medium` Pouch 1200+100, `pack_large` Chest 3000+500, `pack_huge` Vault 6500+1500.
- `register_push_token` errors: `INVALID_TOKEN` (empty) and `INVALID_PLATFORM`. If the token was
  registered by another player on the same device, it moves to the current player. Call it after
  every sign-in and whenever Firebase gives the device a new token.
- Notification `data`: `{"type": "pool_result" | "duel_result", "pool_id": "...", "tournament_id": "...", "game_id": "...", "rank": n, "prize": n}`.
  In the **push** payload, every value is a **string** (FCM requires this), and `notification_id` is added.

### All error codes

`NOT_AUTHENTICATED`, `INVALID_NAME`, `TOURNAMENT_NOT_FOUND`, `INSUFFICIENT_COINS`,
`TICKET_NOT_FOUND`, `TICKET_ALREADY_USED`, `INVALID_SCORE`, `PURCHASES_DISABLED`,
`PACK_NOT_FOUND`, `INVALID_TOKEN`, `INVALID_PLATFORM`,
`INVALID_AVATAR`, `UNKNOWN_CHALLENGE`, `CHALLENGE_NOT_COMPLETE`, `ALREADY_CLAIMED`,
`UNKNOWN_CHEST`, `NOT_ENOUGH_GEMS`, `CHEST_NOT_READY`, `SPIN_NOT_READY`.

## Progression (migration `20261017000004_progression.sql`)

Avatars, XP and levels, gems, leagues, challenges, the 7-day daily bonus, chests and the lucky spin.
Full rules and JSON shapes: [`PROGRESSION_CONTRACT.md`](PROGRESSION_CONTRACT.md).
XP and league points are awarded automatically when a pool completes (trigger `pools_award_progress`).
All times are sent as remaining milliseconds (`*_in_ms`). Day = UTC date; season = ISO week (Monday 00:00 UTC).

| RPC | Returns |
|---|---|
| `get_profile()` | name, avatar, coins, gems, XP/level, trophies, welcome flag, unread count, stats |
| `set_avatar(p_avatar_id)` | – (1..15, else `INVALID_AVATAR`) |
| `ack_welcome()` | – (welcome popup shown) |
| `get_wallet(p_limit)` | now also `gems`, and `gems` per transaction |
| `get_league()` | trophies, tier, season points, time left, tier table, top 10, my rank |
| `get_challenges()` | 17 daily/weekly challenges with progress and claim state |
| `claim_challenge(p_challenge_id)` | `{coins, gems}` |
| `get_daily_bonus()` / `claim_daily_bonus()` | 7-day streak; day 7 opens an epic chest |
| `get_shop_state()` | chest prices, free-chest and spin timers, spin segments, free-coin videos |
| `open_chest(p_chest_id)` | `{coins, gems}`; `free` every 4 hours, others cost gems |
| `spin_wheel()` | `{segment, coins, gems}`; once per UTC day |

The store packs are replaced by five packs (10K–250K coins, ₹ price labels; new `store_packs.tag` column).


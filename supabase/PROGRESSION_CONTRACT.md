# Progression API contract (migration 20261017000004_progression.sql)

Adds: avatars, XP/levels, gems, leagues (trophies + weekly season), daily/weekly challenges,
7-day daily bonus with coins/gems/epic chest, chests, lucky spin, welcome popup flag, avatars in pools.
All RPCs: `security definer`, `set search_path = public`, call `public._require_uid()`,
granted to `authenticated` only (revoke from public/anon), return `jsonb`.
Errors are raised as short UPPERCASE codes like the core migration.
Times are sent as **remaining milliseconds** (`*_in_ms`, bigint, never negative) so the app
does not depend on the phone clock. "Day" = UTC date. "Week/season" = ISO week, Monday 00:00 UTC.

## Rules

- **Levels**: start at level 1; going from level L to L+1 needs `250 * L` XP.
  `level`, `level_xp` (XP into the current level) and `level_xp_needed` (= 250*level) are derived from total `xp`.
- **XP** (awarded once, when a pool becomes `completed`, to every entry): `20` for playing +
  rank bonus: pool rank 1 `+30`, rank 2 `+15`, rank 3 `+5`; duel winner `+30`.
- **Trophies / league points** (same moment): pool rank 1 `100`, rank 2 `60`, rank 3 `30`, others `10`;
  duel winner `80`, duel loser `10`. Added to lifetime `trophies` (profile) AND to this week's season points.
- **League tiers** by lifetime trophies:
  bronze 0 · silver 1000 · gold 2000 · platinum 3000 · diamond 4000 · champion 5000.
  Promotion reward, paid once per tier ever, when trophies first reach the tier's minimum:
  silver 500 coins + 10 gems · gold 1000 + 20 · platinum 2000 + 40 · diamond 4000 + 80 · champion 8000 + 150.
  (transaction description: `League promotion: Silver`). Record promotions (player, tier, created_at).
- **Gems**: `wallets.gems bigint not null default 0 check (gems >= 0)`;
  `wallet_transactions.gems bigint not null default 0` (amount stays the coin delta).
  Helper `_credit2(uid, coins, gems, description)` writes one transaction row with both deltas.
- **Award XP/trophies** with an AFTER UPDATE trigger on `pools` when status changes to `completed`
  (do not rewrite `_settle_pool`). Final ranks are on pool_entries (`rank`, `prize`) at that point —
  verify column names in the core migration.

## RPCs

### get_profile() → jsonb
```json
{ "player_id": "uuid", "display_name": "Mohit", "avatar_id": 1,
  "coins": 12450, "gems": 1250,
  "xp": 45350, "level": 18, "level_xp": 2850, "level_xp_needed": 4500,
  "trophies": 1250,
  "welcome_pending": true, "welcome_coins": 1000,
  "unread_notifications": 1,
  "stats": { "tournaments_played": 120, "matches_played": 540, "games_won": 45, "win_rate": 78,
             "coins_won": 35600, "win_streak": 15, "days_active": 125 } }
```
- matches_played = all my entries; tournaments_played = my entries in `pool`-mode tournaments;
  games_won = completed entries with rank 1; win_rate = round(games_won*100/completed entries), 0 if none;
  coins_won = sum of prizes; win_streak = consecutive rank-1 results counting back from my most recent
  completed entry; days_active = distinct UTC dates of my entries (min 1, counts the sign-up day).
- welcome_pending = profile flag `welcome_seen = false`. (The 1000 welcome coins are still granted by
  `_ensure_profile` as today; the popup only acknowledges them.)
- unread_notifications: count from the existing notifications table (use its existing "read" column).

### set_avatar(p_avatar_id int) → void      errors: INVALID_AVATAR (allowed 1..15)
### ack_welcome() → void                    sets welcome_seen = true

### get_league() → jsonb
```json
{ "trophies": 1250, "tier": "silver", "season_points": 640, "season_ends_in_ms": 553440000,
  "tiers": [ {"id":"bronze","name":"Bronze","min_trophies":0,"reward_coins":0,"reward_gems":0}, … 6 items ],
  "top": [ {"rank":1,"player_id":"…","display_name":"Rohit","avatar_id":4,"points":5620,"tier":"champion"} ],
  "me": { "rank": 12, "points": 640 } }
```
- top: up to 10 players with season_points > 0, ordered points desc, then player_id. `me.rank` null when I have 0 points.

### get_challenges() → jsonb
```json
{ "daily_resets_in_ms": 30240000, "weekly_resets_in_ms": 553440000,
  "challenges": [ { "id":"d_play_3", "period":"daily", "category":"daily", "title":"Play 3 Games",
                    "subtitle":"Play any 3 games", "game_id":null, "target":3, "progress":2,
                    "reward_coins":100, "reward_gems":0, "claimed":false } ] }
```
Definitions (progress capped at target; "today"/"this week" by the entry's join time unless noted):

| id | period | category | title | subtitle | game_id | target | coins | gems | progress |
|---|---|---|---|---|---|---|---|---|---|
| d_play_3 | daily | daily | Play 3 Games | Play any 3 games | – | 3 | 100 | 0 | my entries today |
| d_win_1 | daily | daily | Win a Tournament | Finish #1 in any tournament | – | 1 | 250 | 0 | completed rank-1 entries joined today |
| d_score_5000 | daily | daily | Score 5,000 Points | Score total 5,000 points in any game | – | 5000 | 150 | 0 | sum of my submitted scores today |
| w_bingo_3 | weekly | game | Complete 3 Bingo Flash Games | Play Bingo Flash | bingo | 3 | 200 | 0 | submitted entries this week in game |
| w_block_5 | weekly | game | Play 5 Block Puzzle Games | Play Block Puzzle | block_puzzle | 5 | 150 | 0 | same |
| w_arrows_5 | weekly | game | Play 5 Arrows Games | Play Arrows | arrows | 5 | 200 | 0 | same |
| w_tourn_3 | weekly | tournament | Play 3 Tournaments | 5-player tournaments | – | 3 | 200 | 0 | pool-mode entries this week |
| w_top3_1 | weekly | tournament | Finish in Top 3 | In a 5-player tournament | – | 1 | 250 | 0 | pool-mode completed with prize>0 |
| w_win_1 | weekly | tournament | Win 1 Tournament | Finish #1 | – | 1 | 300 | 0 | completed rank 1 (pool or duel) |
| w_lp_1000 | weekly | league | Earn 1,000 League Points | This season | – | 1000 | 200 | 0 | season points |
| w_top10 | weekly | league | Reach Top 10 | In the league leaderboard | – | 1 | 300 | 0 | 1 if my season rank ≤ 10 |
| w_promote | weekly | league | Get Promoted | Reach the next league | – | 1 | 500 | 0 | promotions recorded this week |
| m_play_10 | weekly | milestone | Play 10 Games | This week | – | 10 | 1000 | 0 | entries this week |
| m_win_3 | weekly | milestone | Win 3 Tournaments | This week | – | 3 | 0 | 20 | rank-1 completed this week |
| m_score_20000 | weekly | milestone | Score 20,000 Points | This week | – | 20000 | 1500 | 0 | sum of scores this week |
| m_challenges_5 | weekly | milestone | Complete 5 Challenges | This week | – | 5 | 0 | 15 | challenge claims this week (any id) |
| s_weekly_champ | weekly | special | Weekly Champion | Complete all tournament & league challenges | – | 6 | 500 | 50 | claimed w_tourn_3,w_top3_1,w_win_1,w_lp_1000,w_top10,w_promote this week |

Claims: table `challenge_claims(player_id, challenge_id, period_start date, claimed_at)` PK (player_id, challenge_id, period_start).

### claim_challenge(p_challenge_id text) → `{"coins":100,"gems":0}`
errors: UNKNOWN_CHALLENGE, CHALLENGE_NOT_COMPLETE, ALREADY_CLAIMED. Description `Challenge: <title>`.

### get_daily_bonus() → jsonb
```json
{ "day": 3, "claimed_today": false, "resets_in_ms": 30240000,
  "rewards": [ {"day":1,"coins":100,"gems":0,"chest":null}, {"day":2,"coins":0,"gems":5,"chest":null},
               {"day":3,"coins":250,"gems":0,"chest":null}, {"day":4,"coins":0,"gems":10,"chest":null},
               {"day":5,"coins":500,"gems":0,"chest":null}, {"day":6,"coins":0,"gems":15,"chest":null},
               {"day":7,"coins":0,"gems":0,"chest":"epic"} ] }
```
Same streak rules and table as the existing daily reward (`_daily_state`, `daily_rewards`).
### claim_daily_bonus() → `{"day":7,"coins":1240,"gems":18,"chest":"epic"}` (chest opened immediately, contents included)
errors: ALREADY_CLAIMED. Description `Daily bonus: day N`.

### get_shop_state() → jsonb
```json
{ "free_chest_ready_in_ms": 0, "spin_ready_in_ms": 0,
  "free_coins_amount": 100, "free_coins_left_today": 3,
  "chests": [ {"id":"wooden","name":"Wooden Chest","gem_cost":10,"description":"Contains coins"},
              {"id":"silver","name":"Silver Chest","gem_cost":25,"description":"Contains coins & gems"},
              {"id":"gold","name":"Gold Chest","gem_cost":60,"description":"Contains more coins & gems"},
              {"id":"diamond","name":"Diamond Chest","gem_cost":100,"description":"Best rewards!"} ],
  "spin_segments": [ {"coins":50,"gems":0}, {"coins":0,"gems":5}, {"coins":100,"gems":0}, {"coins":25,"gems":0},
                     {"coins":200,"gems":0}, {"coins":0,"gems":10}, {"coins":500,"gems":0}, {"coins":150,"gems":0} ] }
```
free_coins_* reuse the existing rewarded-video claim rules.
Chest contents (uniform random, inclusive): free 20–80 coins · wooden 50–150 coins ·
silver 150–400 coins + 1–5 gems · gold 400–1000 + 5–15 · diamond 1000–2500 + 15–40 · epic 800–2000 + 10–30.
Free chest: every 4 hours. Spin: once per UTC day; weights 20,10,18,20,12,6,4,10 (index order above).

### open_chest(p_chest_id text) → `{"coins":120,"gems":0}`
ids: free, wooden, silver, gold, diamond (epic only via daily bonus). errors: UNKNOWN_CHEST, NOT_ENOUGH_GEMS, CHEST_NOT_READY.
### spin_wheel() → `{"segment":4,"coins":200,"gems":0}`   errors: SPIN_NOT_READY

## Changed existing functions (same signatures)
- `_pool_json`: each entry also has `"avatar_id"` (from profiles, default 1).
- `get_wallet`: also returns `"gems"`, and each transaction has `"gems"`.
- Store packs (seed): replace with
  `coins_10k` 10K Coins 10000 ₹89.00 · `coins_25k` 25K 25000 ₹179.00 · `coins_50k` 50K 50000 ₹349.00 (tag Best value) ·
  `coins_100k` 100K 100000 ₹699.00 · `coins_250k` 250K 250000 ₹1,599.00 (bonus 0, deactivate old packs).

## Implementation notes (as built)
- `free_coins_amount` is **50** (3 per day): the existing rewarded-video rule is reused; the 100 above is only an example.
- `store_packs` gets a new nullable `tag` column (shown as a badge). Old packs are deactivated, not deleted.
- The daily bonus shares the streak row with the old `claim_daily_reward`; claiming either one marks today as claimed.
- `claim_daily_bonus` returns `"chest": null` on days 1–6.
- Win streak and "most recent" use `pools.completed_at`, then join order.
- Promotion also inserts an inbox notification (`data.type = "league_promotion"`, `data.tier`), which is pushed like other notifications.
- A promotion can skip tiers in one result (each crossed tier is paid once).


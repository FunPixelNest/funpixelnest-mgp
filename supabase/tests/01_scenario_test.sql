-- =============================================================================
-- TEST ONLY — scenario test for the FunPixelNest MGP backend.
-- Run after 00_supabase_stubs.sql and the three migrations (see tests/README.md).
-- Every check raises an exception on failure; the script stops at the first one.
-- Calls are made as the "authenticated" role with a fake JWT subject, exactly
-- like the app would through Supabase, so grants and RLS are exercised too.
-- =============================================================================
\set ON_ERROR_STOP 1

-- ---------------------------------------------------------------------------
-- Test helpers
-- ---------------------------------------------------------------------------
drop schema if exists t cascade;
create schema t;
grant usage on schema t to anon, authenticated;

-- Act as a signed-in user (role authenticated + JWT sub).
create function t.login(p_uid uuid) returns void language plpgsql as $$
begin
  perform set_config('role', 'authenticated', false);
  perform set_config('request.jwt.claim.sub', coalesce(p_uid::text, ''), false);
end $$;

-- Back to the superuser (bypasses RLS, used for assertions).
create function t.admin() returns void language plpgsql as $$
begin
  perform set_config('role', 'none', false);
  perform set_config('request.jwt.claim.sub', '', false);
end $$;

create function t.check(p_ok boolean, p_label text) returns void language plpgsql as $$
begin
  if p_ok is distinct from true then
    raise exception 'TEST FAILED: %', p_label;
  end if;
  raise notice 'ok  %', p_label;
end $$;

-- Run SQL and expect it to fail with the given message (error code).
create function t.expect_error(p_sql text, p_code text, p_label text) returns void language plpgsql as $$
declare v_msg text;
begin
  begin
    execute p_sql;
  exception when others then
    v_msg := sqlerrm;
  end;
  perform t.check(v_msg = p_code, p_label || ' -> ' || coalesce(v_msg, '<no error>'));
end $$;

create function t.uid(n int) returns uuid language sql immutable as $$
  select ('00000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
$$;

create function t.coins(p_uid uuid) returns bigint language sql as $$
  select coins from public.wallets where player_id = p_uid
$$;

grant execute on all functions in schema t to anon, authenticated;

-- Seven test users.
insert into auth.users (id, email)
select t.uid(n), 'user' || n || '@test' from generate_series(1, 7) n
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Security basics
-- ---------------------------------------------------------------------------
do $$
declare v jsonb;
begin
  -- Signed in but no user id -> NOT_AUTHENTICATED
  perform t.login(null);
  perform t.expect_error('select public.ensure_player(null)', 'NOT_AUTHENTICATED', 'no uid');

  -- anon role may not call RPCs at all
  perform set_config('role', 'anon', false);
  perform t.expect_error($q$select public.join_tournament('solitaire_starter')$q$,
                         'permission denied for function join_tournament', 'anon cannot call RPC');
  -- ...but can read the catalog
  perform t.check((select count(*) from public.games) = 14, 'anon reads 14 games');
  perform t.check((select count(*) from public.tournaments) = 70, 'anon reads 70 tournaments');
  perform t.check((select count(*) from public.store_packs) = 5, 'anon reads 5 packs (progression migration)');
  perform t.check((select count(*) from public.tournaments where featured) = 4, '4 featured tournaments');

  -- authenticated cannot call internal helpers or write tables directly
  perform t.login(t.uid(1));
  perform t.expect_error($q$select public._credit(t.uid(1), 999999, 'hack')$q$,
                         'permission denied for function _credit', 'helper is private');
  perform t.expect_error($q$update public.wallets set coins = 999999$q$,
                         'permission denied for table wallets', 'no direct wallet writes');
  perform t.expect_error($q$select * from public.app_settings$q$,
                         'permission denied for table app_settings', 'app_settings hidden');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 2. ensure_player / set_display_name
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; i int;
begin
  perform t.login(t.uid(1));
  v := public.ensure_player('Alice');
  perform t.check(v = jsonb_build_object('player_id', t.uid(1), 'display_name', 'Alice', 'coins', 1000),
                  'ensure_player creates Alice with 1000: ' || v::text);
  v := public.ensure_player('Zed');
  perform t.check(v ->> 'display_name' = 'Alice', 'ensure_player does not overwrite name');
  perform t.check((v ->> 'coins')::bigint = 1000, 'welcome bonus only once');

  for i in 2..7 loop
    perform t.login(t.uid(i));
    v := public.ensure_player(null);
  end loop;
  perform t.check(v ->> 'display_name' = 'Player0000', 'default name: ' || (v ->> 'display_name'));

  perform t.login(t.uid(2));
  perform t.expect_error($q$select public.set_display_name(' a ')$q$, 'INVALID_NAME', 'name too short');
  perform t.expect_error($q$select public.set_display_name(repeat('x', 21))$q$, 'INVALID_NAME', 'name too long');
  perform public.set_display_name('  Bob  ');
  perform public.ensure_player(null);
  perform t.check(public.ensure_player(null) ->> 'display_name' = 'Bob', 'rename trims');
  perform t.login(t.uid(3)); perform public.set_display_name('Cara');
  perform t.login(t.uid(4)); perform public.set_display_name('Dan');
  perform t.login(t.uid(5)); perform public.set_display_name('Eve');
  perform t.login(t.uid(6)); perform public.set_display_name('Finn');
  perform t.login(t.uid(7)); perform public.set_display_name('Gus');

  v := public.get_wallet(10);
  perform t.check(v -> 'transactions' -> 0 ->> 'description' = 'Welcome bonus', 'wallet history');
  perform t.check(jsonb_typeof(v -> 'transactions' -> 0 -> 'id') = 'string', 'transaction id is text');
  perform t.check(jsonb_typeof(v -> 'coins') = 'number', 'coins is number');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 3. Five players join solitaire_starter and play; payouts 45/27/18
-- ---------------------------------------------------------------------------
create table t.tickets (player int primary key, ticket uuid, pool uuid, seed text);
grant all on t.tickets to authenticated;

do $$
declare v jsonb; i int; v_pool uuid; v_pool2 uuid;
begin
  for i in 1..5 loop
    perform t.login(t.uid(i));
    v := public.join_tournament('solitaire_starter');
    perform t.check(v ?& array['ticket_id','pool_id','tournament_id','game_id','seed'], 'join keys');
    perform t.check(jsonb_typeof(v -> 'seed') = 'string', 'seed is a string');
    perform t.check(v ->> 'game_id' = 'solitaire' and v ->> 'tournament_id' = 'solitaire_starter', 'join ids');
    insert into t.tickets values (i, (v ->> 'ticket_id')::uuid, (v ->> 'pool_id')::uuid, v ->> 'seed');
  end loop;
  perform t.admin();

  perform t.check((select count(distinct pool) from t.tickets) = 1, 'all five in the same pool');
  perform t.check((select count(distinct seed) from t.tickets) = 1, 'all five got the same seed');
  perform t.check((select (seed::bigint) > 0 from t.tickets limit 1), 'seed positive');
  select pool into v_pool from t.tickets limit 1;
  perform t.check((select status from public.pools where id = v_pool) = 'full', 'pool is full');
  perform t.check(t.coins(t.uid(1)) = 980, 'entry fee deducted');
  perform t.check((select description from public.wallet_transactions
                    where player_id = t.uid(1) order by id desc limit 1) = 'Entry: Solitaire Starter',
                  'entry transaction text');

  -- 6th player gets a new pool; Alice joining again lands in that new pool, not the full one
  perform t.login(t.uid(6));
  v := public.join_tournament('solitaire_starter');
  v_pool2 := (v ->> 'pool_id')::uuid;
  perform t.check(v_pool2 <> v_pool, '6th player opens a new pool');
  perform t.login(t.uid(1));
  v := public.join_tournament('solitaire_starter');
  perform t.check((v ->> 'pool_id')::uuid = v_pool2, 'Alice joins second pool');
  -- Alice cannot be put in pool 2 twice: her third join opens pool 3
  v := public.join_tournament('solitaire_starter');
  perform t.check((v ->> 'pool_id')::uuid not in (v_pool, v_pool2), 'never twice in the same pool');

  -- Visibility: Finn (6) is not in pool 1
  perform t.login(t.uid(6));
  perform t.check(public.get_pool(v_pool) is null, 'non-member gets null');
  perform t.check((select count(*) from public.pools where id = v_pool) = 0, 'RLS hides pool from non-member');
  perform t.check((select count(*) from public.pool_entries where pool_id = v_pool) = 0, 'RLS hides entries from non-member');
  perform t.login(t.uid(1));
  perform t.check((select count(*) from public.pool_entries where pool_id = v_pool) = 5, 'member sees 5 entries via RLS');
  v := public.get_pool(v_pool);
  perform t.check(v ->> 'status' = 'full' and jsonb_array_length(v -> 'entries') = 5, 'get_pool for member');
  perform t.check(v -> 'entries' -> 0 ->> 'display_name' = 'Alice', 'entries ordered by joined_at');
  perform t.check(v -> 'entries' -> 0 -> 'score' = 'null'::jsonb and (v -> 'entries' -> 0 ->> 'prize')::int = 0,
                  'unsubmitted entry has null score and prize 0');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 'entries' -> 0) k)
                  = array['avatar_id','display_name','duration_ms','player_id','prize','rank','score','submit_sequence'],
                  'entry keys');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['entries','id','seed','status','tournament_id'], 'pool keys');
  perform t.check(jsonb_array_length(public.get_my_pools(50)) = 3, 'Alice has 3 pools');
  perform t.check((public.get_my_pools(50) -> 2 ->> 'id')::uuid = v_pool, 'get_my_pools newest first');

  -- Scores: Bob 300, Cara 200/1000ms, Dan 200/2000ms, Alice 100, Eve 50
  perform t.login(t.uid(1));
  perform public.submit_score((select ticket from t.tickets where player = 1), 100, 5000);
  perform t.expect_error(format('select public.submit_score(%L, 999, 1)', (select ticket from t.tickets where player = 1)),
                         'TICKET_ALREADY_USED', 'duplicate submit');
  perform t.expect_error(format('select public.submit_score(%L, 999, 1)', (select ticket from t.tickets where player = 2)),
                         'TICKET_NOT_FOUND', 'someone else''s ticket');
  perform t.expect_error(format('select public.submit_score(%L, 1, 1)', gen_random_uuid()),
                         'TICKET_NOT_FOUND', 'unknown ticket');
  perform t.login(t.uid(2));
  perform t.expect_error(format('select public.submit_score(%L, -1, 10)', (select ticket from t.tickets where player = 2)),
                         'INVALID_SCORE', 'negative score');
  perform t.expect_error(format('select public.submit_score(%L, 1, -10)', (select ticket from t.tickets where player = 2)),
                         'INVALID_SCORE', 'negative duration');
  perform public.submit_score((select ticket from t.tickets where player = 2), 300, 9000);
  perform t.login(t.uid(3));
  perform public.submit_score((select ticket from t.tickets where player = 3), 200, 1000);
  perform t.login(t.uid(4));
  v := public.submit_score((select ticket from t.tickets where player = 4), 200, 2000);
  perform t.check(v ->> 'status' = 'full', 'still full before last score');
  perform t.login(t.uid(5));
  v := public.submit_score((select ticket from t.tickets where player = 5), 50, 100);
  perform t.check(v ->> 'status' = 'completed', 'completed after last score');
  perform t.admin();

  perform t.check((select array_agg(display_name || ':' || rank || ':' || prize order by rank)
                     from public.pool_entries where pool_id = v_pool)
                  = array['Bob:1:45','Cara:2:27','Dan:3:18','Alice:4:0','Eve:5:0'], 'ranks and prizes 45/27/18');
  perform t.check((select sum(prize) from public.pool_entries where pool_id = v_pool) = 90, 'prizes sum to pool 90');
  perform t.check((select completed_at is not null from public.pools where id = v_pool), 'completed_at set');
  perform t.check(t.coins(t.uid(2)) = 1025 and t.coins(t.uid(3)) = 1007 and t.coins(t.uid(4)) = 998
                  and t.coins(t.uid(5)) = 980 and t.coins(t.uid(1)) = 940, 'wallet balances after payout');
  perform t.check(exists (select 1 from public.wallet_transactions
                           where player_id = t.uid(2) and amount = 45
                             and description = 'Prize: #1 in Solitaire Starter'), 'prize transaction text');

  -- Notifications
  perform t.check((select count(*) from public.notifications where data ->> 'pool_id' = v_pool::text) = 5,
                  'one notification per player');
  perform t.check(exists (select 1 from public.notifications where player_id = t.uid(2)
                     and title = '🏆 You placed #1 in Solitaire Starter!'
                     and body = '+45 coins added to your balance.'
                     and data = jsonb_build_object('type','pool_result','pool_id',v_pool,'tournament_id','solitaire_starter',
                                                   'game_id','solitaire','rank',1,'prize',45)),
                  'winner notification');
  perform t.check(exists (select 1 from public.notifications where player_id = t.uid(1)
                     and title = 'Results are in: Solitaire Starter'
                     and body = 'You finished #4 of 5. Play again?'), 'no-prize notification');
  -- Vault secrets not configured yet -> trigger did nothing
  perform t.check((select count(*) from net.calls) = 0, 'no push calls without vault secrets');
end $$;

-- ---------------------------------------------------------------------------
-- 4. Duel: Finn vs Gus on solitaire_duel_quick (pot 36), tie broken by submit order
-- ---------------------------------------------------------------------------
-- Configure the vault so the push trigger fires from now on.
insert into vault.secrets_plain values
  ('project_url', 'https://example.supabase.co/'), ('service_role_key', 'test-service-key');

do $$
declare v jsonb; v6 jsonb; v7 jsonb; c6 bigint; c7 bigint;
begin
  c6 := t.coins(t.uid(6)); c7 := t.coins(t.uid(7));
  perform t.login(t.uid(6)); v6 := public.join_tournament('solitaire_duel_quick');
  perform t.login(t.uid(7)); v7 := public.join_tournament('solitaire_duel_quick');
  perform t.check(v6 ->> 'pool_id' = v7 ->> 'pool_id' and v6 ->> 'seed' = v7 ->> 'seed', 'duel players share pool + seed');
  perform t.admin();
  perform t.check((select description from public.wallet_transactions where player_id = t.uid(6)
                   order by id desc limit 1) = 'Entry: Solitaire 1v1 Quick', 'duel entry text');
  perform t.check((select status from public.pools where id = (v6 ->> 'pool_id')::uuid) = 'full', 'duel full at 2');

  -- identical score and time: Finn submitted first, so Finn wins
  perform t.login(t.uid(6)); perform public.submit_score((v6 ->> 'ticket_id')::uuid, 500, 30000);
  perform t.login(t.uid(7)); v := public.submit_score((v7 ->> 'ticket_id')::uuid, 500, 30000);
  perform t.check(v ->> 'status' = 'completed', 'duel completed');
  perform t.admin();
  perform t.check(t.coins(t.uid(6)) = c6 - 20 + 36 and t.coins(t.uid(7)) = c7 - 20, 'duel winner gets 36');
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(6)
                           and amount = 36 and description = 'Prize: won 1v1 Solitaire Quick'), 'duel prize text');
  perform t.check(exists (select 1 from public.notifications where player_id = t.uid(6)
                     and title = '🏆 You won your Solitaire duel!' and body = 'You beat Gus. +36 coins added.'
                     and data ->> 'type' = 'duel_result' and (data ->> 'rank')::int = 1), 'duel winner notification');
  perform t.check(exists (select 1 from public.notifications where player_id = t.uid(7)
                     and title = 'Finn won your Solitaire duel' and body = 'Close one! Tap to play a rematch.'
                     and (data ->> 'prize')::int = 0), 'duel loser notification');
  -- push trigger fired once per notification with the right URL, body and key
  perform t.check((select count(*) from net.calls) = 2, 'two push calls');
  perform t.check((select bool_and(url = 'https://example.supabase.co/functions/v1/send-push'
                                   and headers ->> 'Authorization' = 'Bearer test-service-key'
                                   and body ? 'notification_id') from net.calls), 'push call shape');
end $$;

-- ---------------------------------------------------------------------------
-- 5. Tie-breaks in a 5-player pool: score desc, duration asc, submit order asc
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; tk uuid[]; i int; v_pool uuid;
  scores bigint[] := array[100, 100, 100, 100, 90];
  durs   bigint[] := array[5000, 4000, 4000, 4000, 1];
begin
  -- join order 1..5, submit order 5,4,3,2,1
  for i in 1..5 loop
    perform t.login(t.uid(i));
    v := public.join_tournament('block_puzzle_starter');
    tk[i] := (v ->> 'ticket_id')::uuid; v_pool := (v ->> 'pool_id')::uuid;
  end loop;
  for i in reverse 5..1 loop
    perform t.login(t.uid(i));
    perform public.submit_score(tk[i], scores[i], durs[i]);
  end loop;
  perform t.admin();
  -- 4 then 3 then 2 (same score/time, submitted in that order), then 1 (slower), then 5 (lower score)
  perform t.check((select array_agg(display_name order by rank) from public.pool_entries where pool_id = v_pool)
                  = array['Dan','Cara','Bob','Alice','Eve'], 'tie-break order');
end $$;

-- ---------------------------------------------------------------------------
-- 6. Errors: insufficient coins, unknown tournament
-- ---------------------------------------------------------------------------
do $$
declare c bigint; n bigint;
begin
  perform t.admin();
  -- give Gus exactly 499 coins
  update public.wallets set coins = 499 where player_id = t.uid(7);
  n := (select count(*) from public.pool_entries where player_id = t.uid(7));
  perform t.login(t.uid(7));
  perform t.expect_error($q$select public.join_tournament('solitaire_elite')$q$, 'INSUFFICIENT_COINS', 'insufficient coins');
  perform t.expect_error($q$select public.join_tournament('nope')$q$, 'TOURNAMENT_NOT_FOUND', 'unknown tournament');
  perform t.admin();
  perform t.check(t.coins(t.uid(7)) = 499, 'balance unchanged after failed join');
  perform t.check((select count(*) from public.pool_entries where player_id = t.uid(7)) = n, 'no seat after failed join');
  update public.tournaments set active = false where id = 'catdoku_pro';
  perform t.login(t.uid(7));
  perform t.expect_error($q$select public.join_tournament('catdoku_pro')$q$, 'TOURNAMENT_NOT_FOUND', 'inactive tournament');
  perform t.admin();
  update public.tournaments set active = true where id = 'catdoku_pro';
end $$;

-- ---------------------------------------------------------------------------
-- 7. Rewards: daily streak, free coins, missions
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; c bigint; today date := (now() at time zone 'utc')::date;
begin
  perform t.login(t.uid(5));
  v := public.get_rewards_state();
  perform t.check(v -> 'daily' = '{"day":1,"claimed_today":false,"coins_per_day":[50,75,100,125,150,200,500]}'::jsonb,
                  'fresh daily state: ' || (v -> 'daily')::text);
  perform t.check((v ->> 'free_coins_amount')::int = 50 and (v ->> 'free_coins_left_today')::int = 3, 'free coins state');
  perform t.check((select array_agg(m ->> 'id') from jsonb_array_elements(v -> 'missions') m)
                  = array['play_3','variety_3','duel_1','top_3','win_1'], 'mission order');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 'missions' -> 0) k)
                  = array['claimed','id','progress','reward_coins','target','title'], 'mission keys');

  c := t.coins(t.uid(5));
  perform t.check(public.claim_daily_reward() = 50, 'daily day 1 = 50');
  perform t.check(public.claim_daily_reward() = 0, 'daily twice = 0');
  v := public.get_rewards_state();
  perform t.check((v -> 'daily' ->> 'day')::int = 1 and (v -> 'daily' ->> 'claimed_today')::boolean, 'claimed today');
  perform t.admin();
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(5)
                          and description = 'Daily reward · Day 1' and amount = 50), 'daily tx text');
  -- pretend the claim was yesterday -> day 2 (75)
  update public.daily_rewards set last_claim_date = today - 1 where player_id = t.uid(5);
  perform t.login(t.uid(5));
  perform t.check((public.get_rewards_state() -> 'daily' ->> 'day')::int = 2, 'streak continues to day 2');
  perform t.check(public.claim_daily_reward() = 75, 'day 2 = 75');
  -- day 7 yesterday -> wraps to day 1
  perform t.admin();
  update public.daily_rewards set streak_day = 7, last_claim_date = today - 1 where player_id = t.uid(5);
  perform t.login(t.uid(5));
  perform t.check((public.get_rewards_state() -> 'daily' ->> 'day')::int = 1, 'after day 7 back to day 1');
  -- day 6 yesterday -> day 7 = 500
  perform t.admin();
  update public.daily_rewards set streak_day = 6, last_claim_date = today - 1 where player_id = t.uid(5);
  perform t.login(t.uid(5));
  perform t.check(public.claim_daily_reward() = 500, 'day 7 = 500');
  -- missed a day -> day 1
  perform t.admin();
  update public.daily_rewards set streak_day = 3, last_claim_date = today - 2 where player_id = t.uid(5);
  perform t.login(t.uid(5));
  perform t.check((public.get_rewards_state() -> 'daily' ->> 'day')::int = 1, 'gap resets to day 1');

  -- free coins: 3 per day
  perform t.check(public.claim_free_coins() = 50, 'free 1');
  perform t.check(public.claim_free_coins() = 50, 'free 2');
  perform t.check(public.claim_free_coins() = 50, 'free 3');
  perform t.check(public.claim_free_coins() = 0, 'free 4 = 0');
  perform t.check((public.get_rewards_state() ->> 'free_coins_left_today')::int = 0, 'none left');
  perform t.admin();
  perform t.check(t.coins(t.uid(5)) = c + 50 + 75 + 500 + 150, 'reward coins credited');
  perform t.check((select count(*) from public.wallet_transactions where player_id = t.uid(5)
                   and description = 'Free coins (video)') = 3, 'free coins tx text');

  -- missions. Alice (1): 4 entries (3 solitaire_starter + 1 block_puzzle) in 2 games, no duel, no prize.
  perform t.login(t.uid(1));
  v := public.get_rewards_state() -> 'missions';
  perform t.check((select array_agg((m ->> 'progress')::int) from jsonb_array_elements(v) m)
                  = array[3, 2, 0, 0, 0], 'Alice mission progress (capped): ' || v::text);
  perform t.check(public.claim_mission('variety_3') = 0, 'incomplete mission = 0');
  perform t.check(public.claim_mission('nope') = 0, 'unknown mission = 0');
  perform t.check(public.claim_mission('play_3') = 100, 'play_3 = 100');
  perform t.check(public.claim_mission('play_3') = 0, 'play_3 twice = 0');
  perform t.check((public.get_rewards_state() -> 'missions' -> 0 ->> 'claimed')::boolean, 'play_3 claimed flag');
  -- Bob (2): won pool 1 -> top_3 and win_1
  perform t.login(t.uid(2));
  perform t.check(public.claim_mission('win_1') = 250, 'win_1 = 250');
  perform t.check(public.claim_mission('top_3') = 150, 'top_3 = 150');
  -- Finn (6): won the duel -> duel_1 and win_1, but duel prize does not count for top_3
  perform t.login(t.uid(6));
  v := public.get_rewards_state() -> 'missions';
  perform t.check((select array_agg((m ->> 'progress')::int) from jsonb_array_elements(v) m)
                  = array[2, 1, 1, 0, 1], 'Finn mission progress: ' || v::text);
  perform t.check(public.claim_mission('duel_1') = 100, 'duel_1 = 100');
  perform t.admin();
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(6)
                          and description = 'Mission: Play a 1v1 duel'), 'mission tx text');
end $$;

-- ---------------------------------------------------------------------------
-- 8. Store (demo), push tokens, notifications inbox
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; c bigint;
begin
  c := t.coins(t.uid(3));
  perform t.login(t.uid(3));
  perform t.check(public.purchase_pack_demo('coins_25k') = 25000, 'demo pack = 25000');
  perform t.expect_error($q$select public.purchase_pack_demo('pack_nope')$q$, 'PACK_NOT_FOUND', 'unknown pack');
  perform t.admin();
  perform t.check(t.coins(t.uid(3)) = c + 25000, 'pack credited');
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(3)
                          and description = 'Store: 25K Coins (demo, no charge)'), 'store tx text');
  update public.app_settings set value = 'false' where key = 'demo_purchases_enabled';
  perform t.login(t.uid(3));
  perform t.expect_error($q$select public.purchase_pack_demo('coins_10k')$q$, 'PURCHASES_DISABLED', 'demo disabled');
  perform t.admin();
  update public.app_settings set value = 'true' where key = 'demo_purchases_enabled';

  -- push tokens: register, then the same device signs in as someone else
  perform t.login(t.uid(3));
  perform public.register_push_token('tok-abc', 'android');
  perform public.register_push_token('tok-abc', 'android');
  perform t.expect_error($q$select public.register_push_token('tok-x', 'windows')$q$, 'INVALID_PLATFORM', 'bad platform');
  perform t.login(t.uid(4));
  perform public.register_push_token('tok-abc', 'ios');
  perform t.admin();
  perform t.check((select player_id = t.uid(4) and platform = 'ios' from public.push_tokens where token = 'tok-abc'),
                  'token reassigned to current player');

  -- inbox
  perform t.login(t.uid(2));
  v := public.get_notifications(30);
  perform t.check(jsonb_array_length(v) = 2, 'Bob has 2 notifications');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 0) k)
                  = array['body','created_at','data','id','read','title'], 'notification keys');
  perform t.check(jsonb_typeof(v -> 0 -> 'id') = 'string' and (v -> 0 ->> 'read')::boolean = false, 'unread, id text');
  perform t.check((select count(*) from public.notifications) = 2, 'RLS: only own notifications visible');
  perform public.mark_notifications_read();
  perform t.check((select bool_and((n ->> 'read')::boolean) from jsonb_array_elements(public.get_notifications(30)) n),
                  'all read');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 9. Global invariant: no coins created or lost by tournaments
--    sum(prizes) = sum(fees) * (100 - platform fee) / 100 for each completed pool
-- ---------------------------------------------------------------------------
do $$
begin
  perform t.check(not exists (
    select 1 from public.pools p
     where p.status = 'completed'
       and (select sum(prize) from public.pool_entries e where e.pool_id = p.id)
           <> p.entry_fee * p.pool_size * (100 - p.platform_fee_percent) / 100), 'prize conservation');
  perform t.check(not exists (
    select 1 from public.wallets w
     where w.coins <> (select sum(amount) from public.wallet_transactions x where x.player_id = w.player_id)
       and w.player_id <> t.uid(7)),   -- Gus's balance was edited by hand in section 6
    'wallet = sum of its transactions');
  raise notice 'ALL SCENARIO TESTS PASSED';
end $$;

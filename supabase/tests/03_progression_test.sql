-- =============================================================================
-- TEST ONLY — progression test (migration 20261017000004_progression.sql).
-- Runs after 01_scenario_test.sql (reuses its "t" helper schema).
-- Users: A=201, B=202, C=203, D=204, E=205, F=206. Tournaments: rugby_rumble_*.
-- Every check raises an exception on failure; the script stops at the first one.
-- =============================================================================
\set ON_ERROR_STOP 1

create or replace function t.gems(p_uid uuid) returns bigint language sql as $$
  select gems from public.wallets where player_id = p_uid
$$;

-- Join as player n; returns the ticket.
create or replace function t.join(n int, p_tournament text) returns uuid language plpgsql as $$
declare v jsonb;
begin
  perform t.login(t.uid(n));
  v := public.join_tournament(p_tournament);
  perform t.admin();
  return (v ->> 'ticket_id')::uuid;
end $$;

create or replace function t.submit(n int, p_ticket uuid, p_score bigint) returns jsonb language plpgsql as $$
declare v jsonb;
begin
  perform t.login(t.uid(n));
  v := public.submit_score(p_ticket, p_score, 60000);
  perform t.admin();
  return v;
end $$;

create or replace function t.challenge(p jsonb, p_id text) returns jsonb language sql immutable as $$
  select c from jsonb_array_elements(p -> 'challenges') c where c ->> 'id' = p_id
$$;

grant execute on all functions in schema t to anon, authenticated;

insert into auth.users (id, email)
select t.uid(n), 'prog' || n || '@test' from generate_series(201, 206) n
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Security
-- ---------------------------------------------------------------------------
do $$
begin
  perform set_config('role', 'anon', false);
  perform t.expect_error('select public.get_profile()', 'permission denied for function get_profile', 'anon: get_profile');
  perform t.expect_error('select public.get_league()', 'permission denied for function get_league', 'anon: get_league');
  perform t.expect_error('select public.get_challenges()', 'permission denied for function get_challenges', 'anon: get_challenges');
  perform t.expect_error($q$select public.open_chest('free')$q$, 'permission denied for function open_chest', 'anon: open_chest');
  perform t.expect_error('select public.spin_wheel()', 'permission denied for function spin_wheel', 'anon: spin_wheel');
  perform t.expect_error('select public.claim_daily_bonus()', 'permission denied for function claim_daily_bonus', 'anon: claim_daily_bonus');
  perform t.check((select count(*) from public.store_packs) = 5, 'anon reads 5 packs');
  perform t.check((select tag from public.store_packs where id = 'coins_50k') = 'Best value', '50K tag');
  perform t.check((select price_label from public.store_packs where id = 'coins_250k') = '₹1,599.00', '250K price');

  perform t.login(null);
  perform t.expect_error('select public.get_profile()', 'NOT_AUTHENTICATED', 'no uid: get_profile');

  perform t.login(t.uid(201));
  perform t.expect_error($q$select public._credit2(t.uid(201), 0, 999, 'hack')$q$,
                         'permission denied for function _credit2', '_credit2 private');
  perform t.expect_error($q$select public._roll_chest('diamond')$q$,
                         'permission denied for function _roll_chest', '_roll_chest private');
  perform t.expect_error($q$update public.wallets set gems = 999$q$,
                         'permission denied for table wallets', 'no direct gem writes');
  perform t.expect_error($q$insert into public.challenge_claims values (t.uid(201), 'd_play_3', current_date)$q$,
                         'permission denied for table challenge_claims', 'no direct claim writes');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 2. Level math
-- ---------------------------------------------------------------------------
do $$
declare r record;
begin
  select * into r from public._level_info(0);     perform t.check(r.level = 1 and r.level_xp = 0 and r.level_xp_needed = 250, 'xp 0 -> L1 0/250');
  select * into r from public._level_info(249);   perform t.check(r.level = 1 and r.level_xp = 249, 'xp 249 -> L1');
  select * into r from public._level_info(250);   perform t.check(r.level = 2 and r.level_xp = 0 and r.level_xp_needed = 500, 'xp 250 -> L2 0/500');
  select * into r from public._level_info(750);   perform t.check(r.level = 3 and r.level_xp = 0 and r.level_xp_needed = 750, 'xp 750 -> L3');
  select * into r from public._level_info(41100); perform t.check(r.level = 18 and r.level_xp = 2850 and r.level_xp_needed = 4500, 'xp 41100 -> L18 2850/4500');
end $$;

-- ---------------------------------------------------------------------------
-- 3. New player profile, welcome, avatar
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; n int;
begin
  for n in 201..206 loop
    perform t.login(t.uid(n));
    perform public.ensure_player(chr(64 + n - 200) || 'player');   -- Aplayer, Bplayer, ...
  end loop;

  perform t.login(t.uid(201));
  v := public.get_profile();
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['avatar_id','coins','display_name','gems','level','level_xp','level_xp_needed','player_id',
                          'stats','trophies','unread_notifications','welcome_coins','welcome_pending','xp'], 'profile keys');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 'stats') k)
                  = array['coins_won','days_active','games_won','matches_played','tournaments_played','win_rate','win_streak'],
                  'stats keys');
  perform t.check((v ->> 'coins')::int = 1000 and (v ->> 'gems')::int = 0, 'new: 1000 coins, 0 gems');
  perform t.check((v ->> 'level')::int = 1 and (v ->> 'level_xp')::int = 0 and (v ->> 'level_xp_needed')::int = 250, 'new: level 1');
  perform t.check((v ->> 'avatar_id')::int = 1 and (v ->> 'trophies')::int = 0, 'new: avatar 1, 0 trophies');
  perform t.check((v ->> 'welcome_pending')::boolean and (v ->> 'welcome_coins')::int = 1000, 'welcome pending');
  perform t.check((v -> 'stats' ->> 'matches_played')::int = 0 and (v -> 'stats' ->> 'days_active')::int = 1
                  and (v -> 'stats' ->> 'win_rate')::int = 0, 'new: stats zero, 1 day active');

  perform public.ack_welcome();
  perform t.check(not (public.get_profile() ->> 'welcome_pending')::boolean, 'ack_welcome clears flag');
  perform public.ack_welcome();   -- idempotent

  perform public.set_avatar(7);
  perform t.check((public.get_profile() ->> 'avatar_id')::int = 7, 'set_avatar 7');
  perform t.expect_error('select public.set_avatar(0)', 'INVALID_AVATAR', 'avatar 0');
  perform t.expect_error('select public.set_avatar(16)', 'INVALID_AVATAR', 'avatar 16');
  perform t.expect_error('select public.set_avatar(null)', 'INVALID_AVATAR', 'avatar null');
  perform public.set_avatar(15);
  perform public.set_avatar(7);
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 4. XP / trophies / season points when a pool and a duel complete
-- ---------------------------------------------------------------------------
create table t.tix (name text primary key, ticket uuid);

do $$
declare v jsonb; n int; e jsonb;
begin
  -- 5-player pool: A 500 > B 400 > C 300 > D 200 > E 100
  for n in 201..205 loop
    insert into t.tix values ('starter_' || n, t.join(n, 'rugby_rumble_starter'));
  end loop;
  perform t.check((select xp from public.profiles where id = t.uid(201)) = 0, 'no XP before playing');
  for n in 201..205 loop
    v := t.submit(n, (select ticket from t.tix where name = 'starter_' || n), (206 - n) * 100);
  end loop;
  perform t.check(v ->> 'status' = 'completed', 'starter pool completed');
  perform t.check((select bool_and(e2 ? 'avatar_id') from jsonb_array_elements(v -> 'entries') e2), 'pool json has avatar_id');
  select e2 into e from jsonb_array_elements(v -> 'entries') e2 where e2 ->> 'player_id' = t.uid(201)::text;
  perform t.check((e ->> 'avatar_id')::int = 7, 'A avatar_id 7 in pool');
  select e2 into e from jsonb_array_elements(v -> 'entries') e2 where e2 ->> 'player_id' = t.uid(202)::text;
  perform t.check((e ->> 'avatar_id')::int = 1, 'B default avatar 1 in pool');

  perform t.check((select array_agg(xp order by id) from public.profiles where id between t.uid(201) and t.uid(205))
                  = array[75,60,50,45,45]::bigint[], 'pool XP: 25 per game + 50/35/25/20/20 placement');
  perform t.check((select array_agg(trophies order by id) from public.profiles where id between t.uid(201) and t.uid(205))
                  = array[100,60,30,10,10]::bigint[], 'pool trophies 100/60/30/10/10');
  perform t.check((select array_agg(points order by player_id) from public.league_season_points
                    where player_id between t.uid(201) and t.uid(205) and season_start = public._season_start())
                  = array[100,60,30,10,10]::bigint[], 'season points = trophies');

  -- Re-running the update on a completed pool must not award again.
  update public.pools set status = 'completed' where tournament_id = 'rugby_rumble_starter';
  perform t.check((select xp from public.profiles where id = t.uid(201)) = 75, 'trigger fires only on transition');

  -- Duel: B (900) beats A (100)
  insert into t.tix values ('duel_201', t.join(201, 'rugby_rumble_duel_quick'));
  insert into t.tix values ('duel_202', t.join(202, 'rugby_rumble_duel_quick'));
  perform t.submit(201, (select ticket from t.tix where name = 'duel_201'), 100);
  v := t.submit(202, (select ticket from t.tix where name = 'duel_202'), 900);
  perform t.check(v ->> 'status' = 'completed', 'duel completed');
  perform t.check((select xp from public.profiles where id = t.uid(201)) = 120
              and (select xp from public.profiles where id = t.uid(202)) = 135, 'duel XP: +25 each, loser +20, winner +50');
  perform t.check((select trophies from public.profiles where id = t.uid(201)) = 110
              and (select trophies from public.profiles where id = t.uid(202)) = 140, 'duel trophies: loser +10, winner +80');

  -- Profile stats
  perform t.login(t.uid(201));
  v := public.get_profile();
  perform t.check((v -> 'stats' ->> 'matches_played')::int = 2 and (v -> 'stats' ->> 'tournaments_played')::int = 1,
                  'A: 2 matches, 1 tournament');
  perform t.check((v -> 'stats' ->> 'games_won')::int = 1 and (v -> 'stats' ->> 'win_rate')::int = 50, 'A: 1 win, 50%');
  perform t.check((v -> 'stats' ->> 'coins_won')::int = 45, 'A: coins won 45 (90 pool * 50%)');
  perform t.check((v -> 'stats' ->> 'win_streak')::int = 0, 'A: streak broken by duel loss');
  perform t.check((v ->> 'xp')::int = 120 and (v ->> 'level')::int = 1 and (v ->> 'level_xp')::int = 120, 'A: level 1, 120/250');
  perform t.login(t.uid(202));
  v := public.get_profile();
  perform t.check((v -> 'stats' ->> 'win_streak')::int = 1 and (v -> 'stats' ->> 'win_rate')::int = 50, 'B: streak 1, 50%');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 5. League: tiers, promotion reward once, top list
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; n int; c_coins bigint; c_gems bigint;
begin
  perform t.login(t.uid(201));
  v := public.get_league();
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['me','season_ends_in_ms','season_points','tier','tiers','top','trophies'], 'league keys');
  perform t.check(jsonb_array_length(v -> 'tiers') = 6 and v -> 'tiers' -> 1 ->> 'id' = 'silver'
                  and (v -> 'tiers' -> 1 ->> 'min_trophies')::int = 1000 and (v -> 'tiers' -> 5 ->> 'reward_gems')::int = 150,
                  'tier table');
  perform t.check(v ->> 'tier' = 'bronze' and (v ->> 'trophies')::int = 110 and (v ->> 'season_points')::int = 110, 'A bronze 110');
  perform t.check((v ->> 'season_ends_in_ms')::bigint between 1 and 7 * 86400000, 'season ends within a week');
  -- (Players from 01_scenario_test also have season points, so check relative order.)
  perform t.check((select bool_and((a ->> 'points')::int >= (b ->> 'points')::int and (b ->> 'rank')::int = (a ->> 'rank')::int + 1)
                     from jsonb_array_elements(v -> 'top') with ordinality x(a, i)
                     join jsonb_array_elements(v -> 'top') with ordinality y(b, j) on j = i + 1), 'top ordered by points');
  perform t.check(exists (select 1 from jsonb_array_elements(v -> 'top') r
                           where r ->> 'player_id' = t.uid(201)::text and (r ->> 'points')::int = 110
                             and (r ->> 'avatar_id')::int = 7 and r ->> 'display_name' = 'Aplayer' and r ->> 'tier' = 'bronze'),
                  'A in top with points, avatar, name, tier');
  perform t.admin();
  perform t.check((v -> 'me' ->> 'points')::int = 110 and (v -> 'me' ->> 'rank')::int =
                  1 + (select count(*) from public.league_season_points s
                        where s.season_start = public._season_start()
                          and (s.points > 110 or (s.points = 110 and s.player_id < t.uid(201)))), 'A rank consistent');
  perform t.login(t.uid(201));
  perform t.check((select (r ->> 'rank')::int from jsonb_array_elements(v -> 'top') r
                    where r ->> 'player_id' = t.uid(201)::text) = (v -> 'me' ->> 'rank')::int, 'me.rank = rank in top');
  perform t.check(jsonb_array_length(v -> 'top') <= 10, 'top at most 10');
  perform t.login(t.uid(206));
  v := public.get_league();
  perform t.check(v -> 'me' -> 'rank' = 'null'::jsonb and (v -> 'me' ->> 'points')::int = 0, 'F: no rank');
  perform t.admin();

  -- C at 990 trophies, wins a Pro pool (+100) -> Silver: +500 coins +10 gems, once.
  update public.profiles set trophies = 990 where id = t.uid(203);
  c_coins := t.coins(t.uid(203)); c_gems := t.gems(t.uid(203));
  for n in 201..205 loop
    insert into t.tix values ('pro1_' || n, t.join(n, 'rugby_rumble_pro'));
  end loop;
  for n in 201..205 loop
    v := t.submit(n, (select ticket from t.tix where name = 'pro1_' || n),
                  case n when 203 then 1000 when 201 then 50 else 250 - n end);
  end loop;
  perform t.check(v ->> 'status' = 'completed', 'pro pool 1 completed');
  -- Pro pool: 5*100*90% = 450; C 1st = 225.
  perform t.check(t.coins(t.uid(203)) = c_coins - 100 + 225 + 500, 'C: fee, prize, +500 promotion coins');
  perform t.check(t.gems(t.uid(203)) = c_gems + 10, 'C: +10 promotion gems');
  perform t.check((select count(*) from public.league_promotions where player_id = t.uid(203)) = 1, 'one promotion row');
  perform t.check(exists (select 1 from public.notifications where player_id = t.uid(203)
                           and data ->> 'type' = 'league_promotion' and data ->> 'tier' = 'silver'), 'promotion notification');

  perform t.login(t.uid(203));
  v := public.get_profile();
  perform t.check((v ->> 'trophies')::int = 1090 and (v ->> 'gems')::int = 10, 'C profile 1090 trophies, 10 gems');
  perform t.check((v ->> 'unread_notifications')::int >= 3, 'C unread notifications counted');
  perform t.check(public.get_league() ->> 'tier' = 'silver', 'C is silver');
  perform t.admin();

  -- Back under 1000 and over it again: no second reward.
  update public.profiles set trophies = 995 where id = t.uid(203);
  c_coins := t.coins(t.uid(203));
  for n in 201..205 loop
    insert into t.tix values ('pro2_' || n, t.join(n, 'rugby_rumble_pro'));
  end loop;
  for n in 201..205 loop
    v := t.submit(n, (select ticket from t.tix where name = 'pro2_' || n),
                  case n when 203 then 1000 when 201 then 50 else 250 - n end);
  end loop;
  perform t.check(t.coins(t.uid(203)) = c_coins - 100 + 225, 'C: no second promotion reward');
  perform t.check(t.gems(t.uid(203)) = c_gems + 10, 'C: still +10 gems only');
  perform t.check((select count(*) from public.wallet_transactions
                    where player_id = t.uid(203) and description = 'League promotion: Silver') = 1, 'one promotion tx');
end $$;

-- ---------------------------------------------------------------------------
-- 6. Challenges
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; c jsonb; a_coins bigint; a_gems bigint;
begin
  perform t.login(t.uid(201));
  v := public.get_challenges();
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['challenges','daily_resets_in_ms','weekly_resets_in_ms'], 'challenges keys');
  perform t.check(jsonb_array_length(v -> 'challenges') = 17, '17 challenges');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 'challenges' -> 0) k)
                  = array['category','claimed','game_id','id','period','progress','reward_coins','reward_gems',
                          'subtitle','target','title'], 'challenge keys');
  perform t.check((v ->> 'daily_resets_in_ms')::bigint between 1 and 86400000, 'daily reset < 1 day');
  perform t.check((v ->> 'weekly_resets_in_ms')::bigint between 1 and 7 * 86400000, 'weekly reset < 1 week');
  perform t.check((select count(*) from jsonb_array_elements(v -> 'challenges') x where x ->> 'period' = 'daily') = 3, '3 daily');

  -- A today: starter (500), duel (100), pro1 (50), pro2 (50) -> 4 entries, 700 points, 1 win
  perform t.check((t.challenge(v, 'd_play_3') ->> 'progress')::int = 3, 'd_play_3 capped at 3');
  perform t.check((t.challenge(v, 'd_win_1') ->> 'progress')::int = 1, 'd_win_1 = 1');
  perform t.check((t.challenge(v, 'd_score_5000') ->> 'progress')::int = 700, 'd_score = 700');
  perform t.check((t.challenge(v, 'w_tourn_3') ->> 'progress')::int = 3, 'w_tourn_3 = 3 pool entries');
  perform t.check((t.challenge(v, 'w_top3_1') ->> 'progress')::int = 1, 'w_top3_1 = 1');
  perform t.check((t.challenge(v, 'w_win_1') ->> 'progress')::int = 1, 'w_win_1 = 1');
  perform t.check((t.challenge(v, 'w_lp_1000') ->> 'progress')::int = 230, 'w_lp_1000 = 230 season points');
  perform t.check((t.challenge(v, 'w_top10') ->> 'progress')::int = 1, 'w_top10 = 1');
  perform t.check((t.challenge(v, 'w_promote') ->> 'progress')::int = 0, 'A not promoted');
  perform t.check((t.challenge(v, 'w_bingo_3') ->> 'progress')::int = 0 and t.challenge(v, 'w_bingo_3') ->> 'game_id' = 'bingo',
                  'w_bingo_3 0, game bingo');
  perform t.check((t.challenge(v, 'm_play_10') ->> 'progress')::int = 4, 'm_play_10 = 4');
  perform t.check((t.challenge(v, 'm_win_3') ->> 'progress')::int = 1 and (t.challenge(v, 'm_win_3') ->> 'reward_gems')::int = 20,
                  'm_win_3 = 1, 20 gems');
  perform t.check((t.challenge(v, 'm_score_20000') ->> 'progress')::int = 700, 'm_score = 700');

  -- Claims
  a_coins := t.coins(t.uid(201)); a_gems := t.gems(t.uid(201));
  perform t.check(public.claim_challenge('d_play_3') = '{"coins":100,"gems":0}'::jsonb, 'claim d_play_3');
  perform t.expect_error($q$select public.claim_challenge('d_play_3')$q$, 'ALREADY_CLAIMED', 'claim twice');
  perform t.expect_error($q$select public.claim_challenge('nope')$q$, 'UNKNOWN_CHALLENGE', 'unknown challenge');
  perform t.expect_error($q$select public.claim_challenge('m_play_10')$q$, 'CHALLENGE_NOT_COMPLETE', 'incomplete');
  perform public.claim_challenge('d_win_1');
  perform public.claim_challenge('w_tourn_3');
  perform public.claim_challenge('w_top3_1');
  perform public.claim_challenge('w_win_1');
  perform public.claim_challenge('w_top10');
  perform t.check(t.coins(t.uid(201)) = a_coins + 100 + 250 + 200 + 250 + 300 + 300, 'challenge coins credited');
  v := public.get_challenges();
  perform t.check((t.challenge(v, 'd_play_3') ->> 'claimed')::boolean, 'd_play_3 shows claimed');
  perform t.check((t.challenge(v, 'm_challenges_5') ->> 'progress')::int = 5, 'm_challenges_5 capped at 5');
  perform t.check((t.challenge(v, 's_weekly_champ') ->> 'progress')::int = 4, 's_weekly_champ 4/6');
  perform t.check(public.claim_challenge('m_challenges_5') = '{"coins":0,"gems":15}'::jsonb, 'claim m_challenges_5 = 15 gems');
  perform t.check(t.gems(t.uid(201)) = a_gems + 15, 'gems credited');
  perform t.expect_error($q$select public.claim_challenge('s_weekly_champ')$q$, 'CHALLENGE_NOT_COMPLETE', 'weekly champ incomplete');
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(201)
                           and description = 'Challenge: Play 3 Games' and amount = 100), 'challenge tx text');

  perform t.login(t.uid(203));
  perform t.check((t.challenge(public.get_challenges(), 'w_promote') ->> 'progress')::int = 1, 'C promoted this week');
  perform t.check(public.claim_challenge('w_promote') = '{"coins":500,"gems":0}'::jsonb, 'C claims w_promote');

  perform t.login(t.uid(206));
  perform t.expect_error($q$select public.claim_challenge('d_play_3')$q$, 'CHALLENGE_NOT_COMPLETE', 'F has not played');
  perform t.admin();

  -- Daily claims belong to today only: yesterday's claim does not block today.
  update public.challenge_claims set period_start = period_start - 1
   where player_id = t.uid(201) and challenge_id = 'd_play_3';
  perform t.login(t.uid(201));
  perform t.check(not (t.challenge(public.get_challenges(), 'd_play_3') ->> 'claimed')::boolean, 'new day: d_play_3 claimable again');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 7. Daily bonus (7-day streak, day 7 epic chest)
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; d int; c bigint; g bigint;
begin
  perform t.login(t.uid(206));
  v := public.get_daily_bonus();
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['claimed_today','day','resets_in_ms','rewards'], 'daily bonus keys');
  perform t.check((v ->> 'day')::int = 1 and not (v ->> 'claimed_today')::boolean, 'F day 1, unclaimed');
  perform t.check(jsonb_array_length(v -> 'rewards') = 7 and v -> 'rewards' -> 6 ->> 'chest' = 'epic'
                  and (v -> 'rewards' -> 1 ->> 'gems')::int = 5 and v -> 'rewards' -> 0 -> 'chest' = 'null'::jsonb,
                  'rewards table');
  c := t.coins(t.uid(206)); g := t.gems(t.uid(206));
  perform t.check(public.claim_daily_bonus() = '{"day":1,"coins":100,"gems":0,"chest":null}'::jsonb, 'day 1 = 100 coins');
  perform t.expect_error('select public.claim_daily_bonus()', 'ALREADY_CLAIMED', 'daily bonus twice');
  perform t.check((public.get_daily_bonus() ->> 'claimed_today')::boolean, 'claimed today');
  perform t.check(t.coins(t.uid(206)) = c + 100, 'day 1 credited');
  perform t.admin();

  for d in 2..6 loop
    update public.daily_rewards set last_claim_date = public._utc_today() - 1 where player_id = t.uid(206);
    perform t.login(t.uid(206));
    v := public.claim_daily_bonus();
    perform t.admin();
    perform t.check((v ->> 'day')::int = d, 'streak day ' || d);
  end loop;
  perform t.check(t.coins(t.uid(206)) = c + 100 + 250 + 500 and t.gems(t.uid(206)) = g + 5 + 10 + 15, 'days 1-6 totals');

  update public.daily_rewards set last_claim_date = public._utc_today() - 1 where player_id = t.uid(206);
  c := t.coins(t.uid(206)); g := t.gems(t.uid(206));
  perform t.login(t.uid(206));
  v := public.claim_daily_bonus();
  perform t.admin();
  perform t.check((v ->> 'day')::int = 7 and v ->> 'chest' = 'epic', 'day 7 = epic chest');
  perform t.check((v ->> 'coins')::int between 800 and 2000 and (v ->> 'gems')::int between 10 and 30, 'epic contents in range');
  perform t.check(t.coins(t.uid(206)) = c + (v ->> 'coins')::int and t.gems(t.uid(206)) = g + (v ->> 'gems')::int, 'epic credited');
  perform t.check(exists (select 1 from public.wallet_transactions where player_id = t.uid(206)
                           and description = 'Daily bonus: day 7 (Epic chest)'), 'day 7 tx text');

  -- After day 7 the streak restarts; a missed day also restarts.
  update public.daily_rewards set last_claim_date = public._utc_today() - 1 where player_id = t.uid(206);
  perform t.login(t.uid(206));
  perform t.check((public.get_daily_bonus() ->> 'day')::int = 1, 'after day 7 -> day 1');
  perform t.admin();
  update public.daily_rewards set streak_day = 3, last_claim_date = public._utc_today() - 2 where player_id = t.uid(206);
  perform t.login(t.uid(206));
  perform t.check((public.get_daily_bonus() ->> 'day')::int = 1, 'missed a day -> day 1');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 8. Chests
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; c bigint; g bigint;
begin
  perform t.login(t.uid(205));
  v := public.get_shop_state();
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v) k)
                  = array['chests','free_chest_ready_in_ms','free_coins_amount','free_coins_left_today',
                          'spin_ready_in_ms','spin_segments'], 'shop keys');
  perform t.check(jsonb_array_length(v -> 'chests') = 4 and (v -> 'chests' -> 3 ->> 'gem_cost')::int = 100
                  and v -> 'chests' -> 0 ->> 'id' = 'wooden', 'chest list');
  perform t.check(jsonb_array_length(v -> 'spin_segments') = 8, '8 spin segments');
  perform t.check((v ->> 'free_chest_ready_in_ms')::int = 0 and (v ->> 'spin_ready_in_ms')::int = 0, 'free chest + spin ready');
  perform t.check((v ->> 'free_coins_amount')::int = 50 and (v ->> 'free_coins_left_today')::int = 3, 'free coins state');

  -- E has 0 gems.
  perform t.check(t.gems(t.uid(205)) = 0, 'E has 0 gems');
  perform t.expect_error($q$select public.open_chest('silver')$q$, 'NOT_ENOUGH_GEMS', 'silver without gems');
  perform t.expect_error($q$select public.open_chest('epic')$q$, 'UNKNOWN_CHEST', 'epic not openable');
  perform t.expect_error($q$select public.open_chest('nope')$q$, 'UNKNOWN_CHEST', 'unknown chest');
  perform t.admin();
  perform public._credit2(t.uid(205), 0, 110, 'Test gems');

  c := t.coins(t.uid(205)); g := t.gems(t.uid(205));
  perform t.login(t.uid(205));
  v := public.open_chest('diamond');
  perform t.admin();
  perform t.check((v ->> 'coins')::int between 1000 and 2500 and (v ->> 'gems')::int between 15 and 40, 'diamond contents');
  perform t.check(t.coins(t.uid(205)) = c + (v ->> 'coins')::int and t.gems(t.uid(205)) = g - 100 + (v ->> 'gems')::int,
                  'diamond: -100 gems + contents');

  c := t.coins(t.uid(205)); g := t.gems(t.uid(205));
  perform t.login(t.uid(205));
  v := public.open_chest('wooden');
  perform t.admin();
  perform t.check((v ->> 'coins')::int between 50 and 150 and (v ->> 'gems')::int = 0, 'wooden contents');
  perform t.check(t.coins(t.uid(205)) = c + (v ->> 'coins')::int and t.gems(t.uid(205)) = g - 10, 'wooden: -10 gems');

  -- Free chest: every 4 hours.
  c := t.coins(t.uid(205));
  perform t.login(t.uid(205));
  v := public.open_chest('free');
  perform t.check((v ->> 'coins')::int between 20 and 80 and (v ->> 'gems')::int = 0, 'free chest contents');
  perform t.expect_error($q$select public.open_chest('free')$q$, 'CHEST_NOT_READY', 'free chest cooldown');
  perform t.check((public.get_shop_state() ->> 'free_chest_ready_in_ms')::bigint between 4 * 3600000 - 60000 and 4 * 3600000,
                  'free chest ready in ~4h');
  perform t.admin();
  perform t.check(t.coins(t.uid(205)) = c + (v ->> 'coins')::int, 'free chest credited');
  update public.chest_opens set opened_at = opened_at - interval '5 hours'
   where player_id = t.uid(205) and chest_id = 'free';
  perform t.login(t.uid(205));
  perform t.check((public.get_shop_state() ->> 'free_chest_ready_in_ms')::int = 0, 'free chest ready again');
  perform public.open_chest('free');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
-- 9. Lucky spin (once per UTC day)
-- ---------------------------------------------------------------------------
do $$
declare v jsonb; s record; c bigint; g bigint;
begin
  c := t.coins(t.uid(204)); g := t.gems(t.uid(204));
  perform t.login(t.uid(204));
  v := public.spin_wheel();
  perform t.admin();
  select * into s from public._spin_segments() x where x.idx = (v ->> 'segment')::int;
  perform t.login(t.uid(204));
  perform t.check(s.idx is not null and (v ->> 'coins')::bigint = s.coins and (v ->> 'gems')::bigint = s.gems, 'spin matches its segment');
  perform t.expect_error('select public.spin_wheel()', 'SPIN_NOT_READY', 'spin twice');
  perform t.check((public.get_shop_state() ->> 'spin_ready_in_ms')::bigint between 1 and 86400000, 'spin ready tomorrow');
  perform t.admin();
  perform t.check(t.coins(t.uid(204)) = c + s.coins and t.gems(t.uid(204)) = g + s.gems, 'spin credited');
  update public.wheel_spins set spin_date = spin_date - 1 where player_id = t.uid(204);
  perform t.login(t.uid(204));
  perform public.spin_wheel();
  perform t.admin();
end $$;

-- Weighted pick covers every segment (100 admin draws with the same algorithm).
do $$
declare
  v_total int; v_pick int; s record; seen int[] := '{}'; i int;
begin
  select sum(weight) into v_total from public._spin_segments();
  perform t.check(v_total = 100, 'spin weights sum to 100');
  for i in 1..2000 loop
    v_pick := floor(random() * v_total)::int;
    for s in select * from public._spin_segments() order by idx loop
      exit when v_pick < s.weight;
      v_pick := v_pick - s.weight;
    end loop;
    if not s.idx = any(seen) then seen := seen || s.idx; end if;
  end loop;
  perform t.check(cardinality(seen) = 8, 'all 8 segments reachable');
end $$;

-- ---------------------------------------------------------------------------
-- 10. Wallet with gems + ledger invariants
-- ---------------------------------------------------------------------------
-- Level-up rewards: 100 coins x level (max 2000) + 10 gems every 5th level, each level paid once
-- ---------------------------------------------------------------------------
do $$
declare c bigint; g bigint; n int; tix uuid;
begin
  perform t.check((select coins from public._level_reward(2)) = 200 and (select gems from public._level_reward(2)) = 0, 'L2 reward 200');
  perform t.check((select coins from public._level_reward(5)) = 500 and (select gems from public._level_reward(5)) = 10, 'L5 reward 500 + 10 gems');
  perform t.check((select coins from public._level_reward(30)) = 2000, 'reward capped at 2000');

  -- One game takes F from 230 XP (level 1) over 250: level 2 paid.
  update public.profiles set xp = 230, level_rewarded = 1 where id = t.uid(206);
  c := t.coins(t.uid(206)); g := t.gems(t.uid(206));
  tix := t.join(206, 'rugby_rumble_rookie');
  c := c - (select entry_fee from public.tournaments where id = 'rugby_rumble_rookie');
  perform t.submit(206, tix, 10);
  perform t.check((select xp from public.profiles where id = t.uid(206)) = 255, 'F: +25 XP for the game');
  perform t.check((select level_rewarded from public.profiles where id = t.uid(206)) = 2, 'F: level 2 recorded');
  perform t.check(t.coins(t.uid(206)) = c + 200 and t.gems(t.uid(206)) = g, 'F: +200 coins for level 2');
  perform t.check((select count(*) from public.wallet_transactions
                    where player_id = t.uid(206) and description = 'Level 2 reward') = 1, 'level 2 history line');

  -- Several levels at once (2500 XP = level 5): levels 3, 4, 5 each paid.
  c := t.coins(t.uid(206));
  update public.profiles set xp = 2500 where id = t.uid(206);
  perform t.check(t.coins(t.uid(206)) = c + 300 + 400 + 500 and t.gems(t.uid(206)) = g + 10, 'F: levels 3-5 paid');
  perform t.check((select level_rewarded from public.profiles where id = t.uid(206)) = 5, 'F: level 5 recorded');

  -- More XP inside the same level, or XP going down and up again: nothing paid twice.
  c := t.coins(t.uid(206));
  update public.profiles set xp = 2600 where id = t.uid(206);
  update public.profiles set xp = 100 where id = t.uid(206);
  update public.profiles set xp = 2600 where id = t.uid(206);
  perform t.check(t.coins(t.uid(206)) = c, 'F: no second payment');
end $$;

-- ---------------------------------------------------------------------------
-- Watch video for 2x: the latest matching reward is paid once more, only once
-- ---------------------------------------------------------------------------
do $$
declare c bigint; g bigint; v jsonb;
begin
  perform t.login(t.uid(206));
  -- F's level rewards above: level 5 paid 500 coins + 10 gems a moment ago.
  c := t.coins(t.uid(206)); g := t.gems(t.uid(206));
  v := public.double_reward(500, 10);
  perform t.check(v = '{"coins":500,"gems":10}'::jsonb, 'double level 5 reward');
  perform t.check(t.coins(t.uid(206)) = c + 500 and t.gems(t.uid(206)) = g + 10, '2x credited');
  perform t.expect_error('select public.double_reward(500, 10)', 'NOTHING_TO_DOUBLE', 'same reward twice');
  perform t.expect_error('select public.double_reward(123, 0)', 'NOTHING_TO_DOUBLE', 'amount not granted');
  perform t.expect_error('select public.double_reward(0, 0)', 'NOTHING_TO_DOUBLE', 'nothing');
  perform t.expect_error('select public.double_reward(-5, 0)', 'NOTHING_TO_DOUBLE', 'negative');
  -- Level 4 (400 coins) is older but still in the window: allowed once.
  perform t.check(public.double_reward(400, 0) = '{"coins":400,"gems":0}'::jsonb, 'double level 4 reward');
  -- The welcome bonus (1000) of a player created just now.
  perform t.admin();
  insert into auth.users (id, email) values (t.uid(207), 'prog207@test') on conflict do nothing;
  perform t.login(t.uid(207));
  perform public.ensure_player('Gina2');
  perform t.check(public.double_reward(1000, 0) = '{"coins":1000,"gems":0}'::jsonb, 'double welcome bonus');
  perform t.check(t.coins(t.uid(207)) >= 2000, 'welcome doubled');
  perform t.admin();
  -- Old rewards can't be doubled.
  update public.wallet_transactions set created_at = now() - interval '1 hour'
   where player_id = t.uid(206) and description = 'Level 3 reward';
  perform t.login(t.uid(206));
  perform t.expect_error('select public.double_reward(300, 0)', 'NOTHING_TO_DOUBLE', 'too old');
  perform t.admin();
end $$;

-- ---------------------------------------------------------------------------
do $$
declare v jsonb;
begin
  perform t.login(t.uid(205));
  v := public.get_wallet(5);
  perform t.check(v ? 'gems' and (v ->> 'gems')::bigint = t.gems(t.uid(205)), 'get_wallet gems');
  perform t.check((select array_agg(k order by k) from jsonb_object_keys(v -> 'transactions' -> 0) k)
                  = array['amount','created_at','description','gems','id'], 'transaction keys');
  perform t.check((select count(*) from public.chest_opens) = 4, 'RLS: E sees only own chest opens (4)');
  perform t.admin();

  perform t.check(not exists (
    select 1 from public.wallets w
     where w.player_id <> t.uid(7)
       and (w.coins <> (select sum(amount) from public.wallet_transactions x where x.player_id = w.player_id)
         or w.gems  <> (select sum(gems)   from public.wallet_transactions x where x.player_id = w.player_id))),
    'wallet coins & gems = sum of transactions');
  perform t.check(not exists (select 1 from public.wallets where gems < 0), 'no negative gems');
  raise notice 'ALL PROGRESSION TESTS PASSED';
end $$;

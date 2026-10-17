-- =============================================================================
-- FunPixelNest MGP — progression: avatars, XP & levels, gems, leagues,
-- daily/weekly challenges, 7-day daily bonus, chests, lucky spin, welcome popup.
-- Contract: supabase/PROGRESSION_CONTRACT.md
--
-- Safe to run more than once (if not exists / create or replace / drop policy if exists).
-- Times are returned as remaining milliseconds (*_in_ms). Day = UTC date,
-- week/season = ISO week starting Monday 00:00 UTC.
-- =============================================================================


-- =============================================================================
-- 1. TABLES & COLUMNS
-- =============================================================================

alter table public.profiles
  add column if not exists avatar_id    int     not null default 1
    constraint profiles_avatar_range check (avatar_id between 1 and 15);
alter table public.profiles add column if not exists xp           bigint  not null default 0;
alter table public.profiles add column if not exists trophies     bigint  not null default 0;
alter table public.profiles add column if not exists welcome_seen boolean not null default false;

-- Gems live next to coins. The CHECK makes a negative gem balance impossible.
alter table public.wallets
  add column if not exists gems bigint not null default 0
    constraint wallets_gems_nonneg check (gems >= 0);

-- Every history line can move coins (amount) and/or gems.
alter table public.wallet_transactions add column if not exists gems bigint not null default 0;

-- Store: optional badge text ("Best value").
alter table public.store_packs add column if not exists tag text null;

-- League points earned per weekly season.
create table if not exists public.league_season_points (
  player_id     uuid not null references public.profiles (id) on delete cascade,
  season_start  date not null,
  points        bigint not null default 0,
  updated_at    timestamptz not null default now(),
  primary key (player_id, season_start)
);
create index if not exists league_season_points_rank_idx
  on public.league_season_points (season_start, points desc, player_id);

-- One row per tier a player has ever reached (promotion reward is paid once).
create table if not exists public.league_promotions (
  player_id   uuid not null references public.profiles (id) on delete cascade,
  tier        text not null,
  created_at  timestamptz not null default now(),
  primary key (player_id, tier)
);

-- Collected challenge rewards; period_start = the day (daily) or Monday (weekly).
create table if not exists public.challenge_claims (
  player_id     uuid not null references public.profiles (id) on delete cascade,
  challenge_id  text not null,
  period_start  date not null,
  claimed_at    timestamptz not null default now(),
  primary key (player_id, challenge_id, period_start)
);

-- Chest openings (history + free-chest cooldown).
create table if not exists public.chest_opens (
  id         bigserial primary key,
  player_id  uuid not null references public.profiles (id) on delete cascade,
  chest_id   text not null,
  coins      bigint not null,
  gems       bigint not null,
  opened_at  timestamptz not null default now()
);
create index if not exists chest_opens_player_idx on public.chest_opens (player_id, chest_id, opened_at desc);

-- Lucky spin: one per player per UTC day.
create table if not exists public.wheel_spins (
  player_id  uuid not null references public.profiles (id) on delete cascade,
  spin_date  date not null,
  segment    int not null,
  coins      bigint not null,
  gems       bigint not null,
  primary key (player_id, spin_date)
);


-- =============================================================================
-- 2. ROW LEVEL SECURITY (read own rows; writes only through RPCs)
-- =============================================================================

alter table public.league_season_points enable row level security;
alter table public.league_promotions    enable row level security;
alter table public.challenge_claims     enable row level security;
alter table public.chest_opens          enable row level security;
alter table public.wheel_spins          enable row level security;

drop policy if exists league_season_points_read_own on public.league_season_points;
create policy league_season_points_read_own on public.league_season_points
  for select to authenticated using (player_id = auth.uid());

drop policy if exists league_promotions_read_own on public.league_promotions;
create policy league_promotions_read_own on public.league_promotions
  for select to authenticated using (player_id = auth.uid());

drop policy if exists challenge_claims_read_own on public.challenge_claims;
create policy challenge_claims_read_own on public.challenge_claims
  for select to authenticated using (player_id = auth.uid());

drop policy if exists chest_opens_read_own on public.chest_opens;
create policy chest_opens_read_own on public.chest_opens
  for select to authenticated using (player_id = auth.uid());

drop policy if exists wheel_spins_read_own on public.wheel_spins;
create policy wheel_spins_read_own on public.wheel_spins
  for select to authenticated using (player_id = auth.uid());

revoke insert, update, delete, truncate on
  public.league_season_points, public.league_promotions, public.challenge_claims,
  public.chest_opens, public.wheel_spins
from anon, authenticated;


-- =============================================================================
-- 3. INTERNAL HELPERS
-- =============================================================================

-- Today (UTC) and the Monday that starts this season (UTC).
create or replace function public._utc_today()
returns date language sql stable as $$ select (now() at time zone 'utc')::date $$;

create or replace function public._season_start()
returns date language sql stable as $$
  select date_trunc('week', now() at time zone 'utc')::date
$$;

-- Milliseconds from now until a UTC timestamp (never negative).
create or replace function public._ms_until(p_utc timestamp)
returns bigint language sql stable as $$
  select greatest(0, floor(extract(epoch from (p_utc - (now() at time zone 'utc'))) * 1000))::bigint
$$;

-- Level math: level L -> L+1 needs 250*L XP.
create or replace function public._level_info(p_xp bigint,
  out level int, out level_xp bigint, out level_xp_needed bigint)
language plpgsql immutable as $$
declare
  v_rem bigint := greatest(coalesce(p_xp, 0), 0);
begin
  level := 1;
  while v_rem >= 250::bigint * level loop
    v_rem := v_rem - 250::bigint * level;
    level := level + 1;
  end loop;
  level_xp := v_rem;
  level_xp_needed := 250::bigint * level;
end;
$$;

-- League tiers (by lifetime trophies) and their one-time promotion rewards.
create or replace function public._league_tiers()
returns table (idx int, id text, name text, min_trophies bigint, reward_coins bigint, reward_gems bigint)
language sql immutable as $$
  values
    (0, 'bronze',   'Bronze',   0::bigint,    0::bigint,    0::bigint),
    (1, 'silver',   'Silver',   1000,  500,   10),
    (2, 'gold',     'Gold',     2000, 1000,   20),
    (3, 'platinum', 'Platinum', 3000, 2000,   40),
    (4, 'diamond',  'Diamond',  4000, 4000,   80),
    (5, 'champion', 'Champion', 5000, 8000,  150)
$$;

create or replace function public._tier_for(p_trophies bigint)
returns text language sql immutable as $$
  select id from public._league_tiers() where min_trophies <= coalesce(p_trophies, 0)
   order by min_trophies desc limit 1
$$;

-- Adds coins and/or gems (either may be negative for spending) + one history line.
-- Callers must check balances first; the CHECK constraints are the last safety net.
create or replace function public._credit2(p_uid uuid, p_coins bigint, p_gems bigint, p_description text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(p_coins, 0) = 0 and coalesce(p_gems, 0) = 0 then
    return;
  end if;
  update public.wallets
     set coins = coins + coalesce(p_coins, 0),
         gems  = gems  + coalesce(p_gems, 0),
         updated_at = now()
   where player_id = p_uid;
  insert into public.wallet_transactions (player_id, amount, gems, description)
  values (p_uid, coalesce(p_coins, 0), coalesce(p_gems, 0), p_description);
end;
$$;

-- Uniform random integer in [lo, hi].
create or replace function public._rand_between(p_lo bigint, p_hi bigint)
returns bigint language sql volatile as $$
  select p_lo + floor(random() * (p_hi - p_lo + 1))::bigint
$$;

-- Chest definitions: gem cost (null = not buyable) and content ranges.
create or replace function public._chest_defs()
returns table (ord int, id text, name text, gem_cost bigint, description text,
               coins_min bigint, coins_max bigint, gems_min bigint, gems_max bigint)
language sql immutable as $$
  values
    (0, 'free',    'Free Chest',    null::bigint, 'Free every 4 hours',        20::bigint,   80::bigint,  0::bigint,  0::bigint),
    (1, 'wooden',  'Wooden Chest',  10,  'Contains coins',             50,  150,  0,  0),
    (2, 'silver',  'Silver Chest',  25,  'Contains coins & gems',     150,  400,  1,  5),
    (3, 'gold',    'Gold Chest',    60,  'Contains more coins & gems', 400, 1000,  5, 15),
    (4, 'diamond', 'Diamond Chest', 100, 'Best rewards!',            1000, 2500, 15, 40),
    (5, 'epic',    'Epic Chest',    null, 'Day 7 daily bonus',         800, 2000, 10, 30)
$$;

-- Random contents of one chest.
create or replace function public._roll_chest(p_chest_id text, out coins bigint, out gems bigint)
language plpgsql volatile
set search_path = public
as $$
declare
  d record;
begin
  select * into d from public._chest_defs() c where c.id = p_chest_id;
  if not found then
    raise exception 'UNKNOWN_CHEST';
  end if;
  coins := public._rand_between(d.coins_min, d.coins_max);
  gems  := public._rand_between(d.gems_min, d.gems_max);
end;
$$;

-- Lucky-spin segments (order = wheel order) with weights.
create or replace function public._spin_segments()
returns table (idx int, coins bigint, gems bigint, weight int)
language sql immutable as $$
  values (0, 50::bigint, 0::bigint, 20), (1, 0, 5, 10), (2, 100, 0, 18), (3, 25, 0, 20),
         (4, 200, 0, 12), (5, 0, 10, 6), (6, 500, 0, 4), (7, 150, 0, 10)
$$;

-- 7-day daily bonus amounts.
create or replace function public._daily_bonus_defs()
returns table (day int, coins bigint, gems bigint, chest text)
language sql immutable as $$
  values (1, 100::bigint, 0::bigint, null::text), (2, 0, 5, null), (3, 250, 0, null), (4, 0, 10, null),
         (5, 500, 0, null), (6, 0, 15, null), (7, 0, 0, 'epic')
$$;

-- The caller's season rank (null when they have no points this season).
create or replace function public._season_rank(p_uid uuid)
returns int
language sql stable
security definer
set search_path = public
as $$
  with me as (
    select points from public.league_season_points
     where player_id = p_uid and season_start = public._season_start() and points > 0
  )
  select (select count(*) + 1 from public.league_season_points s, me
           where s.season_start = public._season_start()
             and (s.points > me.points or (s.points = me.points and s.player_id < p_uid)))::int
    from me
$$;

-- Builds the JSON object for one pool (adds avatar_id to the core version).
create or replace function public._pool_json(p_pool_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id',            p.id,
    'tournament_id', p.tournament_id,
    'seed',          p.seed::text,
    'status',        p.status,
    'entries', coalesce((
      select jsonb_agg(jsonb_build_object(
               'player_id',       e.player_id,
               'display_name',    e.display_name,
               'avatar_id',       coalesce(pr.avatar_id, 1),
               'score',           e.score,
               'duration_ms',     e.duration_ms,
               'submit_sequence', e.submit_sequence,
               'rank',            e.rank,
               'prize',           e.prize
             ) order by e.joined_at, e.join_seq)
      from public.pool_entries e
      left join public.profiles pr on pr.id = e.player_id
      where e.pool_id = p.id
    ), '[]'::jsonb)
  )
  from public.pools p
  where p.id = p_pool_id;
$$;

-- Challenge definitions + the player's progress (capped) and claim state.
create or replace function public._challenge_progress(p_uid uuid)
returns table (ord int, id text, period text, category text, title text, subtitle text, game_id text,
               target bigint, reward_coins bigint, reward_gems bigint, progress bigint,
               period_start date, claimed boolean)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
  v_today   date := public._utc_today();
  v_week    date := public._season_start();
  v_day_ts  timestamp := v_today::timestamp;      -- UTC midnight
  v_week_ts timestamp := v_week::timestamp;
  c         record;
  v_season_points bigint;
  v_rank    int;
begin
  select coalesce((select points from public.league_season_points
                    where player_id = p_uid and season_start = v_week), 0) into v_season_points;
  v_rank := public._season_rank(p_uid);

  select
    count(*) filter (where joined >= v_day_ts)                                                  as d_play,
    count(*) filter (where joined >= v_day_ts and status = 'completed' and rank = 1)            as d_win,
    coalesce(sum(score) filter (where submitted >= v_day_ts), 0)                                 as d_score,
    count(*) filter (where submitted >= v_week_ts and game_id = 'bingo')                         as w_bingo,
    count(*) filter (where submitted >= v_week_ts and game_id = 'block_puzzle')                  as w_block,
    count(*) filter (where submitted >= v_week_ts and game_id = 'arrows')                        as w_arrows,
    count(*) filter (where joined >= v_week_ts and mode = 'pool')                                as w_tourn,
    count(*) filter (where joined >= v_week_ts and mode = 'pool' and status = 'completed' and prize > 0) as w_top3,
    count(*) filter (where joined >= v_week_ts and status = 'completed' and rank = 1)            as w_win,
    count(*) filter (where joined >= v_week_ts)                                                  as m_play,
    coalesce(sum(score) filter (where submitted >= v_week_ts), 0)                                as m_score
    into c
    from (select t.game_id, t.mode, p.status, e.rank, e.prize, e.score,
                 e.joined_at at time zone 'utc' as joined, e.submitted_at at time zone 'utc' as submitted
            from public.pool_entries e
            join public.pools p       on p.id = e.pool_id
            join public.tournaments t on t.id = p.tournament_id
           where e.player_id = p_uid
             and e.joined_at >= (v_week_ts - interval '1 day') at time zone 'utc') m;

  return query
  with defs (ord, id, period, category, title, subtitle, game_id, target, reward_coins, reward_gems, raw) as (
    values
      ( 1, 'd_play_3',       'daily',  'daily',      'Play 3 Games',                 'Play any 3 games',                     null::text,     3::bigint,    100::bigint,  0::bigint, c.d_play::bigint),
      ( 2, 'd_win_1',        'daily',  'daily',      'Win a Tournament',             'Finish #1 in any tournament',          null,           1,     250,  0, c.d_win::bigint),
      ( 3, 'd_score_5000',   'daily',  'daily',      'Score 5,000 Points',           'Score total 5,000 points in any game', null,        5000,     150,  0, c.d_score::bigint),
      ( 4, 'w_bingo_3',      'weekly', 'game',       'Complete 3 Bingo Flash Games', 'Play Bingo Flash',                     'bingo',        3,     200,  0, c.w_bingo::bigint),
      ( 5, 'w_block_5',      'weekly', 'game',       'Play 5 Block Puzzle Games',    'Play Block Puzzle',                    'block_puzzle', 5,     150,  0, c.w_block::bigint),
      ( 6, 'w_arrows_5',     'weekly', 'game',       'Play 5 Arrows Games',          'Play Arrows',                          'arrows',       5,     200,  0, c.w_arrows::bigint),
      ( 7, 'w_tourn_3',      'weekly', 'tournament', 'Play 3 Tournaments',           '5-player tournaments',                 null,           3,     200,  0, c.w_tourn::bigint),
      ( 8, 'w_top3_1',       'weekly', 'tournament', 'Finish in Top 3',              'In a 5-player tournament',             null,           1,     250,  0, c.w_top3::bigint),
      ( 9, 'w_win_1',        'weekly', 'tournament', 'Win 1 Tournament',             'Finish #1',                            null,           1,     300,  0, c.w_win::bigint),
      (10, 'w_lp_1000',      'weekly', 'league',     'Earn 1,000 League Points',     'This season',                          null,        1000,     200,  0, v_season_points),
      (11, 'w_top10',        'weekly', 'league',     'Reach Top 10',                 'In the league leaderboard',            null,           1,     300,  0,
                                                         (case when v_rank is not null and v_rank <= 10 then 1 else 0 end)::bigint),
      (12, 'w_promote',      'weekly', 'league',     'Get Promoted',                 'Reach the next league',                null,           1,     500,  0,
                                                         (select count(*) from public.league_promotions lp
                                                           where lp.player_id = p_uid and lp.created_at >= v_week_ts at time zone 'utc')::bigint),
      (13, 'm_play_10',      'weekly', 'milestone',  'Play 10 Games',                'This week',                            null,          10,    1000,  0, c.m_play::bigint),
      (14, 'm_win_3',        'weekly', 'milestone',  'Win 3 Tournaments',            'This week',                            null,           3,       0, 20, c.w_win::bigint),
      (15, 'm_score_20000',  'weekly', 'milestone',  'Score 20,000 Points',          'This week',                            null,       20000,    1500,  0, c.m_score::bigint),
      (16, 'm_challenges_5', 'weekly', 'milestone',  'Complete 5 Challenges',        'This week',                            null,           5,       0, 15,
                                                         (select count(*) from public.challenge_claims cc
                                                           where cc.player_id = p_uid and cc.claimed_at >= v_week_ts at time zone 'utc')::bigint),
      (17, 's_weekly_champ', 'weekly', 'special',    'Weekly Champion',              'Complete all tournament & league challenges', null,    6,     500, 50,
                                                         (select count(*) from public.challenge_claims cc
                                                           where cc.player_id = p_uid and cc.period_start = v_week
                                                             and cc.challenge_id in ('w_tourn_3','w_top3_1','w_win_1','w_lp_1000','w_top10','w_promote'))::bigint)
  )
  select d.ord, d.id, d.period, d.category, d.title, d.subtitle, d.game_id, d.target, d.reward_coins, d.reward_gems,
         least(d.target, greatest(d.raw, 0)),
         case when d.period = 'daily' then v_today else v_week end,
         exists (select 1 from public.challenge_claims cc
                  where cc.player_id = p_uid and cc.challenge_id = d.id
                    and cc.period_start = case when d.period = 'daily' then v_today else v_week end)
    from defs d
   order by d.ord;
end;
$$;


-- =============================================================================
-- 4. AWARDS WHEN A POOL COMPLETES (trigger; _settle_pool is unchanged)
-- =============================================================================

create or replace function public._award_pool_progress()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_mode   text;
  v_season date := public._season_start();
  e        record;
  v_xp     bigint;
  v_points bigint;
  v_old    bigint;
  v_new    bigint;
  t        record;
  v_ok     boolean;
begin
  select mode into v_mode from public.tournaments where id = new.tournament_id;

  for e in select player_id, rank from public.pool_entries where pool_id = new.id order by player_id loop
    if v_mode = 'duel' then
      v_xp     := 20 + case when e.rank = 1 then 30 else 0 end;
      v_points := case when e.rank = 1 then 80 else 10 end;
    else
      v_xp     := 20 + case e.rank when 1 then 30 when 2 then 15 when 3 then 5 else 0 end;
      v_points := case e.rank when 1 then 100 when 2 then 60 when 3 then 30 else 10 end;
    end if;

    update public.profiles
       set xp = xp + v_xp, trophies = trophies + v_points
     where id = e.player_id
    returning trophies - v_points, trophies into v_old, v_new;

    insert into public.league_season_points (player_id, season_start, points)
    values (e.player_id, v_season, v_points)
    on conflict (player_id, season_start)
      do update set points = public.league_season_points.points + excluded.points, updated_at = now();

    -- Promotions crossed by this result (each paid once, ever).
    for t in select * from public._league_tiers() lt
              where lt.min_trophies > 0 and lt.min_trophies > v_old and lt.min_trophies <= v_new
              order by lt.min_trophies loop
      insert into public.league_promotions (player_id, tier) values (e.player_id, t.id)
      on conflict do nothing;
      get diagnostics v_ok = row_count;
      if v_ok then
        perform public._credit2(e.player_id, t.reward_coins, t.reward_gems, 'League promotion: ' || t.name);
        insert into public.notifications (player_id, title, body, data)
        values (e.player_id, 'You reached ' || t.name || ' League!',
                '+' || t.reward_coins || ' coins and +' || t.reward_gems || ' gems added.',
                jsonb_build_object('type', 'league_promotion', 'tier', t.id));
      end if;
    end loop;
  end loop;
  return new;
end;
$$;

drop trigger if exists pools_award_progress on public.pools;
create trigger pools_award_progress
  after update of status on public.pools
  for each row
  when (old.status is distinct from 'completed' and new.status = 'completed')
  execute function public._award_pool_progress();


-- =============================================================================
-- 5. RPCs
-- =============================================================================

-- get_profile: everything the header and Profile screen show.
create or replace function public.get_profile()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := public._require_uid();
  p      public.profiles%rowtype;
  w      public.wallets%rowtype;
  lv     record;
  s      record;
  v_streak int;
  v_days   int;
begin
  perform public._ensure_profile(v_uid, null);
  select * into p from public.profiles where id = v_uid;
  select * into w from public.wallets where player_id = v_uid;
  select * into lv from public._level_info(p.xp);

  select count(*)                                                     as matches,
         count(*) filter (where t.mode = 'pool')                      as tournaments,
         count(*) filter (where po.status = 'completed')              as completed,
         count(*) filter (where po.status = 'completed' and e.rank = 1) as won,
         coalesce(sum(e.prize), 0)                                    as coins_won
    into s
    from public.pool_entries e
    join public.pools po      on po.id = e.pool_id
    join public.tournaments t on t.id = po.tournament_id
   where e.player_id = v_uid;

  -- Consecutive wins counting back from my most recent completed match.
  select count(*) into v_streak
    from (select e.rank, row_number() over (order by po.completed_at desc, e.join_seq desc) rn
            from public.pool_entries e join public.pools po on po.id = e.pool_id
           where e.player_id = v_uid and po.status = 'completed') x
   where x.rn < coalesce((select min(rn) from (
            select e.rank, row_number() over (order by po.completed_at desc, e.join_seq desc) rn
              from public.pool_entries e join public.pools po on po.id = e.pool_id
             where e.player_id = v_uid and po.status = 'completed') y where y.rank <> 1), 2147483647);

  select count(*) into v_days from (
    select (joined_at at time zone 'utc')::date from public.pool_entries where player_id = v_uid
    union
    select (p.created_at at time zone 'utc')::date) d;

  return jsonb_build_object(
    'player_id',       p.id,
    'display_name',    p.display_name,
    'avatar_id',       p.avatar_id,
    'coins',           w.coins,
    'gems',            w.gems,
    'xp',              p.xp,
    'level',           lv.level,
    'level_xp',        lv.level_xp,
    'level_xp_needed', lv.level_xp_needed,
    'trophies',        p.trophies,
    'welcome_pending', not p.welcome_seen,
    'welcome_coins',   1000,
    'unread_notifications', (select count(*) from public.notifications n
                              where n.player_id = v_uid and n.read_at is null),
    'stats', jsonb_build_object(
      'tournaments_played', s.tournaments,
      'matches_played',     s.matches,
      'games_won',          s.won,
      'win_rate',           case when s.completed = 0 then 0 else round(s.won * 100.0 / s.completed) end,
      'coins_won',          s.coins_won,
      'win_streak',         v_streak,
      'days_active',        greatest(v_days, 1)));
end;
$$;

-- set_avatar: pick one of the 15 avatars.
create or replace function public.set_avatar(p_avatar_id int)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  if p_avatar_id is null or p_avatar_id not between 1 and 15 then
    raise exception 'INVALID_AVATAR';
  end if;
  perform public._ensure_profile(v_uid, null);
  update public.profiles set avatar_id = p_avatar_id where id = v_uid;
end;
$$;

-- ack_welcome: the welcome popup was shown (coins were already granted at sign-up).
create or replace function public.ack_welcome()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  perform public._ensure_profile(v_uid, null);
  update public.profiles set welcome_seen = true where id = v_uid;
end;
$$;

-- get_wallet: balance (coins + gems) + newest transactions. Same signature as core.
create or replace function public.get_wallet(p_limit int default 50)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_limit int  := least(greatest(coalesce(p_limit, 50), 1), 500);
begin
  perform public._ensure_profile(v_uid, null);
  return jsonb_build_object(
    'coins', (select coins from public.wallets where player_id = v_uid),
    'gems',  (select gems  from public.wallets where player_id = v_uid),
    'transactions', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id',          x.id::text,
               'amount',      x.amount,
               'gems',        x.gems,
               'description', x.description,
               'created_at',  x.created_at) order by x.id desc)
        from (select * from public.wallet_transactions
               where player_id = v_uid
               order by id desc
               limit v_limit) x
    ), '[]'::jsonb));
end;
$$;

-- get_league: my tier, this season's points and the top 10.
create or replace function public.get_league()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid      uuid := public._require_uid();
  v_season   date := public._season_start();
  v_trophies bigint;
  v_points   bigint;
begin
  perform public._ensure_profile(v_uid, null);
  select trophies into v_trophies from public.profiles where id = v_uid;
  select coalesce((select points from public.league_season_points
                    where player_id = v_uid and season_start = v_season), 0) into v_points;

  return jsonb_build_object(
    'trophies',          v_trophies,
    'tier',              public._tier_for(v_trophies),
    'season_points',     v_points,
    'season_ends_in_ms', public._ms_until((v_season + 7)::timestamp),
    'tiers', (select jsonb_agg(jsonb_build_object(
                       'id', lt.id, 'name', lt.name, 'min_trophies', lt.min_trophies,
                       'reward_coins', lt.reward_coins, 'reward_gems', lt.reward_gems) order by lt.idx)
                from public._league_tiers() lt),
    'top', coalesce((
      select jsonb_agg(jsonb_build_object(
               'rank', x.rn, 'player_id', x.player_id, 'display_name', x.display_name,
               'avatar_id', x.avatar_id, 'points', x.points, 'tier', public._tier_for(x.trophies))
             order by x.rn)
        from (select s.player_id, pr.display_name, pr.avatar_id, pr.trophies, s.points,
                     row_number() over (order by s.points desc, s.player_id) rn
                from public.league_season_points s
                join public.profiles pr on pr.id = s.player_id
               where s.season_start = v_season and s.points > 0
               order by s.points desc, s.player_id
               limit 10) x
    ), '[]'::jsonb),
    'me', jsonb_build_object('rank', public._season_rank(v_uid), 'points', v_points));
end;
$$;

-- get_challenges: daily + weekly challenges with progress.
create or replace function public.get_challenges()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  perform public._ensure_profile(v_uid, null);
  return jsonb_build_object(
    'daily_resets_in_ms',  public._ms_until((public._utc_today() + 1)::timestamp),
    'weekly_resets_in_ms', public._ms_until((public._season_start() + 7)::timestamp),
    'challenges', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', c.id, 'period', c.period, 'category', c.category, 'title', c.title,
               'subtitle', c.subtitle, 'game_id', c.game_id, 'target', c.target, 'progress', c.progress,
               'reward_coins', c.reward_coins, 'reward_gems', c.reward_gems, 'claimed', c.claimed)
             order by c.ord)
        from public._challenge_progress(v_uid) c
    ), '[]'::jsonb));
end;
$$;

-- claim_challenge: collect a completed challenge's reward (once per period).
create or replace function public.claim_challenge(p_challenge_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
  c     record;
  v_ok  boolean;
begin
  perform public._ensure_profile(v_uid, null);
  select * into c from public._challenge_progress(v_uid) x where x.id = p_challenge_id;
  if not found then
    raise exception 'UNKNOWN_CHALLENGE';
  end if;
  if c.claimed then
    raise exception 'ALREADY_CLAIMED';
  end if;
  if c.progress < c.target then
    raise exception 'CHALLENGE_NOT_COMPLETE';
  end if;

  insert into public.challenge_claims (player_id, challenge_id, period_start)
  values (v_uid, c.id, c.period_start)
  on conflict do nothing;
  get diagnostics v_ok = row_count;
  if not v_ok then
    raise exception 'ALREADY_CLAIMED';           -- double tap
  end if;

  perform public._credit2(v_uid, c.reward_coins, c.reward_gems, 'Challenge: ' || c.title);
  return jsonb_build_object('coins', c.reward_coins, 'gems', c.reward_gems);
end;
$$;

-- get_daily_bonus: 7-day streak (same streak as the core daily reward).
create or replace function public.get_daily_bonus()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_state record;
begin
  perform public._ensure_profile(v_uid, null);
  select * into v_state from public._daily_state(v_uid);
  return jsonb_build_object(
    'day',           v_state.day,
    'claimed_today', v_state.claimed_today,
    'resets_in_ms',  public._ms_until((public._utc_today() + 1)::timestamp),
    'rewards', (select jsonb_agg(jsonb_build_object('day', d.day, 'coins', d.coins, 'gems', d.gems, 'chest', d.chest)
                                 order by d.day)
                  from public._daily_bonus_defs() d));
end;
$$;

-- claim_daily_bonus: today's streak reward; day 7's epic chest is opened right away.
create or replace function public.claim_daily_bonus()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_state record;
  d       record;
  v_coins bigint;
  v_gems  bigint;
  v_roll  record;
begin
  perform public._ensure_profile(v_uid, null);
  insert into public.daily_rewards (player_id, streak_day, last_claim_date)
  values (v_uid, 7, date '1970-01-01')
  on conflict (player_id) do nothing;
  perform 1 from public.daily_rewards where player_id = v_uid for update;

  select * into v_state from public._daily_state(v_uid);
  if v_state.claimed_today then
    raise exception 'ALREADY_CLAIMED';
  end if;

  select * into d from public._daily_bonus_defs() x where x.day = v_state.day;
  v_coins := d.coins;
  v_gems  := d.gems;
  if d.chest is not null then
    select * into v_roll from public._roll_chest(d.chest);
    v_coins := v_coins + v_roll.coins;
    v_gems  := v_gems + v_roll.gems;
    insert into public.chest_opens (player_id, chest_id, coins, gems)
    values (v_uid, d.chest, v_roll.coins, v_roll.gems);
  end if;

  update public.daily_rewards
     set streak_day = v_state.day, last_claim_date = public._utc_today()
   where player_id = v_uid;
  perform public._credit2(v_uid, v_coins, v_gems,
    'Daily bonus: day ' || v_state.day || case when d.chest is not null then ' (Epic chest)' else '' end);
  return jsonb_build_object('day', v_state.day, 'coins', v_coins, 'gems', v_gems, 'chest', d.chest);
end;
$$;

-- get_shop_state: chest prices, timers, spin wheel and free-coin videos.
create or replace function public.get_shop_state()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid       uuid := public._require_uid();
  v_last_free timestamptz;
  v_claims    int;
begin
  perform public._ensure_profile(v_uid, null);
  select max(opened_at) into v_last_free from public.chest_opens
   where player_id = v_uid and chest_id = 'free';
  select claims into v_claims from public.free_coin_claims
   where player_id = v_uid and claim_date = public._utc_today();

  return jsonb_build_object(
    'free_chest_ready_in_ms',
      case when v_last_free is null then 0
           else public._ms_until(((v_last_free + interval '4 hours') at time zone 'utc')) end,
    'spin_ready_in_ms',
      case when exists (select 1 from public.wheel_spins
                         where player_id = v_uid and spin_date = public._utc_today())
           then public._ms_until((public._utc_today() + 1)::timestamp) else 0 end,
    'free_coins_amount',     50,
    'free_coins_left_today', greatest(0, 3 - coalesce(v_claims, 0)),
    'chests', (select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'gem_cost', c.gem_cost,
                                                   'description', c.description) order by c.ord)
                 from public._chest_defs() c where c.gem_cost is not null),
    'spin_segments', (select jsonb_agg(jsonb_build_object('coins', s.coins, 'gems', s.gems) order by s.idx)
                        from public._spin_segments() s));
end;
$$;

-- open_chest: free (every 4 hours) or paid with gems.
create or replace function public.open_chest(p_chest_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := public._require_uid();
  d      record;
  v_gems bigint;
  v_last timestamptz;
  v_roll record;
begin
  perform public._ensure_profile(v_uid, null);
  select * into d from public._chest_defs() c where c.id = p_chest_id and c.id <> 'epic';
  if not found then
    raise exception 'UNKNOWN_CHEST';
  end if;

  -- Serialise this player's chest opens (double taps, cooldown, gem balance).
  select gems into v_gems from public.wallets where player_id = v_uid for update;

  if d.id = 'free' then
    select max(opened_at) into v_last from public.chest_opens
     where player_id = v_uid and chest_id = 'free';
    if v_last is not null and v_last > now() - interval '4 hours' then
      raise exception 'CHEST_NOT_READY';
    end if;
  elsif v_gems < d.gem_cost then
    raise exception 'NOT_ENOUGH_GEMS';
  end if;

  select * into v_roll from public._roll_chest(d.id);
  insert into public.chest_opens (player_id, chest_id, coins, gems)
  values (v_uid, d.id, v_roll.coins, v_roll.gems);
  perform public._credit2(v_uid, v_roll.coins, v_roll.gems - coalesce(d.gem_cost, 0), 'Opened ' || d.name);
  return jsonb_build_object('coins', v_roll.coins, 'gems', v_roll.gems);
end;
$$;

-- spin_wheel: one lucky spin per UTC day.
create or replace function public.spin_wheel()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_total int;
  v_pick  int;
  s       record;
  v_ok    boolean;
begin
  perform public._ensure_profile(v_uid, null);
  select sum(weight) into v_total from public._spin_segments();
  v_pick := floor(random() * v_total)::int;
  for s in select * from public._spin_segments() order by idx loop
    exit when v_pick < s.weight;
    v_pick := v_pick - s.weight;
  end loop;

  insert into public.wheel_spins (player_id, spin_date, segment, coins, gems)
  values (v_uid, public._utc_today(), s.idx, s.coins, s.gems)
  on conflict do nothing;
  get diagnostics v_ok = row_count;
  if not v_ok then
    raise exception 'SPIN_NOT_READY';
  end if;

  perform public._credit2(v_uid, s.coins, s.gems, 'Lucky spin');
  return jsonb_build_object('segment', s.idx, 'coins', s.coins, 'gems', s.gems);
end;
$$;


-- =============================================================================
-- 6. STORE PACKS (replaces the seed's packs)
-- =============================================================================

insert into public.store_packs (id, name, coins, bonus_coins, price_label, sort_order, active, tag)
values
  ('coins_10k',  '10K Coins',   10000, 0, '₹89.00',    1, true, null),
  ('coins_25k',  '25K Coins',   25000, 0, '₹179.00',   2, true, null),
  ('coins_50k',  '50K Coins',   50000, 0, '₹349.00',   3, true, 'Best value'),
  ('coins_100k', '100K Coins', 100000, 0, '₹699.00',   4, true, null),
  ('coins_250k', '250K Coins', 250000, 0, '₹1,599.00', 5, true, null)
on conflict (id) do update
  set name = excluded.name, coins = excluded.coins, bonus_coins = excluded.bonus_coins,
      price_label = excluded.price_label, sort_order = excluded.sort_order,
      active = excluded.active, tag = excluded.tag;

update public.store_packs
   set active = false
 where id not in ('coins_10k', 'coins_25k', 'coins_50k', 'coins_100k', 'coins_250k');


-- =============================================================================
-- 7. GRANTS
-- =============================================================================

revoke execute on function
  public._utc_today(),
  public._season_start(),
  public._ms_until(timestamp),
  public._level_info(bigint),
  public._league_tiers(),
  public._tier_for(bigint),
  public._credit2(uuid, bigint, bigint, text),
  public._rand_between(bigint, bigint),
  public._chest_defs(),
  public._roll_chest(text),
  public._spin_segments(),
  public._daily_bonus_defs(),
  public._season_rank(uuid),
  public._pool_json(uuid),
  public._challenge_progress(uuid),
  public._award_pool_progress()
from public, anon, authenticated;

revoke execute on function
  public.get_profile(),
  public.set_avatar(int),
  public.ack_welcome(),
  public.get_wallet(int),
  public.get_league(),
  public.get_challenges(),
  public.claim_challenge(text),
  public.get_daily_bonus(),
  public.claim_daily_bonus(),
  public.get_shop_state(),
  public.open_chest(text),
  public.spin_wheel()
from public, anon;

grant execute on function
  public.get_profile(),
  public.set_avatar(int),
  public.ack_welcome(),
  public.get_wallet(int),
  public.get_league(),
  public.get_challenges(),
  public.claim_challenge(text),
  public.get_daily_bonus(),
  public.claim_daily_bonus(),
  public.get_shop_state(),
  public.open_chest(text),
  public.spin_wheel()
to authenticated;

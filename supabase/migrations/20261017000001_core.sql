-- =============================================================================
-- FunPixelNest MGP — core schema, security rules (RLS) and game functions (RPCs)
-- =============================================================================
--
-- How to read this file
-- ---------------------
-- 1. TABLES      – where data lives (players, wallets, games, tournaments, pools…)
-- 2. RLS         – "row level security": who may READ which rows. Clients may
--                  never write tables directly; every change goes through a
--                  function in section 4, which runs inside one transaction.
-- 3. HELPERS     – small internal functions used by the RPCs (not callable by apps)
-- 4. RPCs        – the functions the app calls (supabase.postgrest.rpc("...")).
--                  Errors are raised as short UPPERCASE codes, e.g. INSUFFICIENT_COINS.
-- 5. GRANTS      – only signed-in users ("authenticated") may call the RPCs.
-- 6. REALTIME    – pools / pool_entries are broadcast so leaderboards update live.
--
-- Safe to run more than once: tables use "if not exists", functions use
-- "create or replace", policies are dropped and re-created.
-- =============================================================================


-- =============================================================================
-- 1. TABLES
-- =============================================================================

-- One profile per signed-in user (guest/anonymous users count too).
create table if not exists public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text not null,
  created_at    timestamptz not null default now()
);

-- Coin balance. The CHECK makes a negative balance impossible at the database level.
create table if not exists public.wallets (
  player_id   uuid primary key references public.profiles (id) on delete cascade,
  coins       bigint not null default 0 check (coins >= 0),
  updated_at  timestamptz not null default now()
);

-- Every coin movement (+ or -) with a human-readable description ("coin history").
create table if not exists public.wallet_transactions (
  id           bigserial primary key,
  player_id    uuid not null references public.profiles (id) on delete cascade,
  amount       bigint not null,
  description  text not null,
  created_at   timestamptz not null default now()
);
create index if not exists wallet_transactions_player_idx
  on public.wallet_transactions (player_id, id desc);

-- Game catalog (filled by the seed migration).
create table if not exists public.games (
  id              text primary key,
  name            text not null,
  category        text not null,
  sort_order      int  not null default 0,
  active          boolean not null default true,
  webgl_base_url  text null,
  webgl_version   text null
);

-- Tournament templates. A tournament is "a way to play a game for coins";
-- actual matches happen in pools (below).
create table if not exists public.tournaments (
  id                    text primary key,
  game_id               text not null references public.games (id),
  title                 text not null,
  mode                  text not null check (mode in ('pool', 'duel')),
  entry_fee             bigint not null check (entry_fee >= 0),
  pool_size             int not null check (pool_size >= 2),
  platform_fee_percent  int not null default 10 check (platform_fee_percent between 0 and 100),
  prize_shares          int[] not null,
  active                boolean not null default true,
  featured              boolean not null default false,
  sort_order            int not null default 0
);
create index if not exists tournaments_game_idx on public.tournaments (game_id);

-- A pool = one match of `pool_size` players who all get the same `seed`.
--   open      -> still has free seats
--   full      -> all seats taken, waiting for scores
--   completed -> everyone submitted, ranked and paid
-- The pool also keeps a copy of the tournament's money rules at creation time,
-- so editing a tournament later never changes the payout of a running match.
create table if not exists public.pools (
  id                    uuid primary key default gen_random_uuid(),
  tournament_id         text not null references public.tournaments (id),
  seed                  bigint not null check (seed > 0),
  status                text not null default 'open' check (status in ('open', 'full', 'completed')),
  pool_size             int not null,
  entry_fee             bigint not null,
  platform_fee_percent  int not null,
  prize_shares          int[] not null,
  created_at            timestamptz not null default now(),
  completed_at          timestamptz null
);
-- Fast lookup of "oldest open pool of this tournament" when someone joins.
create index if not exists pools_open_idx
  on public.pools (tournament_id, created_at) where status = 'open';

-- One row per player per pool. The ticket is what the game sends back with the score.
create table if not exists public.pool_entries (
  pool_id          uuid not null references public.pools (id) on delete cascade,
  player_id        uuid not null references public.profiles (id) on delete cascade,
  display_name     text not null,
  ticket_id        uuid not null unique default gen_random_uuid(),
  joined_at        timestamptz not null default now(),
  score            bigint null,
  duration_ms      bigint null,
  submit_sequence  bigint null,
  submitted_at     timestamptz null,
  rank             int null,
  prize            bigint not null default 0,
  -- Insertion order; breaks ties when two joins share the same joined_at.
  join_seq         bigint generated always as identity,
  primary key (pool_id, player_id)          -- never twice in the same pool
);
create index if not exists pool_entries_player_idx
  on public.pool_entries (player_id, joined_at desc);

-- Global, always-increasing number given to each submission (final tie-breaker).
create sequence if not exists public.submit_sequence_seq;

-- 7-day login streak: which day of the streak was last claimed, and when (UTC date).
create table if not exists public.daily_rewards (
  player_id        uuid primary key references public.profiles (id) on delete cascade,
  streak_day       int not null check (streak_day between 1 and 7),
  last_claim_date  date not null
);

-- "Watch a video for free coins": how many claims per player per UTC day.
create table if not exists public.free_coin_claims (
  player_id   uuid not null references public.profiles (id) on delete cascade,
  claim_date  date not null,
  claims      int not null default 0,
  primary key (player_id, claim_date)
);

-- Missions are one-time: a row here means the reward was already collected.
create table if not exists public.mission_claims (
  player_id   uuid not null references public.profiles (id) on delete cascade,
  mission_id  text not null,
  claimed_at  timestamptz not null default now(),
  primary key (player_id, mission_id)
);

-- Coin packs shown in the Store.
create table if not exists public.store_packs (
  id           text primary key,
  name         text not null,
  coins        bigint not null,
  bonus_coins  bigint not null default 0,
  price_label  text not null,
  sort_order   int not null default 0,
  active       boolean not null default true
);

-- Simple key/value switches for the operator (e.g. demo_purchases_enabled).
create table if not exists public.app_settings (
  key    text primary key,
  value  text not null
);

-- Device tokens for push notifications (Firebase Cloud Messaging).
create table if not exists public.push_tokens (
  token       text primary key,
  player_id   uuid not null references public.profiles (id) on delete cascade,
  platform    text not null check (platform in ('android', 'ios', 'web')),
  updated_at  timestamptz not null default now()
);
create index if not exists push_tokens_player_idx on public.push_tokens (player_id);

-- In-app inbox; every new row is also sent as a push (see the push migration).
create table if not exists public.notifications (
  id          bigserial primary key,
  player_id   uuid not null references public.profiles (id) on delete cascade,
  title       text not null,
  body        text not null,
  data        jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now(),
  read_at     timestamptz null
);
create index if not exists notifications_player_idx on public.notifications (player_id, id desc);


-- =============================================================================
-- 2. ROW LEVEL SECURITY (read access only — all writes go through RPCs)
-- =============================================================================

alter table public.profiles            enable row level security;
alter table public.wallets             enable row level security;
alter table public.wallet_transactions enable row level security;
alter table public.games               enable row level security;
alter table public.tournaments         enable row level security;
alter table public.pools               enable row level security;
alter table public.pool_entries        enable row level security;
alter table public.daily_rewards       enable row level security;
alter table public.free_coin_claims    enable row level security;
alter table public.mission_claims      enable row level security;
alter table public.store_packs         enable row level security;
alter table public.app_settings        enable row level security;
alter table public.push_tokens         enable row level security;
alter table public.notifications       enable row level security;

-- "Am I in this pool?" Runs with owner rights so the pool_entries policy can use it
-- without the policy checking itself forever (recursive policy).
create or replace function public.is_pool_member(p_pool_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.pool_entries
    where pool_id = p_pool_id and player_id = auth.uid()
  );
$$;

-- Catalog: everyone (even signed-out) can see active items.
drop policy if exists games_read on public.games;
create policy games_read on public.games
  for select to anon, authenticated using (active);

drop policy if exists tournaments_read on public.tournaments;
create policy tournaments_read on public.tournaments
  for select to anon, authenticated using (active);

drop policy if exists store_packs_read on public.store_packs;
create policy store_packs_read on public.store_packs
  for select to anon, authenticated using (active);

-- Personal data: only your own rows.
drop policy if exists profiles_read_own on public.profiles;
create policy profiles_read_own on public.profiles
  for select to authenticated using (id = auth.uid());

drop policy if exists wallets_read_own on public.wallets;
create policy wallets_read_own on public.wallets
  for select to authenticated using (player_id = auth.uid());

drop policy if exists wallet_transactions_read_own on public.wallet_transactions;
create policy wallet_transactions_read_own on public.wallet_transactions
  for select to authenticated using (player_id = auth.uid());

drop policy if exists notifications_read_own on public.notifications;
create policy notifications_read_own on public.notifications
  for select to authenticated using (player_id = auth.uid());

drop policy if exists daily_rewards_read_own on public.daily_rewards;
create policy daily_rewards_read_own on public.daily_rewards
  for select to authenticated using (player_id = auth.uid());

drop policy if exists free_coin_claims_read_own on public.free_coin_claims;
create policy free_coin_claims_read_own on public.free_coin_claims
  for select to authenticated using (player_id = auth.uid());

drop policy if exists mission_claims_read_own on public.mission_claims;
create policy mission_claims_read_own on public.mission_claims
  for select to authenticated using (player_id = auth.uid());

drop policy if exists push_tokens_read_own on public.push_tokens;
create policy push_tokens_read_own on public.push_tokens
  for select to authenticated using (player_id = auth.uid());

-- Pools and their entries: visible to the players in that pool.
drop policy if exists pools_read_members on public.pools;
create policy pools_read_members on public.pools
  for select to authenticated using (public.is_pool_member(id));

drop policy if exists pool_entries_read_members on public.pool_entries;
create policy pool_entries_read_members on public.pool_entries
  for select to authenticated using (public.is_pool_member(pool_id));

-- app_settings: no policy at all = invisible to apps (operator-only).

-- Belt and braces: even if a write policy were added by mistake, apps hold no
-- write privilege on these tables. (Supabase grants everything by default.)
revoke insert, update, delete, truncate on
  public.profiles, public.wallets, public.wallet_transactions, public.games,
  public.tournaments, public.pools, public.pool_entries, public.daily_rewards,
  public.free_coin_claims, public.mission_claims, public.store_packs,
  public.app_settings, public.push_tokens, public.notifications
from anon, authenticated;
revoke select on public.app_settings from anon, authenticated;


-- =============================================================================
-- 3. INTERNAL HELPERS (not callable by apps; see GRANTS)
-- =============================================================================

-- Returns the caller's user id, or fails with NOT_AUTHENTICATED.
create or replace function public._require_uid()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_uid;
end;
$$;

-- Random positive 63-bit number used as the shared game seed.
create or replace function public._random_seed()
returns bigint
language sql
volatile
set search_path = public
as $$
  -- 31 random high bits + 32 random low bits = 63 bits; greatest() avoids 0.
  select greatest(1::bigint,
           (floor(random() * 2147483648)::bigint << 32)
         | floor(random() * 4294967296)::bigint);
$$;

-- Creates profile + wallet (+1000 welcome coins) the first time a user is seen.
-- Returns true if the player was created by this call.
create or replace function public._ensure_profile(p_uid uuid, p_display_name text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_name text := nullif(btrim(p_display_name), '');
  v_created boolean;
begin
  if exists (select 1 from public.profiles where id = p_uid) then
    return false;
  end if;

  if v_name is null then
    v_name := 'Player' || upper(left(p_uid::text, 4));
  elsif char_length(v_name) not between 2 and 20 then
    raise exception 'INVALID_NAME';
  end if;

  -- "on conflict do nothing" makes two simultaneous first calls safe:
  -- only one of them actually creates the player.
  insert into public.profiles (id, display_name)
  values (p_uid, v_name)
  on conflict (id) do nothing;
  get diagnostics v_created = row_count;

  if v_created then
    insert into public.wallets (player_id, coins) values (p_uid, 1000);
    insert into public.wallet_transactions (player_id, amount, description)
    values (p_uid, 1000, 'Welcome bonus');
  end if;
  return v_created;
end;
$$;

-- Adds coins (positive amount) to a wallet and writes the history line.
create or replace function public._credit(p_uid uuid, p_amount bigint, p_description text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_amount <= 0 then
    return;
  end if;
  update public.wallets
     set coins = coins + p_amount, updated_at = now()
   where player_id = p_uid;
  insert into public.wallet_transactions (player_id, amount, description)
  values (p_uid, p_amount, p_description);
end;
$$;

-- Builds the JSON object for one pool (shape documented in README "Contract").
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
    'seed',          p.seed::text,          -- text: JS/JSON numbers lose precision above 2^53
    'status',        p.status,
    'entries', coalesce((
      select jsonb_agg(jsonb_build_object(
               'player_id',       e.player_id,
               'display_name',    e.display_name,
               'score',           e.score,
               'duration_ms',     e.duration_ms,
               'submit_sequence', e.submit_sequence,
               'rank',            e.rank,
               'prize',           e.prize
             ) order by e.joined_at, e.join_seq)
      from public.pool_entries e
      where e.pool_id = p.id
    ), '[]'::jsonb)
  )
  from public.pools p
  where p.id = p_pool_id;
$$;

-- Ranks a finished pool, pays the winners and notifies every player.
-- Called only from submit_score. Locks the pool row and checks the status first,
-- so it can never pay twice.
create or replace function public._settle_pool(p_pool_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pool        public.pools%rowtype;
  v_game_id     text;
  v_game_name   text;
  v_title       text;
  v_mode        text;
  v_prize_pool  bigint;
  v_paid_ranks  int;
  v_paid_so_far bigint := 0;
  v_rank        int := 0;
  v_prize       bigint;
  v_entry       record;
  v_opponent    text;
  v_ntitle      text;
  v_nbody       text;
begin
  -- Lock the pool: only one transaction can settle it.
  select * into v_pool from public.pools where id = p_pool_id for update;
  if not found or v_pool.status = 'completed' then
    return;
  end if;

  select t.game_id, g.name, t.title, t.mode
    into v_game_id, v_game_name, v_title, v_mode
    from public.tournaments t
    join public.games g on g.id = t.game_id
   where t.id = v_pool.tournament_id;

  -- Prize pool = all entry fees minus the platform fee (integer division).
  v_prize_pool := v_pool.entry_fee * v_pool.pool_size
                  * (100 - v_pool.platform_fee_percent) / 100;
  v_paid_ranks := least(coalesce(array_length(v_pool.prize_shares, 1), 0), v_pool.pool_size);

  -- Rank: higher score, then shorter time, then earlier submission (no ties possible).
  for v_entry in
    select player_id
      from public.pool_entries
     where pool_id = p_pool_id
     order by score desc, duration_ms asc, submit_sequence asc
  loop
    v_rank := v_rank + 1;
    v_prize := 0;
    if v_rank < v_paid_ranks then
      v_prize := v_prize_pool * v_pool.prize_shares[v_rank] / 100;
    elsif v_rank = v_paid_ranks then
      v_prize := v_prize_pool - v_paid_so_far;   -- last paid rank gets the remainder
    end if;
    v_paid_so_far := v_paid_so_far + v_prize;

    update public.pool_entries
       set rank = v_rank, prize = v_prize
     where pool_id = p_pool_id and player_id = v_entry.player_id;

    if v_prize > 0 then
      perform public._credit(
        v_entry.player_id, v_prize,
        case when v_mode = 'duel'
             then 'Prize: won 1v1 ' || v_game_name || ' ' || v_title
             else 'Prize: #' || v_rank || ' in ' || v_game_name || ' ' || v_title
        end);
    end if;
  end loop;

  update public.pools
     set status = 'completed', completed_at = now()
   where id = p_pool_id;

  -- One notification per player.
  for v_entry in
    select player_id, rank, prize
      from public.pool_entries
     where pool_id = p_pool_id
     order by rank
  loop
    if v_mode = 'duel' then
      select display_name into v_opponent
        from public.pool_entries
       where pool_id = p_pool_id and player_id <> v_entry.player_id
       limit 1;
      if v_entry.rank = 1 then
        v_ntitle := '🏆 You won your ' || v_game_name || ' duel!';
        v_nbody  := 'You beat ' || v_opponent || '. +' || v_entry.prize || ' coins added.';
      else
        v_ntitle := v_opponent || ' won your ' || v_game_name || ' duel';
        v_nbody  := 'Close one! Tap to play a rematch.';
      end if;
    else
      if v_entry.prize > 0 then
        v_ntitle := '🏆 You placed #' || v_entry.rank || ' in ' || v_game_name || ' ' || v_title || '!';
        v_nbody  := '+' || v_entry.prize || ' coins added to your balance.';
      else
        v_ntitle := 'Results are in: ' || v_game_name || ' ' || v_title;
        v_nbody  := 'You finished #' || v_entry.rank || ' of ' || v_pool.pool_size || '. Play again?';
      end if;
    end if;

    insert into public.notifications (player_id, title, body, data)
    values (v_entry.player_id, v_ntitle, v_nbody, jsonb_build_object(
      'type',          case when v_mode = 'duel' then 'duel_result' else 'pool_result' end,
      'pool_id',       p_pool_id,
      'tournament_id', v_pool.tournament_id,
      'game_id',       v_game_id,
      'rank',          v_entry.rank,
      'prize',         v_entry.prize));
  end loop;
end;
$$;

-- Mission definitions + the caller's progress (capped at target).
create or replace function public._mission_progress(p_uid uuid)
returns table (ord int, id text, title text, target int, reward_coins bigint, progress int)
language sql
stable
security definer
set search_path = public
as $$
  with mine as (
    select e.prize, e.rank, t.game_id, t.mode, p.status
      from public.pool_entries e
      join public.pools p       on p.id = e.pool_id
      join public.tournaments t on t.id = p.tournament_id
     where e.player_id = p_uid
  ),
  counts as (
    select
      count(*)                                                              as played,
      count(distinct game_id)                                               as games,
      count(*) filter (where mode = 'duel')                                 as duels,
      count(*) filter (where mode = 'pool' and status = 'completed' and prize > 0) as top3,
      count(*) filter (where status = 'completed' and rank = 1)             as wins
    from mine
  ),
  defs (ord, id, title, target, reward_coins) as (
    values
      (1, 'play_3',    'Play 3 tournaments',       3, 100::bigint),
      (2, 'variety_3', 'Play 3 different games',   3, 200::bigint),
      (3, 'duel_1',    'Play a 1v1 duel',          1, 100::bigint),
      (4, 'top_3',     'Finish in the top 3',      1, 150::bigint),
      (5, 'win_1',     'Win a tournament or duel', 1, 250::bigint)
  )
  select d.ord, d.id, d.title, d.target, d.reward_coins,
         least(d.target, case d.id
                           when 'play_3'    then c.played
                           when 'variety_3' then c.games
                           when 'duel_1'    then c.duels
                           when 'top_3'     then c.top3
                           when 'win_1'     then c.wins
                         end)::int
    from defs d cross join counts c
   order by d.ord;
$$;

-- Daily-streak amounts for days 1..7.
create or replace function public._daily_coins()
returns bigint[]
language sql
immutable
as $$ select array[50, 75, 100, 125, 150, 200, 500]::bigint[] $$;

-- Which streak day the player is on today, and whether it is already claimed.
create or replace function public._daily_state(p_uid uuid, out day int, out claimed_today boolean)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_today date := (now() at time zone 'utc')::date;
  v_row   public.daily_rewards%rowtype;
begin
  select * into v_row from public.daily_rewards where player_id = p_uid;
  if found and v_row.last_claim_date = v_today then
    day := v_row.streak_day;            claimed_today := true;
  elsif found and v_row.last_claim_date = v_today - 1 then
    day := (v_row.streak_day % 7) + 1;  claimed_today := false;
  else
    day := 1;                           claimed_today := false;
  end if;
end;
$$;


-- =============================================================================
-- 4. RPCs — the app's API
-- =============================================================================

-- ensure_player: call once after sign-in. Creates the player on first call.
create or replace function public.ensure_player(p_display_name text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  perform public._ensure_profile(v_uid, p_display_name);
  return (
    select jsonb_build_object(
             'player_id',    p.id,
             'display_name', p.display_name,
             'coins',        coalesce(w.coins, 0))
      from public.profiles p
      left join public.wallets w on w.player_id = p.id
     where p.id = v_uid);
end;
$$;

-- set_display_name: rename (2–20 characters after trimming).
create or replace function public.set_display_name(p_name text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := public._require_uid();
  v_name text := btrim(coalesce(p_name, ''));
begin
  if char_length(v_name) not between 2 and 20 then
    raise exception 'INVALID_NAME';
  end if;
  perform public._ensure_profile(v_uid, null);
  update public.profiles set display_name = v_name where id = v_uid;
  -- Keep leaderboards showing the current name.
  update public.pool_entries set display_name = v_name where player_id = v_uid;
end;
$$;

-- get_wallet: balance + newest transactions.
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
    'transactions', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id',          x.id::text,
               'amount',      x.amount,
               'description', x.description,
               'created_at',  x.created_at) order by x.id desc)
        from (select * from public.wallet_transactions
               where player_id = v_uid
               order by id desc
               limit v_limit) x
    ), '[]'::jsonb));
end;
$$;

-- join_tournament: pay the entry fee and get a seat (and a ticket) in a pool.
create or replace function public.join_tournament(p_tournament_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid       uuid := public._require_uid();
  v_t         public.tournaments%rowtype;
  v_game_name text;
  v_pool      public.pools%rowtype;
  v_ticket    uuid;
  v_name      text;
  v_count     int;
begin
  perform public._ensure_profile(v_uid, null);

  select t.* into v_t
    from public.tournaments t
    join public.games g on g.id = t.game_id
   where t.id = p_tournament_id and t.active and g.active;
  if not found then
    raise exception 'TOURNAMENT_NOT_FOUND';
  end if;
  select name into v_game_name from public.games where id = v_t.game_id;

  -- 1) Take the entry fee. The "coins >= fee" condition makes this atomic:
  --    the balance can never go below zero, even with parallel requests.
  if v_t.entry_fee > 0 then
    update public.wallets
       set coins = coins - v_t.entry_fee, updated_at = now()
     where player_id = v_uid and coins >= v_t.entry_fee;
    if not found then
      raise exception 'INSUFFICIENT_COINS';
    end if;
    insert into public.wallet_transactions (player_id, amount, description)
    values (v_uid, -v_t.entry_fee,
            case when v_t.mode = 'duel'
                 then 'Entry: ' || v_game_name || ' 1v1 ' || v_t.title
                 else 'Entry: ' || v_game_name || ' ' || v_t.title
            end);
  end if;

  -- 2) Find the oldest open pool I am not already in. "skip locked" means two
  --    players joining at the same moment never grab the same last seat.
  select p.* into v_pool
    from public.pools p
   where p.tournament_id = v_t.id
     and p.status = 'open'
     and not exists (select 1 from public.pool_entries e
                      where e.pool_id = p.id and e.player_id = v_uid)
   order by p.created_at, p.id
   limit 1
   for update skip locked;

  -- 3) None available: open a new pool with a fresh seed.
  if not found then
    insert into public.pools (tournament_id, seed, pool_size, entry_fee,
                              platform_fee_percent, prize_shares)
    values (v_t.id, public._random_seed(), v_t.pool_size, v_t.entry_fee,
            v_t.platform_fee_percent, v_t.prize_shares)
    returning * into v_pool;
  end if;

  -- 4) Take the seat.
  select display_name into v_name from public.profiles where id = v_uid;
  insert into public.pool_entries (pool_id, player_id, display_name)
  values (v_pool.id, v_uid, v_name)
  returning ticket_id into v_ticket;

  -- 5) Last seat taken? Then the pool is full.
  select count(*) into v_count from public.pool_entries where pool_id = v_pool.id;
  if v_count >= v_pool.pool_size then
    update public.pools set status = 'full' where id = v_pool.id;
  end if;

  return jsonb_build_object(
    'ticket_id',     v_ticket,
    'pool_id',       v_pool.id,
    'tournament_id', v_t.id,
    'game_id',       v_t.game_id,
    'seed',          v_pool.seed::text);
end;
$$;

-- submit_score: use a ticket exactly once. Settles the pool when it is the last score.
create or replace function public.submit_score(p_ticket_id uuid, p_score bigint, p_duration_ms bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid       uuid := public._require_uid();
  v_pool_id   uuid;
  v_submitted timestamptz;
  v_size      int;
  v_status    text;
  v_total     int;
  v_done      int;
begin
  select pool_id into v_pool_id
    from public.pool_entries
   where ticket_id = p_ticket_id and player_id = v_uid;
  if not found then
    raise exception 'TICKET_NOT_FOUND';
  end if;

  -- Lock every entry of this pool (always in the same order, so no deadlocks).
  -- Submissions to one pool therefore happen one after another, which makes
  -- "am I the last one?" reliable. Joins are not blocked by this.
  perform 1 from public.pool_entries
   where pool_id = v_pool_id
   order by player_id
   for update;

  -- Re-read my entry now that I hold the lock.
  select submitted_at into v_submitted
    from public.pool_entries
   where ticket_id = p_ticket_id;
  if v_submitted is not null then
    raise exception 'TICKET_ALREADY_USED';
  end if;

  if p_score is null or p_duration_ms is null or p_score < 0 or p_duration_ms < 0 then
    raise exception 'INVALID_SCORE';
  end if;

  update public.pool_entries
     set score           = p_score,
         duration_ms     = p_duration_ms,
         submit_sequence = nextval('public.submit_sequence_seq'),
         submitted_at    = now()
   where ticket_id = p_ticket_id;

  -- Everyone in and everyone submitted? Then rank and pay (exactly once).
  select p.pool_size, p.status into v_size, v_status from public.pools p where p.id = v_pool_id;
  select count(*), count(submitted_at) into v_total, v_done
    from public.pool_entries where pool_id = v_pool_id;
  if v_status <> 'completed' and v_total >= v_size and v_done = v_total then
    perform public._settle_pool(v_pool_id);
  end if;

  return public._pool_json(v_pool_id);
end;
$$;

-- get_pool: one pool, only if I am in it (otherwise null).
create or replace function public.get_pool(p_pool_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  if not exists (select 1 from public.pool_entries
                  where pool_id = p_pool_id and player_id = v_uid) then
    return null;
  end if;
  return public._pool_json(p_pool_id);
end;
$$;

-- get_my_pools: my pools, most recently joined first.
create or replace function public.get_my_pools(p_limit int default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_limit int  := least(greatest(coalesce(p_limit, 50), 1), 200);
begin
  return coalesce((
    select jsonb_agg(public._pool_json(x.pool_id) order by x.joined_at desc, x.join_seq desc)
      from (select pool_id, joined_at, join_seq
              from public.pool_entries
             where player_id = v_uid
             order by joined_at desc, join_seq desc
             limit v_limit) x
  ), '[]'::jsonb);
end;
$$;

-- get_rewards_state: everything the Rewards tab shows.
create or replace function public.get_rewards_state()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid    uuid := public._require_uid();
  v_today  date := (now() at time zone 'utc')::date;
  v_daily  record;
  v_claims int;
begin
  perform public._ensure_profile(v_uid, null);
  select * into v_daily from public._daily_state(v_uid);
  select claims into v_claims from public.free_coin_claims
   where player_id = v_uid and claim_date = v_today;

  return jsonb_build_object(
    'daily', jsonb_build_object(
      'day',           v_daily.day,
      'claimed_today', v_daily.claimed_today,
      'coins_per_day', to_jsonb(public._daily_coins())),
    'free_coins_amount',     50,
    'free_coins_left_today', greatest(0, 3 - coalesce(v_claims, 0)),
    'missions', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id',           m.id,
               'title',        m.title,
               'target',       m.target,
               'progress',     m.progress,
               'reward_coins', m.reward_coins,
               'claimed',      exists (select 1 from public.mission_claims c
                                        where c.player_id = v_uid and c.mission_id = m.id))
             order by m.ord)
        from public._mission_progress(v_uid) m
    ), '[]'::jsonb));
end;
$$;

-- claim_daily_reward: returns coins granted (0 if already claimed today).
create or replace function public.claim_daily_reward()
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_today date := (now() at time zone 'utc')::date;
  v_state record;
  v_coins bigint;
begin
  perform public._ensure_profile(v_uid, null);
  -- Lock my streak row (create a placeholder first) so double taps pay once.
  insert into public.daily_rewards (player_id, streak_day, last_claim_date)
  values (v_uid, 7, date '1970-01-01')
  on conflict (player_id) do nothing;
  perform 1 from public.daily_rewards where player_id = v_uid for update;

  select * into v_state from public._daily_state(v_uid);
  if v_state.claimed_today then
    return 0;
  end if;

  v_coins := (public._daily_coins())[v_state.day];
  update public.daily_rewards
     set streak_day = v_state.day, last_claim_date = v_today
   where player_id = v_uid;
  perform public._credit(v_uid, v_coins, 'Daily reward · Day ' || v_state.day);
  return v_coins;
end;
$$;

-- claim_free_coins: 3 × 50 coins per UTC day (after a rewarded video).
create or replace function public.claim_free_coins()
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_today date := (now() at time zone 'utc')::date;
begin
  perform public._ensure_profile(v_uid, null);
  insert into public.free_coin_claims (player_id, claim_date, claims)
  values (v_uid, v_today, 0)
  on conflict (player_id, claim_date) do nothing;

  update public.free_coin_claims
     set claims = claims + 1
   where player_id = v_uid and claim_date = v_today and claims < 3;
  if not found then
    return 0;
  end if;

  perform public._credit(v_uid, 50, 'Free coins (video)');
  return 50;
end;
$$;

-- claim_mission: returns reward (0 if not complete, unknown or already claimed).
create or replace function public.claim_mission(p_mission_id text)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
  v_m   record;
begin
  perform public._ensure_profile(v_uid, null);
  select * into v_m from public._mission_progress(v_uid) where id = p_mission_id;
  if not found or v_m.progress < v_m.target then
    return 0;
  end if;

  insert into public.mission_claims (player_id, mission_id)
  values (v_uid, p_mission_id)
  on conflict (player_id, mission_id) do nothing;
  if not found then
    return 0;                          -- already claimed
  end if;

  perform public._credit(v_uid, v_m.reward_coins, 'Mission: ' || v_m.title);
  return v_m.reward_coins;
end;
$$;

-- purchase_pack_demo: DEMO ONLY — grants a pack for free. Turn off before launch:
--   update public.app_settings set value = 'false' where key = 'demo_purchases_enabled';
create or replace function public.purchase_pack_demo(p_pack_id text)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid     uuid := public._require_uid();
  v_enabled text;
  v_pack    public.store_packs%rowtype;
  v_total   bigint;
begin
  select value into v_enabled from public.app_settings where key = 'demo_purchases_enabled';
  if coalesce(v_enabled, 'true') <> 'true' then
    raise exception 'PURCHASES_DISABLED';
  end if;

  select * into v_pack from public.store_packs where id = p_pack_id and active;
  if not found then
    raise exception 'PACK_NOT_FOUND';
  end if;

  perform public._ensure_profile(v_uid, null);
  v_total := v_pack.coins + v_pack.bonus_coins;
  perform public._credit(v_uid, v_total, 'Store: ' || v_pack.name || ' (demo, no charge)');
  return v_total;
end;
$$;

-- register_push_token: save this device's FCM token for the current player.
create or replace function public.register_push_token(p_token text, p_platform text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_token text := btrim(coalesce(p_token, ''));
begin
  if v_token = '' then
    raise exception 'INVALID_TOKEN';
  end if;
  if p_platform is null or p_platform not in ('android', 'ios', 'web') then
    raise exception 'INVALID_PLATFORM';
  end if;
  perform public._ensure_profile(v_uid, null);
  insert into public.push_tokens (token, player_id, platform, updated_at)
  values (v_token, v_uid, p_platform, now())
  on conflict (token) do update
    set player_id = excluded.player_id,
        platform  = excluded.platform,
        updated_at = now();
end;
$$;

-- get_notifications: my inbox, newest first.
create or replace function public.get_notifications(p_limit int default 30)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  v_limit int  := least(greatest(coalesce(p_limit, 30), 1), 200);
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id',         n.id::text,
             'title',      n.title,
             'body',       n.body,
             'data',       n.data,
             'created_at', n.created_at,
             'read',       n.read_at is not null) order by n.id desc)
      from (select * from public.notifications
             where player_id = v_uid
             order by id desc
             limit v_limit) n
  ), '[]'::jsonb);
end;
$$;

-- mark_notifications_read: mark my whole inbox as read.
create or replace function public.mark_notifications_read()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public._require_uid();
begin
  update public.notifications
     set read_at = now()
   where player_id = v_uid and read_at is null;
end;
$$;


-- =============================================================================
-- 5. GRANTS — who may call what
-- =============================================================================
-- Postgres lets everyone (PUBLIC) execute new functions by default, and Supabase
-- additionally grants anon/authenticated. We take that away and give the app
-- RPCs to signed-in users only. Internal helpers (names starting with "_") stay
-- private: the RPCs run them with owner rights.

revoke execute on function
  public._require_uid(),
  public._random_seed(),
  public._ensure_profile(uuid, text),
  public._credit(uuid, bigint, text),
  public._pool_json(uuid),
  public._settle_pool(uuid),
  public._mission_progress(uuid),
  public._daily_coins(),
  public._daily_state(uuid)
from public, anon, authenticated;

revoke execute on function
  public.is_pool_member(uuid),
  public.ensure_player(text),
  public.set_display_name(text),
  public.get_wallet(int),
  public.join_tournament(text),
  public.submit_score(uuid, bigint, bigint),
  public.get_pool(uuid),
  public.get_my_pools(int),
  public.get_rewards_state(),
  public.claim_daily_reward(),
  public.claim_free_coins(),
  public.claim_mission(text),
  public.purchase_pack_demo(text),
  public.register_push_token(text, text),
  public.get_notifications(int),
  public.mark_notifications_read()
from public, anon;

grant execute on function
  public.is_pool_member(uuid),               -- needed by the RLS policies
  public.ensure_player(text),
  public.set_display_name(text),
  public.get_wallet(int),
  public.join_tournament(text),
  public.submit_score(uuid, bigint, bigint),
  public.get_pool(uuid),
  public.get_my_pools(int),
  public.get_rewards_state(),
  public.claim_daily_reward(),
  public.claim_free_coins(),
  public.claim_mission(text),
  public.purchase_pack_demo(text),
  public.register_push_token(text, text),
  public.get_notifications(int),
  public.mark_notifications_read()
to authenticated;


-- =============================================================================
-- 6. REALTIME — live leaderboard updates
-- =============================================================================
-- Adds pools and pool_entries to Supabase's realtime publication (skipped if
-- already added). Realtime respects the RLS policies above, so players only
-- receive changes for pools they are in.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    if not exists (select 1 from pg_publication_tables
                    where pubname = 'supabase_realtime'
                      and schemaname = 'public' and tablename = 'pools') then
      alter publication supabase_realtime add table public.pools;
    end if;
    if not exists (select 1 from pg_publication_tables
                    where pubname = 'supabase_realtime'
                      and schemaname = 'public' and tablename = 'pool_entries') then
      alter publication supabase_realtime add table public.pool_entries;
    end if;
  else
    raise notice 'Publication supabase_realtime not found; realtime not enabled for pools.';
  end if;
end;
$$;

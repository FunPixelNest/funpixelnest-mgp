-- 2026-11-05: new tournament values for every game (5-player pools).
--   Rookie 50 · Starter 100 · Pro 250 · Champion 500 · Elite 1000, no platform cut, prizes 50 / 30 / 20 %.
--   e.g. Rookie: 5 x 50 = 250 prize pool -> 1st 125, 2nd 75, 3rd 50.
-- 1v1 duels (hidden in the app) are unchanged.

update public.tournaments t
   set entry_fee            = v.fee,
       platform_fee_percent = 0,
       prize_shares         = array[50, 30, 20]::int[]
  from (values ('_rookie', 50::bigint), ('_starter', 100::bigint), ('_pro', 250::bigint),
               ('_champion', 500::bigint), ('_elite', 1000::bigint)) as v (suffix, fee)
 where t.mode = 'pool'
   and t.id = t.game_id || v.suffix;

-- join_tournament: same as before, but only joins open pools that were opened with the current fee and
-- prizes (pools already running at the old values are not mixed with the new ones).
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
     -- Only pools opened with the tournament's current terms: a pool keeps the fee and prizes it was
     -- opened with, so a player paying today's fee must never sit in a pool with older prizes.
     and p.entry_fee = v_t.entry_fee
     and p.platform_fee_percent = v_t.platform_fee_percent
     and p.prize_shares = v_t.prize_shares
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

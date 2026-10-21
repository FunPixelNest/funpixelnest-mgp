-- FunPixelNest MGP — "Watch a video for 2x": the player watched a rewarded ad on a reward pop-up,
-- so the reward they just got is paid once more.
--
-- double_reward(coins, gems) looks at the player's latest rewards (welcome bonus within 24 hours; daily bonus,
-- challenge, chest, lucky spin, level and league rewards within 15 minutes) and pays the newest one with exactly
-- those amounts again, once. Prizes, purchases and free video coins can't be doubled.
-- (The ad view itself is checked on the phone; server-side ad verification can be added later.)
-- Safe to run more than once.

create table if not exists public.reward_doubles (
  tx_id       bigint primary key references public.wallet_transactions (id) on delete cascade,
  player_id   uuid not null references public.profiles (id) on delete cascade,
  coins       bigint not null,
  gems        bigint not null,
  created_at  timestamptz not null default now()
);
alter table public.reward_doubles enable row level security;
revoke all on public.reward_doubles from anon, authenticated;

create or replace function public.double_reward(p_coins bigint, p_gems bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := public._require_uid();
  tx      record;
  v_gems  bigint;
begin
  if coalesce(p_coins, 0) < 0 or coalesce(p_gems, 0) < 0 or (coalesce(p_coins, 0) = 0 and coalesce(p_gems, 0) = 0) then
    raise exception 'NOTHING_TO_DOUBLE';
  end if;

  -- One at a time per player.
  perform 1 from public.wallets where player_id = v_uid for update;

  for tx in
    select x.* from public.wallet_transactions x
     where x.player_id = v_uid
       and not exists (select 1 from public.reward_doubles d where d.tx_id = x.id)
       and x.description not like '%(2x video)'
       and (
            (x.description = 'Welcome bonus' and x.created_at > now() - interval '24 hours')
         or (x.created_at > now() - interval '15 minutes' and (
                x.description like 'Daily bonus:%'
             or x.description like 'Daily reward%'
             or x.description like 'Challenge:%'
             or x.description like 'Mission:%'
             or x.description like 'Opened %'
             or x.description = 'Lucky spin'
             or x.description like 'Level % reward'
             or x.description like 'League promotion:%'))
       )
     order by x.id desc
     limit 20
  loop
    -- A chest's history line has the gems found minus the gems paid; the pop-up shows what was found.
    v_gems := case
      when tx.description like 'Opened %' then coalesce((select c.gems from public.chest_opens c
                                                          where c.player_id = v_uid order by c.id desc limit 1), tx.gems)
      else tx.gems end;
    if tx.amount = coalesce(p_coins, 0) and v_gems = coalesce(p_gems, 0) then
      insert into public.reward_doubles (tx_id, player_id, coins, gems) values (tx.id, v_uid, tx.amount, v_gems);
      perform public._credit2(v_uid, tx.amount, v_gems, tx.description || ' (2x video)');
      return jsonb_build_object('coins', tx.amount, 'gems', v_gems);
    end if;
  end loop;
  raise exception 'NOTHING_TO_DOUBLE';
end;
$$;

revoke execute on function public.double_reward(bigint, bigint) from public, anon;
grant execute on function public.double_reward(bigint, bigint) to authenticated;

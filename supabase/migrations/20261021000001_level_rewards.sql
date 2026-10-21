-- FunPixelNest MGP — level-up rewards and XP for every game.
--
-- * Every submitted score gives 25 XP right away (win or lose). Placement XP is still added
--   when the pool finishes (_award_pool_progress).
-- * Reaching a new level pays its reward once: 100 coins × level (max 2,000), plus 10 gems on
--   every 5th level. Several levels at once pay each one. Same rule as the app (LevelRewards).
-- * profiles.level_rewarded remembers the highest level already paid. Existing players start at
--   their current level, so nobody gets back-pay.
-- Safe to run more than once.

alter table public.profiles add column if not exists level_rewarded int;
update public.profiles p
   set level_rewarded = (public._level_info(p.xp)).level
 where p.level_rewarded is null;
alter table public.profiles alter column level_rewarded set default 1;
alter table public.profiles alter column level_rewarded set not null;

-- Reward for reaching one level.
create or replace function public._level_reward(p_level int, out coins bigint, out gems bigint)
language sql immutable as $$
  select least(100::bigint * p_level, 2000::bigint),
         case when p_level % 5 = 0 then 10::bigint else 0::bigint end
$$;

-- Pays every level between the last paid one and the level of the new XP total.
create or replace function public._pay_level_rewards()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_level int := (public._level_info(new.xp)).level;
  v_from  int := coalesce(old.level_rewarded, 1);
  l       int;
  r       record;
begin
  if v_level <= v_from then
    return new;
  end if;
  for l in (v_from + 1)..v_level loop
    select * into r from public._level_reward(l);
    perform public._credit2(new.id, r.coins, r.gems, 'Level ' || l || ' reward');
  end loop;
  new.level_rewarded := v_level;
  return new;
end;
$$;

drop trigger if exists profiles_level_rewards on public.profiles;
create trigger profiles_level_rewards
  before update of xp on public.profiles
  for each row
  when (new.xp is distinct from old.xp)
  execute function public._pay_level_rewards();

-- 25 XP the moment a score is submitted.
create or replace function public._xp_for_submission()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.profiles set xp = xp + 25 where id = new.player_id;
  return null;
end;
$$;

drop trigger if exists pool_entries_submit_xp on public.pool_entries;
create trigger pool_entries_submit_xp
  after update of submitted_at on public.pool_entries
  for each row
  when (old.submitted_at is null and new.submitted_at is not null)
  execute function public._xp_for_submission();

revoke all on function public._level_reward(int) from public, anon, authenticated;
revoke all on function public._pay_level_rewards() from public, anon, authenticated;
revoke all on function public._xp_for_submission() from public, anon, authenticated;

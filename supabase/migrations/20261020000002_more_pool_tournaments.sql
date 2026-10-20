-- Two more 5-player pools for every game, so each game page lists 5 tournaments
-- (1v1 duels are no longer shown in the app):
--   <game>_rookie    Rookie    pool  fee  10  shares {50,30,20}
--   <game>_champion  Champion  pool  fee 250  shares {50,30,20}
-- Sort order puts them in fee order between the existing pools. Safe to run more than once.

insert into public.tournaments
  (id, game_id, title, mode, entry_fee, pool_size, platform_fee_percent,
   prize_shares, active, featured, sort_order)
select
  g.id || f.suffix, g.id, f.title, 'pool', f.entry_fee, 5, 10,
  array[50, 30, 20]::int[], true, false, f.sort_order
from public.games g
cross join (values
  ('_rookie',   'Rookie',   10::bigint,  2),
  ('_champion', 'Champion', 250::bigint, 5)
) as f (suffix, title, entry_fee, sort_order)
where g.active
on conflict (id) do update
  set game_id              = excluded.game_id,
      title                = excluded.title,
      mode                 = excluded.mode,
      entry_fee            = excluded.entry_fee,
      pool_size            = excluded.pool_size,
      platform_fee_percent = excluded.platform_fee_percent,
      prize_shares         = excluded.prize_shares,
      active               = excluded.active,
      sort_order           = excluded.sort_order;

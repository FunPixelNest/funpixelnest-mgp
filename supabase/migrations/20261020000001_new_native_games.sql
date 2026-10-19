-- New native game (Unity, FunPixelNestGames library): Queen Sudoku, with the same 5 tournament formats as the
-- other games. Also renames "Skill Slots" to "Solitaire Slots" (the slots game is now Solitaire Slots).
-- Safe to run more than once.

insert into public.games (id, name, category, sort_order, active)
values ('queen_sudoku', 'Queen Sudoku', 'Puzzle', 14, true)
on conflict (id) do update
  set name       = excluded.name,
      category   = excluded.category,
      sort_order = excluded.sort_order,
      active     = excluded.active;

update public.games set name = 'Solitaire Slots' where id = 'slots';

insert into public.tournaments
  (id, game_id, title, mode, entry_fee, pool_size, platform_fee_percent,
   prize_shares, active, featured, sort_order)
select
  g.id || f.suffix,
  g.id,
  f.title,
  f.mode,
  f.entry_fee,
  f.pool_size,
  10,
  f.prize_shares,
  true,
  false,
  f.sort_order
from public.games g
cross join (values
  ('_duel_quick', 'Quick',       'duel',  20::bigint, 2, array[100]::int[],        1),
  ('_duel_high',  'High Stakes', 'duel', 100::bigint, 2, array[100]::int[],        2),
  ('_starter',    'Starter',     'pool',  20::bigint, 5, array[50, 30, 20]::int[], 3),
  ('_pro',        'Pro',         'pool', 100::bigint, 5, array[50, 30, 20]::int[], 4),
  ('_elite',      'Elite',       'pool', 500::bigint, 5, array[50, 30, 20]::int[], 5)
) as f (suffix, title, mode, entry_fee, pool_size, prize_shares, sort_order)
where g.id = 'queen_sudoku'
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

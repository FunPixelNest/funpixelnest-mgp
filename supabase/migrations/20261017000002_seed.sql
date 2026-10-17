-- =============================================================================
-- FunPixelNest MGP — catalog seed: games, tournaments, store packs, settings
-- =============================================================================
-- Safe to re-run: existing rows are updated to the values below
-- ("on conflict ... do update"). The demo-purchase switch is the exception:
-- it is only created if missing, so re-running never re-enables demo purchases.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Games (id, name, category, sort order)
-- ---------------------------------------------------------------------------
insert into public.games (id, name, category, sort_order, active)
values
  ('solitaire',      'Solitaire',      'Cards',    1, true),
  ('block_puzzle',   'Block Puzzle',   'Puzzle',   2, true),
  ('bingo',          'Bingo Flash',    'Classic',  3, true),
  ('bubble_shooter', 'Bubble Shooter', 'Arcade',   4, true),
  ('mahjong',        'Mahjong',        'Puzzle',   5, true),
  ('blackjack',      'Blackjack',      'Cards',    6, true),
  ('arrows',         'Arrows',         'Puzzle',   7, true),
  ('slots',          'Skill Slots',    'Arcade',   8, true),
  ('snake_war',      'Snake War',      'Arcade',   9, true),
  ('flap_dash',      'Flap Dash',      'Arcade',  10, true),
  ('kitty_hole',     'Kitty Hole',     'Casual',  11, true),
  ('catdoku',        'Catdoku',        'Puzzle',  12, true),
  ('rugby_rumble',   'Rugby Rumble',   'Sports',  13, true)
on conflict (id) do update
  set name       = excluded.name,
      category   = excluded.category,
      sort_order = excluded.sort_order,
      active     = excluded.active;

-- ---------------------------------------------------------------------------
-- Tournaments: the same 5 formats for every game (platform fee 10%).
--   <game>_duel_quick  Quick        1v1   fee  20  shares {100}
--   <game>_duel_high   High Stakes  1v1   fee 100  shares {100}
--   <game>_starter     Starter      pool  fee  20  shares {50,30,20}
--   <game>_pro         Pro          pool  fee 100  shares {50,30,20}
--   <game>_elite       Elite        pool  fee 500  shares {50,30,20}
-- ---------------------------------------------------------------------------
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
  (g.id || f.suffix) in ('solitaire_elite', 'bubble_shooter_pro', 'mahjong_elite', 'bingo_pro'),
  f.sort_order
from public.games g
cross join (values
  ('_duel_quick', 'Quick',       'duel',  20::bigint, 2, array[100]::int[],        1),
  ('_duel_high',  'High Stakes', 'duel', 100::bigint, 2, array[100]::int[],        2),
  ('_starter',    'Starter',     'pool',  20::bigint, 5, array[50, 30, 20]::int[], 3),
  ('_pro',        'Pro',         'pool', 100::bigint, 5, array[50, 30, 20]::int[], 4),
  ('_elite',      'Elite',       'pool', 500::bigint, 5, array[50, 30, 20]::int[], 5)
) as f (suffix, title, mode, entry_fee, pool_size, prize_shares, sort_order)
where g.id in ('solitaire', 'block_puzzle', 'bingo', 'bubble_shooter', 'mahjong',
               'blackjack', 'arrows', 'slots', 'snake_war', 'flap_dash',
               'kitty_hole', 'catdoku', 'rugby_rumble')
on conflict (id) do update
  set game_id              = excluded.game_id,
      title                = excluded.title,
      mode                 = excluded.mode,
      entry_fee            = excluded.entry_fee,
      pool_size            = excluded.pool_size,
      platform_fee_percent = excluded.platform_fee_percent,
      prize_shares         = excluded.prize_shares,
      active               = excluded.active,
      featured             = excluded.featured,
      sort_order           = excluded.sort_order;

-- ---------------------------------------------------------------------------
-- Store coin packs (coins + bonus). Prices are labels only for now.
-- ---------------------------------------------------------------------------
insert into public.store_packs (id, name, coins, bonus_coins, price_label, sort_order, active)
values
  ('pack_small',  'Handful',  500,    0, '$0.99', 1, true),
  ('pack_medium', 'Pouch',   1200,  100, '$1.99', 2, true),
  ('pack_large',  'Chest',   3000,  500, '$4.99', 3, true),
  ('pack_huge',   'Vault',   6500, 1500, '$9.99', 4, true)
on conflict (id) do update
  set name        = excluded.name,
      coins       = excluded.coins,
      bonus_coins = excluded.bonus_coins,
      price_label = excluded.price_label,
      sort_order  = excluded.sort_order,
      active      = excluded.active;

-- ---------------------------------------------------------------------------
-- Settings. Demo purchases are ON for development; see README to turn off.
-- ---------------------------------------------------------------------------
insert into public.app_settings (key, value)
values ('demo_purchases_enabled', 'true')
on conflict (key) do nothing;

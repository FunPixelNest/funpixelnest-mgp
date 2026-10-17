# Art cut from the design mockups

Source mockups (user's own app designs), 853×1844 each, in /root/.claude/uploads/3f15c7b5-755a-5be2-958f-b4813ac2bfb7/:
1 5a0d5eb0 game paused · 2 25c57814 joining tournament · 3 0e36b175 game detail (Bingo) · 4 017005ce games grid ·
5 a37edf3c leaderboard result · 6 b360bb35 challenges · 7 a86eafbf leagues · 8 b910df37 end game ·
9 a8a4f464 store · 10 b2105432 rewards · 11 3a83b919 welcome bonus · 12 9576c825 daily bonus ·
13 e2a88bcb profile · 14 4735e7dd splash · 15 017a7cd8 create account · 16 fa286e69 more-options drawer

Output: `composeApp/src/commonMain/composeResources/drawable/<name>.webp`, transparent background
(no card/sky background, no text unless noted), trimmed to content with ~4% padding, square canvas for icons
(object centered), lossless-ish WebP (quality 90, alpha). Max side 512 px (do not upscale).

| name | from | what |
|---|---|---|
| art_logo | 14 | FunPixelNest logo with the blue pixel nest (no loading bar) |
| art_hero_chest | 4 | open blue treasure chest with coins & gems (banner right side) |
| avatar_01 … avatar_15 | 13 | the 15 avatars in "Choose avatar", row by row; circular crop of the avatar circle only (no green check, no ring highlight), transparent outside the circle |
| ic_coin | 9 | one gold coin (header coin pill) |
| ic_gem | 9 | blue diamond gem (header gem pill) |
| art_trophy | 5 | gold trophy with laurels above "LEADERBOARD" |
| art_trophy_confetti | 7 | trophy with confetti in "Play More, Earn More!" |
| art_gift | 12 | big purple gift box on the gold/blue pedestal (top of daily bonus) |
| art_gift_small | 10 | purple gift box next to "REWARDS" |
| art_target | 6 | red target with dart next to "CHALLENGES" |
| art_league_shield | 7 | blue shield with crown next to "LEAGUES" |
| art_cart | 9 | blue cart icon in white tile next to "STORE" (cart only) |
| league_bronze, league_silver, league_gold, league_platinum, league_diamond, league_champion | 7 | the six tier badges in "LEAGUE TIERS" (badge only, no label/lock) |
| league_silver_big | 7 | large silver badge in the dark "SILVER LEAGUE" card |
| pack_1 … pack_5 | 9 | BUY COINS art: small coin pile, larger pile, coin bag, chest, vault |
| earn_video, earn_invite, earn_rate, earn_checkin, earn_spin, earn_free_chest | 9 | the six "EARN MORE COINS" icons (video tile, people tile, star tile, calendar, wheel, blue chest) |
| chest_wooden, chest_silver, chest_gold, chest_diamond | 10 | CHEST REWARDS chests |
| chest_epic | 12 | DAY 7 epic chest |
| coins_stack | 12 | DAY 1 coin stack |
| art_welcome_chest | 11 | big chest with flying coins (welcome bonus) |
| coins_pile | 11 | coin pile left of "1,000 COINS" |
| medal_1, medal_2, medal_3 | 5 | ribbon medals with 1/2/3 |
| medal_small_1, medal_small_2, medal_small_3 | 7 | small round medals in TOP PLAYERS |
| ch_gamepad, ch_trophy, ch_star | 6 | daily challenge icons (blue gamepad, purple trophy, green star) — icon only, no white tile |
| ch_podium, ch_cup, ch_trophy_red | 6 | tournament challenge icons |
| ch_lp, ch_crown, ch_promote | 6 | league challenge icons (LP hexagon, crown tile, green arrow hexagon) |
| ch_chest | 6 | special challenge chest with gems |
| ch_target | 10 | red target with dart (Complete 5 Challenges) |
| ic_stopwatch | 1 | blue stopwatch left of 04:37 |
| ic_how_to_play | 1 | blue book with question mark |
| logo_google, logo_apple, logo_facebook | 15 | sign-in brand logos (exactly as drawn) |
| art_cards_banner | 3 | gold trophy + coins group on the right of the "COMPETE, WIN" banner — ONLY if it can be cut without the word BINGO; otherwise skip |

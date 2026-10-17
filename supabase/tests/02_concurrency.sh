#!/usr/bin/env bash
# TEST ONLY — concurrency checks (run by run_tests.sh after the scenario test).
#  A) 40 players join the same tournament at the same moment:
#     no pool may ever have more than 5 players, no player twice in a pool,
#     and every pool except at most a few must be exactly full.
#  B) The 5 players of each full pool submit at the same moment:
#     every pool must be settled exactly once (5 notifications, correct prize sum).
set -euo pipefail
DB="${TEST_DB:-mgp_test}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -d "$DB")
N=40
T=arrows_pro

# Create N users (ids ...-0000000001xx) with wallets.
"${PSQL[@]}" <<SQL
insert into auth.users (id) select ('00000000-0000-4000-8000-' || lpad((100 + n)::text, 12, '0'))::uuid
  from generate_series(1, $N) n on conflict do nothing;
SQL
for n in $(seq 1 $N); do
  uid="00000000-0000-4000-8000-$(printf '%012d' $((100 + n)))"
  "${PSQL[@]}" -c "select set_config('role','authenticated',false), set_config('request.jwt.claim.sub','$uid',false);" \
               -c "select public.ensure_player(null);" >/dev/null
done

echo "A) $N simultaneous joins on $T"
for n in $(seq 1 $N); do
  uid="00000000-0000-4000-8000-$(printf '%012d' $((100 + n)))"
  "${PSQL[@]}" -c "select set_config('role','authenticated',false), set_config('request.jwt.claim.sub','$uid',false);" \
               -c "select public.join_tournament('$T');" >/dev/null &
done
wait

"${PSQL[@]}" -At <<SQL
do \$\$
declare bad int; total int; pools int;
begin
  select count(*) into bad from (
    select p.id from public.pools p join public.pool_entries e on e.pool_id = p.id
     where p.tournament_id = '$T' group by p.id, p.status
    having count(*) > 5 or (count(*) = 5) <> (p.status = 'full')) x;
  select count(*) into total from public.pool_entries e join public.pools p on p.id = e.pool_id
   where p.tournament_id = '$T';
  select count(*) into pools from public.pools where tournament_id = '$T';
  if bad > 0 then raise exception 'TEST FAILED: % pools over-filled or wrong status', bad; end if;
  if total <> $N then raise exception 'TEST FAILED: expected $N entries, got %', total; end if;
  raise notice 'ok  % entries in % pools, none over-filled, statuses correct', total, pools;
end \$\$;
SQL

echo "B) simultaneous submits in every full pool"
mapfile -t ROWS < <("${PSQL[@]}" -At -F' ' -c "
  select e.player_id, e.ticket_id from public.pool_entries e join public.pools p on p.id = e.pool_id
   where p.tournament_id = '$T' and p.status = 'full'")
for row in "${ROWS[@]}"; do
  set -- $row
  score=$(( RANDOM % 50 ))   # small range so ties on score are common
  "${PSQL[@]}" -c "select set_config('role','authenticated',false), set_config('request.jwt.claim.sub','$1',false);" \
               -c "select public.submit_score('$2', $score, 1000);" >/dev/null &
done
wait

"${PSQL[@]}" -At <<SQL
do \$\$
declare bad int; done_pools int;
begin
  select count(*) into done_pools from public.pools where tournament_id = '$T' and status = 'completed';
  select count(*) into bad from public.pools p
   where p.tournament_id = '$T' and p.status = 'completed'
     and ( (select count(*) from public.notifications n where n.data ->> 'pool_id' = p.id::text) <> 5
        or (select sum(prize) from public.pool_entries e where e.pool_id = p.id) <> 450
        or (select count(distinct rank) from public.pool_entries e where e.pool_id = p.id) <> 5
        or (select count(*) from public.wallet_transactions x
             where x.description like 'Prize: #% in Arrows Pro'
               and x.player_id in (select player_id from public.pool_entries e where e.pool_id = p.id)) < 3);
  if done_pools = 0 then raise exception 'TEST FAILED: no pool completed'; end if;
  if exists (select 1 from public.pools where tournament_id = '$T' and status = 'full') then
    raise exception 'TEST FAILED: a full pool with all scores in was not settled';
  end if;
  if bad > 0 then raise exception 'TEST FAILED: % pools settled incorrectly', bad; end if;
  if (select count(*) from public.wallet_transactions where description like 'Prize: #% in Arrows Pro')
     <> done_pools * 3 then
    raise exception 'TEST FAILED: prize transactions <> 3 per pool (double payout?)';
  end if;
  raise notice 'ok  % pools settled exactly once under concurrent submits', done_pools;
end \$\$;
SQL
echo "CONCURRENCY TESTS PASSED"

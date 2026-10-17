-- =============================================================================
-- FunPixelNest MGP — push notifications
-- =============================================================================
-- Every new row in public.notifications triggers an HTTP call to the Edge
-- Function "send-push", which forwards it to the player's phones via Firebase.
--
-- The call is made with pg_net (asynchronous: it is queued and sent after the
-- transaction commits, so it never slows down or blocks settlement).
--
-- The trigger needs two secrets stored in Supabase Vault (see README → Push):
--   project_url       e.g. https://abcdefghijkl.supabase.co
--   service_role_key  the project's service_role key
-- If they are missing — or anything else goes wrong — the trigger silently does
-- nothing. A failed push must never undo a payout.
-- =============================================================================

-- pg_net ships with every Supabase project; enable it if it is not already on.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_net') then
    create extension if not exists pg_net with schema extensions;
  else
    raise notice 'pg_net is not available on this server; push trigger will do nothing.';
  end if;
end;
$$;

create or replace function public._notification_push_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_url text;
  v_key text;
begin
  -- Inner block: any error here (no vault, no pg_net, bad secret…) is swallowed.
  begin
    select decrypted_secret into v_url
      from vault.decrypted_secrets where name = 'project_url' limit 1;
    select decrypted_secret into v_key
      from vault.decrypted_secrets where name = 'service_role_key' limit 1;

    if coalesce(v_url, '') = '' or coalesce(v_key, '') = '' then
      return new;                       -- push not configured yet: do nothing
    end if;

    perform net.http_post(
      url     := rtrim(v_url, '/') || '/functions/v1/send-push',
      body    := jsonb_build_object('notification_id', new.id),
      headers := jsonb_build_object(
                   'Content-Type',  'application/json',
                   'Authorization', 'Bearer ' || v_key),
      timeout_milliseconds := 10000
    );
  exception when others then
    raise warning 'send-push trigger skipped: %', sqlerrm;
  end;
  return new;
end;
$$;

revoke execute on function public._notification_push_trigger() from public, anon, authenticated;

drop trigger if exists notifications_send_push on public.notifications;
create trigger notifications_send_push
  after insert on public.notifications
  for each row
  execute function public._notification_push_trigger();

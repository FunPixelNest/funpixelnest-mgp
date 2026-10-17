-- =============================================================================
-- TEST ONLY — never run this on a real Supabase project.
-- Recreates the few Supabase-specific objects the migrations depend on, so the
-- migrations can be tested on a plain local PostgreSQL.
-- =============================================================================

-- Roles Supabase uses for API requests.
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then
    create role service_role nologin bypassrls;
  end if;
end;
$$;

-- Supabase grants these roles everything in "public" by default; mimic that so
-- the migrations' REVOKEs are actually tested.
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables    to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;

-- auth schema: users table + auth.uid() (same definition as Supabase).
create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key,
  email text
);
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
  )::uuid
$$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;

-- Realtime publication.
do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
end;
$$;

-- Vault: a plain table + view with the same name/columns as Supabase's.
create schema if not exists vault;
create table if not exists vault.secrets_plain (name text primary key, secret text);
create or replace view vault.decrypted_secrets as
  select name, secret as decrypted_secret from vault.secrets_plain;

-- pg_net stand-in: records calls instead of making HTTP requests.
create schema if not exists net;
create table if not exists net.calls (
  id bigserial primary key,
  url text, body jsonb, headers jsonb, created_at timestamptz default now()
);
create or replace function net.http_post(
  url text,
  body jsonb default '{}'::jsonb,
  params jsonb default '{}'::jsonb,
  headers jsonb default '{"Content-Type": "application/json"}'::jsonb,
  timeout_milliseconds int default 5000
) returns bigint
language sql
as $$
  insert into net.calls (url, body, headers) values (url, body, headers) returning id;
$$;

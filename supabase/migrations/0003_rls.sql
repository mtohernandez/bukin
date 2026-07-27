-- Deny-all RLS plus one narrow EXECUTE surface.
--
-- The anon key ships inside the APK and is public. The whole security model is that it
-- buys nothing except the right to call the functions in 0002_functions.sql, each of which
-- validates before it writes.
--
-- If a direct table query ever succeeds from the client, this migration is wrong.

-- Row level security ON, with no policies at all. RLS with zero policies denies
-- everything to every non-owner role, which is exactly what is wanted here.
alter table curso       enable row level security;
alter table instancia   enable row level security;
alter table colaborador enable row level security;
alter table inscripcion enable row level security;

-- Belt as well as braces. Supabase's default privileges hand anon and authenticated
-- table-level grants in `public`; RLS alone would stop the reads, but a role that holds
-- no privilege in the first place cannot be let through by a policy added later by mistake.
revoke all on curso       from anon, authenticated;
revoke all on instancia   from anon, authenticated;
revoke all on colaborador from anon, authenticated;
revoke all on inscripcion from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;

-- Postgres grants EXECUTE to PUBLIC on every new function by default, and every function
-- here is SECURITY DEFINER. Without this revoke, any helper added later would be callable
-- by anyone the moment it was created. Start from nothing and grant back by name.
revoke execute on all functions in schema public from public, anon, authenticated;

grant execute on function identificar_colaborador(text)                  to anon;
grant execute on function listar_instancias(uuid)                        to anon;
grant execute on function inscribir(integer, uuid)                       to anon;
grant execute on function crear_instancia(text, integer)                 to anon;
grant execute on function abrir_instancia(integer, text)                 to anon;
grant execute on function confirmar_asistencia(integer, uuid, text)      to anon;
grant execute on function registrar_manual(integer, uuid, uuid)          to anon;
grant execute on function listar_asistencia(integer)                     to anon;
grant execute on function listar_colaboradores(integer)                  to anon;

-- Deliberately NOT granted: ventana_activa. It is an internal helper called from inside
-- the SECURITY DEFINER functions above, where it runs with their privileges. Nothing on
-- the client has any reason to call it.

-- `usage` on the schema itself must stay, or PostgREST cannot resolve the function names
-- and every RPC 404s.
grant usage on schema public to anon;

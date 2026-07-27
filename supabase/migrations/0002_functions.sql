-- Every read and write the app performs goes through one of these functions.
--
-- The anon key ships inside the APK and must be assumed public, so no table is directly
-- reachable (0003_rls.sql). Each function is SECURITY DEFINER with a pinned search_path,
-- which is what lets it read tables the caller cannot touch.
--
-- Byte arguments (`_hex` suffix) are passed as lowercase hex TEXT rather than bytea.
-- JSON has no byte type; PostgREST's bytea escaping is one more thing that can silently
-- differ between the client and a psql test. Hex is unambiguous on both sides and is what
-- the Kotlin, Swift, and SQL test vectors are all written in.

-- ---------------------------------------------------------------------------------------
-- The attendance window.
--
-- Defined once because two callers must agree on it: `listar_instancias` decides whether
-- the app enables the scan, and `confirmar_asistencia` decides whether to accept the write.
-- If those two ever disagreed, a user would see an unlocked button that always fails.
--
-- Availability is a property of the clock, not of whether a host pressed a button. A
-- collaborator signs into a session ahead of time and waits for its hour.
-- ---------------------------------------------------------------------------------------
create function ventana_activa(
    p_inicio     timestamptz,
    p_fin        timestamptz,
    p_extension  timestamptz
) returns boolean
language sql
stable
as $$
    select now() >= p_inicio - interval '10 minutes'
       and now() <= coalesce(p_extension, p_fin) + interval '20 minutes';
$$;

-- ---------------------------------------------------------------------------------------
-- Identity. There is no authentication in v1: a person types their name, and that is it.
--
-- Find-or-create on the normalized name so reinstalling the app returns the same person
-- instead of accumulating duplicates. `colaborador_id` is therefore client-asserted and
-- forgeable — a deliberate scope cut, documented in context/architecture.md. Swapping it
-- for a JWT claim is a one-argument change to every function below.
-- ---------------------------------------------------------------------------------------
create function identificar_colaborador(p_nombre text)
returns uuid
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_nombre text := btrim(p_nombre);
    v_id     uuid;
begin
    if v_nombre = '' then
        raise exception 'nombre vacio';
    end if;

    insert into colaborador (nombre_completo)
    values (v_nombre)
    on conflict (lower(btrim(nombre_completo))) do update
        set nombre_completo = excluded.nombre_completo
    returning colaborador_id into v_id;

    return v_id;
end;
$$;

-- ---------------------------------------------------------------------------------------
-- The session list. One function serves both roles and both list screens.
--
-- `activa` is what unlocks the scan on the collaborator's phone. `inscrito` and
-- `asistencia` are what let the list say "ya marcaste" instead of offering a second tap.
-- ---------------------------------------------------------------------------------------
create function listar_instancias(p_colaborador_id uuid default null)
returns table (
    instancia_id      integer,
    curso_nombre      text,
    duracion_minutos  integer,
    fecha_inicio      timestamptz,
    fecha_fin         timestamptz,
    estado            text,
    activa            boolean,
    abierta           boolean,
    inscrito          boolean,
    asistencia        boolean
)
language sql
security definer
set search_path = public, extensions
as $$
    select i.instancia_id,
           c.nombre,
           c.duracion_minutos,
           i.fecha_inicio,
           i.fecha_fin,
           i.estado,
           ventana_activa(i.fecha_inicio, i.fecha_fin, i.fecha_extension),
           i.instance_key is not null,
           ins.inscripcion_id is not null,
           coalesce(ins.asistencia, false)
      from instancia i
      join curso c on c.curso_id = i.curso_id
      left join inscripcion ins
             on ins.instancia_id = i.instancia_id
            and ins.colaborador_id = p_colaborador_id
     order by i.fecha_inicio desc;
$$;

-- ---------------------------------------------------------------------------------------
-- Signing into a session ahead of time. This is what makes someone PRE_INSCRITO.
-- Anyone who never did this and simply shows up becomes a WALK_IN at confirmation time.
-- ---------------------------------------------------------------------------------------
create function inscribir(p_instancia_id integer, p_colaborador_id uuid)
returns void
language sql
security definer
set search_path = public, extensions
as $$
    insert into inscripcion (instancia_id, colaborador_id, origen, aprobado, fecha_aprobacion)
    values (p_instancia_id, p_colaborador_id, 'PRE_INSCRITO', true, now())
    on conflict (colaborador_id, instancia_id) do nothing;
$$;

-- ---------------------------------------------------------------------------------------
-- The host naming a session. Creates the course and one instance starting now.
-- ---------------------------------------------------------------------------------------
create function crear_instancia(p_nombre text, p_duracion_minutos integer)
returns integer
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_nombre    text := btrim(p_nombre);
    v_duracion  integer := greatest(coalesce(p_duracion_minutos, 60), 1);
    v_curso     uuid;
    v_instancia integer;
begin
    if v_nombre = '' then
        raise exception 'nombre vacio';
    end if;

    insert into curso (nombre, duracion_minutos)
    values (v_nombre, v_duracion)
    returning curso_id into v_curso;

    insert into instancia (curso_id, fecha_inicio, fecha_fin)
    values (v_curso, now(), now() + make_interval(mins => v_duracion))
    returning instancia_id into v_instancia;

    return v_instancia;
end;
$$;

-- ---------------------------------------------------------------------------------------
-- Opening the room.
--
-- The key travels UP and never back down. No function here returns `instance_key`: with no
-- authentication, an endpoint that handed it out would let anyone generate valid codes from
-- anywhere in the world, which is strictly worse than the relay attack because it needs no
-- physical presence at all.
--
-- The returned timestamp is what the host uses to correct its own clock. A host phone with
-- automatic time switched off generates codes that are rejected 100% of the time with no
-- symptom a user could diagnose; one subtraction removes the entire failure class.
--
-- docs/overview.md §2.2 assumes a background job flips PROGRAMADO -> ABIERTO. There is no
-- worker in this demo, so this is what sets `estado`. Note that `estado` is informational:
-- `confirmar_asistencia` gates on the clock, not on this column.
-- ---------------------------------------------------------------------------------------
create function abrir_instancia(p_instancia_id integer, p_key_hex text)
returns timestamptz
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_key bytea := decode(p_key_hex, 'hex');
begin
    if length(v_key) = 0 then
        raise exception 'llave vacia';
    end if;

    update instancia
       set instance_key = v_key,
           estado       = 'ABIERTO'
     where instancia_id = p_instancia_id;

    if not found then
        raise exception 'instancia % no existe', p_instancia_id;
    end if;

    return now();
end;
$$;

-- ---------------------------------------------------------------------------------------
-- The core. Recomputes the rotating code server-side and writes the attendance row.
--
-- A client saying "the code was valid" is not authorization. The collaborator holds no key
-- and only relays an opaque 8 bytes it overheard; this function is what decides.
--
-- Returns one of: OK | YA_REGISTRADO | CODIGO_INVALIDO | FUERA_DE_VENTANA
-- ---------------------------------------------------------------------------------------
create function confirmar_asistencia(
    p_instancia_id    integer,
    p_colaborador_id  uuid,
    p_code_hex        text
) returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_inst    instancia%rowtype;
    v_code    bytea := decode(p_code_hex, 'hex');
    v_counter bigint;
    v_c       bigint;
    v_ok      boolean := false;
begin
    select * into v_inst from instancia where instancia_id = p_instancia_id;
    if not found then
        return 'FUERA_DE_VENTANA';
    end if;

    -- The hour decides, not the host and not `estado`.
    if not ventana_activa(v_inst.fecha_inicio, v_inst.fecha_fin, v_inst.fecha_extension) then
        return 'FUERA_DE_VENTANA';
    end if;

    -- No host ever opened this room, so no code for it can be valid.
    if v_inst.instance_key is null then
        return 'CODIGO_INVALIDO';
    end if;

    -- Same derivation as domain/.../crypto/RotatingCode.kt: HMAC-SHA256 over a 12-byte
    -- message of big-endian int32 instancia_id followed by big-endian int64 counter,
    -- truncated to the first 8 bytes. int4send/int8send produce exactly the widths and
    -- byte order that Kotlin's ByteBuffer.putInt/putLong do.
    --
    -- RFC 6238 §5.2 recommends accepting +/- 1 time step. A host whose clock runs slightly
    -- fast emits counter+1 codes, and rejecting those would fail honest check-ins with no
    -- visible cause.
    v_counter := floor(extract(epoch from now()) / 30)::bigint;

    for v_c in (v_counter - 1)..(v_counter + 1) loop
        if substring(
               extensions.hmac(int4send(p_instancia_id) || int8send(v_c),
                               v_inst.instance_key, 'sha256')
               from 1 for 8) = v_code
        then
            v_ok := true;
            exit;
        end if;
    end loop;

    if not v_ok then
        return 'CODIGO_INVALIDO';
    end if;

    -- The INSERT branch handles walk-ins; the UPDATE branch handles the pre-enrolled.
    -- `origen` is deliberately not in the UPDATE set, so someone who signed in ahead of
    -- time stays PRE_INSCRITO.
    --
    -- `where inscripcion.fecha_llegada is null` is what makes a second tap touch nothing.
    -- It reports success anyway: a duplicate confirmation must never surface an error.
    insert into inscripcion (instancia_id, colaborador_id, origen,
                             fecha_llegada, metodo_confirmacion, asistencia)
    values (p_instancia_id, p_colaborador_id, 'WALK_IN', now(), 'BLE', true)
    on conflict (colaborador_id, instancia_id) do update
        set fecha_llegada       = now(),
            metodo_confirmacion = 'BLE',
            asistencia          = true
      where inscripcion.fecha_llegada is null;

    if found then
        return 'OK';
    else
        return 'YA_REGISTRADO';
    end if;
end;
$$;

-- ---------------------------------------------------------------------------------------
-- The documented fallback for a dead or incompatible phone, and the thing that closes the
-- biggest hole in a BLE-only design. No code is checked because there is no radio involved;
-- the host vouching is the evidence, and `atestiguado_por_id` records who vouched.
-- ---------------------------------------------------------------------------------------
create function registrar_manual(
    p_instancia_id    integer,
    p_colaborador_id  uuid,
    p_host_id         uuid
) returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if not exists (select 1 from instancia where instancia_id = p_instancia_id) then
        raise exception 'instancia % no existe', p_instancia_id;
    end if;

    insert into inscripcion (instancia_id, colaborador_id, origen, fecha_llegada,
                             metodo_confirmacion, asistencia, atestiguado_por_id)
    values (p_instancia_id, p_colaborador_id, 'WALK_IN', now(),
            'MANUAL', true, p_host_id)
    on conflict (colaborador_id, instancia_id) do update
        set fecha_llegada       = now(),
            metodo_confirmacion = 'MANUAL',
            asistencia          = true,
            atestiguado_por_id  = p_host_id
      where inscripcion.fecha_llegada is null;

    if found then
        return 'OK';
    else
        return 'YA_REGISTRADO';
    end if;
end;
$$;

-- ---------------------------------------------------------------------------------------
-- The host's live roster, polled every few seconds while the room is open.
--
-- Kept deliberately narrow: only the four columns the list renders. This is the one query
-- whose cost grows with attendance — at 300 people it returns 300 rows every few seconds
-- and is the only part of the backend that notices the difference between a small class
-- and a full auditorium.
-- ---------------------------------------------------------------------------------------
create function listar_asistencia(p_instancia_id integer)
returns table (
    colaborador_id       uuid,
    nombre_completo      text,
    fecha_llegada        timestamptz,
    metodo_confirmacion  text,
    origen               text
)
language sql
security definer
set search_path = public, extensions
as $$
    select c.colaborador_id,
           c.nombre_completo,
           i.fecha_llegada,
           i.metodo_confirmacion,
           i.origen
      from inscripcion i
      join colaborador c on c.colaborador_id = i.colaborador_id
     where i.instancia_id = p_instancia_id
     order by i.fecha_llegada desc nulls last, c.nombre_completo;
$$;

-- ---------------------------------------------------------------------------------------
-- Who the host can register by hand, and who is already accounted for. Host-side only.
-- ---------------------------------------------------------------------------------------
create function listar_colaboradores(p_instancia_id integer)
returns table (
    colaborador_id   uuid,
    nombre_completo  text,
    ya_registrado    boolean
)
language sql
security definer
set search_path = public, extensions
as $$
    select c.colaborador_id,
           c.nombre_completo,
           exists (
               select 1 from inscripcion i
                where i.instancia_id = p_instancia_id
                  and i.colaborador_id = c.colaborador_id
                  and i.fecha_llegada is not null
           )
      from colaborador c
     order by c.nombre_completo;
$$;

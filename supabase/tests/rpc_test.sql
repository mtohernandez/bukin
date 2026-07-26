-- Server-side acceptance tests for spec 03.
--
-- Run before wiring any client:
--   psql "$BUKIN_PGURL" -f supabase/tests/rpc_test.sql
--
-- Everything runs inside one transaction and rolls back, so it is safe against the live
-- project and leaves no rows behind.
--
-- Test 1 is the load-bearing one. It pins this SQL to the exact same bytes that
-- domain/src/test/.../RotatingCodeTest.kt and tools/mac-ble/BukInProtocol.swift assert.
-- If the three ever disagree, the failure shows up here rather than as an unexplainable
-- rejection on demo day. Every later test derives its codes the same way, so they check
-- the RPC's window and idempotency logic on top of a derivation test 1 has already proved.

\set ON_ERROR_STOP on
\pset pager off

begin;

create temporary table resultado (
    n        integer,
    caso     text,
    esperado text,
    obtenido text,
    ok       boolean generated always as (esperado is not distinct from obtenido) stored
);

-- Same expression as confirmar_asistencia, in one place so the tests read as intent.
create function pg_temp.codigo(p_inst integer, p_key bytea, p_counter bigint)
returns text language sql as $$
    select encode(substring(extensions.hmac(int4send(p_inst) || int8send(p_counter),
                                            p_key, 'sha256') from 1 for 8), 'hex');
$$;

create function pg_temp.contador() returns bigint language sql as $$
    select floor(extract(epoch from now()) / 30)::bigint;
$$;

-- ---------------------------------------------------------------------------------------
-- 1. The known vector. Kotlin, Swift and SQL must produce the same 8 bytes.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 1,
       'vector conocido: key=000102..0f instancia=42 counter=58000000',
       '67e94bf8a08959ea',
       pg_temp.codigo(42, decode('000102030405060708090a0b0c0d0e0f', 'hex'), 58000000);

-- ---------------------------------------------------------------------------------------
-- Fixtures: one instance inside its window, one that finished yesterday.
-- ---------------------------------------------------------------------------------------
create temporary table fix as
with c as (
    insert into curso (nombre, duracion_minutos)
    values ('TEST activa', 60), ('TEST vencida', 60)
    returning curso_id, nombre
),
i_activa as (
    insert into instancia (curso_id, fecha_inicio, fecha_fin)
    select curso_id, now() - interval '5 minutes', now() + interval '55 minutes'
      from c where nombre = 'TEST activa'
    returning instancia_id
),
i_vencida as (
    insert into instancia (curso_id, fecha_inicio, fecha_fin)
    select curso_id, now() - interval '2 days', now() - interval '47 hours'
      from c where nombre = 'TEST vencida'
    returning instancia_id
)
select (select instancia_id from i_activa)  as activa,
       (select instancia_id from i_vencida) as vencida;

create temporary table quien as
select identificar_colaborador('TEST Preinscrito') as preinscrito,
       identificar_colaborador('TEST WalkIn')      as walkin,
       identificar_colaborador('TEST Tardio')      as tardio,
       identificar_colaborador('TEST Temprano')    as temprano,
       identificar_colaborador('TEST Rechazado')   as rechazado,
       identificar_colaborador('TEST Manual')      as manual,
       identificar_colaborador('TEST Anfitrion')   as anfitrion;

-- Only the first is signed in ahead of time. The rest are walk-ins by construction.
select inscribir(activa, preinscrito) from fix, quien;

-- The host opens the room; this is the only way instance_key is ever set.
select abrir_instancia(activa, '000102030405060708090a0b0c0d0e0f') from fix;

create temporary table llave as
select activa as inst,
       decode('000102030405060708090a0b0c0d0e0f', 'hex') as key
  from fix;

-- ---------------------------------------------------------------------------------------
-- 2. A code from the current window is accepted, and a pre-enrolled person keeps
--    origen = PRE_INSCRITO (the UPDATE branch of the upsert).
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 2, 'codigo de la ventana actual', 'OK',
       confirmar_asistencia(l.inst, q.preinscrito,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador()))
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 3, 'preinscrito conserva origen=PRE_INSCRITO y metodo=BLE', 'PRE_INSCRITO/BLE/true',
       i.origen || '/' || i.metodo_confirmacion || '/' || i.asistencia::text
  from inscripcion i, llave l, quien q
 where i.instancia_id = l.inst and i.colaborador_id = q.preinscrito;

-- ---------------------------------------------------------------------------------------
-- 4. A second tap: one row, success, and nothing changed. No error is ever shown for
--    double-tapping.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 4, 'segundo toque devuelve YA_REGISTRADO', 'YA_REGISTRADO',
       confirmar_asistencia(l.inst, q.preinscrito,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador()))
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 5, 'sigue habiendo exactamente una fila', '1',
       count(*)::text
  from inscripcion i, llave l, quien q
 where i.instancia_id = l.inst and i.colaborador_id = q.preinscrito;

-- ---------------------------------------------------------------------------------------
-- 6-7. RFC 6238 §5.2 tolerance: one window either side is accepted.
--      Someone who saw the host a moment ago, and a host whose clock runs slightly fast.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 6, 'codigo de una ventana atras (counter-1)', 'OK',
       confirmar_asistencia(l.inst, q.tardio,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador() - 1))
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 7, 'codigo de una ventana adelante (counter+1)', 'OK',
       confirmar_asistencia(l.inst, q.temprano,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador() + 1))
  from llave l, quien q;

-- ---------------------------------------------------------------------------------------
-- 8. Two windows stale is rejected. This is the replay defence: a code captured and
--    submitted a minute later is worthless.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 8, 'codigo de dos ventanas atras es RECHAZADO (replay)', 'CODIGO_INVALIDO',
       confirmar_asistencia(l.inst, q.rechazado,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador() - 2))
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 9, 'codigo de dos ventanas adelante es RECHAZADO', 'CODIGO_INVALIDO',
       confirmar_asistencia(l.inst, q.rechazado,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador() + 2))
  from llave l, quien q;

-- ---------------------------------------------------------------------------------------
-- 10-11. A code that was never derived from this key, and a code for another instance.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 10, 'codigo inventado es RECHAZADO', 'CODIGO_INVALIDO',
       confirmar_asistencia(l.inst, q.rechazado, 'deadbeefdeadbeef')
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 11, 'codigo valido de OTRA instancia es RECHAZADO', 'CODIGO_INVALIDO',
       confirmar_asistencia(l.inst, q.rechazado,
                            pg_temp.codigo(l.inst + 1000, l.key, pg_temp.contador()))
  from llave l, quien q;

-- ---------------------------------------------------------------------------------------
-- 12. Outside the hour nothing is accepted, however good the code is. Availability is a
--     property of the clock.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 12, 'instancia fuera de su horario', 'FUERA_DE_VENTANA',
       confirmar_asistencia(f.vencida, q.rechazado,
                            pg_temp.codigo(f.vencida, l.key, pg_temp.contador()))
  from fix f, llave l, quien q;

-- ---------------------------------------------------------------------------------------
-- 13. Someone who never signed in becomes a WALK_IN (the INSERT branch).
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 13, 'no inscrito previamente se acepta', 'OK',
       confirmar_asistencia(l.inst, q.walkin,
                            pg_temp.codigo(l.inst, l.key, pg_temp.contador()))
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 14, 'y queda como origen=WALK_IN', 'WALK_IN/BLE',
       i.origen || '/' || i.metodo_confirmacion
  from inscripcion i, llave l, quien q
 where i.instancia_id = l.inst and i.colaborador_id = q.walkin;

-- ---------------------------------------------------------------------------------------
-- 15. Manual registration: no radio involved, the host vouches and is recorded.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 15, 'registro manual', 'OK',
       registrar_manual(l.inst, q.manual, q.anfitrion)
  from llave l, quien q;

insert into resultado (n, caso, esperado, obtenido)
select 16, 'manual queda con MANUAL y atestiguado_por_id', 'MANUAL/true',
       i.metodo_confirmacion || '/' || (i.atestiguado_por_id = q.anfitrion)::text
  from inscripcion i, llave l, quien q
 where i.instancia_id = l.inst and i.colaborador_id = q.manual;

-- ---------------------------------------------------------------------------------------
-- 17-18. The window helper the app and the RPC share, and the roster.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 17, 'listar_instancias marca activa/abierta correctamente', 'true/true/false',
       (li.activa)::text || '/' || (li.abierta)::text || '/' ||
       (select activa::text from listar_instancias() lv where lv.instancia_id = f.vencida)
  from fix f, listar_instancias() li
 where li.instancia_id = f.activa;

insert into resultado (n, caso, esperado, obtenido)
select 18, 'roster muestra las 5 llegadas', '5',
       count(*) filter (where fecha_llegada is not null)::text
  from listar_asistencia((select activa from fix));

-- ---------------------------------------------------------------------------------------
-- 19. Identity is find-or-create: the same typed name is the same person.
-- ---------------------------------------------------------------------------------------
insert into resultado (n, caso, esperado, obtenido)
select 19, 'identificar_colaborador es find-or-create', 'true',
       (identificar_colaborador('  test preinscrito  ') = q.preinscrito)::text
  from quien q;

-- ---------------------------------------------------------------------------------------

\echo ''
select n, caso, esperado, obtenido, case when ok then 'PASS' else '*** FAIL ***' end as r
  from resultado order by n;

\echo ''
select count(*) filter (where ok)         as pasaron,
       count(*) filter (where not ok)     as fallaron,
       case when count(*) filter (where not ok) = 0
            then 'TODO VERDE' else '*** HAY FALLOS ***' end as veredicto
  from resultado;

rollback;

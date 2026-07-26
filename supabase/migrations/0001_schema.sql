-- BukIn schema.
--
-- Follows docs/overview.md §6 with two changes:
--   * `codigo_ble` is dropped. It was one static code per instance, which is exactly the
--     design that lets someone screenshot the code and share it. `instance_key` plus the
--     30-second rotation replaces it.
--   * `Outbox Event` is omitted. It stays in the architecture document, not the demo.

-- Courses. A host naming a session creates one of these; the name lives here, so no
-- extra column is needed anywhere.
create table curso (
    curso_id          uuid primary key default gen_random_uuid(),
    nombre            text    not null,
    duracion_minutos  integer not null,
    modalidad         text    not null default 'PRESENCIAL'
);

-- One concrete sitting of a course.
--
-- `instancia_id` is an INTEGER and must stay one: it is serialized as a big-endian int32
-- inside the 128-bit BLE service UUID (see domain/.../crypto/AdvertisementPayload.kt).
-- A uuid would not fit in the four bytes the wire format allows.
create table instancia (
    instancia_id     integer generated always as identity primary key,
    curso_id         uuid        not null references curso (curso_id) on delete cascade,
    fecha_inicio     timestamptz not null,
    fecha_fin        timestamptz not null,
    fecha_extension  timestamptz,
    estado           text        not null default 'PROGRAMADO'
                     check (estado in ('PROGRAMADO', 'ABIERTO', 'CERRADO')),

    -- The per-instance HMAC key. Generated on the host device, sent up by
    -- `abrir_instancia`, and never returned by any endpoint. See 0002_functions.sql.
    instance_key     bytea
);

-- There is no authentication. A collaborator types their name and that is the whole
-- identity model for v1. `email` and `rut` are kept from the source ER model so the
-- production shape is unchanged, but nothing populates them yet.
create table colaborador (
    colaborador_id   uuid primary key default gen_random_uuid(),
    nombre_completo  text not null,
    email            text,
    rut              text
);

-- Attendance is a column on the enrollment, not a separate table. That is what makes
-- UNIQUE (colaborador_id, instancia_id) the thing enforcing idempotency: a second
-- confirmation collides with the row that already exists instead of inserting a duplicate.
create table inscripcion (
    inscripcion_id       uuid primary key default gen_random_uuid(),
    instancia_id         integer not null references instancia (instancia_id) on delete cascade,
    colaborador_id       uuid    not null references colaborador (colaborador_id) on delete cascade,
    aprobado             boolean not null default false,
    asistencia           boolean not null default false,
    fecha_aprobacion     timestamptz,
    origen               text    not null check (origen in ('PRE_INSCRITO', 'WALK_IN')),
    fecha_llegada        timestamptz,
    fecha_salida         timestamptz,
    metodo_confirmacion  text    check (metodo_confirmacion in ('AUTO', 'BLE', 'MANUAL')),
    atestiguado_por_id   uuid    references colaborador (colaborador_id),

    -- Not optional. This is the constraint the whole idempotency and concurrency story
    -- rests on; 300 simultaneous check-ins contend for nothing because each one touches
    -- its own row and this constraint decides insert-vs-update per row.
    unique (colaborador_id, instancia_id)
);

-- The host roster reads by instance and orders by arrival. It is the only query whose
-- cost grows with attendance.
create index inscripcion_por_instancia on inscripcion (instancia_id, fecha_llegada);

-- The session list orders by start time.
create index instancia_por_fecha on instancia (fecha_inicio);

-- `identificar_colaborador` looks people up by normalized name.
create unique index colaborador_nombre_normalizado
    on colaborador (lower(btrim(nombre_completo)));

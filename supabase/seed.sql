-- Demo fixtures.
--
-- Times are relative to now(), so the three instances stay meaningful whenever this is
-- run: one inside its window, one that has not started, one that finished yesterday.
-- That is what makes "availability is decided by the clock" visible in the app rather
-- than something you have to take on trust.
--
-- No instance is seeded with an `instance_key`. Nothing is open until a host opens it,
-- and opening it is what uploads the key. To drive a demo from the Mac beacon alone,
-- open the active instance with the known test vector key:
--
--   select abrir_instancia(
--     (select instancia_id from instancia i join curso c using (curso_id)
--       where c.nombre = 'Manejo de alimentos'),
--     '000102030405060708090a0b0c0d0e0f');
--
-- then run `swift beacon.swift --instancia <that id>` from tools/mac-ble/.

insert into curso (nombre, duracion_minutos) values
    ('Manejo de alimentos',            120),
    ('Seguridad en el puesto de trabajo', 90),
    ('Inducción corporativa',           60);

insert into instancia (curso_id, fecha_inicio, fecha_fin)
select curso_id, now() - interval '15 minutes', now() + interval '105 minutes'
  from curso where nombre = 'Manejo de alimentos';

insert into instancia (curso_id, fecha_inicio, fecha_fin)
select curso_id, now() + interval '4 hours', now() + interval '5 hours 30 minutes'
  from curso where nombre = 'Seguridad en el puesto de trabajo';

insert into instancia (curso_id, fecha_inicio, fecha_fin)
select curso_id, now() - interval '1 day', now() - interval '23 hours'
  from curso where nombre = 'Inducción corporativa';

insert into colaborador (nombre_completo, email) values
    ('Ana Restrepo',      'ana.restrepo@buk.co'),
    ('Carlos Mejía',      'carlos.mejia@buk.co'),
    ('Diana Osorio',      'diana.osorio@buk.co'),
    ('Esteban Gallego',   'esteban.gallego@buk.co'),
    ('Felipe Cárdenas',   'felipe.cardenas@buk.co'),
    ('Gabriela Ruiz',     'gabriela.ruiz@buk.co'),
    ('Héctor Zapata',     'hector.zapata@buk.co'),
    ('Isabela Naranjo',   'isabela.naranjo@buk.co');

-- Four of the eight are signed into the active session, so the roster has content and the
-- PRE_INSCRITO branch of the upsert is exercised. The other four are not, so anyone who
-- checks in from that group lands as a WALK_IN — both branches demonstrable on one screen.
insert into inscripcion (instancia_id, colaborador_id, origen, aprobado, fecha_aprobacion)
select i.instancia_id, c.colaborador_id, 'PRE_INSCRITO', true, now()
  from instancia i
  join curso cu on cu.curso_id = i.curso_id
  join colaborador c on c.nombre_completo in
       ('Ana Restrepo', 'Carlos Mejía', 'Diana Osorio', 'Esteban Gallego')
 where cu.nombre = 'Manejo de alimentos';

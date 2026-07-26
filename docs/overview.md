bukin is an application that is designed not to revolutionize the market but to improve it, attacking all the pains of the users, it is designed for the company buk originally from chile but in this case the application will be implemented in colombia. 

on the architecture.md you got my way of structuring this monorepo using conventional tech, which only the monorepo needs to be defined nothing else, the android app will be kotlin, simple and lightweight, built on the raw platform BLE APIs (BluetoothLeAdvertiser / BluetoothLeScanner). 

the scope must be ultra simple just make the demostration that the app works with proximity and that allows the users to actually take attendance with a host and a collaborator in the same room using BLE which all phones have, and the development is for android for now just to make the test and make the app work. 

we are not implementing authentication yet, we are not implementing anything robust, we are essentially reducing the scope givent the 1 day tight deadline. 

we are not spending money, we are attacking free tiers in the services that we are using so we must think of solutions and tech that works initially free and then can be scaled easily to AWS since the config is easy to maintain and to switch. 

what it is actually nice to have is to onboard the users on a 3 screen short introduction showing the capabilities and showing.

thing we haven't solved yet is to take the check out automatically, users will forget to mark it so just check in is the priority for now. 

The original assesment lives inside the assets directory at original-assesment.md

The pain points from the users live inside feedback.md which are real reviews from users on the original application called "Buk asistencia colaborador" and live on the play store: https://play.google.com/store/apps/details?id=cl.buk.asistencia.personal

The screens to the design live inside assets and you can check the intention, that is not the final UI but it is the base UI to make the test and to make the app actually standout, to feel like the entrance of a concert but corporate and make users forget it. As you can see some screens feel a bit cluttered and they need to be simplified with the only needed stuff to operate.

Full solution to the application (needs a bit of condensation to ship an initial version as soon as possible):

Solution Architect Document. BukIn: Attendance application.

1. Requisitos

R1: El sistema debe permitir que un colaborador confirme su propia asistencia a una instancia mediante una sola acción, sin que el anfitrión digite o valide el registro manualmente en flujo normal.

R2: El sistema debe permitir que un colaborador sin inscripción previa en una instancia confirme su asistencia, creando la inscripción en el acto, sin proceso retroactivo.

R3: El sistema debe verificar que el colaborador se encuentra físicamente en el lugar del curso al momento de confirmar, y no únicamente que cuenta con una sesión válida.

R4: El sistema debe producir un único registro de asistencia, sin mostrar error visible y sin generar duplicados en base de datos.

R5: El sistema debe determinar si la entrada y la salida del colaborador fueron a tiempo o tardías, tomando como referencia la fecha de inicio y final de la instancia.

R6: El sistema debe soportar concurrencia por instancia en una ventana de corto plazo, sin degradar tiempos de respuesta ni generar inconsistencias.

R7: El sistema debe propagar cada confirmación e inscripción nueva hacia el sistema externo de cursos progresivamente dependiendo del acceso a internet disponible.


2. La Propuesta

Los cursos presenciales ocurren en contextos muy distintos: oficinas con buena conectividad y bodegas o trastiendas con señal intermitente y equipos de gama media-baja. Un mecanismo que solo funcione en línea sirve al primero y falla en el segundo. La propuesta separa la verificación de presencia física, que ocurre siempre en el lugar vía Bluetooth Low Energy (BLE), de la entrega de esa confirmación al servidor, que puede ser inmediata o diferida según la conectividad disponible.

2.1 Autenticación y Seguridad de Identidad
Se asume que el colaborador ya cuenta con una sesión válida y activa a través del proveedor de identidad de su empresa. colaborador_id nunca se transmite; el backend extrae este dato de manera inmutable del token de sesión JSON Web Token (JWT).

2.2 Ciclo de Vida de la Instancia
Un proceso periódico en segundo plano transiciona automáticamente la instancia de "PROGRAMADO" a "ABIERTO" exactamente diez (10) minutos antes de la hora de inicio estipulada (fecha_inicio). La instancia se hace visible en la app de los colaboradores inscritos, pero el botón para confirmar asistencia se mantiene bloqueado hasta validar su presencia física.

2.3 Validación de Presencia Física
El anfitrión, al ingresar al aula activa la emisión de anuncio publicitario BLE, el cual se genera dinámicamente y se asocia temporalmente a la instancia_id mientras la sesión continúe abierta. El dispositivo del colaborador escanea el entorno; el botón de registro se habilita al detectar y validar el codigo_ble.

2.4 Canal Síncrono
Si el colaborador dispone de conectividad al presionar el botón de asistencia ("Check In" o "Me retiro"), el servidor registra las marcas temporales (fecha_llegada y fecha_salida) y determina la puntualidad. Si la persona olvida "Me retiro" el sistema llena metodo_confirmacion automáticamente como "AUTO" en lugar de "MANUAL", "ONLINE" o "BLE".

2.5 Canal Asíncrono de Relevo
Para evadir las severas restricciones de procesamiento en segundo plano (Background Execution limits) de iOS y Android, el dispositivo del colaborador transmite inmediatamente este paquete a través de BLE al dispositivo del anfitrión. Al recuperar conectividad, el dispositivo del anfitrión actúa como un relevo, empujando el lote completo al backend mediante el endpoint /asistencia-lote.

2.6 Registro Manual
El anfitrión puede utilizar un buscador integrado para ubicar al colaborador y registrar presencia manualmente mediante el endpoint /registro-manual. Esto se graba en la marca de asistencia asociando la llave atestiguado_por_id con el identificador del anfitrión para fines de auditoría.

2.7 Extensión de Ventana
La instancia se cierra si no hay extensión tras 20 minutos en fecha_fin, enviando una notificación vía cola al pasar a "CERRADO", a menos que el anfitrión amplíe la ventana (fecha_extension) hasta un máximo de 60 minutos. Para garantizar la seguridad, el backend recalcula y valida individualmente la firma HMAC-SHA256 del payload (colaborador_id + instancia_id + codigo_ble + fecha_llegada) generada por la app con el token de sesión del colaborador, rechazando los intentos inválidos.


3. Diseño API

POST /instancia/{id}/asistencia
Request (Auth, Body): Token colaborador. { "codigo_ble" }
Responses: 201 Created: Registrada. | 200 OK: El Registro ya existía. | 403 Forbidden: BLE inválido. | 409 Conflict: Tiempo para registrar agotado.

POST /instancia/{id}/salida
Request (Auth, Body): Token colaborador. { "codigo_ble" }
Responses: 200 OK: Registrada. | 403 Forbidden: BLE inválido. | 409 Conflict: Tiempo para registrar agotado.

POST /instancia/{id}/extension-tiempo
Request (Auth, Body): Token anfitrión. { "minutos" }
Responses: 200 OK: Autorizada. | 403 Forbidden: No autorizado. | 409 Conflict: Fuera de rango.

POST /instancia/{id}/registro-manual
Request (Auth, Body): Token anfitrión. { "colaborador_id", "accion" }
Responses: 201 Created: Registrada. | 200 OK: Registro ya existía. | 403 Forbidden: No autorizado. | 409 Conflict: Fuera de rango.

POST /instancia/{id}/asistencia-lote
Request (Auth, Body): Token anfitrión. { "registros": [ { "colaborador_id", "codigo_ble", "accion", "fecha_llegada", "firma" } ] }
Responses: 200 OK: Sincronizado. | 403 Forbidden: No autorizado.

GET /instancia/{id}/colaboradores
Request (Auth, Body): Token de Anfitrión, Token de Administrador o Token de Reportería.
Responses: 200 OK: Colaboradores registrados.


4. Limitaciones

Descripción: Si el colaborador no posee cuenta corporativa activa o tiene el dispositivo inoperable.
Mitigación: Se implementa /registro-manual para que el anfitrión busque al colaborador por su identificador corporativo y fuerce el registro.

Descripción: Requiere que la app tenga Bluetooth activo y permisos concedidos.
Mitigación: La app comprueba proactivamente el estado del Bluetooth y sus permisos antes de iniciar la sesión.

Descripción: En entornos sin conexión, la persistencia de las asistencias firmadas depende transitoriamente de la memoria del dispositivo del anfitrión.
Mitigación: La app del host persiste cada confirmación en almacenamiento local apenas la recibe por BLE, antes de intentar enviarla, para sobrevivir a un reinicio de la app.


5. Arquitectura (C4)

[Diagrama — resumen del contenido visual, no texto corrido:]
- Colaborador (Persona) y Anfitrión (Persona), agrupados como app móvil "BukIn", sincronizados entre sí vía Peer-to-Peer local BLE Sync.
- Colaborador → HTTPS (POST) → Controlador de Asistencia (Software System): valida autenticación JWT, aplica lógica transaccional.
- Anfitrión → HTTPS (GET/POST) → Controlador de Asistencia.
- Controlador de Asistencia y Worker Outbox (Container) agrupados como "Buk Backend (Monolito)".
- Controlador de Asistencia → Escritura Transaccional → Base de datos (Container: PostgreSQL).
- Worker Outbox → Lectura FIFO (SKIP LOCKED) → Base de datos.
- Worker Outbox → Publicar Events → Queue (Container).
- Queue → Sistema de Cursos (Software System, externo).


6. Modelo de datos (ER)

[Diagrama entidad-relación — resumen de tablas y campos:]

Curso
- PK curso_id
- nombre (VARCHAR)
- duracion_minutos (INTEGER)
- modalidad (VARCHAR)

Instancia
- PK instancia_id
- FK curso_id
- fecha_inicio (TIMESTAMP WITH TIME ZONE)
- fecha_fin (TIMESTAMP WITH TIME ZONE)
- fecha_extension (TIMESTAMP WITH TIME ZONE)
- codigo_ble (VARCHAR)
- estado (VARCHAR: PROGRAMADO/ABIERTO/CERRADO)

Colaborador
- PK colaborador_id
- nombre_completo (VARCHAR)
- email (VARCHAR)
- rut (VARCHAR)

Inscripcion
- PK inscripcion_id
- FK instancia_id
- FK colaborador_id
- aprobado (BOOLEAN)
- asistencia (BOOLEAN)
- fecha_aprobacion (TIMESTAMP WITH TIME ZONE)
- origen (VARCHAR: PRE_INSCRITO/WALK_IN)
- fecha_llegada (TIMESTAMP WITH TIME ZONE)
- fecha_salida (TIMESTAMP WITH TIME ZONE)
- metodo_confirmacion (VARCHAR: AUTO/BLE/MANUAL)
- FK atestiguado_por_id (UUID, Nullable)
- UNIQUE (colaborador_id, instancia_id)

Outbox Event
- PK event_id
- tipo_evento (VARCHAR)
- payload (JSONB)
- estado (VARCHAR: PENDING/PROCESSED/FAILED)
- creado_en (TIMESTAMP WITH TIME ZONE)


7. Especificaciones Técnicas

R1 — Como se satisface: Sesión existente de bukin (token) y un único endpoint de confirmación de entrada; sin pantalla ni hardware adicional en la vía en línea.

R2 — Como se satisface: Rama INSERT del upsert; origen queda en WALK_IN cuando no existía inscripción previa para ese colaborador e instancia.

R3 — Como se satisface: El botón de confirmación solo se habilita al detectar el código BLE del host, y el backend valida este código contra la instancia.

R4 — Como se satisface: ON CONFLICT (colaborador_id, instancia_id) DO UPDATE ... WHERE fecha_llegada IS NULL (mismo patrón para fecha_salida); la segunda confirmación no toca la fila ni produce error.

R5 — Como se satisface: Puntualidad se calcula comparando fecha_llegada contra fecha_inicio y fecha_salida contra fecha_fin.

R6 — Como se satisface: Restricción UNIQUE por fila individual; no existe un contador compartido que sea punto de contención bajo carga.

R7 — Como se satisface: Un worker hace polling con SELECT ... FOR UPDATE SKIP LOCKED, que evita que múltiples workers compitan por la misma fila.


8. Alternativas Consideradas

Alternativa Evaluada: Códigos QR estáticos / GPS.
Alternativa Seleccionada: Transmisión Dinámica por BLE (Bluetooth Low Energy).
Justificación: Vulnerable a fraude por compartir capturas o spoofing de coordenadas GPS.

Alternativa Evaluada: Bloqueos distribuidos de aplicación (Redis / Redlock).
Alternativa Seleccionada: Sentencia nativa SQL UPSERT en PostgreSQL con índice Compuesto Único.
Justificación: Muy bajo en consumo de recursos y mitiga reintentos accidentales sin mostrar pantallas de error.

Alternativa Evaluada: Autosincronización en segundo plano de cada colaborador (como principal).
Alternativa Seleccionada: Relevo por Anfitrión.
Justificación: Centraliza la sincronización asíncrona en el dispositivo del anfitrión.

Alternativa Evaluada: Llamados síncronos HTTP durante la petición de asistencia.
Alternativa Seleccionada: Outbox asíncrono con SKIP LOCKED.
Justificación: Una caída del sistema externo de capacitaciones no impide que los colaboradores marquen su asistencia sin contratiempos.


9. Riesgos

Riesgo: Degradación de rendimiento del sistema externo durante la propagación de eventos.
Mitigación: Ante fallos HTTP, el worker reintenta el envío y desvía el registro a una Dead Letter Queue (DLQ) con alertas tras N intentos.
Costo-Beneficio: Bajo, requiriendo únicamente una tabla operativa adicional y la lógica del worker en el backend.

Riesgo: Apagado forzado o pérdida de señal del anfitrión.
Mitigación: El anfitrión guarda el paquete de forma local; lo sube al backend automáticamente al recuperar conexión.
Costo-Beneficio: Moderado, debido a la necesidad de gestionar colas de sincronización locales.

Riesgo: Desincronización maliciosa.
Mitigación: El backend procesa /asistencia-lote verificando que la fecha_llegada del paquete firmado esté dentro de un rango de tiempo permitido.
Costo-Beneficio: Nulo, cláusula condicional de validación en el controlador de la API.

Riesgo: El job que cachea estado se atrasa o falla.
Mitigación: La validación real se recalcula en vivo, así que el peor caso es un panel desactualizado.
Costo-Beneficio: Bajo, ninguna falla de negocio.

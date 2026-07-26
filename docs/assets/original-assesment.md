Prueba Técnica -Ejecución y Reportería Buk
Bienvenido/a al proceso de reclutamiento para sumarte como Software Engineer al equipo de
Ejecución y reportería.
A grandes rasgos el proceso consiste en:
-
Entrevista con equipo de reclutamiento de buk
-
Entrevista con EM del equipo
-
Ejercicio de misión ( 👈 Tú estás aquí 📌 )
El ejercicio de misión es algo clave para nosotros, no buscamos ver cómo desarrollas,
buscamos entender cómo piensas y tu capacidad para presentar una solución (como dato,
también para resolver dudas, ahí te la dejo 👀 ).
Este ejercicio consta de 3 etapas:
1. Discovery técnico: 4 días para investigar sobre el tema y presentar una propuesta de
solución.
a. Participantes:
i. Candidato
ii. EM
b. Entregable:
i. Documento de plantilla técnica del problema.
ii. Deadline: 3 días después de enviado
c. Modalidad: Async
2. Presentación discovery técnico: reunión de 45 minutos donde discutimos la
propuesta
a. Participantes:
i. Candidato
ii. EML
iii. EM
b. Entregable:
i. Documento de plantilla técnica del problema.
ii. Deadline: 3 días después de enviado
c. Modalidad: Sync (reunión)
Siguientes pasos
1. Analiza el problema que te presentamos
2. Elabora una propuesta que resuelva este problema (guíate por la plantilla técnica).
3. Enjoy!
¡Hola! Te damos la bienvenida a la siguiente etapa de nuestro proceso de selección.
Esta prueba tiene como objetivo conocer tu enfoque para resolver problemas complejos, tu
capacidad para diseñar soluciones técnicas robustas y tu habilidad para comunicar tus
decisiones. No esperamos un código listo para producción ni una implementación completa.
Nos interesa mucho más tu proceso de pensamiento y la solidez de tu propuesta.
En Buk no desarrollamos cualquier cosa, aplicamos estrategia en todo lo que hacemos, pero
¿qué implica esto?, sencillo, nos cuestionamos las cosas antes de ejecutarlas, velando por que
nuestro trabajo sea EXCELENTE, pero algo clave al mismo tiempo, sabemos que la excelencia
no se da de un día para otro, por lo que creemos en las entregas de valor constante (acotando
los problemas) que nos llevan hacia allá.
Para lograr esto, primero:
-
-
-
-
Identificamos un problema (en base a evidencia)
Priorizamos (en base a números de mayor impacto y/o necesidad)
Definimos el éxito esperado
Proponemos una solución para resolver estos problemas
En este ejercicio daremos por hecho de que el problema y la priorización justifican avanzar en
esta misión.
1. Contexto del Problema
Somos un equipo que desarrolla el módulo de capacitaciones integrada dentro de todo el
ecosistema de Buk (Gestión de personas). Uno de los flujos que administra este módulo son
los cursos presenciales: capacitaciones que se dictan en terreno (salas, plantas, oficinas) y
que requieren dejar evidencia de quiénes asistieron, tanto para fines de gestión como
normativos o de auditoría.
Hoy, cuando una empresa dicta un curso presencial, el registro de asistencia se hace de forma
manual: alguien recorre la sala con una lista impresa o un Excel, y luego una persona
administrativa digita esa información en el sistema después de terminado el curso. Esto genera:
-
-
-
-
Alto reproceso administrativo (digitar entre 80 y 100 asistentes después de cada curso).
Errores humanos (identificadores mal digitados, listas incompletas).
Falta de certeza de que quien firmó fue realmente quien asistió.
Demoras para responder ante una auditoría sobre quién asistió a qué curso y cuándo.
Además, no todas las personas que asisten a un curso presencial estaban inscritas de
antemano: en la práctica, es común que alguien llegue y participe sin estar en el listado original,
y hoy esa persona queda fuera del registro o debe inscribirse manualmente después, de forma
retroactiva.
2. El Desafío
Tu misión es diseñar un mecanismo de autorregistro de asistencia para cursos presenciales,
que le permita a cada participante confirmar su propia asistencia desde su celular al momento
del curso, sin depender de que un administrador digite nada después.
El problema central es: ¿cómo diseñar un flujo que permita a un participante confirmar su
asistencia a una instancia específica de un curso, sin fricción, funcionando tanto para
quienes ya estaban inscritos como para quienes no, y dejando un registro confiable y
auditable de esa confirmación?
Algunas restricciones y particularidades a considerar en tu diseño:
-
-
-
-
-
Un mismo curso puede tener múltiples instancias (fechas/sesiones), cada una con su
propio grupo de participantes.
Un participante podría intentar confirmar su asistencia más de una vez (por error, o por
mala fe) y el sistema debe comportarse de forma consistente frente a eso.
El registro de asistencia debería quedar acotado a la ventana de tiempo real de la
instancia (evitar que alguien confirme asistencia días después, o antes de que empiece
el curso).
Es esperable que en una misma instancia, decenas o cientos de participantes confirmen
su asistencia en un lapso de pocos minutos (al inicio del curso).
Los datos de asistencia y las inscripciones nuevas (para quienes no estaban inscritos)
eventualmente deben reflejarse en otros sistemas o reportes que consumen esta
información; no pueden quedar aislados en este módulo.
3. Alcance
No es necesario resolver todo el ciclo de vida del módulo de capacitaciones. Para este
ejercicio, enfócate en la habilitación tecnológica del flujo de autorregistro: qué modelos,
servicios y endpoints se necesitan para que esto funcione. No es necesario diseñar las
pantallas ni el flujo de UI en detalle.
Fuera de alcance para esta primera iteración:
-
-
-
Acciones masivas de registro por parte del administrador.
Reglas de aprobación o certificación del curso (nota mínima, porcentaje de asistencia
requerido, etc.).
Diseño visual de las pantallas.
Si quieres ir más lejos, puedes describir brevemente cómo evolucionarías la solución para
permitir que el administrador marque asistencia de forma manual o masiva desde una vista
propia, o cómo emitirías un certificado de asistencia a partir de estos datos.
4. Requisitos Clave de la Solución
Tu propuesta debe considerar los siguientes puntos:
-
-
-
-
-
-
-
Modelo de datos: diseña el esquema necesario para representar un curso, sus
instancias, los participantes y el registro de asistencia (tanto para inscritos previamente
como para quienes no lo estaban).
Identificación y confirmación: define cómo un participante llega a confirmar su
asistencia a una instancia específica y cómo el sistema lo identifica.
Caso "no inscrito": define qué ocurre cuando alguien confirma asistencia sin estar
previamente inscrito en la instancia.
Idempotencia y ventana de validez: define cómo evitar registros duplicados o
confirmaciones fuera de la ventana de tiempo válida de la instancia.
Concurrencia: la solución debe soportar que muchos participantes confirmen su
asistencia casi al mismo tiempo, sin generar inconsistencias.
Propagación: define cómo los datos de asistencia (y las inscripciones nuevas) se
comunican hacia el resto del sistema o hacia sistemas externos que dependen de esta
información, considerando que ese envío puede fallar.
API: define los endpoints principales que expondría este módulo, tanto para el
participante como para quien necesite consultar el estado de asistencia.
Contexto adicional
Para el desarrollo de la solución, puedes suponer que trabajas:
-
-
-
-
En una aplicación monolítica, donde el resto de los equipos consume estos datos
directamente vía métodos de servicio o vistas dentro de la misma aplicación.
Con una base de datos relacional (PostgreSQL).
Con una cola de mensajes para comunicarte de forma asíncrona con un sistema
externo que también gestiona parte de la información de cursos; puedes tratarlo como
una caja negra con la que te integras mediante mensajes (no necesitas conocer su
implementación interna).
El módulo puede recibir picos de cientos de confirmaciones de asistencia en pocos
minutos, coincidiendo con el inicio de cursos masivos.
Modelo de datos existente (simplificado)
Antes de esta iteración, el sistema ya cuenta con estas entidades. Puedes asumir que existen y
diseñar sobre ellas, extendiéndolas si lo necesitas:
class Diagram
class Curso {
+String nombre
+Integer duracion_minutos
+String modalidad
}
class Instancia {
+DateTime fecha_inicio
+DateTime fecha_fin
+String lugar
}
class Colaborador {
+String nombre_completo
+String email
+String rut
}
class Inscripcion {
+Boolean aprobado
+Boolean asistencia
+DateTime fecha_aprobacion
}
Curso "1" -- "*" Instancia : tiene
Instancia "1" -- "*" Inscripcion : agrupa
Colaborador "1" -- "*" Inscripcion : tiene
Descripción de las entidades:
-
-
-
-
Curso: representa un curso presencial ofrecido por la empresa.
Instancia: representa una sesión o fecha concreta en la que se dicta el curso.
Colaborador: representa a la persona que puede participar en un curso.
Inscripción: representa la relación entre un colaborador y una instancia, junto con su
estado (asistencia, aprobación, etc.).
5. Entregables Esperados
Debes crear un documento técnico (formato Google Docs o PDF, máximo 5 páginas) que
detalle tu propuesta de solución. Este documento debe incluir:
1. Resumen de la Solución: Una breve descripción de tu enfoque y las decisiones
principales que tomaste.
2. Diseño de Arquitectura: Un diagrama de alto nivel (puedes usar C4, diagramas de
componentes o el que prefieras) que muestre cómo interactúa este nuevo módulo
con el resto del sistema y con los proveedores externos.
3. Diseño del Modelo de Datos: Un diagrama Entidad-Relación (ERD) o un esquema
similar que represente las tablas principales, sus campos y relaciones (ej. Cursos,
instancias, inscripciones, etc.)
4. Diseño de la API: la definición de al menos tres endpoints clave, describiendo
request, response y su propósito.
5. Justificación técnica: explica por qué tomaste ciertas decisiones y qué
alternativas consideraste, especialmente para los puntos de concurrencia,
idempotencia y comunicación con el sistema externo.
6. Consideraciones adicionales (opcional): cómo abordarías la seguridad, el
manejo de errores, o qué podría salir mal y cómo lo mitigarías.
Cada uno de estos puntos se pueden ver en la plantilla técnica.
6. Criterios de Evaluación
Evaluaremos principalmente:
-
-
-
-
Claridad y solidez del diseño.
Capacidad para identificar y resolver los puntos críticos del problema: no inscritos,
duplicados, concurrencia, comunicación con sistemas externos.
Justificación de decisiones: tu capacidad para explicar por qué tomaste ciertas
decisiones técnicas y qué alternativas consideraste.
Pragmatismo: un enfoque realista y equilibrado entre la solución ideal y lo que es
práctico de implementar en el tiempo dado.
7. Proceso y Tiempos
●
●
●
Tiempo de entrega: Tienes 1 semana para completar y enviar tu propuesta.
○
💡 Recomendamos que antes de esta semana nos compartas el documento,
no importa que aún no esté listo, ¡podemos darte feedback si lo necesitas!
Formato de entrega: Comparte el documento vía Google Docs (con permisos para
comentar) o envía un PDF a la dirección de correo proporcionada. (Ideal el google
doc)
Siguiente paso: Tras la revisión, agendarémos una reunión de 45 minutos para que
presentes tu solución, nos cuentes tu razonamiento y respondas a nuestras preguntas.
¡Mucha suerte! Estamos muy interesados en ver cómo abordarías este desafío.
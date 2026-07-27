# BukIn

Asistencia a capacitaciones con un toque. El anfitrión abre la sala y su teléfono emite una
señal Bluetooth. Los colaboradores que están en la sala reciben esa señal y confirman su
asistencia con un toque. Nadie escribe listas después.

Android, Kotlin, Jetpack Compose. Base de datos en Supabase. Sin servidor propio.

## Descarga

El APK está en la sección **Releases** de este repositorio. Se instala directamente en un
teléfono Android 8.0 o superior.

## La idea en tres pasos

```mermaid
flowchart LR
    A["Anfitrión<br/>abre la sala"] --> B["El teléfono emite<br/>un código por Bluetooth"]
    B --> C["El colaborador<br/>recibe el código"]
    C --> D["Un toque"]
    D --> E["Postgres verifica<br/>y guarda la asistencia"]
```

## Por qué el código cambia cada 30 segundos

Un código fijo se puede fotografiar y mandar por WhatsApp a alguien que está en su casa. Ese
es el fraude que este diseño elimina.

Cada instancia tiene una llave secreta de 16 bytes. El anfitrión la genera en su teléfono y
la sube una sola vez. Con esa llave se calcula un código nuevo cada 30 segundos:

```
código = HMAC-SHA256(llave, instancia_id + contador)[primeros 8 bytes]
contador = segundos_unix / 30
```

El colaborador escucha ese código y lo reenvía tal cual. Postgres vuelve a calcularlo con la
llave que tiene guardada y compara. Si coinciden, guarda la asistencia. Si el código tiene
más de una ventana de antigüedad, lo rechaza.

La llave viaja hacia arriba solamente. El anfitrión la sube, la base de datos la guarda,
ningún endpoint la devuelve. Así el teléfono del colaborador nunca puede generar códigos por
su cuenta.

```mermaid
sequenceDiagram
    participant H as Anfitrión
    participant C as Colaborador
    participant P as Postgres
    H->>P: abrir_instancia(id, llave)
    loop cada 30 segundos
        H-->>C: Bluetooth: BUKN + instancia_id + código
    end
    C->>P: confirmar_asistencia(id, colaborador, código)
    P->>P: recalcula el código con la llave guardada
    P-->>C: OK, CODIGO_INVALIDO o FUERA_DE_VENTANA
```

## El esquema de la base de datos

Cuatro tablas.

```mermaid
erDiagram
    curso ||--o{ instancia : "tiene"
    instancia ||--o{ inscripcion : "recibe"
    colaborador ||--o{ inscripcion : "hace"

    curso {
        uuid curso_id PK
        text nombre
        integer duracion_minutos
        text modalidad
    }
    instancia {
        integer instancia_id PK
        uuid curso_id FK
        timestamptz fecha_inicio
        timestamptz fecha_fin
        text estado
        bytea instance_key
    }
    colaborador {
        uuid colaborador_id PK
        text nombre_completo
        text email
        text rut
    }
    inscripcion {
        uuid inscripcion_id PK
        integer instancia_id FK
        uuid colaborador_id FK
        boolean asistencia
        timestamptz fecha_llegada
        text origen
        text metodo_confirmacion
        uuid atestiguado_por_id
    }
```

**curso** es el contenido. Manejo de alimentos, primeros auxilios.

**instancia** es una fecha concreta de ese curso. Aquí vive `instance_key`, la llave con la
que se calculan los códigos.

`instancia_id` es un entero y tiene que seguir siéndolo. Va serializado como int32 dentro
del UUID de 128 bits que viaja por Bluetooth, y ahí caben cuatro bytes exactos.

**colaborador** es una persona.

**inscripcion** conecta a una persona con una fecha. La asistencia es una columna de esta
tabla, no una tabla aparte. Eso hace que `UNIQUE (colaborador_id, instancia_id)` sea lo que
garantiza que dos toques seguidos produzcan una sola fila.

Tres columnas cuentan la historia de cada asistencia:

| columna | valores | qué significa |
| --- | --- | --- |
| `origen` | `PRE_INSCRITO`, `WALK_IN` | si la persona se inscribió antes o llegó directo |
| `metodo_confirmacion` | `BLE`, `MANUAL`, `AUTO` | cómo quedó registrada |
| `atestiguado_por_id` | uuid del anfitrión | quién dio fe, cuando el registro fue a mano |

## Cómo está protegida

La llave pública de Supabase viaja dentro del APK. Cualquiera que descargue el archivo la
tiene. El diseño parte de esa base.

Todas las tablas tienen row level security activo y cero políticas. Los permisos de tabla
están revocados. Esa llave pública solo puede ejecutar nueve funciones, y cada una valida lo
que recibe antes de tocar una fila.

```mermaid
flowchart TD
    APK["APK con la llave pública"] --> RPC["Nueve funciones validadas"]
    APK -.->|"42501 permiso denegado"| T["curso, instancia,<br/>colaborador, inscripcion"]
    RPC --> T
```

Consultar una tabla directamente con esa llave responde `42501 permission denied`. Está
comprobado sobre las cuatro tablas.

## Los estados de la pantalla

La pantalla del colaborador es una máquina de estados. El ticket y el pie de página se
quedan quietos. Solo cambia el centro.

```mermaid
stateDiagram-v2
    [*] --> EsperandoHora: la sesión abre más tarde
    EsperandoHora --> Buscando: llegó la hora
    [*] --> Buscando
    Buscando --> Listo: escuchó al anfitrión
    Listo --> Buscando: perdió la señal
    Listo --> Enviando: un toque
    Enviando --> Registrado
    Enviando --> Error: el servidor rechazó
    Error --> Buscando: reintentar
    Registrado --> [*]
```

Cada estado se ve distinto de un vistazo. Esa fue la queja más fuerte sobre el producto que
esto reemplaza: los botones se quedaban iguales y la gente terminaba sin saber si su
asistencia había quedado.

Cuando el servidor rechaza el código dos veces seguidas, la app deja de prometer que se va a
arreglar sola y dice qué pasa: la señal de la sala no coincide con lo que el servidor tiene.
Ahí ofrece el camino que sí funciona, que es pedirle al anfitrión el registro a mano.

## Estructura del proyecto

```
app/                    navegación y pantallas compartidas
domain/                 el códec del código rotativo, Kotlin puro, con pruebas
core/ble/               emisor, receptor, permisos, servicio en primer plano
core/data/              cliente de Supabase y repositorio
core/designsystem/      colores, tipografía, componentes, textos
features/onboarding/    las cuatro pantallas de bienvenida
features/checkin/       la pantalla del colaborador
features/host/          sala, lista de llegadas, registro a mano
supabase/migrations/    el esquema y las nueve funciones
```

`domain` no importa nada de Android. Por eso el códec se prueba en la JVM, sin teléfono.

## Compilar

```bash
./gradlew assembleDebug
./gradlew test
```

Las pruebas del códec incluyen un vector conocido. Con la llave
`000102030405060708090a0b0c0d0e0f`, la instancia 42 y el contador 58000000, el código es
`67e94bf8a08959ea`. Kotlin, Swift y SQL calculan ese mismo valor. Ese vector es la prueba de
que las tres implementaciones hablan el mismo idioma.

## Lo que este demo hace y lo que deja pendiente

Registra la entrada. La salida la maneja el anfitrión.

Hay dos límites que conviene decir en voz alta.

**Reenvío en tiempo real.** Alguien puede retransmitir la señal por internet a un cómplice
que está lejos. Ningún esquema de proximidad por Bluetooth resuelve eso. La rotación del
código sí elimina las fotos, los reenvíos por chat y la repetición de un código viejo. La
lista de llegadas del anfitrión queda como verificación humana.

**Identidad.** Bluetooth prueba que un teléfono estuvo en la sala. No prueba de quién es.
En esta versión la persona escribe su nombre. Con autenticación esto se cierra, y el diseño
ya está preparado: el `colaborador_id` que hoy manda el cliente pasa a salir del token, que
es un argumento de diferencia en cada función.

## Hardware con el que se probó

Un teléfono Samsung Galaxy A54 con Android 16, conectado por adb inalámbrico, y un Mac que
hace de segunda radio Bluetooth. El Mac emite la misma señal que emitiría un anfitrión
Android, porque el diseño permite que cualquier equipo con la llave transmita el mismo
código. Las herramientas del Mac están en `tools/mac-ble/`.

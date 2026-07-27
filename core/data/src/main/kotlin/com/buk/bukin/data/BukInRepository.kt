package com.buk.bukin.data

import com.buk.bukin.data.dto.AsistenciaDto
import com.buk.bukin.data.dto.ColaboradorDto
import com.buk.bukin.data.dto.InstanciaDto
import com.buk.bukin.domain.model.Asistente
import com.buk.bukin.domain.model.Colaborador
import com.buk.bukin.domain.model.Instancia
import com.buk.bukin.domain.model.MetodoConfirmacion
import com.buk.bukin.domain.model.Origen
import com.buk.bukin.domain.model.ResultadoConfirmacion
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Every network call the app makes.
 *
 * One repository rather than one per table: all of this is a single PostgREST surface of
 * nine functions, and splitting it by table would be three files of delegation for a demo
 * with no second consumer. DTOs stay private to this module; everything returned here is a
 * `:domain` type.
 *
 * There is no direct table access anywhere in this file, and there cannot be — row level
 * security denies it. If a table query ever started working, that would be a bug in the
 * migration, not an opportunity.
 */
object BukInRepository {

    private val postgrest get() = BukInBackend.client.postgrest

    // ------------------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------------------

    /**
     * Find-or-create by typed name. Returns the collaborator id to remember on device.
     *
     * Names are matched normalized server-side, so reinstalling returns the same person
     * rather than a duplicate.
     */
    suspend fun identificar(nombre: String): Result<Colaborador> = call {
        val id = postgrest.rpc("identificar_colaborador", buildJsonObject {
            put("p_nombre", nombre)
        }).decodeAs<String>()
        Colaborador(id = id, nombre = nombre.trim())
    }

    // ------------------------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------------------------

    /**
     * Every session, newest first, annotated for [colaboradorId] if one is given.
     *
     * `activa` is computed by the server from the clock. Deciding it on device would let the
     * app and the RPC disagree, which shows up as a button that unlocks and then fails.
     */
    suspend fun listarInstancias(colaboradorId: String?): Result<List<Instancia>> = call {
        postgrest.rpc("listar_instancias", buildJsonObject {
            put("p_colaborador_id", colaboradorId)
        }).decodeList<InstanciaDto>().map { it.toDomain() }
    }

    /** Signing into a session ahead of time. This is what makes someone `PRE_INSCRITO`. */
    suspend fun inscribir(instanciaId: Int, colaboradorId: String): Result<Unit> = call {
        postgrest.rpc("inscribir", buildJsonObject {
            put("p_instancia_id", instanciaId)
            put("p_colaborador_id", colaboradorId)
        })
        Unit
    }

    /** The host naming a session. Returns the new instancia id. */
    suspend fun crearInstancia(nombre: String, duracionMinutos: Int): Result<Int> = call {
        postgrest.rpc("crear_instancia", buildJsonObject {
            put("p_nombre", nombre)
            put("p_duracion_minutos", duracionMinutos)
        }).decodeAs<Int>()
    }

    // ------------------------------------------------------------------------------------
    // Host
    // ------------------------------------------------------------------------------------

    /**
     * Opens the room: uploads the per-instance key and returns server time.
     *
     * The key travels up and never comes back down — no endpoint returns it. With no
     * authentication, an endpoint that handed it out would let anyone generate valid codes
     * from anywhere, which needs no physical presence at all and is strictly worse than the
     * relay attack.
     *
     * The returned [Instant] is what corrects the host's clock. A host phone with automatic
     * time switched off otherwise emits codes rejected 100% of the time, with no symptom.
     */
    suspend fun abrirInstancia(instanciaId: Int, key: ByteArray): Result<Instant> = call {
        val serverNow = postgrest.rpc("abrir_instancia", buildJsonObject {
            put("p_instancia_id", instanciaId)
            put("p_key_hex", key.toHex())
        }).decodeAs<String>()
        serverNow.toInstant()
    }

    /** The live roster. Narrow on purpose — this is the only query that grows with attendance. */
    suspend fun listarAsistencia(instanciaId: Int): Result<List<Asistente>> = call {
        postgrest.rpc("listar_asistencia", buildJsonObject {
            put("p_instancia_id", instanciaId)
        }).decodeList<AsistenciaDto>().map { it.toDomain() }
    }

    /** Who the host can register by hand, and who is already accounted for. */
    suspend fun listarColaboradores(instanciaId: Int): Result<List<Colaborador>> = call {
        postgrest.rpc("listar_colaboradores", buildJsonObject {
            put("p_instancia_id", instanciaId)
        }).decodeList<ColaboradorDto>().map {
            Colaborador(it.colaboradorId, it.nombreCompleto, it.yaRegistrado)
        }
    }

    /**
     * The documented fallback for a dead or incompatible phone. No code is checked because
     * no radio is involved; [hostId] is recorded as `atestiguado_por_id` so the row says who
     * vouched for it.
     */
    suspend fun registrarManual(
        instanciaId: Int,
        colaboradorId: String,
        hostId: String,
    ): ResultadoConfirmacion = confirmacion {
        postgrest.rpc("registrar_manual", buildJsonObject {
            put("p_instancia_id", instanciaId)
            put("p_colaborador_id", colaboradorId)
            put("p_host_id", hostId)
        }).decodeAs<String>()
    }

    // ------------------------------------------------------------------------------------
    // The check-in itself
    // ------------------------------------------------------------------------------------

    /**
     * Submits the eight opaque bytes the phone overheard and lets Postgres judge them.
     *
     * The client holds no key and validates nothing. A client saying "the code was valid" is
     * not authorization — invariant 4 in `context/architecture.md`.
     */
    suspend fun confirmarAsistencia(
        instanciaId: Int,
        colaboradorId: String,
        code: ByteArray,
    ): ResultadoConfirmacion = confirmacion {
        postgrest.rpc("confirmar_asistencia", buildJsonObject {
            put("p_instancia_id", instanciaId)
            put("p_colaborador_id", colaboradorId)
            put("p_code_hex", code.toHex())
        }).decodeAs<String>()
    }

    // ------------------------------------------------------------------------------------

    /**
     * Runs a call off the main thread and turns a transport failure into a failed [Result]
     * rather than a thrown exception. [CancellationException] is deliberately rethrown —
     * swallowing it would break structured concurrency and leave cancelled scopes running.
     */
    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                // Rethrown, never swallowed: catching this would break structured
                // concurrency and leave cancelled scopes running.
                throw e
            } catch (e: Exception) {
                // Everything else becomes a failed Result. This is a trust boundary, and
                // nothing a server says may crash the app: a constraint violation, a
                // malformed request, a 500, or an id this device remembers that no longer
                // exists all arrive here as PostgrestRestException, not as an IOException.
                // Narrowing this to transport failures cost one FATAL EXCEPTION on the
                // phone — an unknown colaborador_id took the whole process down.
                Result.failure(e)
            }
        }

    /** True when the request never reached the server, as opposed to being refused by it. */
    private fun Throwable.esFalloDeRed(): Boolean =
        this is HttpRequestException || this is IOException

    /**
     * The two calls whose answer is a server verdict rather than data.
     *
     * A transport failure maps to [ResultadoConfirmacion.SinRed] and not to a rejection:
     * nothing was decided, and telling someone their code was invalid when the request never
     * arrived would send them chasing the wrong problem.
     */
    private suspend fun confirmacion(block: suspend () -> String): ResultadoConfirmacion =
        call(block).fold(
            onSuccess = { veredicto ->
                when (veredicto) {
                    "OK" -> ResultadoConfirmacion.Ok
                    "YA_REGISTRADO" -> ResultadoConfirmacion.YaRegistrado
                    "FUERA_DE_VENTANA" -> ResultadoConfirmacion.FueraDeVentana
                    else -> ResultadoConfirmacion.CodigoInvalido
                }
            },
            // A request that never arrived is not the same as one the server refused, and
            // telling someone "sin conexión" while they hold a working phone sends them
            // chasing a problem they do not have.
            onFailure = {
                if (it.esFalloDeRed()) {
                    ResultadoConfirmacion.SinRed
                } else {
                    ResultadoConfirmacion.ErrorServidor
                }
            },
        )
}

private fun InstanciaDto.toDomain() = Instancia(
    id = instanciaId,
    cursoNombre = cursoNombre,
    duracionMinutos = duracionMinutos,
    fechaInicio = fechaInicio.toInstant(),
    fechaFin = fechaFin.toInstant(),
    activa = activa,
    abierta = abierta,
    inscrito = inscrito,
    asistencia = asistencia,
)

private fun AsistenciaDto.toDomain() = Asistente(
    colaboradorId = colaboradorId,
    nombre = nombreCompleto,
    fechaLlegada = fechaLlegada?.toInstant(),
    metodo = metodoConfirmacion?.let { runCatching { MetodoConfirmacion.valueOf(it) }.getOrNull() },
    origen = runCatching { Origen.valueOf(origen) }.getOrDefault(Origen.WALK_IN),
)

/** PostgREST renders `timestamptz` as ISO-8601 with an offset. */
private fun String.toInstant(): Instant = OffsetDateTime.parse(this).toInstant()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

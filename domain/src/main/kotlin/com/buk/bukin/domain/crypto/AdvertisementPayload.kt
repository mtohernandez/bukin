package com.buk.bukin.domain.crypto

import java.nio.ByteBuffer
import java.util.UUID

/**
 * The wire format. Sixteen bytes, exactly one 128-bit service UUID:
 *
 * ```
 *   [0..3]   magic         0x42554B4E ("BUKN") — constant; what the scan filter matches
 *   [4..7]   instancia_id  uint32, big-endian
 *   [8..15]  code          8 bytes, truncated HMAC — see RotatingCode
 *
 *   rendered: 42554B4E-XXXX-XXXX-YYYY-YYYYYYYYYYYY
 * ```
 *
 * The payload rides **inside the service UUID** rather than in a service-data field, and
 * that is the single decision this whole session rests on. iOS `CBPeripheralManager`
 * accepts only a local name and service UUIDs — an iPhone cannot advertise service data at
 * all — so a service-data payload would have made the host role permanently Android-only.
 * The same constraint is what lets a Mac stand in as the second radio during development.
 *
 * Lives in `:domain` rather than `:core:ble` because it is pure format with no Android in
 * it: `java.util.UUID` is JVM stdlib. That keeps it unit-testable without a device, which
 * is where spec 02's file manifest puts its test.
 */
object AdvertisementPayload {

    /** "BUKN". Constant across every instance, which is what makes prefix filtering work. */
    const val MAGIC: Int = 0x42554B4E

    /**
     * Scan mask: only the 4-byte magic must match, the rest is ignored. Bits set to 1 are
     * compared, bits set to 0 are not. Handed to `ScanFilter.setServiceUuid(uuid, mask)` so
     * the Bluetooth controller does the filtering — in a crowded room that keeps the app
     * off the CPU for every unrelated advertisement in range.
     */
    val MASK: UUID = UUID.fromString("ffffffff-0000-0000-0000-000000000000")

    /** Any UUID carrying the magic matches the mask; this is the one handed to the filter. */
    val FILTER: UUID = encode(instanciaId = 0, code = ByteArray(RotatingCode.CODE_BYTES))

    /** A decoded advertisement. [code] is opaque to the collaborator — it never validates it. */
    data class Decoded(val instanciaId: Int, val code: ByteArray) {
        // Data class over a ByteArray needs these; the generated ones compare identity.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Decoded && instanciaId == other.instanciaId && code.contentEquals(other.code))

        override fun hashCode(): Int = 31 * instanciaId + code.contentHashCode()
    }

    fun encode(instanciaId: Int, code: ByteArray): UUID {
        require(code.size == RotatingCode.CODE_BYTES) {
            "code must be ${RotatingCode.CODE_BYTES} bytes, was ${code.size}"
        }
        val high = (MAGIC.toLong() shl 32) or (instanciaId.toLong() and 0xFFFFFFFFL)
        val low = ByteBuffer.wrap(code).long
        return UUID(high, low)
    }

    /**
     * Returns null on anything that is not a BukIn advertisement. Never throws — this is
     * untrusted input off the air, and a malformed UUID from a stranger's beacon must cost
     * a null and not a crash.
     *
     * There is no length check to make here and that is structural, not an omission: a
     * [UUID] is 128 bits by construction and both fields below are fixed-width slices of
     * it. The only thing that can be wrong is the magic.
     */
    fun decode(uuid: UUID): Decoded? {
        val high = uuid.mostSignificantBits
        if ((high ushr 32).toInt() != MAGIC) return null
        return Decoded(
            instanciaId = high.toInt(),
            code = ByteBuffer.allocate(RotatingCode.CODE_BYTES)
                .putLong(uuid.leastSignificantBits)
                .array(),
        )
    }
}

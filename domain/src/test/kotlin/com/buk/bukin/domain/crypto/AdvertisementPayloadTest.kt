package com.buk.bukin.domain.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The decoder takes untrusted input off the air, so most of what matters here is what it
 * does with rubbish: it returns null and never throws. A stranger's beacon in the same
 * room must cost a null, not a crash on the collaborator's phone.
 */
class AdvertisementPayloadTest {

    private val code = "67e94bf8a08959ea".hexToBytes()

    @Test
    fun `round trips`() {
        val decoded = AdvertisementPayload.decode(AdvertisementPayload.encode(42, code))
        assertNotNull(decoded)
        assertEquals(42, decoded?.instanciaId)
        assertArrayEquals(code, decoded?.code)
    }

    @Test
    fun `magic occupies the first four bytes`() {
        assertTrue(
            AdvertisementPayload.encode(42, code).toString().startsWith("42554b4e-"),
        )
    }

    @Test
    fun `rejects a UUID with the wrong magic`() {
        // A real advertisement from something else entirely — Google Fast Pair's 16-bit
        // UUID promoted to 128 bits, which the phone genuinely sees in the same room.
        assertNull(AdvertisementPayload.decode(UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun `rejects a UUID that is one bit off the magic`() {
        assertNull(AdvertisementPayload.decode(UUID.fromString("42554b4f-0000-002a-67e9-4bf8a08959ea")))
    }

    @Test
    fun `accepts the all-zero instancia and the all-zero code`() {
        // Degenerate but well-formed. Only the magic decides, and the filter UUID itself
        // is exactly this shape — if it did not decode, the scan filter would be wrong.
        val decoded = AdvertisementPayload.decode(AdvertisementPayload.FILTER)
        assertNotNull(decoded)
        assertEquals(0, decoded?.instanciaId)
    }

    @Test
    fun `instancia ids above Int MAX_VALUE survive the round trip`() {
        // The layout says uint32; Kotlin has no unsigned Int here, so the top bit must
        // ride through as a negative Int rather than being lost or sign-extended into the
        // magic. Anything else silently corrupts high instance ids.
        val decoded = AdvertisementPayload.decode(AdvertisementPayload.encode(-1, code))
        assertEquals(-1, decoded?.instanciaId)
        assertArrayEquals(code, decoded?.code)
    }

    @Test
    fun `encode rejects a code of the wrong length`() {
        // A programming error, not untrusted input — this one is allowed to throw.
        listOf(ByteArray(0), ByteArray(7), ByteArray(9), ByteArray(32)).forEach { bad ->
            try {
                AdvertisementPayload.encode(42, bad)
                throw AssertionError("expected a rejection for ${bad.size} bytes")
            } catch (expected: IllegalArgumentException) {
                // as intended
            }
        }
    }

    @Test
    fun `the mask covers exactly the magic`() {
        assertEquals(-0x100000000L, AdvertisementPayload.MASK.mostSignificantBits) // ffffffff_00000000
        assertEquals(0L, AdvertisementPayload.MASK.leastSignificantBits)
    }
}

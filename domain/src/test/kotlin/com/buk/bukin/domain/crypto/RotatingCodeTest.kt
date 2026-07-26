package com.buk.bukin.domain.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The known vector. This is not a smoke test — it is the contract between three
 * implementations that can never call each other: this Kotlin, the Swift beacon in
 * `tools/mac-ble/beacon.swift`, and the `pgcrypto` expression session 3 verifies with.
 * All three must produce `67e94bf8a08959ea` from these inputs or one of them is wrong.
 *
 * Change these constants only if you are prepared to change the Swift and the SQL in the
 * same commit.
 */
class RotatingCodeTest {

    private val key = ByteArray(16) { it.toByte() }   // 000102030405060708090a0b0c0d0e0f
    private val instanciaId = 42                      // 0x0000002a
    private val counter = 58_000_000L                 // window starting unix 1_740_000_000

    private val expectedCode = "67e94bf8a08959ea".hexToBytes()

    @Test
    fun `derive matches the known vector`() {
        assertArrayEquals(expectedCode, RotatingCode.derive(key, instanciaId, counter))
    }

    @Test
    fun `known vector renders the expected service UUID`() {
        assertEquals(
            "42554b4e-0000-002a-67e9-4bf8a08959ea",
            AdvertisementPayload.encode(instanciaId, expectedCode).toString(),
        )
    }

    @Test
    fun `code is truncated to eight bytes`() {
        assertEquals(RotatingCode.CODE_BYTES, RotatingCode.derive(key, instanciaId, counter).size)
    }

    @Test
    fun `counter is the 30 second window`() {
        assertEquals(counter, RotatingCode.counterFor(1_740_000_000))
        assertEquals(counter, RotatingCode.counterFor(1_740_000_029))
        assertEquals(counter + 1, RotatingCode.counterFor(1_740_000_030))
    }

    @Test
    fun `clock offset shifts the window`() {
        assertEquals(counter + 1, RotatingCode.counterFor(1_740_000_000, clockOffsetSeconds = 30))
        assertEquals(counter - 1, RotatingCode.counterFor(1_740_000_000, clockOffsetSeconds = -30))
    }

    @Test
    fun `a different instancia gives a different code`() {
        val other = RotatingCode.derive(key, instanciaId + 1, counter)
        assertFalse(expectedCode.contentEquals(other))
    }

    @Test
    fun `a different window gives a different code`() {
        val other = RotatingCode.derive(key, instanciaId, counter + 1)
        assertFalse(expectedCode.contentEquals(other))
    }

    // ----- verify: the RFC 6238 §5.2 tolerance -----

    @Test
    fun `verify accepts the current window`() {
        assertTrue(RotatingCode.verify(key, instanciaId, expectedCode, unixSeconds = 1_740_000_000))
    }

    @Test
    fun `verify accepts one window late`() {
        // The user detected the host in the previous window and tapped in this one.
        assertTrue(RotatingCode.verify(key, instanciaId, expectedCode, unixSeconds = 1_740_000_030))
    }

    @Test
    fun `verify accepts one window early`() {
        // A host clock running slightly fast. Rejecting this fails real check-ins silently.
        assertTrue(RotatingCode.verify(key, instanciaId, expectedCode, unixSeconds = 1_739_999_970))
    }

    @Test
    fun `verify rejects two windows out`() {
        assertFalse(RotatingCode.verify(key, instanciaId, expectedCode, unixSeconds = 1_740_000_060))
        assertFalse(RotatingCode.verify(key, instanciaId, expectedCode, unixSeconds = 1_739_999_940))
    }

    @Test
    fun `verify rejects the wrong key`() {
        val wrongKey = ByteArray(16) { (it + 1).toByte() }
        assertFalse(RotatingCode.verify(wrongKey, instanciaId, expectedCode, 1_740_000_000))
    }

    @Test
    fun `verify rejects the wrong instancia`() {
        assertFalse(RotatingCode.verify(key, instanciaId + 1, expectedCode, 1_740_000_000))
    }

    @Test
    fun `verify rejects a code of the wrong length`() {
        assertFalse(RotatingCode.verify(key, instanciaId, ByteArray(4), 1_740_000_000))
        assertFalse(RotatingCode.verify(key, instanciaId, ByteArray(0), 1_740_000_000))
    }

    @Test
    fun `a sighting from this instant is fresh`() {
        assertTrue(RotatingCode.isFresh(seenAtElapsedMillis = 500_000, nowElapsedMillis = 500_000))
    }

    @Test
    fun `a sighting is fresh up to and including one whole window`() {
        assertTrue(RotatingCode.isFresh(500_000, 500_000 + 29_999))
        assertTrue(RotatingCode.isFresh(500_000, 500_000 + 30_000))
    }

    @Test
    fun `a sighting older than one window is stale`() {
        // The server accepts counter +/- 1, so this code would be rejected. Do not send it.
        assertFalse(RotatingCode.isFresh(500_000, 500_000 + 30_001))
        assertFalse(RotatingCode.isFresh(500_000, 500_000 + 60_000))
    }

    @Test
    fun `a sighting from the future is not treated as fresh`() {
        // Cannot happen with a monotonic clock, but a negative age must never read as fresh.
        assertFalse(RotatingCode.isFresh(500_000, 499_999))
    }
}

internal fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

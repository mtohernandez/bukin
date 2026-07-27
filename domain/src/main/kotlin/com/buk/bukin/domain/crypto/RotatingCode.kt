package com.buk.bukin.domain.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The rotating attendance code. TOTP-shaped, on a 30-second window.
 *
 * This is the most important code in the project and the easiest to get subtly wrong, so
 * it lives here — pure Kotlin, no Android, unit-testable without a device — and it is
 * pinned by a known-vector test. Three independent implementations must agree byte for
 * byte: this one, the Swift beacon in `tools/mac-ble/`, and the `pgcrypto` expression that
 * session 3 verifies against. The test vector is the contract between them.
 *
 * What rotation buys: a screenshot of the code is worthless a minute later, a captured
 * code cannot be replayed, and the beacon cannot be spoofed without the per-instance key.
 * What it does not buy: immunity to a live radio relay. See `context/architecture.md`.
 */
object RotatingCode {

    /** One code per 30 seconds. Short enough to kill sharing, long enough to tolerate skew. */
    const val WINDOW_SECONDS: Long = 30

    /** Truncated HMAC length. Eight bytes is what fits beside the magic and the id in one UUID. */
    const val CODE_BYTES: Int = 8

    private const val ALGORITHM = "HmacSHA256"

    /**
     * The window number for a moment in time.
     *
     * [clockOffsetSeconds] is the correction the server hands back when the session opens.
     * It is always 0 today and wired for real in session 3, but it is a parameter now so
     * that is a change of argument rather than a change of signature. A host phone with
     * automatic time switched off would otherwise generate codes rejected 100% of the time
     * with no diagnosable symptom.
     */
    fun counterFor(unixSeconds: Long, clockOffsetSeconds: Long = 0): Long =
        Math.floorDiv(unixSeconds + clockOffsetSeconds, WINDOW_SECONDS)

    /**
     * Whether an observed code is still worth submitting.
     *
     * A person can spot the host, get distracted, and tap a minute later. The server accepts
     * ±1 window, so anything older than one window is certain to be rejected — and a
     * rejection the user cannot act on reads as the app being broken. Better to admit the
     * host was lost and go back to looking.
     *
     * Both arguments are `SystemClock.elapsedRealtime()` milliseconds. Monotonic on purpose:
     * wall time can jump backwards mid-session and would make a fresh sighting look ancient.
     */
    fun isFresh(seenAtElapsedMillis: Long, nowElapsedMillis: Long): Boolean {
        val age = nowElapsedMillis - seenAtElapsedMillis
        return age in 0..(WINDOW_SECONDS * 1000)
    }

    /**
     * `HMAC-SHA256(key, instanciaId ‖ counter)[0..7]`, both operands big-endian.
     *
     * Big-endian is not incidental: it is what makes the same bytes fall out of Swift's
     * CryptoKit and of Postgres `hmac()` over the same inputs.
     */
    fun derive(key: ByteArray, instanciaId: Int, counter: Long): ByteArray {
        val message = ByteBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES)
            .putInt(instanciaId)
            .putLong(counter)
            .array()

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(message).copyOf(CODE_BYTES)
    }

    /**
     * Whether [code] is a code this key would have produced around [unixSeconds].
     *
     * Accepts `counter ± 1` per RFC 6238 §5.2. A host clock a few seconds fast produces
     * `counter + 1` codes; rejecting those would fail legitimate check-ins at every window
     * boundary for no reason a user could see or act on.
     *
     * Note this never runs on the collaborator's phone — it has no key. It exists for the
     * known-vector test and as the reference the SQL in session 3 must match. Invariant 4
     * in `architecture.md`: the server decides, not the client.
     */
    fun verify(
        key: ByteArray,
        instanciaId: Int,
        code: ByteArray,
        unixSeconds: Long,
        clockOffsetSeconds: Long = 0,
    ): Boolean {
        if (code.size != CODE_BYTES) return false
        val counter = counterFor(unixSeconds, clockOffsetSeconds)
        // MessageDigest.isEqual is the JDK's constant-time array compare.
        return (counter - 1..counter + 1).any {
            MessageDigest.isEqual(derive(key, instanciaId, it), code)
        }
    }
}

package com.buk.bukin.designsystem.component

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.buk.bukin.designsystem.R

/**
 * The confirmation tone, and the one rule that governs it.
 *
 * It plays **only** when the ringer is in `RINGER_MODE_NORMAL`. A phone on vibrate or
 * silent in a training room gets the haptic and nothing else — a check-in app that beeps
 * across a classroom because someone forgot a setting is worse than one that stays quiet.
 *
 * `SoundPool` rather than `MediaPlayer`: one short clip, decoded once and held ready, with
 * none of the prepare/start/release lifecycle a `MediaPlayer` would drag in.
 */
class BukSound internal constructor(
    private val pool: SoundPool,
    private val audio: AudioManager,
) {
    private var soundId: Int = 0
    private var loaded: Boolean = false

    internal fun load(context: Context) {
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = pool.load(context, R.raw.confirm, 1)
    }

    /** No-op on a silent or vibrating phone, and no-op if the clip is not decoded yet. */
    fun confirm() {
        if (!loaded) return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    internal fun release() = pool.release()
}

/** Null in a preview, so `@Preview` neither decodes audio nor touches the ringer. */
@Composable
fun rememberBukSound(): BukSound? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current

    val sound = remember(context) {
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Not USAGE_MEDIA: this is a UI acknowledgement, so it follows the
                    // ringer and ducks under whatever the person is actually listening to.
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        BukSound(pool, audio).also { it.load(context) }
    }

    DisposableEffect(sound) { onDispose { sound.release() } }
    return sound
}

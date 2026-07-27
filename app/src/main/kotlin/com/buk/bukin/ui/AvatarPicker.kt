package com.buk.bukin.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Picking a profile photo.
 *
 * `ActivityResultContracts.PickVisualMedia` — the photo picker, which **requires no
 * permission at all**. That matters more here than it would in another app: the whole pitch
 * of this product is that it asks for nothing, and it already declines to request location
 * for Bluetooth scanning. Asking for READ_MEDIA_IMAGES to set an avatar would undo that in
 * the one place a person is most likely to notice.
 *
 * The picked image is copied into `filesDir` because the URI the picker returns is a
 * short-lived grant that does not survive a process restart.
 *
 * @return a callback that launches the picker; the new absolute path is handed back.
 */
@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyIntoFilesDir(context, uri) }
            if (path != null) onPicked(path)
        }
    }

    return remember(launcher) {
        {
            launcher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }
    }
}

/**
 * Written to a temporary name and then renamed, so a failure part-way through cannot leave
 * a half-copied file behind that `Avatar` would try to decode.
 */
private fun copyIntoFilesDir(context: Context, uri: Uri): String? = runCatching {
    val target = File(context.filesDir, AVATAR_FILE)
    val staging = File(context.filesDir, "$AVATAR_FILE.tmp")

    context.contentResolver.openInputStream(uri)?.use { input ->
        staging.outputStream().use { output -> input.copyTo(output) }
    } ?: return null

    if (target.exists()) target.delete()
    if (!staging.renameTo(target)) return null
    target.absolutePath
}.getOrNull()

private const val AVATAR_FILE = "avatar.jpg"

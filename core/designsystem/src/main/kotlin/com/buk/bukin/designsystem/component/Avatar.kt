package com.buk.bukin.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * A photo if there is one, initials if there is not.
 *
 * **The monogram is not a placeholder, it is the normal case.** It is what the roster shows
 * for all 300 people, and it is what every collaborator sees until they choose otherwise —
 * so it has to look deliberate rather than like a missing image.
 *
 * Decoded with `BitmapFactory` and `inSampleSize` at a capped size. There is no image
 * library here on purpose: this is one small local file per install, and adding Coil to
 * decode it would be a dependency serving a single call site.
 */
@Composable
fun Avatar(
    nombre: String,
    modifier: Modifier = Modifier,
    photoPath: String? = null,
    size: Dp = AvatarSize,
    onClick: (() -> Unit)? = null,
) {
    val description = stringResource(R.string.perfil_foto_description)
    val bitmap = rememberAvatarBitmap(photoPath, size)

    Box(
        modifier = modifier
            .size(size)
            .clip(BukShape.full)
            .background(BukBlue)
            .then(
                if (onClick != null) {
                    Modifier.bukPressable(onClick = onClick, onClickLabel = description)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = iniciales(nombre),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}

/** First letter of the first two words. "Ana María Restrepo" → "AM". */
internal fun iniciales(nombre: String): String =
    nombre.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
        .uppercase(Locale.ROOT)

@Composable
private fun rememberAvatarBitmap(path: String?, size: Dp): Bitmap? {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capPx = with(density) { (size * 2).roundToPx() }

    val bitmap by produceState<Bitmap?>(initialValue = null, path, capPx) {
        value = if (path == null) null else withContext(Dispatchers.IO) { decodeCapped(path, capPx) }
    }
    return bitmap
}

/**
 * Two passes: bounds only, then a power-of-two subsample. A 12-megapixel camera photo
 * decoded at full size to fill a 40dp circle is 48 MB of heap for 6,400 visible pixels, and
 * on a mid-range phone that is an OOM rather than a slow frame.
 */
private fun decodeCapped(path: String, maxPx: Int): Bitmap? {
    if (!File(path).exists()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > maxPx && bounds.outHeight / sample > maxPx) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

val AvatarSize: Dp = 44.dp

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun AvatarPreview() {
    BukInTheme {
        Box(Modifier.size(BukSpacing.xxxl), contentAlignment = Alignment.Center) {
            Avatar(nombre = "Ana Restrepo")
        }
    }
}

package com.buk.bukin.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The two icons in the app.
 *
 * Material Symbols geometry, declared as `ImageVector` rather than pulled from
 * `material-icons-core` — material3 1.4.0 no longer brings that artifact, and adding it to
 * ship two glyphs is a dependency for 24 lines of path data. Declaring them here also keeps
 * the project's own rule intact: a `<vector>` drawable has to bake its fill colour into the
 * XML, which would put a colour outside `Color.kt`. These take a tint from the theme.
 *
 * The count is the point. The ticket used to carry a hand-drawn clock beside the largest
 * number on the screen and a hand-drawn sun on the help strip — two glyphs at unrelated
 * stroke weights, disambiguating nothing. Both are gone.
 */
object BukIcons {

    /** The help strip's affordance: this pulls up. */
    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            moveTo(7.41f, 15.41f)
            lineTo(12f, 10.83f)
            lineTo(16.59f, 15.41f)
            lineTo(18f, 14f)
            lineTo(12f, 8f)
            lineTo(6f, 14f)
            close()
        }
    }

    /** The leading back affordance in `BukTopBar`. */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }
}

private fun icon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black), pathBuilder = block)
}.build()

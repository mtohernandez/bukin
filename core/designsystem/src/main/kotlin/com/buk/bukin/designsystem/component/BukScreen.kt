package com.buk.bukin.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.bukGutter

/**
 * The one scaffold.
 *
 * Six screens each re-implemented the same
 * `Column + safeDrawingPadding + padding(gutter) + … + BukInFooter + Spacer`, which is how
 * they drifted apart: different top spacing, different footer treatment, and four of them
 * grew a bottom-of-screen `TextButton` labelled *Volver* that is not an Android pattern and
 * that no other Android app has. All six copies are deleted.
 *
 * The footer is **opt-in and off by default**. Its microcopy warns about Bluetooth before a
 * failure that mentions it, which earns its place on the check-in screen and nowhere else.
 *
 * @param title shown in the top bar; omitting both this and [onBack] omits the bar entirely.
 * @param onBack a **leading** back affordance, which is where Android puts it.
 */
@Composable
fun BukScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    footer: Boolean = false,
    footerMicrocopy: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BukField)
            .safeDrawingPadding(),
    ) {
        if (title != null || onBack != null) {
            BukTopBar(
                title = title,
                onBack = onBack,
                actions = actions,
                modifier = Modifier.padding(horizontal = bukGutter),
            )
        } else {
            Spacer(Modifier.height(BukSpacing.md))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = bukGutter),
            content = content,
        )

        if (footer) {
            Spacer(Modifier.height(BukSpacing.sm))
            BukInFooter(
                modifier = Modifier.padding(horizontal = bukGutter),
                microcopy = footerMicrocopy,
            )
        }
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun BukScreenPreview() {
    BukInTheme {
        BukScreen(title = "Tus sesiones", onBack = {}, footer = true) {
            Spacer(Modifier.height(BukSpacing.md))
        }
    }
}

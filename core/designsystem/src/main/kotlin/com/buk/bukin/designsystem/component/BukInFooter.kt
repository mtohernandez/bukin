package com.buk.bukin.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * The wordmark and the proximity microcopy.
 *
 * The microcopy is doing real work, not filling space: it explains why Bluetooth matters
 * *before* the user hits a failure that mentions it. That is why it stays visible even in
 * the success state — and it is also why the footer now appears **only on the check-in
 * screen**. On a list screen there is no imminent Bluetooth failure to pre-empt, so the
 * same two lines are just furniture.
 *
 * The whole thing is decorative to a screen reader. It is skipped with
 * `clearAndSetSemantics {}` so TalkBack focus goes ticket → action → help and stops.
 */
@Composable
fun BukInFooter(modifier: Modifier = Modifier, microcopy: Boolean = true) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark()
        if (microcopy) {
            Spacer(Modifier.height(BukSpacing.sm))
            Text(
                text = stringResource(R.string.footer_microcopy),
                style = MaterialTheme.typography.labelSmall,
                color = BukInkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `buk in.`, the real letterforms.
 *
 * Replaces a `buildAnnotatedString` that faked the mark with a system italic — a bespoke
 * script `in` is not something a font-style flag reproduces, and next to the real logo on
 * a slide the difference was the first thing anyone would see.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = WordmarkHeight) {
    Icon(
        painter = painterResource(R.drawable.buk_wordmark),
        contentDescription = stringResource(R.string.footer_wordmark_description),
        tint = BukInkMuted,
        modifier = modifier
            .height(height)
            .width(height * WordmarkAspect),
    )
}

private val WordmarkHeight = 18.dp

/** 434.39 / 129.41, the trimmed viewport of `buk_wordmark.xml`. */
private const val WordmarkAspect = 3.357f

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun BukInFooterPreview() {
    BukInTheme {
        BukInFooter(
            Modifier
                .background(BukField)
                .padding(BukSpacing.lg),
        )
    }
}

package com.buk.bukin.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * Persistent across every collaborator state.
 *
 * The microcopy is doing real work, not filling space: it explains why Bluetooth matters
 * *before* the user hits a failure that mentions it. That is why it stays visible even in
 * the success state.
 */
@Composable
fun BukInFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark()
        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = stringResource(R.string.footer_microcopy),
            style = MaterialTheme.typography.labelSmall,
            color = BukInkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** `·buk in·`, with `in` set in italic. */
@Composable
private fun Wordmark() {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(stringResource(R.string.footer_wordmark_prefix))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) {
            append(stringResource(R.string.footer_wordmark_italic))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(stringResource(R.string.footer_wordmark_suffix))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        color = BukInkMuted,
    )
}

@Preview(showBackground = true)
@Composable
private fun BukInFooterPreview() {
    BukInTheme {
        BukInFooter(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.lg),
        )
    }
}

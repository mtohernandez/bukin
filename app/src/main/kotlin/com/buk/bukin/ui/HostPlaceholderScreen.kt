package com.buk.bukin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * Stands in for `:features:host` until session 2. It is still a real destination with a
 * way back — an unfinished screen is not an excuse for a dead end.
 */
@Composable
fun HostPlaceholderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        NoticeCard(
            title = stringResource(R.string.host_placeholder_title),
            body = stringResource(R.string.host_placeholder_body),
            actionLabel = stringResource(R.string.host_placeholder_action),
            onAction = onBack,
        )
        Spacer(Modifier.weight(1f))
        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Preview(showBackground = true)
@Composable
private fun HostPlaceholderPreview() {
    BukInTheme { HostPlaceholderScreen(onBack = {}) }
}

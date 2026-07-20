package ca.voiditswarranty.roadtripradar.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.LEGAL_DISCLAIMER_TEXT
import ca.voiditswarranty.roadtripradar.model.SAFETY_NOTICE_TEXT

@Composable
fun TermsOverlay(
    visible: Boolean,
    needsAcceptance: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (needsAcceptance) {
        BackHandler {}
    }

    LaunchedEffect(visible, needsAcceptance) {
        if (visible && needsAcceptance) {
            scrollState.scrollTo(0)
        }
    }

    val maxScroll = scrollState.maxValue
    val reachedBottom = maxScroll <= 0 || scrollState.value >= maxScroll - 2
    val moreContentBelow = needsAcceptance && maxScroll > scrollState.value
    val canAccept = !needsAcceptance || reachedBottom

    val scrollMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp.coerceAtMost(480.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = scrollMaxHeight),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        Text(
                            text = LEGAL_DISCLAIMER_TEXT.trimIndent().trim(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (moreContentBelow) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        ),
                                    ),
                                ),
                        )
                    }
                }

                if (needsAcceptance && maxScroll > 0 && !reachedBottom) {
                    Text(
                        text = stringResource(R.string.terms_scroll_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (needsAcceptance) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        TextButton(onClick = {
                            (context as? Activity)?.finishAffinity()
                            onDecline()
                        }) {
                            Text(stringResource(R.string.action_decline))
                        }
                        Button(
                            onClick = onAccept,
                            enabled = canAccept,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(stringResource(R.string.action_accept))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyOverlay(
    visible: Boolean,
    onOk: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    if (!visible) return

    BackHandler { onOk() }

    val scrollMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp.coerceAtMost(480.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = scrollMaxHeight),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = SAFETY_NOTICE_TEXT.trimIndent().trim(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDontShowAgain) {
                        Text(stringResource(R.string.action_dont_show_again))
                    }
                    Button(
                        onClick = onOk,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

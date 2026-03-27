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
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.TERMS_AND_CONDITIONS_TEXT
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun TermsOverlay(vm: MapViewModel) {
    if (!vm.showTerms) return

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val needsAcceptance = vm.termsNeedAcceptance

    if (needsAcceptance) {
        BackHandler {}
    }

    LaunchedEffect(vm.showTerms, needsAcceptance) {
        if (vm.showTerms && needsAcceptance) {
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
                            text = TERMS_AND_CONDITIONS_TEXT.trimIndent().trim(),
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
                        text = "Scroll down to read the rest, then you can accept.",
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
                            System.exit(0)
                        }) {
                            Text("Decline")
                        }
                        Button(
                            onClick = { vm.acceptTerms() },
                            enabled = canAccept,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text("Accept")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { vm.dismissTerms() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

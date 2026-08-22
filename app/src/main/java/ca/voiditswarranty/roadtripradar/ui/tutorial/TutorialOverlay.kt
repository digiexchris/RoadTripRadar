package ca.voiditswarranty.roadtripradar.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R

private val ScrimColor = Color.Black.copy(alpha = 0.65f)
private val SpotlightPaddingPx = 12f
private val SpotlightCornerDp = 16.dp

@Composable
fun TutorialOverlay(
    activeGroup: TutorialGroup?,
    stepIndex: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val group = activeGroup ?: return
    val steps = remember(group) { stepsFor(group) }
    val index = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val anchors = LocalTutorialAnchors.current
    val anchorRect: Rect? = anchors?.get(step.anchorId)

    BackHandler { onSkip() }

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { SpotlightCornerDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Swallow all pointer input so underlying UI cannot be interacted with
            // while a coachmark is shown.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val maxWidthPx = with(density) { maxW.toPx() }
        val maxHeightPx = with(density) { maxH.toPx() }

        // Scrim with a spotlight cutout (if anchor is available).
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = ScrimColor, size = size)
            val rect = anchorRect
            if (rect != null && rect.width > 0f && rect.height > 0f) {
                val padded = Rect(
                    left = (rect.left - SpotlightPaddingPx).coerceAtLeast(0f),
                    top = (rect.top - SpotlightPaddingPx).coerceAtLeast(0f),
                    right = (rect.right + SpotlightPaddingPx).coerceAtMost(size.width),
                    bottom = (rect.bottom + SpotlightPaddingPx).coerceAtMost(size.height),
                )
                when (step.spotlightShape) {
                    SpotlightShape.CIRCLE -> {
                        val cx = (padded.left + padded.right) / 2f
                        val cy = (padded.top + padded.bottom) / 2f
                        val radius = maxOf(padded.width, padded.height) / 2f
                        drawCircle(
                            color = Color.Black,
                            radius = radius,
                            center = Offset(cx, cy),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    SpotlightShape.ROUNDED_RECT -> {
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(padded.left, padded.top),
                            size = Size(padded.width, padded.height),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                }
            }
        }

        // Caption card, positioned near the spotlight (or centered for full-screen steps).
        val cardMaxWidthDp = 360.dp
        val cardSideInsetDp = 16.dp
        val captionModifier = Modifier
            .widthIn(max = cardMaxWidthDp)
            .width(
                (maxW - cardSideInsetDp * 2).coerceAtMost(cardMaxWidthDp)
            )

        if (anchorRect == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = cardSideInsetDp),
                contentAlignment = Alignment.Center,
            ) {
                CaptionCard(
                    step = step,
                    stepIndex = index,
                    stepCount = steps.size,
                    onBack = onBack.takeIf { index > 0 },
                    onSkip = onSkip,
                    onNext = onNext,
                    modifier = captionModifier,
                )
            }
        } else {
            // Decide whether to place caption above or below the spotlight.
            val spotlightCenterYPx = (anchorRect.top + anchorRect.bottom) / 2f
            val placeBelow = spotlightCenterYPx < maxHeightPx / 2f
            val gapDp = 24.dp
            val gapPx = with(density) { gapDp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = cardSideInsetDp, vertical = 16.dp),
            ) {
                val topPaddingDp = if (placeBelow) {
                    with(density) {
                        (anchorRect.bottom + SpotlightPaddingPx + gapPx)
                            .coerceIn(0f, maxHeightPx)
                            .toDp()
                    }
                } else {
                    0.dp
                }
                val bottomPaddingDp = if (!placeBelow) {
                    with(density) {
                        (maxHeightPx - anchorRect.top + SpotlightPaddingPx + gapPx)
                            .coerceIn(0f, maxHeightPx)
                            .toDp()
                    }
                } else {
                    0.dp
                }

                CaptionCard(
                    step = step,
                    stepIndex = index,
                    stepCount = steps.size,
                    onBack = onBack.takeIf { index > 0 },
                    onSkip = onSkip,
                    onNext = onNext,
                    modifier = captionModifier
                        .align(
                            if (placeBelow) Alignment.TopCenter else Alignment.BottomCenter
                        )
                        .padding(top = topPaddingDp, bottom = bottomPaddingDp),
                )
            }
        }
    }
}

@Composable
private fun CaptionCard(
    step: TutorialStep,
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.tutorial_step_progress, stepIndex + 1, stepCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.tutorial_action_skip))
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.tutorial_action_back))
                    }
                }
                Button(onClick = onNext) {
                    val isLast = stepIndex >= stepCount - 1
                    Text(
                        text = if (isLast) {
                            stringResource(R.string.tutorial_action_done)
                        } else {
                            stringResource(R.string.tutorial_action_next)
                        },
                    )
                }
            }
        }
    }
}

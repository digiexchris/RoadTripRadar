package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Drawer controls sized for gloved use: taller segmented segments (~2×) and sliders with a thicker
 * track (~1.5×) and wider thumb (~4× width vs default).
 *
 * See [Slider](https://developer.android.com/develop/ui/compose/components/slider) and
 * [Segmented buttons](https://developer.android.com/develop/ui/compose/components/segmented-button).
 */
object DrawerControlSizing {
    val segmentedButtonHeight = 80.dp
    private val defaultThumbWidth = 20.dp
    private val defaultThumbHeight = 20.dp
    private val defaultTrackHeight = 4.dp
    private val defaultSliderMinHeight = 48.dp

    val sliderThumbSize = DpSize(
        width = defaultThumbWidth * 4f,
        height = defaultThumbHeight * 1.5f,
    )
    val sliderTrackHeight = defaultTrackHeight * 1.5f
    val sliderComponentMinHeight = defaultSliderMinHeight * 1.5f
}

@Composable
fun selectionLabelTextStyle() = MaterialTheme.typography.titleMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GloveFriendlySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(DrawerControlSizing.sliderComponentMinHeight),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                thumbSize = DrawerControlSizing.sliderThumbSize,
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(DrawerControlSizing.sliderTrackHeight),
            )
        },
    )
}

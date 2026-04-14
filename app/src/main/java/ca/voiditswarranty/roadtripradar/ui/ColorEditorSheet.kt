package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.StyleJsonPatcher
import ca.voiditswarranty.roadtripradar.data.THEME_COLOR_CATEGORIES
import ca.voiditswarranty.roadtripradar.data.ThemeColorCategory
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bottom sheet for editing colors of a custom theme.
 *
 * Opens a color editor for the given [style] (must be CUSTOM_LIGHT or CUSTOM_DARK).
 * If no custom theme exists for that slot yet, offers to create one from the closest built-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorEditorSheet(
    style: MapStyle,
    vm: MapViewModel,
    currentStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    require(style.isCustom) { "ColorEditorSheet requires a custom style" }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasTheme = if (style == MapStyle.CUSTOM_LIGHT) vm.hasCustomLight else vm.hasCustomDark

    if (!hasTheme) {
        // Offer to create from built-in
        CreateFromBuiltInDialog(
            targetSlot = style,
            vm = vm,
            onStyleChange = onStyleChange,
            onDismiss = onDismiss,
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ColorEditorContent(
            style = style,
            vm = vm,
            currentStyle = currentStyle,
            onStyleChange = onStyleChange,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CreateFromBuiltInDialog(
    targetSlot: MapStyle,
    vm: MapViewModel,
    onStyleChange: (MapStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val slotLabel = if (targetSlot == MapStyle.CUSTOM_LIGHT) stringResource(R.string.style_custom_light) else stringResource(R.string.style_custom_dark)
    val builtInSource = if (targetSlot == MapStyle.CUSTOM_DARK) stringResource(R.string.style_dark_small_roads) else stringResource(R.string.style_liberty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_editor_create_title, slotLabel)) },
        text = {
            Text(
                stringResource(R.string.color_editor_no_theme, slotLabel, builtInSource),
            )
        },
        confirmButton = {
            Button(onClick = {
                if (targetSlot == MapStyle.CUSTOM_DARK) {
                    vm.initCustomThemeFromAsset(MapStyle.COLOR_DARK, targetSlot, onStyleChange)
                } else {
                    vm.initCustomThemeFromUrl(MapStyle.LIBERTY.styleUri, targetSlot, onStyleChange)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.color_editor_create_from, builtInSource))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ColorEditorContent(
    style: MapStyle,
    vm: MapViewModel,
    currentStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    // Load original JSON once
    val originalJson = remember(style) {
        vm.customThemeRepo.readThemeJson(style) ?: ""
    }

    // Working copy of per-category colors (mutable)
    val editedColors = remember(originalJson) {
        mutableStateOf(
            THEME_COLOR_CATEGORIES.associate { cat ->
                cat.id to (StyleJsonPatcher.extractColor(originalJson, cat) ?: Color.Gray)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.color_editor_edit_colors, stringResource(style.displayNameRes)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            THEME_COLOR_CATEGORIES.forEach { category ->
                val currentColor = editedColors.value[category.id] ?: Color.Gray
                ColorCategoryRow(
                    category = category,
                    color = currentColor,
                    onColorChange = { newColor ->
                        editedColors.value = editedColors.value.toMutableMap()
                            .also { it[category.id] = newColor }
                    },
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                // Revert to original colors
                editedColors.value = THEME_COLOR_CATEGORIES.associate { cat ->
                    cat.id to (StyleJsonPatcher.extractColor(originalJson, cat) ?: Color.Gray)
                }
            }) {
                Text(stringResource(R.string.action_reset))
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }

            TextButton(onClick = {
                applyAndPreview(vm, style, originalJson, editedColors.value, currentStyle, onStyleChange)
            }) {
                Text(stringResource(R.string.color_editor_preview))
            }

            Button(onClick = {
                applyAndPreview(vm, style, originalJson, editedColors.value, currentStyle, onStyleChange)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

private fun applyAndPreview(
    vm: MapViewModel,
    style: MapStyle,
    originalJson: String,
    colors: Map<String, Color>,
    currentStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
) {
    val patched = StyleJsonPatcher.applyColors(originalJson, colors)
    vm.customThemeRepo.writeThemeJson(style, patched)
    vm.notifyCustomThemeUpdated()
    // If this theme isn't currently active, switch to it so the user sees the preview
    if (currentStyle != style) {
        onStyleChange(style)
    }
}

@Composable
private fun ColorCategoryRow(
    category: ThemeColorCategory,
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorSwatch(color = color, modifier = Modifier.size(32.dp))
            Text(
                category.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
            )
        }

        if (expanded) {
            ColorPickerExpanded(color = color, onColorChange = onColorChange)
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    )
}

@Composable
private fun ColorPickerExpanded(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    var hexInput by remember(color) { mutableStateOf(StyleJsonPatcher.colorToHex(color)) }
    var hexError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Red slider
        LabeledColorSlider(
            label = "R",
            value = color.red,
            trackColor = Color.Red,
            onValueChange = { onColorChange(color.copy(red = it)) },
        )
        // Green slider
        LabeledColorSlider(
            label = "G",
            value = color.green,
            trackColor = Color(0xFF00C853),
            onValueChange = { onColorChange(color.copy(green = it)) },
        )
        // Blue slider
        LabeledColorSlider(
            label = "B",
            value = color.blue,
            trackColor = Color(0xFF2979FF),
            onValueChange = { onColorChange(color.copy(blue = it)) },
        )
        // Alpha slider
        LabeledColorSlider(
            label = "A",
            value = color.alpha,
            trackColor = MaterialTheme.colorScheme.onSurface,
            onValueChange = { onColorChange(color.copy(alpha = it)) },
        )

        // Hex input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                    val parsed = StyleJsonPatcher.parseColorString(raw.trim())
                    if (parsed != null) {
                        hexError = false
                        onColorChange(parsed)
                    } else {
                        hexError = raw.isNotEmpty()
                    }
                },
                label = { Text(stringResource(R.string.color_editor_hex)) },
                isError = hexError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(160.dp),
                leadingIcon = {
                    ColorSwatch(color = color, modifier = Modifier.size(24.dp))
                },
            )
        }
    }
}

@Composable
private fun LabeledColorSlider(
    label: String,
    value: Float,
    trackColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(12.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(32.dp),
        )
        Text(
            "${(value * 255).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(24.dp),
        )
    }
}

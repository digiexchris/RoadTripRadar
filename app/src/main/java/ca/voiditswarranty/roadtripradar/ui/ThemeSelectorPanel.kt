package ca.voiditswarranty.roadtripradar.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

// Hard-coded representative background colors for built-in themes.
// Kept as file-level vals so BuiltInThemeCard's AUTO swatch can reference them
// in its horizontal gradient; the canonical lookup lives in ThemeSelectorLogic.
private val swatchLiberty = Color(0xFFF5F1EC)
private val swatchColorDark = Color(0xFF1A1A2E)

// Local fallbacks for custom-theme swatches before the user's JSON is read.
private val swatchCustomLightFallback = Color(0xFFEEEEEE)
private val swatchCustomDarkFallback = Color(0xFF2A2A2A)

// -------- Pure helpers are extracted to ThemeSelectorLogic.kt --------

@Composable
fun ThemeSelectorPanel(
    vm: MapViewModel,
    currentStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
    onEditColors: (MapStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingImportTarget by remember { mutableStateOf<MapStyle?>(null) }

    // Memoize custom swatch colors to avoid re-reading files on every recomposition.
    val customLightSwatch = remember(vm.customThemeVersion) {
        vm.customThemeRepo.readThemeJson(MapStyle.CUSTOM_LIGHT)
            ?.let { extractBackgroundColor(it, swatchCustomLightFallback) } ?: swatchCustomLightFallback
    }
    val customDarkSwatch = remember(vm.customThemeVersion) {
        vm.customThemeRepo.readThemeJson(MapStyle.CUSTOM_DARK)
            ?.let { extractBackgroundColor(it, swatchCustomDarkFallback) } ?: swatchCustomDarkFallback
    }
    fun resolvedSwatchColor(style: MapStyle): Color = when (style) {
        MapStyle.CUSTOM_LIGHT -> customLightSwatch
        MapStyle.CUSTOM_DARK -> customDarkSwatch
        else -> swatchColorForStyle(style)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val target = pendingImportTarget ?: return@rememberLauncherForActivityResult
        vm.importCustomTheme(uri, target, onStyleChange)
        pendingImportTarget = null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.theme_map_theme), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { vm.closeThemeSelector() }) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_close))
                }
            }

            HorizontalDivider()

            // Theme cards
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // AUTO card
                item {
                    AutoThemeCard(
                        selected = currentStyle == MapStyle.AUTO,
                        onClick = { onStyleChange(MapStyle.AUTO) },
                    )
                }
                // Built-in theme cards
                items(listOf(MapStyle.LIBERTY, MapStyle.DARK, MapStyle.COLOR_DARK)) { style ->
                    BuiltInThemeCard(
                        style = style,
                        swatchColor = resolvedSwatchColor(style),
                        selected = currentStyle == style,
                        onClick = { onStyleChange(style) },
                    )
                }
                // Custom Light card (import if missing)
                item {
                    if (vm.hasCustomLight) {
                        BuiltInThemeCard(
                            style = MapStyle.CUSTOM_LIGHT,
                            swatchColor = customLightSwatch,
                            selected = currentStyle == MapStyle.CUSTOM_LIGHT,
                            onClick = { onStyleChange(MapStyle.CUSTOM_LIGHT) },
                        )
                    } else {
                        ImportThemeCard(
                            label = stringResource(R.string.style_custom_light),
                            onClick = {
                                pendingImportTarget = MapStyle.CUSTOM_LIGHT
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                        )
                    }
                }
                // Custom Dark card (import if missing)
                item {
                    if (vm.hasCustomDark) {
                        BuiltInThemeCard(
                            style = MapStyle.CUSTOM_DARK,
                            swatchColor = customDarkSwatch,
                            selected = currentStyle == MapStyle.CUSTOM_DARK,
                            onClick = { onStyleChange(MapStyle.CUSTOM_DARK) },
                        )
                    } else {
                        ImportThemeCard(
                            label = stringResource(R.string.style_custom_dark),
                            onClick = {
                                pendingImportTarget = MapStyle.CUSTOM_DARK
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                        )
                    }
                }
            }

            // Per-theme action row
            val showBuiltInActions = currentStyle != MapStyle.AUTO && !currentStyle.isCustom
            val showCustomActions = currentStyle.isCustom &&
                ((currentStyle == MapStyle.CUSTOM_LIGHT && vm.hasCustomLight) ||
                    (currentStyle == MapStyle.CUSTOM_DARK && vm.hasCustomDark))

            if (showBuiltInActions) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                BuiltInThemeActions(
                    style = currentStyle,
                    customTargetExists = if (currentStyle.intrinsicallyDark) vm.hasCustomDark else vm.hasCustomLight,
                    onOpenMaputnik = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onUseAsCustomBase = { target ->
                        if (currentStyle.intrinsicallyDark) {
                            vm.initCustomThemeFromAsset(currentStyle, target, onStyleChange)
                        } else {
                            vm.initCustomThemeFromUrl(currentStyle.styleUri, target, onStyleChange)
                        }
                    },
                )
            } else if (showCustomActions) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                val autoEnabled = if (currentStyle == MapStyle.CUSTOM_LIGHT) vm.customLightAutoEnabled else vm.customDarkAutoEnabled
                val exportChooserTitle = stringResource(R.string.theme_export)
                val exportUri = vm.customThemeRepo.exportThemeUri(currentStyle)
                CustomThemeActions(
                    style = currentStyle,
                    autoEnabled = autoEnabled,
                    onAutoModeChange = { enabled ->
                        if (currentStyle == MapStyle.CUSTOM_LIGHT) vm.updateCustomLightAutoEnabled(enabled)
                        else vm.updateCustomDarkAutoEnabled(enabled)
                    },
                    onEditColors = onEditColors,
                    onSaveToDownloads = { style ->
                        val name = vm.customThemeRepo.saveToDownloads(style)
                        val msg = if (name != null) {
                            context.getString(R.string.theme_saved_to_downloads, name)
                        } else {
                            context.getString(R.string.theme_save_failed)
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onOpenMaputnik = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://maplibre.org/maputnik/")),
                        )
                    },
                    onShare = {
                        val uri = exportUri ?: return@CustomThemeActions
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, exportChooserTitle))
                    },
                    onDelete = { style ->
                        vm.deleteCustomTheme(style, style, onStyleChange)
                    },
                    shareEnabled = exportUri != null,
                )
            }
        }
    }

    // Import error dialog
    val importError = vm.customThemeImportError
    if (importError != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissCustomThemeImportError() },
            title = { Text(stringResource(R.string.theme_invalid_file)) },
            text = { Text(importError) },
            confirmButton = {
                TextButton(onClick = { vm.dismissCustomThemeImportError() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
internal fun AutoThemeCard(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(listOf(swatchLiberty, swatchColorDark)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoMode,
                contentDescription = null,
                tint = Color(0xFF808080),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            stringResource(R.string.theme_auto),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun BuiltInThemeCard(
    style: MapStyle,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp

    val swatchTextColor = if (swatchColor.luminance() < 0.4f) Color.White else Color(0xFF333333)

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatchColor),
            contentAlignment = Alignment.Center,
        ) {
            if (style.isCustom) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = swatchTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            stringResource(style.displayNameRes),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun ImportThemeCard(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
internal fun BuiltInThemeActions(
    style: MapStyle,
    customTargetExists: Boolean,
    onOpenMaputnik: (String) -> Unit,
    onUseAsCustomBase: (MapStyle) -> Unit,
) {
    val maputnikUrl = style.maputnikUrl
    // Which custom slot does this built-in map to?
    val customTarget = if (style.intrinsicallyDark) MapStyle.CUSTOM_DARK else MapStyle.CUSTOM_LIGHT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (maputnikUrl != null) {
            TextButton(onClick = { onOpenMaputnik(maputnikUrl) }) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.theme_customize_maputnik))
            }
        }
        Spacer(Modifier.weight(1f))
        if (!style.intrinsicallyDark) {
            TextButton(onClick = { onUseAsCustomBase(customTarget) }) {
                Text(if (customTargetExists) stringResource(R.string.theme_overwrite_custom_light) else stringResource(R.string.theme_use_as_custom_light_base))
            }
        } else {
            TextButton(onClick = { onUseAsCustomBase(customTarget) }) {
                Text(if (customTargetExists) stringResource(R.string.theme_overwrite_custom_dark) else stringResource(R.string.theme_use_as_custom_dark_base))
            }
        }
    }
}

@Composable
internal fun CustomThemeActions(
    style: MapStyle,
    autoEnabled: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    onEditColors: (MapStyle) -> Unit,
    onSaveToDownloads: (MapStyle) -> Unit,
    onOpenMaputnik: () -> Unit,
    onShare: () -> Unit,
    onDelete: (MapStyle) -> Unit,
    shareEnabled: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Auto mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoMode,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.theme_use_in_auto_mode),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = autoEnabled,
                onCheckedChange = onAutoModeChange,
            )
        }

        // Edit / Save / Maputnik / Share / Delete row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { onEditColors(style) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_colors))
            }

            FilledTonalIconButton(onClick = { onSaveToDownloads(style) }) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save_json_downloads))
            }

            FilledTonalIconButton(onClick = onOpenMaputnik) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.cd_open_maputnik))
            }

            Spacer(Modifier.weight(1f))

            FilledTonalIconButton(
                onClick = onShare,
                enabled = shareEnabled,
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
            }

            FilledTonalIconButton(onClick = { onDelete(style) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_custom_theme),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Labels for the action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActionLabel(stringResource(R.string.theme_action_edit), Modifier.width(48.dp))
            ActionLabel(stringResource(R.string.theme_action_save), Modifier.width(48.dp))
            ActionLabel(stringResource(R.string.theme_action_maputnik), Modifier.width(48.dp))
            Spacer(Modifier.weight(1f))
            ActionLabel(stringResource(R.string.theme_action_share), Modifier.width(48.dp))
            ActionLabel(stringResource(R.string.theme_action_delete), Modifier.width(48.dp))
        }
    }
}

@Composable
internal fun ActionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}


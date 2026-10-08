package com.proudvocab.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proudvocab.android.R
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.FontKeys
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.TextAlignPref
import com.proudvocab.android.core.settings.TextStylePref
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.theme.previewSample
import com.proudvocab.android.ui.components.ColorSwatches
import com.proudvocab.android.ui.components.GhostButton
import com.proudvocab.android.ui.components.PreferenceRow
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.components.SegmentedPreference
import com.proudvocab.android.ui.components.SettingsCard
import com.proudvocab.android.ui.components.SliderPreference
import com.proudvocab.android.ui.components.SwitchPreference
import com.proudvocab.android.ui.theme.FontGroup
import com.proudvocab.android.ui.theme.FontOption
import com.proudvocab.android.ui.theme.rememberTargetStyle
import java.util.Locale

/** Human name of every style area. */
private fun StyleTarget.label(): Int = when (this) {
    StyleTarget.APP -> R.string.typography_app
    StyleTarget.SUBTITLE_PRIMARY -> R.string.typography_subtitle_primary
    StyleTarget.SUBTITLE_SECONDARY -> R.string.typography_subtitle_secondary
    StyleTarget.TRANSCRIPT -> R.string.typography_transcript
    StyleTarget.WORD_CARD -> R.string.typography_word_card
    StyleTarget.WORD_TRANSLATION -> R.string.typography_word_translation
    StyleTarget.DICTIONARY -> R.string.typography_dictionary
    StyleTarget.FLASHCARD_FRONT -> R.string.typography_flashcard_front
    StyleTarget.FLASHCARD_BACK -> R.string.typography_flashcard_back
    StyleTarget.GAME -> R.string.typography_game
    StyleTarget.ARCHIVE_WORD -> R.string.typography_archive_word
    StyleTarget.ARCHIVE_TRANSLATION -> R.string.typography_archive_translation
}

/** Areas that need a shadow slider (text painted over video). */
private val SHADOW_TARGETS = setOf(
    StyleTarget.SUBTITLE_PRIMARY,
    StyleTarget.SUBTITLE_SECONDARY,
    StyleTarget.TRANSCRIPT
)

private val TEXT_COLORS = listOf(
    "#FFFFFFFF", "#FFCBD5E1", "#FF94A3B8", "#FF0F172A",
    "#FFEAB308", "#FF22C55E", "#FF38BDF8", "#FFA855F7",
    "#FFF43F5E", "#FFF97316", "#FF000000", "#FF6366F1"
)

private val BACKGROUND_COLORS = listOf(
    "#FF000000", "#FF0A0F1E", "#FFFFFFFF", "#FFF1F5F9",
    "#FF1E293B", "#FF020617", "#FFFFF7ED", "#FF0F172A",
    "#80000000", "#B3000000", "#00FFFFFF", "#FFFFFBEB"
)

/**
 * Typography is edited **per area**: pick one of the twelve areas, then change
 * its font, size, colour, background, spacing, line-height and alignment.
 * Nothing here touches any other area, so the app menu can stay tiny while the
 * subtitle stays huge, and the learning language can use a different family
 * from its translation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypographyPage(vm: SettingsViewModel, settings: AppSettings) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val target = state.styleTarget
    val pref = settings.styleFor(target)
    var fontDialog by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFont(uri) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            SectionHeader(text = stringResource(R.string.typography_intro))
            SettingsCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyleTarget.ALL.forEach { area ->
                            AreaChip(
                                label = stringResource(area.label()),
                                selected = area == target,
                                onClick = { vm.setStyleTarget(area) }
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.style_preview))
            StylePreview(target = target, settings = settings)
        }

        item {
            SectionHeader(text = stringResource(target.label()))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.font_family),
                    subtitle = fontLabel(pref.font),
                    onClick = { fontDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                SliderPreference(
                    title = stringResource(R.string.font_size),
                    value = pref.scale,
                    range = 0.6f..2.2f,
                    steps = 31,
                    display = String.format(Locale.US, "%.0f%%", pref.scale * 100f),
                    onValueChange = { value -> vm.updateStyle(target) { p -> p.copy(scale = roundStep(value)) } }
                )
                SliderPreference(
                    title = stringResource(R.string.letter_spacing),
                    value = pref.letterSpacingSp,
                    range = -1f..4f,
                    steps = 19,
                    display = String.format(Locale.US, "%.1f", pref.letterSpacingSp),
                    onValueChange = { vm.updateStyle(target) { p -> p.copy(letterSpacingSp = it) } }
                )
                SliderPreference(
                    title = stringResource(R.string.line_height),
                    value = pref.lineHeight,
                    range = 0.9f..2.4f,
                    steps = 29,
                    display = String.format(Locale.US, "%.2f", pref.lineHeight),
                    onValueChange = { vm.updateStyle(target) { p -> p.copy(lineHeight = it) } }
                )
                if (target in SHADOW_TARGETS) {
                    SliderPreference(
                        title = stringResource(R.string.typography_shadow),
                        value = pref.shadowDp,
                        range = 0f..8f,
                        steps = 15,
                        display = String.format(Locale.US, "%.0f", pref.shadowDp),
                        onValueChange = { vm.updateStyle(target) { p -> p.copy(shadowDp = it) } }
                    )
                }
            }
        }

        item {
            SettingsCard {
                SwitchPreference(
                    title = stringResource(R.string.font_bold),
                    checked = pref.bold ?: target.defaultBold,
                    onCheckedChange = { vm.updateStyle(target) { p -> p.copy(bold = it) } }
                )
                SwitchPreference(
                    title = stringResource(R.string.font_italic),
                    checked = pref.italic,
                    onCheckedChange = { vm.updateStyle(target) { p -> p.copy(italic = it) } }
                )
                SwitchPreference(
                    title = stringResource(R.string.font_underline),
                    checked = pref.underline,
                    onCheckedChange = { vm.updateStyle(target) { p -> p.copy(underline = it) } }
                )
            }
        }

        item {
            SettingsCard {
                SegmentedPreference(
                    title = stringResource(R.string.text_align),
                    options = listOf(
                        TextAlignPref.START to stringResource(R.string.align_start),
                        TextAlignPref.CENTER to stringResource(R.string.align_center),
                        TextAlignPref.END to stringResource(R.string.align_end)
                    ),
                    selected = pref.align,
                    onSelect = { vm.setAlign(target, it) }
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.text_color))
            SettingsCard {
                ColorSwatches(
                    colors = TEXT_COLORS,
                    selected = pref.color,
                    onSelect = { vm.updateStyle(target) { p -> p.copy(color = it) } },
                    allowAuto = true,
                    autoLabel = stringResource(R.string.color_inherit)
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.text_background))
            SettingsCard {
                ColorSwatches(
                    colors = BACKGROUND_COLORS,
                    selected = pref.background,
                    onSelect = { vm.updateStyle(target) { p -> p.copy(background = it) } },
                    allowAuto = true,
                    autoLabel = stringResource(R.string.color_inherit)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    onClick = { vm.resetStyle(target) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.style_reset_area), fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    onClick = { vm.resetAllStyles() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.style_reset_all),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    if (fontDialog) {
        FontPickerDialog(
            current = pref.font,
            fontsVersion = state.fontsVersion,
            onDismiss = { fontDialog = false },
            onSelect = { key ->
                vm.updateStyle(target) { p -> p.copy(font = key) }
                fontDialog = false
            },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onDelete = { vm.deleteFont(it) }
        )
    }
}

/** Keeps a float rounded to the slider's 0.05 step so the label stays clean. */
private fun roundStep(value: Float): Float = Math.round(value * 20f) / 20f

@Composable
private fun AreaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "areaChip"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "areaChipText"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}

/** Live preview painted with exactly the style the user is editing. */
@Composable
private fun StylePreview(target: StyleTarget, settings: AppSettings) {
    val deps = LocalDependencies.current
    val resolved = rememberTargetStyle(target, settings, deps.fonts)
    val surface = resolved.background
        ?: if (target in SHADOW_TARGETS) Color(0xFF0A0F1E) else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .then(
                if (resolved.background == null && target !in SHADOW_TARGETS) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
            .heightIn(min = 92.dp)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = when (resolved.align) {
            androidx.compose.ui.text.style.TextAlign.Center -> Alignment.Center
            androidx.compose.ui.text.style.TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = target.previewSample,
            style = resolved.textStyle,
            textAlign = resolved.align
        )
    }
}

private fun fontLabel(key: String): String = when {
    FontKeys.isBuiltIn(key) -> key
    FontKeys.isDevice(key) -> FontKeys.deviceName(key)
    FontKeys.isCustom(key) -> FontKeys.customName(key)
    FontKeys.isBundled(key) -> FontKeys.bundledName(key)
    else -> key
}

@Composable
private fun FontPickerDialog(
    current: String,
    fontsVersion: Int,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit
) {
    val deps = LocalDependencies.current
    // Enumerating the device fonts touches the filesystem, so it happens off
    // the UI thread instead of inside `remember` during composition.
    var options by remember { mutableStateOf<List<FontOption>>(emptyList()) }
    var loadingFonts by remember { mutableStateOf(true) }
    LaunchedEffect(fontsVersion) {
        loadingFonts = true
        options = deps.fonts.allAsync()
        loadingFonts = false
    }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.group }.toList().sortedBy { it.first.ordinal }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = { Text(stringResource(R.string.font_family)) },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    onClick = onImport
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.font_import),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (loadingFonts) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                Column(modifier = Modifier.heightIn(max = 380.dp)) {
                    grouped.forEach { (group, items) ->
                        Text(
                            text = stringResource(
                                when (group) {
                                    FontGroup.BUILTIN -> R.string.font_group_builtin
                                    FontGroup.DEVICE -> R.string.font_group_device
                                    FontGroup.CUSTOM -> R.string.font_group_custom
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        items.forEach { option ->
                            FontOptionRow(
                                option = option,
                                selected = option.key == current,
                                onSelect = { onSelect(option.key) },
                                onDelete = if (group == FontGroup.CUSTOM) {
                                    { onDelete(option.key) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun FontOptionRow(
    option: FontOption,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (option.group == FontGroup.BUILTIN) {
                Text(
                    text = stringResource(
                        when (option.key) {
                            FontKeys.SYSTEM -> R.string.font_builtin_system
                            FontKeys.SANS -> R.string.font_builtin_sans
                            FontKeys.SERIF -> R.string.font_builtin_serif
                            else -> R.string.font_builtin_mono
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

package com.proudvocab.android.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proudvocab.android.BuildConfig
import com.proudvocab.android.R
import com.proudvocab.android.core.model.AppLanguage
import com.proudvocab.android.core.model.Languages
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.ThemeMode
import com.proudvocab.android.core.settings.TranslationEngine
import com.proudvocab.android.core.translate.ModelState
import com.proudvocab.android.core.util.TextUtils
import com.proudvocab.android.ui.components.ColorSwatches
import com.proudvocab.android.ui.components.Pill
import com.proudvocab.android.ui.components.PreferenceRow
import com.proudvocab.android.ui.components.PrimaryButton
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.components.SegmentedPreference
import com.proudvocab.android.ui.components.SettingsCard
import com.proudvocab.android.ui.components.SliderPreference
import com.proudvocab.android.ui.components.SwitchPreference
import kotlinx.coroutines.launch

// ================================================================ appearance

@Composable
fun AppearancePage(vm: SettingsViewModel, settings: AppSettings) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_theme))
            SettingsCard {
                SegmentedPreference(
                    title = null,
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.AMOLED to stringResource(R.string.theme_amoled)
                    ),
                    selected = settings.themeMode(),
                    onSelect = vm::setTheme
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_accent))
            SettingsCard {
                ColorSwatches(
                    colors = AppSettings.ACCENTS,
                    selected = settings.accentHex,
                    onSelect = vm::setAccent
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_section_appearance))
            SettingsCard {
                SwitchPreference(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    onCheckedChange = vm::setDynamicColor
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_animations),
                    subtitle = stringResource(R.string.settings_animations_desc),
                    checked = settings.animationsEnabled,
                    onCheckedChange = vm::setAnimations
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_persian_digits),
                    subtitle = stringResource(R.string.settings_persian_digits_desc),
                    checked = settings.usePersianDigits,
                    onCheckedChange = vm::setPersianDigits
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// ================================================================ languages

@Composable
fun LanguagesPage(vm: SettingsViewModel, settings: AppSettings) {
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_languages))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.settings_learning_language),
                    subtitle = Languages.byCode(settings.learningLanguage).nativeName,
                    onClick = { pickerFor = 0 },
                    trailing = {
                        Text(
                            text = Languages.byCode(settings.learningLanguage).nativeName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.settings_translation_language),
                    subtitle = Languages.byCode(settings.translationLanguage).nativeName,
                    onClick = { pickerFor = 1 },
                    trailing = {
                        Text(
                            text = Languages.byCode(settings.translationLanguage).nativeName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.settings_app_language),
                    subtitle = if (settings.appLanguage.isBlank()) {
                        stringResource(R.string.theme_system)
                    } else {
                        Languages.byCode(settings.appLanguage).nativeName
                    },
                    onClick = { pickerFor = 2 }
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }

    pickerFor?.let { which ->
        LanguagePickerDialog(
            title = when (which) {
                0 -> stringResource(R.string.settings_learning_language)
                1 -> stringResource(R.string.settings_translation_language)
                else -> stringResource(R.string.settings_app_language)
            },
            selected = when (which) {
                0 -> settings.learningLanguage
                1 -> settings.translationLanguage
                else -> settings.appLanguage
            },
            onDismiss = { pickerFor = null },
            onSelect = { code ->
                when (which) {
                    0 -> vm.setLearningLanguage(code)
                    1 -> vm.setTranslationLanguage(code)
                    else -> vm.setAppLanguage(code)
                }
            },
            reloadApp = which == 2
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    title: String,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: suspend (String) -> Unit,
    reloadApp: Boolean = false
) {
    val activity = com.proudvocab.android.LocalHostActivity.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val systemLanguageName = stringResource(R.string.language_system_default)
    val systemLanguageDescription = stringResource(R.string.language_system_desc)
    val list = remember(query, reloadApp, systemLanguageName, systemLanguageDescription) {
        val available = if (reloadApp) {
            listOf(
                AppLanguage("", systemLanguageName, systemLanguageDescription),
                Languages.byCode("en"),
                Languages.byCode("fa")
            )
        } else {
            Languages.ALL
        }
        if (query.isBlank()) available else {
            val q = query.trim()
            available.filter {
                it.code.startsWith(q, ignoreCase = true) ||
                    it.englishName.contains(q, ignoreCase = true) ||
                    it.nativeName.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(list, key = { it.code }) { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    scope.launch {
                                        onSelect(language.code)
                                        if (reloadApp) activity.recreate() else onDismiss()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = language.nativeName,
                                    fontWeight = if (language.code == selected) FontWeight.Bold
                                    else FontWeight.Normal
                                )
                                Text(
                                    text = language.englishName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (language.code == selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// =============================================================== subtitles

@Composable
fun SubtitlesPage(vm: SettingsViewModel, settings: AppSettings) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_subtitles))
            SettingsCard {
                SliderPreference(
                    title = stringResource(R.string.settings_sub_position),
                    value = settings.subtitlePositionBottom,
                    range = 0f..0.6f,
                    onValueChange = vm::setSubtitlePosition,
                    display = "${(settings.subtitlePositionBottom * 100).toInt()}%"
                )
                SliderPreference(
                    title = stringResource(R.string.settings_sub_bg_opacity),
                    value = settings.subtitleBackgroundOpacity,
                    range = 0f..1f,
                    onValueChange = vm::setSubtitleOpacity,
                    display = "${(settings.subtitleBackgroundOpacity * 100).toInt()}%"
                )
                SliderPreference(
                    title = stringResource(R.string.settings_sub_max_lines),
                    value = settings.subtitleMaxLines.toFloat(),
                    range = 1f..4f,
                    steps = 2,
                    onValueChange = { vm.setSubtitleMaxLines(it.toInt().coerceIn(1, 4)) },
                    display = settings.subtitleMaxLines.toString()
                )
                SliderPreference(
                    title = stringResource(R.string.player_delay),
                    value = settings.subtitleDelayMs.toFloat(),
                    range = -5_000f..5_000f,
                    steps = 19,
                    onValueChange = { vm.setSubtitleDelay(it.toLong()) },
                    display = "${settings.subtitleDelayMs} ms"
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_dual),
                    subtitle = stringResource(R.string.settings_sub_dual_desc),
                    checked = settings.subtitleDual,
                    onCheckedChange = vm::setSubtitleDual
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_word_chips),
                    checked = settings.subtitleWordChips,
                    onCheckedChange = vm::setSubtitleWordChips
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_highlight_cefr),
                    checked = settings.subtitleHighlightCefr,
                    onCheckedChange = vm::setSubtitleHighlightCefr
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_highlight_idioms),
                    checked = settings.subtitleHighlightIdioms,
                    onCheckedChange = vm::setSubtitleHighlightIdioms
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_shadowing),
                    subtitle = stringResource(R.string.settings_sub_shadowing_desc),
                    checked = settings.subtitleShadowing,
                    onCheckedChange = vm::setSubtitleShadowing
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_sub_auto_translate_lines),
                    subtitle = stringResource(R.string.settings_sub_auto_translate_lines_desc),
                    checked = settings.subtitleTranslateWholeLine,
                    onCheckedChange = vm::setSubtitleTranslateWholeLine
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_sub_bg))
            SettingsCard {
                ColorSwatches(
                    colors = listOf(
                        "#FF0A0F1E", "#FF000000", "#FF1E293B", "#FF312E81",
                        "#FF134E4A", "#FF7F1D1D", "#FFFFFFFF", "#FFFDE68A"
                    ),
                    selected = settings.subtitleBackground,
                    onSelect = { vm.setSubtitleBackground(it ?: "#FF0A0F1E") },
                    allowAuto = false
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// ============================================================= translation

@Composable
fun TranslationPage(vm: SettingsViewModel, settings: AppSettings, state: SettingsUiState) {
    val context = LocalContext.current
    val dictionaryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importDictionary(it) } }

    var confirmEraseModel by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_engine))
            SettingsCard {
                EngineOption(
                    title = stringResource(R.string.engine_auto),
                    subtitle = stringResource(R.string.engine_auto_desc),
                    selected = settings.engine() == TranslationEngine.AUTO,
                    onClick = { vm.setEngine(TranslationEngine.AUTO) }
                )
                EngineOption(
                    title = stringResource(R.string.engine_online),
                    subtitle = stringResource(R.string.engine_online_desc),
                    selected = settings.engine() == TranslationEngine.ONLINE,
                    onClick = { vm.setEngine(TranslationEngine.ONLINE) }
                )
                EngineOption(
                    title = stringResource(R.string.engine_offline),
                    subtitle = stringResource(R.string.engine_offline_desc),
                    selected = settings.engine() == TranslationEngine.OFFLINE,
                    onClick = { vm.setEngine(TranslationEngine.OFFLINE) }
                )
                EngineOption(
                    title = stringResource(R.string.engine_dictionary),
                    subtitle = stringResource(R.string.engine_dictionary_desc),
                    selected = settings.engine() == TranslationEngine.DICTIONARY,
                    onClick = { vm.setEngine(TranslationEngine.DICTIONARY) }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_online_fallback),
                    subtitle = stringResource(R.string.settings_online_fallback_desc),
                    checked = settings.onlineFallback,
                    onCheckedChange = vm::setOnlineFallback
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.offline_model_title))
            SettingsCard {
                ModelRow(
                    state = state.modelState,
                    language = Languages.byCode(settings.translationLanguage).nativeName,
                    onDownload = vm::downloadModel,
                    onDelete = { confirmEraseModel = true }
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.dict_db_title))
            SettingsCard {
                PreferenceRow(
                    title = if (settings.dictionaryImported) {
                        stringResource(R.string.dict_db_custom)
                    } else {
                        stringResource(R.string.dict_db_using_starter)
                    },
                    subtitle = stringResource(
                        R.string.dict_db_count,
                        TextUtils.formatNumber(settings.dictionaryWordCount, settings.usePersianDigits)
                    ),
                    icon = Icons.Rounded.Storage
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.dict_db_import),
                    icon = Icons.Rounded.Download,
                    onClick = { dictionaryLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.dictionaryBusy
                )
                if (settings.dictionaryImported) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                    )
                    PreferenceRow(
                        title = stringResource(R.string.delete),
                        icon = Icons.Rounded.DeleteForever,
                        onClick = vm::removeDictionary
                    )
                }
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }

    if (confirmEraseModel) {
        AlertDialog(
            onDismissRequest = { confirmEraseModel = false },
            title = { Text(stringResource(R.string.offline_model_delete)) },
            text = { Text(stringResource(R.string.offline_model_missing)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteModel()
                    confirmEraseModel = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEraseModel = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EngineOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelRow(
    state: ModelState,
    language: String,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.offline_model_title))
                Text(
                    text = when (state) {
                        ModelState.Ready -> stringResource(R.string.offline_model_ready, language)
                        ModelState.Missing -> stringResource(R.string.offline_model_missing)
                        ModelState.Checking -> stringResource(R.string.loading)
                        ModelState.Downloading -> stringResource(R.string.offline_model_downloading)
                        is ModelState.Failed -> state.message
                        ModelState.Unknown -> stringResource(R.string.offline_model_missing)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state == ModelState.Downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == ModelState.Missing || state is ModelState.Failed) {
                PrimaryButton(
                    text = stringResource(R.string.offline_model_download, language),
                    onClick = onDownload,
                    icon = Icons.Rounded.Download
                )
            }
            if (state == ModelState.Ready) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.offline_model_delete))
                }
            }
        }
    }
}

// ================================================================ learning

@Composable
fun LearningPage(vm: SettingsViewModel, settings: AppSettings) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_learning))
            SettingsCard {
                SwitchPreference(
                    title = stringResource(R.string.settings_auto_pause),
                    checked = settings.autoPauseOnLookup,
                    onCheckedChange = vm::setAutoPause
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_auto_rewind),
                    checked = settings.autoRewind,
                    onCheckedChange = vm::setAutoRewind
                )
                if (settings.autoRewind) {
                    SliderPreference(
                        title = stringResource(R.string.settings_rewind_seconds),
                        value = settings.rewindSeconds.toFloat(),
                        range = 1f..10f,
                        steps = 8,
                        onValueChange = { vm.setRewindSeconds(it.toInt()) },
                        display = "${settings.rewindSeconds}s"
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_word_family),
                    checked = settings.showWordFamily,
                    onCheckedChange = vm::setShowWordFamily
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_word_tags),
                    checked = settings.showWordTags,
                    onCheckedChange = vm::setShowWordTags
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_word_details),
                    checked = settings.showWordDetails,
                    onCheckedChange = vm::setShowWordDetails
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_quick_access),
                    checked = settings.quickAccess,
                    onCheckedChange = vm::setQuickAccess
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// ================================================================== review

@Composable
fun ReviewSettingsPage(vm: SettingsViewModel, settings: AppSettings) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_srs))
            SettingsCard {
                SegmentedPreference(
                    title = null,
                    options = listOf("classic" to "ProudVocab", "sm2" to "SM-2"),
                    selected = settings.srsAlgorithm,
                    onSelect = vm::setSrsAlgorithm
                )
                SliderPreference(
                    title = stringResource(R.string.review_new_limit),
                    value = settings.newLimit.toFloat(),
                    range = 5f..50f,
                    steps = 8,
                    onValueChange = { vm.setNewLimit(it.toInt()) },
                    display = "${settings.newLimit}"
                )
                SliderPreference(
                    title = stringResource(R.string.review_session_limit),
                    value = settings.sessionLimit.toFloat(),
                    range = 5f..100f,
                    steps = 18,
                    onValueChange = { vm.setSessionLimit(it.toInt()) },
                    display = "${settings.sessionLimit}"
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_section_data))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.settings_reset_srs),
                    onClick = vm::resetSrs
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// =================================================================== games

@Composable
fun GamesSettingsPage(vm: SettingsViewModel, settings: AppSettings) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.games_title))
            SettingsCard {
                SwitchPreference(
                    title = stringResource(R.string.games_sound),
                    checked = settings.gameSound,
                    onCheckedChange = vm::setGameSound
                )
                SwitchPreference(
                    title = stringResource(R.string.games_auto_pronounce),
                    checked = settings.gameAutoPronounce,
                    onCheckedChange = vm::setGameAutoPronounce
                )
                SwitchPreference(
                    title = stringResource(R.string.games_show_learned),
                    checked = settings.gameShowLearned,
                    onCheckedChange = vm::setGameShowLearned
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_section_data))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.settings_reset_games),
                    onClick = vm::resetGames
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// ==================================================================== data

@Composable
fun DataPage(vm: SettingsViewModel, settings: AppSettings) {
    var confirmErase by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.importJson(it) { count ->
                android.widget.Toast.makeText(
                    context,
                    if (count > 0) context.getString(R.string.archive_import_done, count)
                    else context.getString(R.string.settings_import_failed),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun share(intent: Intent?) {
        if (intent != null) {
            shareLauncher.launch(Intent.createChooser(intent, null))
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.settings_export_failed_hint),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_export))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.archive_export_json),
                    subtitle = stringResource(R.string.settings_export_done),
                    icon = Icons.Rounded.Storage,
                    onClick = { vm.exportJson(::share) }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.archive_export_anki),
                    onClick = { vm.exportAnki(::share) }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.archive_import_json),
                    icon = Icons.Rounded.Download,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.reset))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.settings_reset_srs),
                    onClick = vm::resetSrs
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.settings_reset_games),
                    onClick = vm::resetGames
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.style_reset_all),
                    onClick = vm::resetAllStyles
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                PreferenceRow(
                    title = stringResource(R.string.settings_reset_all),
                    onClick = { confirmErase = true }
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.settings_reset_all_title)) },
            text = { Text(stringResource(R.string.settings_reset_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.eraseEverything()
                    confirmErase = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ============================================================= permissions

@Composable
fun PermissionsPage() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_permissions))
            SettingsCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.perm_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.perm_storage_title))
            SettingsCard {
                Text(
                    text = stringResource(R.string.perm_storage_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.perm_network_title))
            SettingsCard {
                Text(
                    text = stringResource(R.string.perm_network_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// =================================================================== about

@Composable
fun AboutPage() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_section_about))
            SettingsCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Pill(
                        text = stringResource(R.string.about_no_licence),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

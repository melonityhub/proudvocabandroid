package com.proudvocab.android.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proudvocab.android.R
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.PreferenceRow
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.components.SettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val deps = LocalDependencies.current
    val vm: SettingsViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())

    BackHandler(enabled = state.page != SettingsPage.ROOT) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.page == SettingsPage.ROOT) R.string.settings_title
                            else pageTitle(state.page)
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (state.page != SettingsPage.ROOT) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    if (targetState == SettingsPage.ROOT) {
                        (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { -it / 6 })
                            .togetherWith(fadeOut(tween(160)) + slideOutHorizontally { it / 6 })
                    } else {
                        (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 6 })
                            .togetherWith(fadeOut(tween(160)) + slideOutHorizontally { -it / 6 })
                    }
                },
                label = "settingsPage"
            ) { page ->
                when (page) {
                    SettingsPage.ROOT -> RootPage(vm, settings)
                    SettingsPage.APPEARANCE -> AppearancePage(vm, settings)
                    SettingsPage.TYPOGRAPHY -> TypographyPage(vm, settings)
                    SettingsPage.SUBTITLES -> SubtitlesPage(vm, settings)
                    SettingsPage.LANGUAGES -> LanguagesPage(vm, settings)
                    SettingsPage.TRANSLATION -> TranslationPage(vm, settings, state)
                    SettingsPage.LEARNING -> LearningPage(vm, settings)
                    SettingsPage.REVIEW -> ReviewSettingsPage(vm, settings)
                    SettingsPage.GAMES -> GamesSettingsPage(vm, settings)
                    SettingsPage.DATA -> DataPage(vm, settings)
                    SettingsPage.PERMISSIONS -> PermissionsPage()
                    SettingsPage.ABOUT -> AboutPage()
                }
            }
        }
    }
}

private fun pageTitle(page: SettingsPage): Int = when (page) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.APPEARANCE -> R.string.settings_section_appearance
    SettingsPage.TYPOGRAPHY -> R.string.settings_section_typography
    SettingsPage.SUBTITLES -> R.string.settings_section_subtitles
    SettingsPage.LANGUAGES -> R.string.settings_section_languages
    SettingsPage.TRANSLATION -> R.string.settings_section_translation
    SettingsPage.LEARNING -> R.string.settings_section_learning
    SettingsPage.REVIEW -> R.string.settings_section_srs
    SettingsPage.GAMES -> R.string.games_title
    SettingsPage.DATA -> R.string.settings_section_data
    SettingsPage.PERMISSIONS -> R.string.settings_section_permissions
    SettingsPage.ABOUT -> R.string.settings_section_about
}

private data class SettingsEntry(
    val page: SettingsPage,
    val title: Int,
    val icon: ImageVector
)

@Composable
private fun RootPage(vm: SettingsViewModel, settings: AppSettings) {
    val entries = listOf(
        SettingsEntry(SettingsPage.APPEARANCE, R.string.settings_section_appearance, Icons.Rounded.Palette),
        SettingsEntry(SettingsPage.TYPOGRAPHY, R.string.settings_section_typography, Icons.Rounded.TextFields),
        SettingsEntry(SettingsPage.SUBTITLES, R.string.settings_section_subtitles, Icons.Rounded.Subtitles),
        SettingsEntry(SettingsPage.LANGUAGES, R.string.settings_section_languages, Icons.Rounded.Language),
        SettingsEntry(SettingsPage.TRANSLATION, R.string.settings_section_translation, Icons.Rounded.Translate),
        SettingsEntry(SettingsPage.LEARNING, R.string.settings_section_learning, Icons.AutoMirrored.Rounded.ListAlt),
        SettingsEntry(SettingsPage.REVIEW, R.string.settings_section_srs, Icons.Rounded.Settings),
        SettingsEntry(SettingsPage.GAMES, R.string.games_title, Icons.Rounded.Extension),
        SettingsEntry(SettingsPage.DATA, R.string.settings_section_data, Icons.Rounded.Storage),
        SettingsEntry(SettingsPage.PERMISSIONS, R.string.settings_section_permissions, Icons.Rounded.Security),
        SettingsEntry(SettingsPage.ABOUT, R.string.settings_section_about, Icons.Rounded.Info)
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(text = stringResource(R.string.settings_title))
            SettingsCard {
                entries.forEach { entry ->
                    PreferenceRow(
                        title = stringResource(entry.title),
                        icon = entry.icon,
                        onClick = { vm.navigate(entry.page) }
                    )
                    if (entry != entries.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.settings_section_offline))
            SettingsCard {
                PreferenceRow(
                    title = stringResource(R.string.offline_model_title),
                    subtitle = stringResource(R.string.dict_offline_only_hint),
                    icon = Icons.Rounded.CloudDownload,
                    onClick = { vm.navigate(SettingsPage.TRANSLATION) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

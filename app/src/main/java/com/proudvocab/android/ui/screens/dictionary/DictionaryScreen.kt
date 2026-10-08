package com.proudvocab.android.ui.screens.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proudvocab.android.R
import com.proudvocab.android.core.dict.WordEntry
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.speech.Speaker
import com.proudvocab.android.core.util.TextUtils
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.CefrBadge
import com.proudvocab.android.ui.components.EmptyState
import com.proudvocab.android.ui.components.Pill
import com.proudvocab.android.ui.components.PrimaryButton
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.theme.rememberTargetStyle

@Composable
fun DictionaryScreen(
    initialQuery: String? = null,
    onInitialQueryConsumed: () -> Unit = {}
) {
    val deps = LocalDependencies.current
    val vm: DictionaryViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var selectedWord by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            vm.setTab(DictionaryTab.SEARCH)
            vm.search(initialQuery)
            onInitialQueryConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            DictionaryTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { vm.setTab(tab) },
                    text = {
                        Text(
                            when (tab) {
                                DictionaryTab.SEARCH -> stringResource(R.string.search)
                                DictionaryTab.TRANSLATE -> stringResource(R.string.dict_translate_text)
                                DictionaryTab.WORDS -> stringResource(R.string.dict_history)
                            }
                        )
                    }
                )
            }
        }

        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                (slideInHorizontally(tween(220)) { it / 6 } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut())
            },
            label = "dictTabs",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                DictionaryTab.SEARCH -> SearchPane(
                    state = state,
                    settings = settings,
                    onQueryChange = vm::onQueryChange,
                    onOpen = { vm.openWord(it); selectedWord = it },
                    onSuggestion = { vm.search(it) },
                    modifier = Modifier.fillMaxSize()
                )

                DictionaryTab.TRANSLATE -> TranslatePane(
                    state = state,
                    settings = settings,
                    onInput = vm::onTranslateInput,
                    onTranslate = vm::translateText,
                    onSwap = vm::swapLanguages,
                    modifier = Modifier.fillMaxSize()
                )

                DictionaryTab.WORDS -> WordsPane(
                    state = state,
                    settings = settings,
                    onWord = { vm.openWord(it); selectedWord = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (selectedWord != null && state.entry != null && state.entryWord == selectedWord) {
        EntrySheet(
            entry = state.entry!!,
            settings = settings,
            isSaved = state.savedWords.contains(state.entryWord),
            isFavourite = state.favourites.contains(state.entryWord),
            onDismiss = { selectedWord = null; vm.closeEntry() },
            onSave = { vm.saveWord(state.entryWord, state.entry?.shortGloss.orEmpty()) },
            onFavourite = { vm.toggleFavourite(state.entryWord) }
        )
    }
}

// ------------------------------------------------------------------ search

@Composable
private fun SearchPane(
    state: DictionaryUiState,
    settings: AppSettings,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deps = LocalDependencies.current
    val resultStyle = rememberTargetStyle(StyleTarget.DICTIONARY, settings, deps.fonts)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.dict_search_hint),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(
                text = stringResource(R.string.dict_offline_badge),
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.dict_db_count,
                    TextUtils.formatNumber(state.databaseWords, settings.usePersianDigits)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.searching) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        }

        if (state.results.isEmpty() && state.query.isNotBlank() && !state.searching) {
            if (state.suggestions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.dict_no_result),
                    body = stringResource(R.string.dict_no_result_body)
                )
            } else {
                SectionHeader(text = stringResource(R.string.dict_suggestions))
                FlowRowPills(state.suggestions, onSuggestion)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.results, key = { "${it.word}_${it.wordId}" }) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(result.word) }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.word,
                            style = resultStyle.textStyle.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (result.meaning.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = result.meaning,
                                style = resultStyle.textStyle.copy(
                                    fontSize = (resultStyle.fontSizeSp * 0.85f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2
                            )
                        }
                    }
                    CefrBadge(level = deps.dictionary.cefr(result.word))
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowPills(items: List<String>, onClick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { Pill(text = it, onClick = { onClick(it) }) }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        placeholder = {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

// --------------------------------------------------------------- translate

@Composable
private fun TranslatePane(
    state: DictionaryUiState,
    settings: AppSettings,
    onInput: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = com.proudvocab.android.core.model.Languages.byCode(settings.learningLanguage).nativeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.cd_swap))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = com.proudvocab.android.core.model.Languages.byCode(settings.translationLanguage).nativeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        TextField(
            value = state.translateInput,
            onValueChange = onInput,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text(stringResource(R.string.dict_translate_input_hint)) },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = stringResource(R.string.dict_translate_text),
            onClick = onTranslate,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Translate
        )

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = state.translating) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        }

        val output = state.translateOutput
        if (output != null && !state.translating) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (output.text.isBlank()) stringResource(R.string.error) else output.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Pill(
                        text = stringResource(
                            if (output.offline) R.string.dict_offline_badge else R.string.dict_online_badge
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------- history / favs

@Composable
private fun WordsPane(
    state: DictionaryUiState,
    settings: AppSettings,
    onWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        if (state.favourites.isNotEmpty()) {
            item {
                SectionHeader(text = stringResource(R.string.dict_favorites))
            }
            items(state.favourites.toList()) { word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWord(word) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(word, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.history.isNotEmpty()) {
            item { SectionHeader(text = stringResource(R.string.dict_history)) }
            items(state.history) { word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWord(word) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.History,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(word, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.history.isEmpty() && state.favourites.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.dict_no_result),
                    body = stringResource(R.string.dict_search_hint)
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

// ------------------------------------------------------------ entry sheet

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun EntrySheet(
    entry: WordEntry,
    settings: AppSettings,
    isSaved: Boolean,
    isFavourite: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onFavourite: () -> Unit
) {
    val context = LocalContext.current
    val deps = LocalDependencies.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val headwordStyle = rememberTargetStyle(StyleTarget.WORD_CARD, settings, deps.fonts)
    val translationStyle = rememberTargetStyle(StyleTarget.WORD_TRANSLATION, settings, deps.fonts)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .imePadding()
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.word,
                        style = headwordStyle.textStyle,
                        modifier = Modifier.weight(1f)
                    )
                    CefrBadge(level = entry.cefr ?: deps.dictionary.cefr(entry.word))
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onFavourite) {
                        Icon(
                            if (isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = if (isFavourite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val phonetic = entry.phoneticUs ?: entry.phoneticUk
                if (!phonetic.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { speaker.speak(entry.word, settings.learningLanguage) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = phonetic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = stringResource(if (isSaved) R.string.popup_remove else R.string.dict_save_word),
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (isSaved) Icons.Rounded.Clear else Icons.Rounded.Bookmark
                )
                Spacer(Modifier.height(16.dp))
            }

            if (entry.meanings.isNotEmpty()) {
                item { SectionHeader(text = stringResource(R.string.dict_meanings)) }
                items(entry.meanings) { meaning ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Text(
                            text = if (meaning.partOfSpeech != 0) meaning.posName else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(96.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meaning.persianMeaning,
                                style = translationStyle.textStyle
                            )
                            if (meaning.categories.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    meaning.categories.take(3).forEach { cat ->
                                        Pill(
                                            text = com.proudvocab.android.core.dict.FdTables
                                                .categoryName(cat)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val forms = entry.forms.asPairs()
            if (forms.isNotEmpty()) {
                item {
                    SectionHeader(text = stringResource(R.string.dict_forms))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        forms.take(5).forEach { (label, value) ->
                            Pill(text = value, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            if (entry.idioms.isNotEmpty()) {
                item {
                    SectionHeader(text = stringResource(R.string.dict_idioms))
                    entry.idioms.take(8).forEach { (phrase, meaning) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(phrase, fontWeight = FontWeight.SemiBold)
                            Text(
                                meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (entry.family.isNotEmpty()) {
                item {
                    SectionHeader(text = stringResource(R.string.dict_family))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.family.take(6).forEach { Pill(text = it) }
                    }
                }
            }

            val synonyms = entry.synonyms.flatMap { it.synonyms }.distinct()
            val antonyms = entry.synonyms.flatMap { it.antonyms }.distinct()
            if (synonyms.isNotEmpty()) {
                item {
                    SectionHeader(text = stringResource(R.string.dict_synonyms))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        synonyms.take(8).forEach { Pill(text = it, color = MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
            if (antonyms.isNotEmpty()) {
                item {
                    SectionHeader(text = stringResource(R.string.dict_antonyms))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        antonyms.take(8).forEach { Pill(text = it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            if (entry.sentences.isNotEmpty()) {
                item { SectionHeader(text = stringResource(R.string.dict_examples)) }
                items(entry.sentences.take(10)) { sentence ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(sentence.source, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            sentence.target,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

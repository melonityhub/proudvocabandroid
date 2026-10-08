package com.proudvocab.android.ui.screens.archive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proudvocab.android.R
import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.util.TextUtils
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.CefrBadge
import com.proudvocab.android.ui.components.EmptyState
import com.proudvocab.android.ui.components.Pill
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.theme.rememberTargetStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen() {
    val context = LocalContext.current
    val deps = LocalDependencies.current
    val vm: ArchiveViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val wordStyle = rememberTargetStyle(StyleTarget.ARCHIVE_WORD, settings, deps.fonts)
    val translationStyle = rememberTargetStyle(StyleTarget.ARCHIVE_TRANSLATION, settings, deps.fonts)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importJson(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* the share sheet handles the rest */ }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2200)
            vm.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search + actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.archive_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterMode.entries.forEach { mode ->
                Pill(
                    text = when (mode) {
                        FilterMode.ALL -> stringResource(R.string.all)
                        FilterMode.IDIOM -> stringResource(R.string.archive_filter_idiom)
                        FilterMode.PHRASAL -> stringResource(R.string.archive_filter_phrasal)
                        FilterMode.LEARNED -> stringResource(R.string.archive_filter_learned)
                    },
                    selected = state.filter == mode,
                    onClick = { vm.setFilter(mode) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.archive_count,
                    TextUtils.formatNumber(state.visible.size, settings.usePersianDigits)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            SortMode.entries.forEach { mode ->
                Text(
                    text = when (mode) {
                        SortMode.NEWEST -> stringResource(R.string.archive_sort_newest)
                        SortMode.OLDEST -> stringResource(R.string.archive_sort_oldest)
                        SortMode.ALPHA -> stringResource(R.string.archive_sort_alpha)
                        SortMode.LEVEL -> stringResource(R.string.archive_sort_level)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (state.sort == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (state.sort == mode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.setSort(mode) }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(2.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = {
                vm.exportAnki()?.let { intent ->
                    exportLauncher.launch(Intent.createChooser(intent, null))
                }
            }) {
                Icon(Icons.Rounded.FileDownload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.archive_export_anki), fontSize = 12.sp)
            }
            TextButton(onClick = {
                vm.exportJson()?.let { intent ->
                    exportLauncher.launch(Intent.createChooser(intent, null))
                }
            }) {
                Icon(Icons.Rounded.FileDownload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.archive_export_json), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.archive_import_json), fontSize = 12.sp)
            }
        }

        if (state.visible.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.archive_empty_title),
                body = stringResource(R.string.archive_empty_body),
                icon = {
                    Icon(
                        Icons.Rounded.Bookmark,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                }
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visible, key = { it.id }) { word ->
                    WordRow(
                        word = word,
                        expanded = state.expandedId == word.id,
                        wordStyle = wordStyle.textStyle,
                        translationStyle = translationStyle.textStyle,
                        persianDigits = settings.usePersianDigits,
                        onExpand = { vm.expand(if (state.expandedId == word.id) null else word.id) },
                        onToggleLearned = { vm.toggleLearned(word) },
                        onDelete = { vm.askDelete(word) },
                        onAddTag = { vm.addTag(word, it) },
                        onTranslationChange = { vm.updateTranslation(word, it) }
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }

    state.confirmDelete?.let { word ->
        AlertDialog(
            onDismissRequest = { vm.askDelete(null) },
            title = { Text(stringResource(R.string.archive_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.archive_delete_confirm_body, word.word))
            },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.askDelete(null) }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordRow(
    word: SavedWord,
    expanded: Boolean,
    wordStyle: androidx.compose.ui.text.TextStyle,
    translationStyle: androidx.compose.ui.text.TextStyle,
    persianDigits: Boolean,
    onExpand: () -> Unit,
    onToggleLearned: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: (String) -> Unit,
    onTranslationChange: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("d MMM yyyy", Locale.US) }
    var tagDraft by remember(word.id) { mutableStateOf("") }
    var translationDraft by remember(word.id) { mutableStateOf(word.translation) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(Spring.DampingRatioLowBouncy), label = "rot")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (word.learned) Color(0xFF22C55E)
                        else MaterialTheme.colorScheme.primary
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = word.word, style = wordStyle, modifier = Modifier.weight(1f, false))
                    Spacer(Modifier.width(8.dp))
                    if (word.cefr.isNotBlank()) CefrBadge(level = word.cefr, outlined = true)
                }
                if (word.translation.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = word.translation,
                        style = translationStyle,
                        maxLines = if (expanded) 6 else 2
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                if (word.contextSentence.isNotBlank()) {
                    SectionHeader(text = stringResource(R.string.popup_context))
                    Text(
                        text = "“${word.contextSentence}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = translationDraft,
                    onValueChange = {
                        translationDraft = it
                        onTranslationChange(it)
                    },
                    label = { Text(stringResource(R.string.typography_word_translation)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    word.tagsList.forEach { tag ->
                        Pill(text = tag, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        modifier = Modifier.weight(1f).height(54.dp),
                        placeholder = { Text(stringResource(R.string.archive_add_tag), fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (tagDraft.isNotBlank()) {
                            onAddTag(tagDraft)
                            tagDraft = ""
                        }
                    }) { Text(stringResource(R.string.save)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(Date(word.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = onToggleLearned) {
                            Text(stringResource(R.string.review_learned))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

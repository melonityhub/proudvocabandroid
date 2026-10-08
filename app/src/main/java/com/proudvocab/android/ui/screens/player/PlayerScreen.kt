package com.proudvocab.android.ui.screens.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.proudvocab.android.R
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.subtitle.SubtitleParser
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.CefrBadge
import com.proudvocab.android.ui.components.EmptyState
import com.proudvocab.android.ui.components.PrimaryButton
import com.proudvocab.android.ui.screens.dictionary.WordLookupSheet
import com.proudvocab.android.ui.theme.CefrColors
import com.proudvocab.android.ui.theme.rememberTargetStyle
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(incomingIntent: Intent?) {
    val context = LocalContext.current
    val deps = LocalDependencies.current
    val vm: PlayerViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    var showTranscript by remember { mutableStateOf(false) }
    var transcriptTab by remember { mutableStateOf(0) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.openVideo(context, it)
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.loadSubtitle(context, it)
        }
    }

    // "Open with ProudVocab" from another app.
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val uri = intent.data ?: return@LaunchedEffect
        if (intent.action == Intent.ACTION_VIEW) {
            val name = uri.lastPathSegment.orEmpty()
            when {
                name.endsWith(".srt", true) || name.endsWith(".vtt", true) ||
                    name.endsWith(".ass", true) || name.endsWith(".ssa", true) ||
                    name.endsWith(".sub", true) -> vm.loadSubtitle(context, uri)
                else -> vm.openVideo(context, uri)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { /* the ViewModel releases the player in onCleared() */ }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.videoUri == null) {
            EmptyState(
                title = stringResource(R.string.player_no_video_title),
                body = stringResource(R.string.player_no_video_body),
                modifier = Modifier.align(Alignment.Center),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                },
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.player_no_video_action),
                        onClick = { videoPicker.launch(arrayOf("video/*")) }
                    )
                }
            )
        } else {
            VideoSurface(vm = vm, modifier = Modifier.fillMaxSize())
        }

        if (state.videoUri != null) {
            SubtitleOverlay(
                state = state,
                settings = settings,
                onWordClick = { word ->
                    vm.lookUp(word, state.activeCue?.text.orEmpty())
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart)
            )

            PlayerControls(
                state = state,
                settings = settings,
                onTogglePlay = vm::togglePlay,
                onSeekBack = { vm.seekBy(-5000) },
                onSeekForward = { vm.seekBy(5000) },
                onRepeat = vm::repeatCue,
                onOpenVideo = { videoPicker.launch(arrayOf("video/*")) },
                onOpenSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
                onToggleSubtitles = {
                    scope.launch {
                        deps.settings.update { s -> s.copy(subtitleDual = !s.subtitleDual) }
                    }
                },
                onOpenTranscript = { showTranscript = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 96.dp)
            )
        }
    }

    if (showTranscript) {
        TranscriptSheet(
            state = state,
            settings = settings,
            tab = transcriptTab,
            onTabChange = { transcriptTab = it },
            onCueClick = { index -> vm.seekToCue(index) },
            onDismiss = { showTranscript = false }
        )
    }

    state.lookup?.let { lookup ->
        WordLookupSheet(
            lookup = lookup,
            settings = settings,
            onDismiss = vm::dismissLookup,
            onToggleSave = vm::toggleSaveLookup,
            onOpenDictionary = null
        )
    }
}

// ------------------------------------------------------------------- video

@Composable
private fun VideoSurface(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            PlayerView(context).apply {
                player = vm.player
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> if (view.player !== vm.player) view.player = vm.player }
    )
}

// ---------------------------------------------------------------- subtitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleOverlay(
    state: PlayerUiState,
    settings: AppSettings,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deps = LocalDependencies.current
    val primary = rememberTargetStyle(StyleTarget.SUBTITLE_PRIMARY, settings, deps.fonts)
    val secondary = rememberTargetStyle(StyleTarget.SUBTITLE_SECONDARY, settings, deps.fonts)
    val cue = state.activeCue
    val bottomPadding = remember(settings.subtitlePositionBottom) {
        (settings.subtitlePositionBottom.coerceIn(0f, 0.7f) * 100).dp
    }
    val background = remember(settings.subtitleBackground, settings.subtitleBackgroundOpacity) {
        val parsed = com.proudvocab.android.core.util.ColorCodec.parseULong(settings.subtitleBackground)
        if (parsed != null) {
            Color(parsed).copy(alpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f))
        } else Color.Black.copy(alpha = settings.subtitleBackgroundOpacity)
    }

    if (cue == null || !settings.subtitleDual && !settings.subtitleWordChips) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = bottomPadding)
                .align(Alignment.BottomCenter)
                .padding(bottom = 0.dp)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 4 },
                exit = fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(background)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .fillMaxWidth()
                ) {
                    if (settings.subtitleWordChips) {
                        val chips = remember(cue.text, settings.translationLanguage) {
                            PlayerViewModel.chipsFor(cue.text, settings.translationLanguage, deps.dictionary)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            chips.forEach { (text, kind) ->
                                val level = if (settings.subtitleHighlightCefr) {
                                    deps.dictionary.cefr(text)
                                } else null
                                val color = when {
                                    kind == 1 && settings.subtitleHighlightIdioms -> Color(0xFFF0ABFC)
                                    kind == 2 && settings.subtitleHighlightIdioms -> Color(0xFF7DD3FC)
                                    level != null -> CefrColors.forLevel(level)
                                    else -> primary.color
                                }
                                Text(
                                    text = text,
                                    style = primary.textStyle.copy(color = color),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onWordClick(text) }
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = cue.text,
                            style = primary.textStyle,
                            textAlign = primary.align,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (settings.subtitleDual) {
                        val translated = state.lineTranslations[state.activeIndex]
                        Spacer(Modifier.height(6.dp))
                        if (translated.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = secondary.color.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.popup_translating),
                                    style = secondary.textStyle.copy(
                                        fontSize = (secondary.fontSizeSp * 0.8f).sp
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = translated,
                                style = secondary.textStyle,
                                textAlign = secondary.align,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- controls

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    settings: AppSettings,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRepeat: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Scrub bar
        val progress = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SubtitleParser.formatTime(state.positionMs, settings.usePersianDigits),
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = SubtitleParser.formatTime(state.durationMs, settings.usePersianDigits),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            ControlChip(icon = Icons.Rounded.Subtitles, onClick = onOpenSubtitle)
            ControlChip(icon = Icons.Rounded.FolderOpen, onClick = onOpenVideo)
            ControlChip(
                icon = Icons.Rounded.Translate,
                onClick = onToggleSubtitles,
                active = settings.subtitleDual
            )
            ControlChip(icon = Icons.AutoMirrored.Rounded.ListAlt, onClick = onOpenTranscript)
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlChip(icon = Icons.Rounded.Replay, onClick = onRepeat)
            Spacer(Modifier.width(18.dp))
            ControlChip(icon = Icons.Rounded.FastRewind, onClick = onSeekBack)
            Spacer(Modifier.width(14.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(58.dp)
                    .clickable(onClick = onTogglePlay)
            ) {
                val scale by animateFloatAsState(
                    if (state.isPlaying) 1f else 0.92f,
                    spring(Spring.DampingRatioMediumBouncy),
                    label = "play"
                )
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            ControlChip(icon = Icons.Rounded.FastForward, onClick = onSeekForward)
            Spacer(Modifier.width(18.dp))
            ControlChip(icon = Icons.Rounded.Fullscreen, onClick = { })
        }
    }
}

@Composable
private fun ControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                else Color.White.copy(alpha = 0.12f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

// -------------------------------------------------------------- transcript

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptSheet(
    state: PlayerUiState,
    settings: AppSettings,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onCueClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val deps = LocalDependencies.current
    val transcriptStyle = rememberTargetStyle(StyleTarget.TRANSCRIPT, settings, deps.fonts)
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.activeIndex) {
        if (state.activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(state.activeIndex) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.player_transcript),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            if (state.cues.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.player_no_subtitle),
                    body = ""
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    itemsIndexed(state.shiftedCues, key = { _, cue -> cue.index }) { index, cue ->
                        val isActive = index == state.activeIndex
                        val background by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else Color.Transparent,
                            label = "cueBg"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(background)
                                .clickable { onCueClick(index) }
                                .padding(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = SubtitleParser.formatTime(cue.startMs, settings.usePersianDigits),
                                style = transcriptStyle.textStyle.copy(
                                    fontSize = (transcriptStyle.fontSizeSp * 0.78f).sp,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.width(56.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = cue.text, style = transcriptStyle.textStyle)
                                val translated = state.lineTranslations[index]
                                if (!translated.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = translated,
                                        style = transcriptStyle.textStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

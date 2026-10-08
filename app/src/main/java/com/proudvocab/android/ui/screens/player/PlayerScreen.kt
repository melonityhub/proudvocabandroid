package com.proudvocab.android.ui.screens.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
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
fun PlayerScreen(
    incomingIntent: Intent?,
    fullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onIncomingIntentHandled: () -> Unit,
    onOpenDictionary: (String) -> Unit
) {
    BackHandler(enabled = fullscreen) { onToggleFullscreen(false) }
    val context = LocalContext.current
    val deps = LocalDependencies.current
    val vm: PlayerViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    var showTranscript by remember { mutableStateOf(false) }

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

    // "Open with ProudVocab" from another app. MIME type is authoritative;
    // providers are allowed to expose URIs that have no filename extension.
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) {
            val mime = intent.type.orEmpty().lowercase()
            val name = uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast(':')
            val isSubtitle = mime.contains("subrip") || mime == "text/vtt" ||
                mime.contains("x-ssa") || mime.contains("x-ass") ||
                listOf(".srt", ".vtt", ".ass", ".ssa", ".sub").any {
                    name.endsWith(it, ignoreCase = true)
                }
            if (isSubtitle) vm.loadSubtitle(context, uri) else vm.openVideo(context, uri)
        }
        onIncomingIntentHandled()
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
                onTranslateLine = vm::translateActiveLine,
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
                onSeekTo = vm::seekTo,
                onSetSpeed = vm::setSpeed,
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
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
                    .padding(bottom = if (fullscreen) 24.dp else 12.dp)
            )
        }

        if (state.loadingSubtitle) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (fullscreen) 16.dp else 44.dp)
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.player_subtitle_loading))
                }
            }
        } else {
            state.subtitleError?.let { error ->
                val errorRes = when (error) {
                    "empty" -> R.string.player_subtitle_empty
                    "too_large" -> R.string.player_subtitle_too_large
                    else -> R.string.player_subtitle_error
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (fullscreen) 16.dp else 44.dp)
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (showTranscript) {
        TranscriptSheet(
            state = state,
            settings = settings,
            onCueClick = { index -> vm.seekToCue(index) },
            onPreviousCue = vm::previousCue,
            onNextCue = vm::nextCue,
            onDismiss = { showTranscript = false }
        )
    }

    state.lookup?.let { lookup ->
        WordLookupSheet(
            lookup = lookup,
            settings = settings,
            onDismiss = vm::dismissLookup,
            onToggleSave = vm::toggleSaveLookup,
            onOpenDictionary = if (settings.quickAccess) {
                { onOpenDictionary(lookup.word) }
            } else null
        )
    }
}

// ------------------------------------------------------------------- video

@UnstableApi
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
                keepScreenOn = player?.isPlaying == true
            }
        },
        update = { view ->
            if (view.player !== vm.player) view.player = vm.player
            view.keepScreenOn = vm.player.isPlaying
        }
    )
}

// ---------------------------------------------------------------- subtitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleOverlay(
    state: PlayerUiState,
    settings: AppSettings,
    onWordClick: (String) -> Unit,
    onTranslateLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deps = LocalDependencies.current
    val primary = rememberTargetStyle(StyleTarget.SUBTITLE_PRIMARY, settings, deps.fonts)
    val secondary = rememberTargetStyle(StyleTarget.SUBTITLE_SECONDARY, settings, deps.fonts)
    val cue = state.activeCue
    val background = remember(settings.subtitleBackground, settings.subtitleBackgroundOpacity) {
        val parsed = com.proudvocab.android.core.util.ColorCodec.parseULong(settings.subtitleBackground)
        if (parsed != null) {
            Color(parsed).copy(alpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f))
        } else Color.Black.copy(alpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f))
    }

    if (cue == null) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Keep captions clear of the control cluster, then let the slider add
        // user-controlled vertical breathing room as a proportion of the view.
        val maxInset = maxHeight * 0.55f
        val bottomInset = (180.dp + maxHeight * settings.subtitlePositionBottom.coerceIn(0f, 0.6f))
            .coerceAtMost(maxInset)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInset)
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
                        if (chips.isNotEmpty() &&
                            chips.size <= settings.subtitleMaxLines.coerceIn(1, 4) * 6
                        ) {
                            FlowRow(
                                maxLines = settings.subtitleMaxLines.coerceIn(1, 4),
                                overflow = FlowRowOverflow.Clip,
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                maxLines = settings.subtitleMaxLines.coerceIn(1, 4),
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = cue.text,
                            style = primary.textStyle,
                            textAlign = primary.align,
                            maxLines = settings.subtitleMaxLines.coerceIn(1, 4),
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (settings.subtitleDual && !settings.subtitleShadowing) {
                        val translated = state.lineTranslations[state.activeIndex]
                        val translatingThisLine = state.translatingLineIndex == state.activeIndex
                        Spacer(Modifier.height(6.dp))
                        when {
                            !translated.isNullOrBlank() -> Text(
                                text = translated,
                                style = secondary.textStyle,
                                textAlign = secondary.align,
                                maxLines = settings.subtitleMaxLines.coerceIn(1, 4),
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            state.activeIndex in state.failedLineTranslations -> Column {
                                Text(
                                    text = stringResource(R.string.player_translation_unavailable),
                                    style = secondary.textStyle.copy(
                                        fontSize = (secondary.fontSizeSp * 0.8f).sp
                                    ),
                                    color = secondary.color.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = onTranslateLine,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_translate_line),
                                        style = secondary.textStyle.copy(
                                            fontSize = (secondary.fontSizeSp * 0.8f).sp
                                        )
                                    )
                                }
                            }

                            translatingThisLine || settings.subtitleTranslateWholeLine -> Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            else -> TextButton(
                                onClick = onTranslateLine,
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.player_translate_line),
                                    style = secondary.textStyle.copy(
                                        fontSize = (secondary.fontSizeSp * 0.8f).sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
    fullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val seekDescription = stringResource(R.string.player_seek)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = if (scrubbing) scrubProgress else progress,
            onValueChange = {
                scrubProgress = it
                scrubbing = true
            },
            onValueChangeFinished = {
                onSeekTo((scrubProgress * state.durationMs).toLong())
                scrubbing = false
            },
            enabled = state.durationMs > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekDescription },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
            )
        )

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
            Text(" / ", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Text(
                text = SubtitleParser.formatTime(state.durationMs, settings.usePersianDigits),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            ControlChip(
                icon = Icons.Rounded.Subtitles,
                contentDescription = stringResource(R.string.player_open_subtitle),
                onClick = onOpenSubtitle
            )
            ControlChip(
                icon = Icons.Rounded.FolderOpen,
                contentDescription = stringResource(R.string.player_open_video),
                onClick = onOpenVideo
            )
            ControlChip(
                icon = Icons.Rounded.Translate,
                contentDescription = stringResource(R.string.player_dual_subtitles),
                onClick = onToggleSubtitles,
                active = settings.subtitleDual
            )
            ControlChip(
                icon = Icons.AutoMirrored.Rounded.ListAlt,
                contentDescription = stringResource(R.string.player_transcript),
                onClick = onOpenTranscript
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlChip(
                icon = Icons.Rounded.Replay,
                contentDescription = stringResource(R.string.player_repeat_line),
                onClick = onRepeat
            )
            Spacer(Modifier.width(4.dp))
            SpeedChip(speed = state.speed, onSpeedChange = onSetSpeed)
            Spacer(Modifier.width(4.dp))
            ControlChip(
                icon = Icons.Rounded.FastRewind,
                contentDescription = stringResource(R.string.player_seek_back),
                onClick = onSeekBack
            )
            Spacer(Modifier.width(10.dp))
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
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_pause else R.string.player_play
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            ControlChip(
                icon = Icons.Rounded.FastForward,
                contentDescription = stringResource(R.string.player_seek_forward),
                onClick = onSeekForward
            )
            Spacer(Modifier.width(14.dp))
            ControlChip(
                icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = stringResource(
                    if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                onClick = { onToggleFullscreen(!fullscreen) }
            )
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, onSpeedChange: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val index = speeds.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
    val label = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    val description = stringResource(R.string.player_speed_content_desc, label)
    Surface(
        modifier = Modifier
            .width(56.dp)
            .height(48.dp)
            .semantics { contentDescription = description }
            .clickable { onSpeedChange(speeds[(index + 1).mod(speeds.size)]) },
        shape = RoundedCornerShape(24.dp),
        color = if (speed == 1f) Color.White.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${label}×",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                else Color.White.copy(alpha = 0.12f)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
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
    onCueClick: (Int) -> Unit,
    onPreviousCue: () -> Unit,
    onNextCue: () -> Unit,
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
            if (state.cues.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onPreviousCue,
                        enabled = state.activeIndex > 0
                    ) {
                        Text(stringResource(R.string.player_prev_line))
                    }
                    TextButton(
                        onClick = onNextCue,
                        enabled = state.activeIndex < state.shiftedCues.lastIndex
                    ) {
                        Text(stringResource(R.string.player_next_line))
                    }
                }
            }
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

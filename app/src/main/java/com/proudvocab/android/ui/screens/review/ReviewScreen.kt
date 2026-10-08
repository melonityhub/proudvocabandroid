package com.proudvocab.android.ui.screens.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proudvocab.android.R
import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.srs.IntervalUnit
import com.proudvocab.android.core.srs.Rating
import com.proudvocab.android.core.util.TextUtils
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.AnimatedAppearance
import com.proudvocab.android.ui.components.AnimatedCounter
import com.proudvocab.android.ui.components.CefrBadge
import com.proudvocab.android.ui.components.EmptyState
import com.proudvocab.android.ui.components.PrimaryButton
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.theme.rememberTargetStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReviewScreen() {
    val vm: ReviewViewModel = viewModel()
    val deps = LocalDependencies.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            Tab(
                selected = state.tab == ReviewTab.SRS,
                onClick = { vm.setTab(ReviewTab.SRS) },
                text = { Text(stringResource(R.string.review_title)) }
            )
            Tab(
                selected = state.tab == ReviewTab.GAMES,
                onClick = { vm.setTab(ReviewTab.GAMES) },
                text = { Text(stringResource(R.string.games_title)) }
            )
        }

        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 8 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "reviewTabs",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                ReviewTab.SRS -> SrsPane(
                    state = state,
                    settings = settings,
                    vm = vm,
                    modifier = Modifier.fillMaxSize()
                )
                ReviewTab.GAMES -> GamesPane(
                    state = state,
                    settings = settings,
                    vm = vm,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ===================================================================== SRS

@Composable
private fun SrsPane(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    when {
        state.session.isNotEmpty() && !state.finished ->
            SessionRunner(state, settings, vm, modifier)
        state.finished -> SessionSummary(state, vm, modifier)
        else -> SrsHome(state, settings, vm, modifier)
    }
}

@Composable
private fun SrsHome(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    val persian = settings.usePersianDigits
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.review_due),
                        value = state.dueCount,
                        color = MaterialTheme.colorScheme.primary,
                        persianDigits = persian
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.review_total),
                        value = state.totalCount,
                        color = MaterialTheme.colorScheme.secondary,
                        persianDigits = persian
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.review_streak),
                        value = state.streak,
                        color = MaterialTheme.colorScheme.tertiary,
                        persianDigits = persian,
                        icon = {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                null,
                                tint = Color(0xFFFB923C),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.review_start),
                    onClick = vm::startSession,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PlayArrow,
                    enabled = state.dueCount + state.newCount > 0
                )
            }
        }

        if (state.heatmap.isNotEmpty()) {
            item { SectionHeader(text = stringResource(R.string.review_heatmap)) }
            item { Heatmap(state.heatmap) }
        }

        if (state.dueCount == 0 && state.newCount == 0) {
            item {
                EmptyState(
                    title = stringResource(R.string.review_empty_title),
                    body = stringResource(R.string.review_empty_body)
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.settings_section_srs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StepperCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_new_limit),
                    value = settings.newLimit,
                    persianDigits = persian,
                    onChange = { }
                )
                StepperCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_session_limit),
                    value = settings.sessionLimit,
                    persianDigits = persian,
                    onChange = { }
                )
            }
        }
        item { Spacer(Modifier.height(110.dp)) }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Int,
    color: Color,
    persianDigits: Boolean,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    AnimatedAppearance(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = color.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) icon()
                Text(
                    text = TextUtils.formatNumber(value, persianDigits),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: Int,
    persianDigits: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = TextUtils.formatNumber(value, persianDigits),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Heatmap(data: Map<String, Int>) {
    val weeks = 26
    val cell = 13.dp
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val days = remember(data) {
        val cal = Calendar.getInstance()
        val list = ArrayList<Pair<String, Int>>()
        for (i in (weeks * 7 - 1) downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val key = formatter.format(c.time)
            list += key to (data[key] ?: 0)
        }
        list
    }
    val max = days.maxOf { it.second }.coerceAtLeast(1)
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        for (week in 0 until weeks) {
            Row {
                for (day in 0 until 7) {
                    val index = week * 7 + day
                    val (_, count) = days.getOrNull(index) ?: ("" to 0)
                    val intensity = (count.toFloat() / max).coerceIn(0f, 1f)
                    val color = if (count == 0) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * intensity)
                    }
                    Box(
                        modifier = Modifier
                            .padding(1.5.dp)
                            .size(cell)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------- session

@Composable
private fun SessionRunner(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    val deps = LocalDependencies.current
    val card = state.currentCard ?: return
    val frontStyle = rememberTargetStyle(StyleTarget.FLASHCARD_FRONT, settings, deps.fonts)
    val backStyle = rememberTargetStyle(StyleTarget.FLASHCARD_BACK, settings, deps.fonts)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.sessionIndex + 1} / ${state.session.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val progress by animateFloatAsState(
                    state.sessionProgress, tween(320), label = "srsProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.review_quit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { vm.quitSession() }
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            FlipCard(
                revealed = state.revealed,
                onFlip = { if (!state.revealed) vm.reveal() },
                front = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = card.word,
                            style = frontStyle.textStyle,
                            textAlign = TextAlign.Center
                        )
                        if (card.cefr.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            CefrBadge(level = card.cefr, outlined = true)
                        }
                    }
                },
                back = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = card.translation.ifBlank { "—" },
                            style = backStyle.textStyle,
                            textAlign = TextAlign.Center
                        )
                        if (card.contextSentence.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "“${card.contextSentence}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = !state.revealed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = stringResource(R.string.review_reveal),
                    onClick = vm::reveal,
                    icon = Icons.Rounded.Visibility
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.review_flip_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = state.revealed,
            enter = slideInVertically(tween(260)) { it } + fadeIn(),
            exit = slideOutVertically(tween(180)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_again),
                    interval = state.intervals[Rating.AGAIN],
                    color = Color(0xFFEF4444),
                    onClick = { vm.rate(Rating.AGAIN) }
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_hard),
                    interval = state.intervals[Rating.HARD],
                    color = Color(0xFFF59E0B),
                    onClick = { vm.rate(Rating.HARD) }
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_good),
                    interval = state.intervals[Rating.GOOD],
                    color = Color(0xFF6366F1),
                    onClick = { vm.rate(Rating.GOOD) }
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.review_easy),
                    interval = state.intervals[Rating.EASY],
                    color = Color(0xFF22C55E),
                    onClick = { vm.rate(Rating.EASY) }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun RatingButton(
    label: String,
    interval: Double?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intervalLabel = interval?.let { formatIntervalShort(it) } ?: ""
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.16f),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, color.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        fontSize = 13.sp
                    )
                    if (intervalLabel.isNotBlank()) {
                        Text(
                            text = intervalLabel,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatIntervalShort(days: Double): String {
    val parts = com.proudvocab.android.core.srs.SrsScheduler.formatInterval(days)
    val value = parts.value
    val number = if (value is Double) String.format(Locale.US, "%.1f", value) else value.toString()
    return when (parts.unit) {
        IntervalUnit.MINUTE -> stringResource(R.string.interval_minutes, number.toIntOrNull() ?: 10)
        IntervalUnit.HOUR -> stringResource(R.string.interval_hours, number.toIntOrNull() ?: 1)
        IntervalUnit.DAY -> stringResource(R.string.interval_days, number.toIntOrNull() ?: 1)
        IntervalUnit.MONTH -> stringResource(R.string.interval_months, number.toIntOrNull() ?: 1)
        IntervalUnit.YEAR -> stringResource(R.string.interval_years, number)
    }
}

@Composable
private fun FlipCard(
    revealed: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "flip"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onFlip),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            CardFace { front() }
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                CardFace { back() }
            }
        }
    }
}

@Composable
private fun CardFace(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(22.dp)) {
            content()
        }
    }
}

@Composable
private fun SessionSummary(
    state: ReviewUiState,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedAppearance {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.review_finished),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedCounter(
                value = state.sessionCorrect,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF22C55E)
            )
            Text("/")
            AnimatedCounter(
                value = state.sessionWrong,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFEF4444)
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = stringResource(R.string.done),
            onClick = vm::quitSession,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun SavedWord.displayTranslation(): String =
    translation.ifBlank { contextSentence }

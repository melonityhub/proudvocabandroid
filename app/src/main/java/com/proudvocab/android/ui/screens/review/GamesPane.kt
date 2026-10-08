package com.proudvocab.android.ui.screens.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.JoinInner
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proudvocab.android.R
import com.proudvocab.android.core.games.GameQuestion
import com.proudvocab.android.core.games.GameType
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.speech.Speaker
import com.proudvocab.android.core.util.TextUtils
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.AnimatedAppearance
import com.proudvocab.android.ui.components.EmptyState
import com.proudvocab.android.ui.components.Pill
import com.proudvocab.android.ui.components.PrimaryButton
import com.proudvocab.android.ui.components.SectionHeader
import com.proudvocab.android.ui.theme.rememberTargetStyle

private data class GameInfo(
    val type: GameType,
    val titleRes: Int,
    val descRes: Int,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun GamesPane(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    when {
        state.activeGame != null && !state.gameFinished ->
            GameRunner(state, settings, vm, modifier)
        state.gameFinished -> GameSummary(state, vm, modifier)
        else -> GamesHub(state, settings, vm, modifier)
    }
}

@Composable
private fun GamesHub(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    val games = listOf(
        GameInfo(
            GameType.MULTIPLE_CHOICE, R.string.games_mc_title, R.string.games_mc_desc,
            Color(0xFFA5B4FC), Icons.Rounded.Checklist
        ),
        GameInfo(
            GameType.FILL_BLANK, R.string.games_blank_title, R.string.games_blank_desc,
            Color(0xFF7DD3FC), Icons.Rounded.Edit
        ),
        GameInfo(
            GameType.SCRAMBLE, R.string.games_scramble_title, R.string.games_scramble_desc,
            Color(0xFFC9A3FB), Icons.Rounded.Shuffle
        ),
        GameInfo(
            GameType.MATCH, R.string.games_match_title, R.string.games_match_desc,
            Color(0xFF86EFAC), Icons.Rounded.JoinInner
        ),
        GameInfo(
            GameType.DICTATION, R.string.games_dictation_title, R.string.games_dictation_desc,
            Color(0xFFFDBA74), Icons.AutoMirrored.Rounded.VolumeUp
        ),
        GameInfo(
            GameType.CONTEXT_CHOICE, R.string.games_context_title, R.string.games_context_desc,
            Color(0xFF93C5FD), Icons.AutoMirrored.Rounded.Article
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(text = stringResource(R.string.games_choose_prompt))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            games.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { game ->
                        GameTile(
                            game = game,
                            plays = state.gameStats.firstOrNull { it.game == game.type.key }?.plays ?: 0,
                            best = state.gameStats.firstOrNull { it.game == game.type.key }?.bestScore ?: 0,
                            persianDigits = settings.usePersianDigits,
                            onClick = { vm.startGame(game.type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.gameStats.isNotEmpty()) {
            SectionHeader(text = stringResource(R.string.games_stats))
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                state.gameStats.forEach { stat ->
                    val info = games.firstOrNull { it.type.key == stat.game }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info?.let { stringResource(it.titleRes) } ?: stat.game,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = TextUtils.formatNumber(stat.bestScore, settings.usePersianDigits),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun GameTile(
    game: GameInfo,
    plays: Int,
    best: Int,
    persianDigits: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedAppearance(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = game.color.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, game.color.copy(alpha = 0.32f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = game.color.copy(alpha = 0.20f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(game.icon, null, tint = game.color, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(game.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(game.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2
                )
                if (plays > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "★ " + TextUtils.formatNumber(best, persianDigits),
                        style = MaterialTheme.typography.labelSmall,
                        color = game.color
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ runner

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRunner(
    state: ReviewUiState,
    settings: AppSettings,
    vm: ReviewViewModel,
    modifier: Modifier = Modifier
) {
    val deps = LocalDependencies.current
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val gameStyle = rememberTargetStyle(StyleTarget.GAME, settings, deps.fonts)
    val question = state.currentQuestion

    var typed by remember(question) { mutableStateOf("") }
    var selectedWord by remember(question) { mutableStateOf<String?>(null) }
    var matched by remember(question) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(question, settings.gameAutoPronounce) {
        if (question != null && settings.gameAutoPronounce &&
            question.type == GameType.DICTATION
        ) {
            speaker.speak(question.word, settings.learningLanguage)
        }
    }

    if (question == null) {
        EmptyState(
            title = stringResource(R.string.games_not_enough_words, 4),
            body = "",
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.gameIndex + 1} / ${state.questions.size}",
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
                    state.gameProgress, tween(320), label = "gameProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = TextUtils.formatNumber(state.gameScore, settings.usePersianDigits),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.games_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { vm.exitGame() }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))

            when (question.type) {
                GameType.MULTIPLE_CHOICE -> {
                    Text(
                        text = question.word,
                        style = gameStyle.textStyle,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.games_mc_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GameType.FILL_BLANK, GameType.CONTEXT_CHOICE -> {
                    Text(
                        text = question.context,
                        style = gameStyle.textStyle.copy(fontSize = (gameStyle.fontSizeSp * 0.85f).sp),
                        textAlign = TextAlign.Center
                    )
                }
                GameType.SCRAMBLE -> {
                    Text(
                        text = question.scrambled.uppercase(),
                        style = gameStyle.textStyle.copy(
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = question.context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                GameType.MATCH -> {
                    Text(
                        text = stringResource(R.string.games_match_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GameType.DICTATION -> {
                    IconButton(
                        onClick = { speaker.speak(question.word, settings.learningLanguage) },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = question.context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            when (question.type) {
                GameType.DICTATION, GameType.SCRAMBLE -> {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.games_dictation_desc)) }
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = stringResource(R.string.games_next),
                        onClick = {
                            vm.answer(typed)
                            typed = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = typed.isNotBlank()
                    )
                }
                GameType.MATCH -> {
                    val pairs = state.matchPairs
                    val shuffledMeanings = remember(pairs) { pairs.shuffled() }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            pairs.forEach { pair ->
                                val isMatched = pair.word in matched
                                Pill(
                                    text = pair.word,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    selected = selectedWord == pair.word,
                                    color = if (isMatched) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.primary,
                                    onClick = { selectedWord = pair.word }
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            shuffledMeanings.forEach { pair ->
                                val isMatched = pair.word in matched
                                Pill(
                                    text = pair.meaning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    color = if (isMatched) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.secondary,
                                    onClick = {
                                        if (selectedWord == pair.word) {
                                            vm.answer(pair.word)
                                            matched = matched + pair.word
                                            selectedWord = null
                                        } else {
                                            vm.answer("")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (matched.size == pairs.size && pairs.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = stringResource(R.string.games_next),
                            onClick = vm::nextQuestion,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        question.options.forEach { option ->
                            OptionButton(
                                text = option,
                                enabled = state.lastAnswerCorrect == null,
                                isCorrect = state.lastAnswerCorrect?.let {
                                    if (option == question.answer) true else null
                                },
                                onClick = { vm.answer(option) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(
                visible = state.lastAnswerCorrect != null,
                enter = slideInVertically(tween(240)) { it } + fadeIn(),
                exit = slideOutVertically(tween(160)) + fadeOut()
            ) {
                val correct = state.lastAnswerCorrect == true
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (correct) Color(0xFF22C55E).copy(alpha = 0.16f)
                        else Color(0xFFEF4444).copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (correct) "✓" else "✕",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (correct) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                            if (!correct) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.games_correct_answer_was, question.answer
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = stringResource(R.string.games_next),
                        onClick = vm::nextQuestion,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun OptionButton(
    text: String,
    enabled: Boolean,
    isCorrect: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (isCorrect) {
        true -> Color(0xFF22C55E)
        false -> Color(0xFFEF4444)
        null -> MaterialTheme.colorScheme.primary
    }
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.98f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "option"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = if (isCorrect == null) 0.10f else 0.22f),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GameSummary(
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
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.games_finished),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.gameScore.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = stringResource(R.string.games_score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.gameCorrect.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF22C55E)
                )
                Text(
                    text = stringResource(R.string.games_correct),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.gameWrong.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
                Text(
                    text = stringResource(R.string.games_wrong),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        PrimaryButton(
            text = stringResource(R.string.games_replay),
            onClick = { state.activeGame?.let { vm.startGame(it) } },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.RestartAlt
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = vm::exitGame) {
            Text(stringResource(R.string.games_exit))
        }
    }
}

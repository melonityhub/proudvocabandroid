package com.proudvocab.android.ui.screens.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proudvocab.android.R
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.WordKind
import com.proudvocab.android.core.speech.Speaker
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.CefrBadge
import com.proudvocab.android.ui.components.Pill
import com.proudvocab.android.ui.screens.player.WordLookup
import com.proudvocab.android.ui.theme.rememberTargetStyle

/**
 * The sheet that opens when a word is tapped anywhere in the app
 * (subtitle chips, dictionary results, archive rows).
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun WordLookupSheet(
    lookup: WordLookup,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenDictionary: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val deps = LocalDependencies.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    val headwordStyle = rememberTargetStyle(StyleTarget.WORD_CARD, settings, deps.fonts)
    val translationStyle = rememberTargetStyle(StyleTarget.WORD_TRANSLATION, settings, deps.fonts)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp)
                .animateContentSize(spring(Spring.DampingRatioNoBouncy))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lookup.word,
                    style = headwordStyle.textStyle,
                    modifier = Modifier.weight(1f)
                )
                if (lookup.cefr != null) {
                    CefrBadge(level = lookup.cefr)
                    Spacer(Modifier.width(8.dp))
                }
                SaveButton(saved = lookup.saved, onClick = onToggleSave)
            }

            if (lookup.kind != WordKind.WORD.id) {
                Spacer(Modifier.height(8.dp))
                Pill(
                    text = stringResource(
                        if (lookup.kind == WordKind.IDIOM.id) R.string.popup_idiom
                        else R.string.popup_phrasal
                    ),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = lookup.loading,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut())
                },
                label = "lookupBody"
            ) { loading ->
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.popup_translating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        if (!lookup.translation.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lookup.translation,
                                    style = translationStyle.textStyle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        val phonetic = lookup.entry?.phoneticUs ?: lookup.entry?.phoneticUk
                        if (!phonetic.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { speaker.speak(lookup.word, settings.learningLanguage) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = stringResource(R.string.dict_pronounce),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = phonetic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (lookup.offline) {
                                    Spacer(Modifier.width(8.dp))
                                    Pill(
                                        text = stringResource(R.string.dict_offline_badge),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        val meanings = lookup.entry?.meanings.orEmpty()
                        if (meanings.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            meanings.take(6).forEach { meaning ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    if (meaning.partOfSpeech != 0) {
                                        Text(
                                            text = meaning.posName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(72.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.width(72.dp))
                                    }
                                    Text(
                                        text = meaning.persianMeaning,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (settings.showWordFamily) {
                            val family = lookup.entry?.family?.takeIf { it.isNotEmpty() }
                                ?: deps.dictionary.generateFamily(lookup.word)
                            if (family.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.popup_word_family),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    family.take(6).forEach { word ->
                                        Pill(text = word)
                                    }
                                }
                            }
                        }

                        if (lookup.contextSentence.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.popup_context),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "“${lookup.contextSentence}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onOpenDictionary != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenDictionary,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.popup_open_in_dictionary))
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(saved: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (saved) 1.15f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "save"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (saved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = null,
            tint = if (saved) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

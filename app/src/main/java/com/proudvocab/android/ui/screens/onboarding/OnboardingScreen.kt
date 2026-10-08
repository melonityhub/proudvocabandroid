package com.proudvocab.android.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proudvocab.android.R
import com.proudvocab.android.core.model.Languages
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.ui.LocalDependencies
import com.proudvocab.android.ui.components.PrimaryButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val deps = LocalDependencies.current
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    val pages = listOf(
        Page(Icons.Rounded.AutoAwesome, R.string.ob_welcome_title, R.string.ob_welcome_body),
        Page(Icons.Rounded.Language, R.string.ob_languages_title, R.string.ob_languages_body),
        Page(Icons.Rounded.Palette, R.string.ob_style_title, R.string.ob_style_body),
        Page(Icons.Rounded.CloudOff, R.string.ob_offline_title, R.string.ob_offline_body)
    )
    val isLast = step == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            // Page indicator
            Row(horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { index ->
                    val width by animateDpAsState(
                        targetValue = if (index == step) 26.dp else 8.dp,
                        animationSpec = spring(),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (index == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            tween(320, easing = FastOutSlowInEasing)
                        ) { it / 3 } + fadeIn(),
                        initialContentExit = ExitTransition.None,
                        sizeTransform = SizeTransform(clip = false)
                    )
                },
                label = "onboarding",
                modifier = Modifier.weight(1f)
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val page = pages[index]
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(104.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = stringResource(page.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(page.body),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(26.dp))
                    when (index) {
                        1 -> LanguageChoices(settings.learningLanguage, settings.translationLanguage) { learn, translate ->
                            scope.launch {
                                deps.settings.setLearningLanguage(learn)
                                deps.settings.setTranslationLanguage(translate)
                            }
                        }
                        2 -> StylePreviewHint()
                        3 -> OfflineHint()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLast) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.ob_skip))
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(if (isLast) R.string.ob_start else R.string.ob_next),
                    onClick = {
                        if (isLast) onFinish() else step++
                    }
                )
            }
        }
    }
}

private data class Page(val icon: ImageVector, val title: Int, val body: Int)

@Composable
private fun LanguageChoices(
    learn: String,
    translate: String,
    onChange: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageRow(
            label = stringResource(R.string.settings_learning_language),
            selected = learn,
            onSelect = { onChange(it, translate) }
        )
        LanguageRow(
            label = stringResource(R.string.settings_translation_language),
            selected = translate,
            onSelect = { onChange(learn, it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageRow(
    label: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    val popular = listOf("en", "fa", "tr", "de", "fr", "es", "ar", "ru", "ja", "ko")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            popular.forEach { code ->
                val lang = Languages.byCode(code)
                val isSelected = code == selected
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .heightIn(min = 44.dp)
                        .clickable { onSelect(code) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = lang.nativeName,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePreviewHint() {
    val deps = LocalDependencies.current
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val subStyle = settings.styleFor(com.proudvocab.android.core.settings.StyleTarget.SUBTITLE_PRIMARY)
    val transStyle = settings.styleFor(com.proudvocab.android.core.settings.StyleTarget.SUBTITLE_SECONDARY)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "I couldn’t have done it without you.",
            fontSize = (subStyle.scale * 18).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "بدون تو نمی‌توانستم انجامش دهم.",
            fontSize = (transStyle.scale * 15).sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OfflineHint() {
    Text(
        text = stringResource(R.string.dict_offline_only_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

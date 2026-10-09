package com.proudvocab.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.TextAlignPref
import com.proudvocab.android.core.settings.TextStylePref
import com.proudvocab.android.core.util.ColorCodec

/** A [StyleTarget] fully resolved into something Compose can paint with. */
data class ResolvedStyle(
    val textStyle: TextStyle,
    val color: Color,
    val background: Color?,
    val align: TextAlign,
    val fontFamily: FontFamily,
    val pref: TextStylePref,
    val fontSizeSp: Float
)

@Composable
fun rememberTargetStyle(
    target: StyleTarget,
    settings: AppSettings,
    fonts: FontRepository
): ResolvedStyle {
    val pref = settings.styleFor(target)
    val scheme = MaterialTheme.colorScheme
    val fallbackColor = when (target) {
        StyleTarget.APP -> scheme.onSurface
        StyleTarget.SUBTITLE_PRIMARY -> Color.White
        StyleTarget.SUBTITLE_SECONDARY -> Color(0xFFCBD5E1)
        StyleTarget.TRANSCRIPT -> scheme.onSurfaceVariant
        StyleTarget.WORD_CARD -> scheme.onSurface
        StyleTarget.WORD_TRANSLATION -> scheme.onSurface.copy(alpha = 0.82f)
        StyleTarget.DICTIONARY -> scheme.onSurface
        StyleTarget.FLASHCARD_FRONT -> scheme.onSurface
        StyleTarget.FLASHCARD_BACK -> scheme.primary
        StyleTarget.GAME -> scheme.onSurface
        StyleTarget.ARCHIVE_WORD -> scheme.onSurface
        StyleTarget.ARCHIVE_TRANSLATION -> scheme.onSurface.copy(alpha = 0.75f)
    }

    return remember(pref, target, scheme, fonts) {
        val weight = if (pref.bold ?: target.defaultBold) FontWeight.Bold else FontWeight.Normal
        val fontStyle = if (pref.italic) FontStyle.Italic else FontStyle.Normal
        val family = fonts.resolve(pref.font, weight, fontStyle)
        val size = (target.baseSizeSp * pref.scale).sp
        val color = ColorCodec.parseColor(pref.color) ?: fallbackColor
        val background = ColorCodec.parseColor(pref.background)
        val shadow = if (pref.shadowDp > 0f) {
            Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = pref.shadowDp)
        } else null
        ResolvedStyle(
            textStyle = TextStyle(
                fontFamily = family,
                fontWeight = weight,
                fontStyle = fontStyle,
                fontSize = size,
                letterSpacing = pref.letterSpacingSp.sp,
                lineHeight = (target.baseSizeSp * pref.scale * pref.lineHeight).sp,
                color = color,
                background = background ?: Color.Unspecified,
                textAlign = pref.align.toTextAlign(),
                textDecoration = if (pref.underline) TextDecoration.Underline else TextDecoration.None,
                shadow = shadow
            ),
            color = color,
            background = background,
            align = pref.align.toTextAlign(),
            fontFamily = family,
            pref = pref,
            fontSizeSp = target.baseSizeSp * pref.scale
        )
    }
}

fun TextAlignPref.toTextAlign(): TextAlign = when (this) {
    TextAlignPref.START -> TextAlign.Start
    TextAlignPref.CENTER -> TextAlign.Center
    TextAlignPref.END -> TextAlign.End
}

/** Same resolution, without Compose — handy for previews and export. */
fun resolveFontSize(target: StyleTarget, pref: TextStylePref): TextUnit =
    (target.baseSizeSp * pref.scale).sp

val StyleTarget.previewSample: String
    get() = when (this) {
        StyleTarget.APP -> "Saved words 128"
        StyleTarget.SUBTITLE_PRIMARY -> "I couldn’t have done it without you."
        StyleTarget.SUBTITLE_SECONDARY -> "بدون تو نمی‌توانستم انجامش دهم."
        StyleTarget.TRANSCRIPT -> "00:12 · She said it was the best day of her life."
        StyleTarget.WORD_CARD -> "serendipity"
        StyleTarget.WORD_TRANSLATION -> "اتفاق خوش‌یمن، کشف خوش‌اقبال"
        StyleTarget.DICTIONARY -> "resilient — able to recover quickly"
        StyleTarget.FLASHCARD_FRONT -> "meticulous"
        StyleTarget.FLASHCARD_BACK -> "دقیق و موشکاف"
        StyleTarget.GAME -> "Which one means “ambiguous”?"
        StyleTarget.ARCHIVE_WORD -> "reluctant"
        StyleTarget.ARCHIVE_TRANSLATION -> "بی‌میل، ناخواسته"
    }

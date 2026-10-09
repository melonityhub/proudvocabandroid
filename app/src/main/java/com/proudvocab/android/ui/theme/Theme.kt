package com.proudvocab.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.TextAlignPref
import com.proudvocab.android.core.settings.TextStylePref
import com.proudvocab.android.core.settings.ThemeMode
import com.proudvocab.android.core.util.ColorCodec

/** Accent colour used before the user picks one; matches the brand gradient. */
val BrandIndigo = Color(0xFF6366F1)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1B1B3A),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF06283D),
    secondaryContainer = Color(0xFF0E4B63),
    onSecondaryContainer = Color(0xFFCBE9FF),
    tertiary = Color(0xFFF0ABFC),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF141A2E),
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF222A44),
    onSurfaceVariant = Color(0xFFB9C0DA),
    outline = Color(0xFF3A4470),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0A06)
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0E1220),
    primaryContainer = Color(0xFF241E5E)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EEFF),
    onSecondaryContainer = Color(0xFF04263A),
    tertiary = Color(0xFFA21CAF),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF161A2B),
    surface = Color.White,
    onSurface = Color(0xFF161A2B),
    surfaceVariant = Color(0xFFE7E9F5),
    onSurfaceVariant = Color(0xFF454A64),
    outline = Color(0xFFC3C7DE),
    error = Color(0xFFB3261E),
    onError = Color.White
)

/** Palette used by the word-chip CEFR colouring. */
object CefrColors {
    val A1 = Color(0xFF4ADE80)
    val A2 = Color(0xFFA3E635)
    val B1 = Color(0xFFFACC15)
    val B2 = Color(0xFFFB923C)
    val C1 = Color(0xFFF87171)
    val C2 = Color(0xFFE879F9)
    val Unknown = Color(0xFF94A3B8)

    fun forLevel(level: String?): Color = when (level?.uppercase()) {
        "A1" -> A1
        "A2" -> A2
        "B1" -> B1
        "B2" -> B2
        "C1" -> C1
        "C2" -> C2
        else -> Unknown
    }
}

@Composable
fun ProudVocabTheme(
    settings: AppSettings,
    fonts: FontRepository,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val mode = settings.themeMode()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val colorScheme = remember(settings.theme, settings.dynamicColor, settings.accentHex, dark) {
        val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            settings.dynamicColor
        val base = when {
            supportsDynamic && dark -> dynamicDarkColorScheme(context)
            supportsDynamic && !dark -> dynamicLightColorScheme(context)
            dark && mode == ThemeMode.AMOLED -> AmoledColors
            dark -> DarkColors
            else -> LightColors
        }
        val accent = settings.accentHex?.let { ColorCodec.parseULong(it) }
        val accentColor = ColorCodec.parseColor(settings.accentHex)
        if (accent != null && accentColor != null && !supportsDynamic) {
            base.copy(
                primary = accentColor,
                onPrimary = if (ColorCodec.isDark(accent)) Color.White else Color.Black
            )
        } else base
    }

    val appText = settings.styleFor(StyleTarget.APP)
    val appWeight = appText.bold?.let { if (it) FontWeight.Bold else FontWeight.Normal }
        ?: FontWeight.Normal
    val appStyle = if (appText.italic) FontStyle.Italic else FontStyle.Normal
    val appFamily = remember(appText.font, appWeight, appStyle, fonts) {
        fonts.resolve(appText.font, appWeight, appStyle)
    }
    val appColor = ColorCodec.parseColor(appText.color)
    val appBackground = ColorCodec.parseColor(appText.background)
    val typography = remember(appText, appFamily, appColor, appBackground) {
        AppTypography.withAppStyle(appText, appFamily, appColor, appBackground)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

private fun Typography.withAppStyle(
    pref: TextStylePref,
    family: FontFamily,
    color: Color?,
    background: Color?
): Typography {
    val scale = pref.scale.coerceIn(0.5f, 2.5f)
    val lineHeightScale = (pref.lineHeight / 1.35f).coerceIn(0.6f, 2f)
    val weight = pref.bold?.let { if (it) FontWeight.Bold else FontWeight.Normal }
    val alignment = when (pref.align) {
        TextAlignPref.START -> TextAlign.Start
        TextAlignPref.CENTER -> TextAlign.Center
        TextAlignPref.END -> TextAlign.End
    }
    val shadow = if (pref.shadowDp > 0f) {
        Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = pref.shadowDp)
    } else null

    fun TextStyle.styled(): TextStyle = copy(
        fontFamily = family,
        fontWeight = weight ?: fontWeight,
        fontStyle = if (pref.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * scale,
        lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight
        else lineHeight * scale * lineHeightScale,
        letterSpacing = when {
            letterSpacing == TextUnit.Unspecified -> pref.letterSpacingSp.sp
            letterSpacing.type == TextUnitType.Sp -> (letterSpacing.value + pref.letterSpacingSp).sp
            else -> letterSpacing
        },
        color = color ?: this.color,
        background = background ?: this.background,
        textAlign = alignment,
        textDecoration = if (pref.underline) TextDecoration.Underline else TextDecoration.None,
        shadow = shadow
    )

    return Typography(
        displayLarge = displayLarge.styled(),
        displayMedium = displayMedium.styled(),
        displaySmall = displaySmall.styled(),
        headlineLarge = headlineLarge.styled(),
        headlineMedium = headlineMedium.styled(),
        headlineSmall = headlineSmall.styled(),
        titleLarge = titleLarge.styled(),
        titleMedium = titleMedium.styled(),
        titleSmall = titleSmall.styled(),
        bodyLarge = bodyLarge.styled(),
        bodyMedium = bodyMedium.styled(),
        bodySmall = bodySmall.styled(),
        labelLarge = labelLarge.styled(),
        labelMedium = labelMedium.styled(),
        labelSmall = labelSmall.styled()
    )
}

private val AppTypography = Typography()

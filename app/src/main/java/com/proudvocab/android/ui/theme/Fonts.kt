package com.proudvocab.android.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Xml
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

enum class FontGroup { BUILTIN, DEVICE, CUSTOM }

data class FontOption(val key: String, val name: String, val group: FontGroup)

/**
 * Resolves the `font` field of a [com.proudvocab.android.core.settings.TextStylePref]
 * into a Compose [FontFamily].
 *
 * Three sources are supported and mixed in the same picker:
 *  * the four built-in generic families,
 *  * every font family installed on the device,
 *  * font files the user imported from storage.
 */
class FontRepository(private val context: Context) {

    val fontsDir: File
        get() = File(context.filesDir, "fonts").also { it.mkdirs() }

    // Read from the composition thread and written from the font import
    // coroutine, so it has to be a concurrent map.
    private val cache = ConcurrentHashMap<String, FontFamily>()

    fun builtIns(): List<FontOption> = listOf(
        FontOption(com.proudvocab.android.core.settings.FontKeys.SYSTEM, "system", FontGroup.BUILTIN),
        FontOption(com.proudvocab.android.core.settings.FontKeys.SANS, "sans", FontGroup.BUILTIN),
        FontOption(com.proudvocab.android.core.settings.FontKeys.SERIF, "serif", FontGroup.BUILTIN),
        FontOption(com.proudvocab.android.core.settings.FontKeys.MONO, "mono", FontGroup.BUILTIN)
    )

    /** Every font family declared by the system font configuration. */
    fun deviceFonts(): List<FontOption> {
        val names = LinkedHashSet<String>()
        for (path in listOf(
            "/system/etc/fonts.xml",
            "/system/fonts.xml",
            "/vendor/etc/fonts.xml",
            "/product/etc/fonts.xml"
        )) {
            runCatching {
                val file = File(path)
                if (!file.exists()) return@runCatching
                FileInputStream(file).use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input, null)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "family") {
                            val name = parser.getAttributeValue(null, "name")
                            if (!name.isNullOrBlank()) names += name
                        }
                        event = parser.next()
                    }
                }
            }
        }
        if (names.isEmpty()) names += "sans-serif"
        return names.sorted().map {
            FontOption(
                com.proudvocab.android.core.settings.FontKeys.DEVICE_PREFIX + it,
                it,
                FontGroup.DEVICE
            )
        }
    }

    fun importedFonts(): List<FontOption> = fontsDir.listFiles()
        ?.filter { it.isFile && it.length() > 0L }
        ?.sortedBy { it.name }
        ?.map {
            FontOption(
                com.proudvocab.android.core.settings.FontKeys.CUSTOM_PREFIX + it.name,
                displayNameOf(it.name),
                FontGroup.CUSTOM
            )
        } ?: emptyList()

    fun all(): List<FontOption> = builtIns() + deviceFonts() + importedFonts()

    /**
     * [all] reads `/system/etc/fonts.xml` and lists the imported-font folder,
     * which is disk I/O and must not run on the UI thread.
     */
    suspend fun allAsync(): List<FontOption> = withContext(Dispatchers.IO) {
        runCatching { all() }.getOrDefault(builtIns())
    }

    fun displayNameOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')

    /** Copies the picked file into the app's font folder. */
    suspend fun importFont(uri: Uri): Result<FontOption> = withContext(Dispatchers.IO) {
        runCatching {
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull() ?: "font_${System.currentTimeMillis()}.ttf"
            val safe = name.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                .ifBlank { "font_${System.currentTimeMillis()}.ttf" }
            val target = File(fontsDir, safe)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("cannot read the file")
                // Fail fast when the file is not a real font. Typeface throws
                // for garbage input, so the probe itself must be guarded; the
                // copy is removed so it never shows up in the font list.
                val probe = runCatching { Typeface.createFromFile(target) }.getOrNull()
                if (probe == null) error("not a font file")
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            cache.clear()
            FontOption(
                com.proudvocab.android.core.settings.FontKeys.CUSTOM_PREFIX + safe,
                displayNameOf(safe),
                FontGroup.CUSTOM
            )
        }
    }

    suspend fun deleteFont(option: FontOption) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(fontsDir, option.key.removePrefix(
                com.proudvocab.android.core.settings.FontKeys.CUSTOM_PREFIX
            ))
            if (file.exists()) file.delete()
            cache.clear()
        }
    }

    /**
     * Never throws: an unknown or missing font silently falls back to the
     * device default so a broken setting can never crash the UI.
     */
    fun resolve(
        key: String,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal
    ): FontFamily {
        val cacheKey = "$key|${weight.weight}|$style"
        cache[cacheKey]?.let { return it }
        val keys = com.proudvocab.android.core.settings.FontKeys
        val family: FontFamily = when {
            key == keys.MONO -> FontFamily.Monospace
            key == keys.SERIF -> FontFamily.Serif
            key == keys.SANS -> FontFamily.SansSerif
            key == keys.SYSTEM -> FontFamily.Default
            keys.isDevice(key) -> runCatching {
                Typeface.create(keys.deviceName(key), typefaceStyle(weight, style))
                    ?.let { FontFamily(it) }
                    ?: FontFamily.Default
            }.getOrDefault(FontFamily.Default)

            keys.isCustom(key) -> runCatching {
                val file = File(fontsDir, keys.customName(key))
                if (!file.exists()) return@runCatching FontFamily.Default
                FontFamily(Font(file, weight, style))
            }.getOrDefault(FontFamily.Default)

            else -> FontFamily.Default
        }
        cache[cacheKey] = family
        return family
    }

    private fun typefaceStyle(weight: FontWeight, style: FontStyle): Int =
        if (style == FontStyle.Italic) {
            if (weight >= FontWeight.Bold) Typeface.BOLD_ITALIC else Typeface.ITALIC
        } else {
            if (weight >= FontWeight.Bold) Typeface.BOLD else Typeface.NORMAL
        }

    fun clearCache() = cache.clear()
}

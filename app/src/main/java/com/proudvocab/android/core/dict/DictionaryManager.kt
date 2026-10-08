package com.proudvocab.android.core.dict

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** What kind of phrase matched inside a subtitle line. */
data class PhraseMatch(
    val phrase: String,
    val firstWordIndex: Int,
    val wordCount: Int,
    val meaning: String,
    val kindInt: Int
)

/**
 * Owns the offline dictionary (the bundled starter pack or a database the user
 * imported) and the small linguistic assets that ship with the app:
 * the CEFR level list, the idiom and the phrasal-verb tables.
 */
class DictionaryManager(private val context: Context) {

    private val mutex = Mutex()
    private var database: FastdicDatabase? = null
    private var openedPath: String? = null

    private val cefrCache = HashMap<String, String>()
    private var cefrLoaded = false
    private val idiomCache = HashMap<String, Map<String, String>>()
    private val phrasalCache = HashMap<String, Map<String, String>>()

    private val json = Json { ignoreUnknownKeys = true }

    val dir: File get() = File(context.filesDir, "dictionary").also { it.mkdirs() }
    private val importedFile get() = File(dir, "imported.sqlite")
    private val starterFile get() = File(dir, "starter.sqlite")

    /** True when a user-imported database is present. */
    fun hasImported(): Boolean = importedFile.exists() && importedFile.length() > 0L

    suspend fun open(forceReload: Boolean = false): FastdicDatabase? = mutex.withLock {
        if (database != null && !forceReload) return database
        database?.close()
        database = null
        val path = when {
            hasImported() -> importedFile.absolutePath
            else -> ensureStarterExtracted()?.absolutePath
        }
        database = path?.let { FastdicDatabase.open(it) }
        openedPath = if (database != null) path else null
        return database
    }

    private fun ensureStarterExtracted(): File? {
        if (starterFile.exists() && starterFile.length() > 0L) return starterFile
        return runCatching {
            context.assets.open("starter_dictionary.sqlite").use { input ->
                FileOutputStream(starterFile).use { output -> input.copyTo(output) }
            }
            starterFile
        }.getOrNull()
    }

    suspend fun wordCount(): Long = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext 0L
        runCatching { db.countEnglish() + db.countPersian() }.getOrDefault(0L)
    }

    /** Copies the file behind [uri] into the app and validates it. */
    suspend fun importDatabase(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(dir, "import.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: error("cannot open file")
            val probe = FastdicDatabase.open(tmp.absolutePath)
                ?: error("not a FastDic database")
            val count = probe.countEnglish() + probe.countPersian()
            probe.close()
            if (importedFile.exists()) importedFile.delete()
            tmp.renameTo(importedFile)
            open(forceReload = true)
            count
        }
    }

    suspend fun removeImported() = withContext(Dispatchers.IO) {
        if (importedFile.exists()) importedFile.delete()
        open(forceReload = true)
    }

    // ------------------------------------------------------------- lookups

    suspend fun search(query: String, limit: Int = 50): List<DictSearchResult> =
        withContext(Dispatchers.IO) {
            val db = open() ?: return@withContext emptyList()
            runCatching { db.search(query, limit) }.getOrDefault(emptyList())
        }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext emptyList()
        runCatching { db.suggestions(query) }.getOrDefault(emptyList())
    }

    suspend fun entry(word: String): WordEntry? = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext null
        runCatching { db.entry(word) }.getOrNull()
    }

    suspend fun gloss(word: String): String = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext ""
        runCatching { db.gloss(word) }.getOrDefault("")
    }

    // --------------------------------------------------------------- assets

    private fun loadCefr() {
        if (cefrLoaded) return
        synchronized(this) {
            if (cefrLoaded) return
            runCatching {
                context.assets.open("cefr.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val idx = line.indexOf('=')
                        if (idx <= 0) continue
                        cefrCache[line.substring(0, idx)] = line.substring(idx + 1).trim()
                    }
                }
            }
            cefrLoaded = true
        }
    }

    /** CEFR level of a word, from the bundled list; falls back to the database. */
    fun cefr(word: String): String? {
        loadCefr()
        val key = word.lowercase().trim()
        return cefrCache[key]
    }

    private fun idioms(lang: String): Map<String, String> =
        idiomCache.getOrPut(lang) { loadMap("idioms.json", lang) }

    private fun phrasals(lang: String): Map<String, String> =
        phrasalCache.getOrPut(lang) { loadMap("phrasal.json", lang) }

    private fun loadMap(asset: String, lang: String): Map<String, String> = runCatching {
        val raw = context.assets.open(asset).bufferedReader().use { it.readText() }
        val all: Map<String, Map<String, String>> = json.decodeFromString(raw)
        all[lang] ?: all["en"] ?: emptyMap()
    }.getOrDefault(emptyMap())

    /**
     * Finds idioms and phrasal verbs inside a subtitle line.
     *
     * @param words the word tokens of the line, in order
     * @param lang  code used to pick the explanation language
     */
    fun findPhrases(words: List<String>, lang: String): List<PhraseMatch> {
        if (words.isEmpty()) return emptyList()
        val idioms = idioms(lang)
        val phrasals = phrasals(lang)
        val out = ArrayList<PhraseMatch>()
        val taken = BooleanArray(words.size)
        val maxLen = minOf(6, words.size)
        for (len in maxLen downTo 2) {
            for (start in 0..words.size - len) {
                if (taken[start]) continue
                var overlap = false
                for (k in start until start + len) if (taken[k]) overlap = true
                if (overlap) continue
                val phrase = words.subList(start, start + len).joinToString(" ")
                val idiom = idioms[phrase.lowercase()]
                if (idiom != null) {
                    out += PhraseMatch(phrase, start, len, idiom, WordKindInt.IDIOM)
                    for (k in start until start + len) taken[k] = true
                    continue
                }
                val phrasal = phrasals[phrase.lowercase()]
                if (phrasal != null) {
                    out += PhraseMatch(phrase, start, len, phrasal, WordKindInt.PHRASAL)
                    for (k in start until start + len) taken[k] = true
                }
            }
        }
        return out.sortedBy { it.firstWordIndex }
    }

    fun isIdiom(phrase: String, lang: String): Boolean =
        idioms(lang).containsKey(phrase.lowercase())

    fun isPhrasal(phrase: String, lang: String): Boolean =
        phrasals(lang).containsKey(phrase.lowercase())

    fun idiomMeaning(phrase: String, lang: String): String? =
        idioms(lang)[phrase.lowercase()] ?: phrasals(lang)[phrase.lowercase()]

    /** Generates a plausible word family from suffix rules. */
    fun generateFamily(word: String): List<String> {
        val w = word.lowercase()
        if (w.length < 3) return emptyList()
        val out = LinkedHashSet<String>()
        out += w
        when {
            w.endsWith("e") -> {
                out += w + "s"; out += w.dropLast(1) + "ing"; out += w + "d"
                out += w.dropLast(1) + "able"; out += w.dropLast(1) + "ion"
                out += w.dropLast(1) + "ive"; out += w.dropLast(1) + "er"
            }
            w.endsWith("y") -> {
                out += w.dropLast(1) + "ies"; out += w.dropLast(1) + "ied"
                out += w.dropLast(1) + "ying"; out += w + "ly"; out += w.dropLast(1) + "iness"
            }
            else -> {
                out += w + "s"; out += w + "ing"; out += w + "ed"; out += w + "er"
                out += w + "est"; out += w + "ly"; out += w + "ness"; out += w + "able"
            }
        }
        out.remove(word.lowercase())
        return out.toList().take(8)
    }

    object WordKindInt {
        const val WORD = 0
        const val IDIOM = 1
        const val PHRASAL = 2
        const val COLLOCATION = 3
    }
}

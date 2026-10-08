package com.proudvocab.android.core.dict

import android.database.sqlite.SQLiteDatabase
import com.proudvocab.android.core.util.TextUtils
import kotlin.math.min

/**
 * Read-only access to a *FastDic* dictionary database
 * (`fastdic_plain.sqlite`) — the very same schema the desktop dictionary
 * project reads, with the very same queries (GLOB prefix search on
 * `english_word`, `word_in_number` for Persian, Levenshtein suggestions,…).
 */
class FastdicDatabase private constructor(private val db: SQLiteDatabase) : AutoCloseable {

    private enum class Lang { EN, FA }

    fun isValid(): Boolean = runCatching {
        query("SELECT 1 FROM english_words LIMIT 1").use { it.moveToFirst() }
    }.getOrDefault(false)

    fun countEnglish(): Long = scalarLong("SELECT COUNT(*) FROM english_words")
    fun countPersian(): Long = scalarLong("SELECT COUNT(*) FROM persian_words")

    // ------------------------------------------------------------- search

    /**
     * `FDDatabaseManager.search`: prefix search, 50 results max, exactly the
     * ordering the original app uses.
     */
    fun search(text: String, limit: Int = 50): List<DictSearchResult> {
        val w = TextUtils.trimLower(text) ?: return emptyList()
        if (w.isEmpty() || w.length >= 100) return emptyList()

        val isEnglish = TextUtils.isEnglish(w)
        val rows: List<Triple<String, Long, Long?>> = if (isEnglish) {
            val sql =
                "SELECT w.english_word AS word, w.english_word_id AS word_id, " +
                    "w.english_word_id_parent AS word_id_parent " +
                    "FROM english_words w WHERE w.english_word GLOB ? " +
                    "ORDER BY w.english_word LIMIT ?"
            query(sql, TextUtils.globEscape(w) + "*", limit.toString()).use { c ->
                val out = ArrayList<Triple<String, Long, Long?>>()
                while (c.moveToNext()) {
                    val parent = if (c.isNull(2)) null else c.getLong(2)
                    out += Triple(c.getString(0), c.getLong(1), parent)
                }
                out
            }
        } else {
            val sql =
                "SELECT w.persian_word AS word, w.persian_word_id AS word_id, " +
                    "w.persian_word_id_parent AS word_id_parent " +
                    "FROM persian_words w WHERE w.word_in_number GLOB ? " +
                    "ORDER BY length(w.word_in_number) LIMIT ?"
            query(sql, TextUtils.peToNumber(w) + "*", limit.toString()).use { c ->
                val out = ArrayList<Triple<String, Long, Long?>>()
                while (c.moveToNext()) {
                    val parent = if (c.isNull(2)) null else c.getLong(2)
                    out += Triple(c.getString(0), c.getLong(1), parent)
                }
                out
            }
        }

        return rows.map { (word, id, parent) ->
            val targetId = parent ?: id
            val meaning = if (parent == null) {
                val meanings = meanings(if (isEnglish) Lang.EN else Lang.FA, targetId)
                meanings.joinToString(if (isEnglish) "، " else ", ") { it.persianMeaning }
            } else {
                ""
            }
            DictSearchResult(
                word = word,
                meaning = meaning,
                wordId = targetId,
                isPersian = !isEnglish,
                isParentRedirect = parent != null
            )
        }
    }

    /** One-line gloss used by chips and by the offline word-by-word translator. */
    fun gloss(word: String): String {
        val w = TextUtils.trimLower(word) ?: return ""
        if (w.isEmpty()) return ""
        return if (TextUtils.isEnglish(w)) {
            scalarString(
                "SELECT d.persian_meaning FROM english_details d " +
                    "JOIN english_words w ON w.english_word_id = d.english_word_id " +
                    "WHERE w.english_word = ? ORDER BY d.position, d.english_detail_id LIMIT 1",
                w
            )
        } else {
            scalarString(
                "SELECT d.english_meaning FROM persian_details d " +
                    "JOIN persian_words w ON w.persian_word_id = d.persian_word_id " +
                    "WHERE w.persian_word = ? ORDER BY d.position, d.persian_detail_id LIMIT 1",
                TextUtils.normalizeForMatch(w)
            )
        }
    }

    private fun meanings(lang: Lang, wordId: Long): List<Meaning> {
        val sql = if (lang == Lang.EN) {
            "SELECT persian_meaning FROM english_details WHERE english_word_id = ? " +
                "ORDER BY position, english_detail_id"
        } else {
            "SELECT english_meaning FROM persian_details WHERE persian_word_id = ? " +
                "ORDER BY position, persian_detail_id"
        }
        return query(sql, wordId.toString()).use { c ->
            val out = ArrayList<Meaning>()
            while (c.moveToNext()) {
                val text = c.getString(0) ?: continue
                out += Meaning(persianMeaning = text)
            }
            out
        }
    }

    /** `FDSuggestions.getLevenshtein` – distance ≤ 2 over a prefix/middle/suffix filter. */
    fun suggestions(text: String, max: Int = 8): List<String> {
        val t = TextUtils.trimLower(text)?.trim() ?: return emptyList()
        if (t.isEmpty() || t.length >= 100) return emptyList()
        val n = t.length
        val pre = t.substring(0, min(2, n))
        val suf = t.substring((n - 2).coerceAtLeast(0))
        val middle = if (n % 2 == 0) t.substring(0, n / 2) else t.substring(0, (n + 1) / 2)
        val mid = middle.substring((middle.length - 2).coerceAtLeast(0))

        val isEnglish = TextUtils.isEnglish(t)
        val sql = if (isEnglish) {
            "SELECT english_word AS word FROM english_words WHERE LENGTH(english_word) < ? " +
                "AND (english_word GLOB ? OR english_word GLOB ? OR english_word GLOB ?)"
        } else {
            "SELECT persian_word AS word FROM persian_words WHERE LENGTH(persian_word) < ? " +
                "AND (persian_word GLOB ? OR persian_word GLOB ? OR persian_word GLOB ?)"
        }
        val candidates = query(
            sql,
            (n * 2).toString(),
            TextUtils.globEscape(pre) + "*",
            "*" + TextUtils.globEscape(mid) + "*",
            "*" + TextUtils.globEscape(suf)
        ).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }

        return candidates
            .map { TextUtils.levenshtein(t, it) to it }
            .filter { it.first <= 2 }
            .sortedWith(compareBy({ it.first }, { it.second.length }))
            .map { it.second }
            .distinct()
            .take(max)
    }

    // -------------------------------------------------------------- entry

    /** Resolves a word to its database id, trying the base forms as well. */
    fun resolveId(word: String): Pair<Long, Boolean>? {
        val candidates = TextUtils.lemmaCandidates(word)
        for (candidate in candidates) {
            val cid = TextUtils.trimLower(candidate) ?: continue
            if (cid.isEmpty()) continue
            if (TextUtils.isEnglish(cid)) {
                val id = scalarLongOrNull(
                    "SELECT english_word_id FROM english_words WHERE english_word = ? LIMIT 1",
                    cid
                )
                if (id != null) return id to false
            } else {
                val id = scalarLongOrNull(
                    "SELECT persian_word_id FROM persian_words WHERE persian_word = ? LIMIT 1",
                    TextUtils.normalizeForMatch(cid)
                )
                if (id != null) return id to true
            }
        }
        return null
    }

    fun entry(word: String): WordEntry? {
        val resolved = resolveId(word) ?: return null
        return if (resolved.second) persianEntry(word, resolved.first) else englishEntry(word, resolved.first)
    }

    private fun englishEntry(displayWord: String, wordId: Long): WordEntry {
        val meanings = ArrayList<Meaning>()
        var phoneticUs: String? = null
        var phoneticUk: String? = null
        var description: String? = null
        val forms = HashMap<String, String?>()

        val detailSql =
            "SELECT d.english_detail_id, COALESCE(d.persian_meaning, ''), " +
                "d.countable_uncountable, d.formal_informal, d.british_american, d.cefr, " +
                "d.most_common, d.description_html " +
                "FROM english_details d WHERE d.english_word_id = ? " +
                "ORDER BY d.position, d.english_detail_id"
        val detailIds = ArrayList<Long>()
        query(detailSql, wordId.toString()).use { c ->
            while (c.moveToNext()) {
                val detailId = c.getLong(0)
                detailIds += detailId
                meanings += Meaning(
                    persianMeaning = c.getString(1) ?: "",
                    countable = if (c.isNull(2)) null else c.getInt(2) == 1,
                    formal = if (c.isNull(3)) null else c.getInt(3) == 1,
                    british = if (c.isNull(4)) null else c.getInt(4) == 1,
                    cefr = if (c.isNull(5)) null else c.getString(5),
                    mostCommon = c.getInt(6) == 1,
                    detailId = detailId
                )
                if (description == null && !c.isNull(7)) {
                    val d = c.getString(7)
                    if (!d.isNullOrBlank()) description = d
                }
            }
        }

        // Part of speech lives in its own table.
        val posMap = HashMap<Long, Int>()
        if (detailIds.isNotEmpty()) {
            val placeholders = detailIds.joinToString(",") { "?" }
            query(
                "SELECT english_detail_id, pos FROM english_pos WHERE english_detail_id IN ($placeholders)",
                *detailIds.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) posMap[c.getLong(0)] = c.getInt(1)
            }
            val catMap = HashMap<Long, ArrayList<Int>>()
            query(
                "SELECT english_detail_id, category FROM english_categories WHERE english_detail_id IN ($placeholders)",
                *detailIds.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    val list = catMap.getOrPut(c.getLong(0)) { ArrayList() }
                    list += c.getInt(1)
                }
            }
            val enriched = meanings.map { m ->
                m.copy(
                    partOfSpeech = posMap[m.detailId] ?: 0,
                    categories = catMap[m.detailId] ?: emptyList()
                )
            }
            meanings.clear()
            meanings.addAll(enriched)
        }

        // Verb / noun forms are stored as ids pointing at other rows.
        query(
            "SELECT simple_past, past_participle, infinitive, third_person_singular, " +
                "present_participle, plural, comparative, superlative, word_description_html " +
                "FROM english_words WHERE english_word_id = ?",
            wordId.toString()
        ).use { c ->
            if (c.moveToNext()) {
                forms["past"] = resolveWordName(c.getLong(0))
                forms["participle"] = resolveWordName(c.getLong(1))
                forms["infinitive"] = resolveWordName(c.getLong(2))
                forms["third"] = resolveWordName(c.getLong(3))
                forms["ing"] = resolveWordName(c.getLong(4))
                forms["plural"] = resolveWordName(c.getLong(5))
                forms["comparative"] = resolveWordName(c.getLong(6))
                forms["superlative"] = resolveWordName(c.getLong(7))
                if (description == null && !c.isNull(8)) description = c.getString(8)
            }
        }

        phoneticUs = scalarStringOrNull(
            "SELECT phonetic FROM english_american_audios WHERE english_word_id = ? " +
                "AND phonetic IS NOT NULL LIMIT 1",
            wordId.toString()
        )
        phoneticUk = scalarStringOrNull(
            "SELECT phonetic FROM english_british_audios WHERE english_word_id = ? " +
                "AND phonetic IS NOT NULL LIMIT 1",
            wordId.toString()
        )

        val sentences = ArrayList<ExampleSentence>()
        if (detailIds.isNotEmpty()) {
            val placeholders = detailIds.joinToString(",") { "?" }
            query(
                "SELECT english_sentence, persian_sentence, position FROM english_sentences " +
                    "WHERE english_detail_id IN ($placeholders) ORDER BY position, english_sentence_id LIMIT 12",
                *detailIds.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    sentences += ExampleSentence(
                        source = c.getString(0) ?: "",
                        target = c.getString(1) ?: "",
                        position = c.getInt(2)
                    )
                }
            }
        }

        val synonyms = ArrayList<SynonymGroup>()
        query(
            "SELECT definitions, synonyms, antonyms, pos, position FROM english_synonyms_antonyms " +
                "WHERE english_word_id = ? ORDER BY position, english_synonyms_antonyms_id",
            wordId.toString()
        ).use { c ->
            while (c.moveToNext()) {
                synonyms += SynonymGroup(
                    definition = if (c.isNull(0)) null else c.getString(0),
                    synonyms = (c.getString(1) ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    antonyms = (c.getString(2) ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    partOfSpeech = c.getString(3) ?: "",
                    position = c.getInt(4)
                )
            }
        }

        val family = ArrayList<String>()
        val familyId = scalarLongOrNull(
            "SELECT english_word_family_id FROM english_word_families_words WHERE word = ? LIMIT 1",
            displayWord.lowercase()
        )
        if (familyId != null) {
            query(
                "SELECT word, pos FROM english_word_families_words WHERE english_word_family_id = ? " +
                    "ORDER BY pos, english_word_family_words_id",
                familyId.toString()
            ).use { c ->
                while (c.moveToNext()) family += c.getString(0)
            }
        }

        val idioms = ArrayList<Pair<String, String>>()
        query(
            "SELECT w.english_word, d.persian_meaning FROM english_idioms i " +
                "LEFT JOIN english_details d ON i.english_idiom_word_id = d.english_word_id " +
                "LEFT JOIN english_words w ON i.english_idiom_word_id = w.english_word_id " +
                "WHERE i.english_word_id = ? " +
                "GROUP BY w.english_word, d.persian_meaning " +
                "ORDER BY i.position, i.english_idiom_id LIMIT 20",
            wordId.toString()
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0) ?: continue
                val m = c.getString(1) ?: ""
                idioms += w to m
            }
        }

        val cefr = meanings.firstOrNull { !it.cefr.isNullOrBlank() }?.cefr
        return WordEntry(
            word = displayWord,
            isPersian = false,
            wordId = wordId,
            meanings = meanings,
            sentences = sentences,
            synonyms = synonyms,
            family = family.distinct(),
            idioms = idioms,
            forms = WordForms(
                plural = forms["plural"],
                simplePast = forms["past"],
                pastParticiple = forms["participle"],
                presentParticiple = forms["ing"],
                thirdPerson = forms["third"],
                comparative = forms["comparative"],
                superlative = forms["superlative"],
                infinitive = forms["infinitive"]
            ),
            phoneticUs = phoneticUs,
            phoneticUk = phoneticUk,
            descriptionHtml = description,
            cefr = cefr
        )
    }

    private fun persianEntry(displayWord: String, wordId: Long): WordEntry {
        val meanings = ArrayList<Meaning>()
        val detailIds = ArrayList<Long>()
        var description: String? = null
        query(
            "SELECT persian_detail_id, english_meaning, COALESCE(persian_meaning, ''), " +
                "persian_phonetic FROM persian_details WHERE persian_word_id = ? " +
                "ORDER BY position, persian_detail_id",
            wordId.toString()
        ).use { c ->
            while (c.moveToNext()) {
                val detailId = c.getLong(0)
                detailIds += detailId
                meanings += Meaning(
                    persianMeaning = c.getString(2).ifBlank { c.getString(1) },
                    detailId = detailId
                )
            }
        }
        if (detailIds.isNotEmpty()) {
            val placeholders = detailIds.joinToString(",") { "?" }
            val posMap = HashMap<Long, Int>()
            query(
                "SELECT persian_detail_id, pos FROM persian_pos WHERE persian_detail_id IN ($placeholders)",
                *detailIds.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) posMap[c.getLong(0)] = c.getInt(1)
            }
            val enriched = meanings.map { m -> m.copy(partOfSpeech = posMap[m.detailId] ?: 0) }
            meanings.clear()
            meanings.addAll(enriched)
        }

        val sentences = ArrayList<ExampleSentence>()
        if (detailIds.isNotEmpty()) {
            val placeholders = detailIds.joinToString(",") { "?" }
            query(
                "SELECT persian_sentence, english_sentence, position FROM persian_sentences " +
                    "WHERE persian_detail_id IN ($placeholders) ORDER BY position LIMIT 12",
                *detailIds.map { it.toString() }.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    sentences += ExampleSentence(
                        source = c.getString(0) ?: "",
                        target = c.getString(1) ?: "",
                        position = c.getInt(2)
                    )
                }
            }
        }

        val synonyms = ArrayList<SynonymGroup>()
        query(
            "SELECT synonym_antonym, position FROM persian_synonyms_antonyms " +
                "WHERE persian_word_id = ? ORDER BY position",
            wordId.toString()
        ).use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(0) ?: continue
                synonyms += SynonymGroup(
                    synonyms = raw.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() },
                    position = c.getInt(1)
                )
            }
        }

        return WordEntry(
            word = displayWord,
            isPersian = true,
            wordId = wordId,
            meanings = meanings,
            sentences = sentences,
            synonyms = synonyms,
            descriptionHtml = description
        )
    }

    private fun resolveWordName(id: Long): String? =
        if (id <= 0L) null else scalarStringOrNull(
            "SELECT english_word FROM english_words WHERE english_word_id = ? LIMIT 1",
            id.toString()
        )

    // ------------------------------------------------------------- helpers

    private fun query(sql: String, vararg args: String): android.database.Cursor =
        db.rawQuery(sql, args)

    private fun scalarLong(sql: String): Long = runCatching {
        query(sql).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }.getOrDefault(0L)

    private fun scalarLongOrNull(sql: String, vararg args: String): Long? = runCatching {
        query(sql, *args).use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    private fun scalarString(sql: String, arg: String): String = runCatching {
        query(sql, arg).use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }
    }.getOrDefault("")

    private fun scalarStringOrNull(sql: String, vararg args: String): String? = runCatching {
        query(sql, *args).use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    override fun close() {
        runCatching { db.close() }
    }

    companion object {
        /** Opens [path] read-only. Returns null when it is not a usable database. */
        fun open(path: String): FastdicDatabase? = runCatching {
            val db = SQLiteDatabase.openDatabase(
                path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            FastdicDatabase(db).takeIf { it.isValid() }
        }.getOrNull()
    }
}

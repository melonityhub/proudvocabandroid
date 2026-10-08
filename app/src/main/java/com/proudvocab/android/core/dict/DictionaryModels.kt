package com.proudvocab.android.core.dict

/** One line in the search result list. */
data class DictSearchResult(
    val word: String,
    val meaning: String,
    val wordId: Long,
    val isPersian: Boolean,
    val isParentRedirect: Boolean = false
)

data class Meaning(
    val persianMeaning: String,
    val partOfSpeech: Int = 0,
    val cefr: String? = null,
    val detailId: Long = 0,
    val formal: Boolean? = null,
    val british: Boolean? = null,
    val countable: Boolean? = null,
    val mostCommon: Boolean = false,
    val categories: List<Int> = emptyList()
) {
    val posName: String get() = FdTables.posName(partOfSpeech, persian = true)
    val posNameEn: String get() = FdTables.posName(partOfSpeech, persian = false)
}

data class ExampleSentence(
    val source: String,
    val target: String,
    val position: Int = 0
)

data class SynonymGroup(
    val definition: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val partOfSpeech: String = "",
    val position: Int = 0
)

data class WordForms(
    val plural: String? = null,
    val simplePast: String? = null,
    val pastParticiple: String? = null,
    val presentParticiple: String? = null,
    val thirdPerson: String? = null,
    val comparative: String? = null,
    val superlative: String? = null,
    val infinitive: String? = null
) {
    fun asPairs(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        plural?.let { out += "plural" to it }
        thirdPerson?.let { out += "3rd person" to it }
        simplePast?.let { out += "past" to it }
        pastParticiple?.let { out += "participle" to it }
        presentParticiple?.let { out += "–ing" to it }
        comparative?.let { out += "comparative" to it }
        superlative?.let { out += "superlative" to it }
        infinitive?.let { out += "infinitive" to it }
        return out
    }
}

/** A fully expanded dictionary entry. */
data class WordEntry(
    val word: String,
    val isPersian: Boolean,
    val wordId: Long,
    val meanings: List<Meaning> = emptyList(),
    val sentences: List<ExampleSentence> = emptyList(),
    val synonyms: List<SynonymGroup> = emptyList(),
    val family: List<String> = emptyList(),
    val idioms: List<Pair<String, String>> = emptyList(),
    val forms: WordForms = WordForms(),
    val phoneticUs: String? = null,
    val phoneticUk: String? = null,
    val descriptionHtml: String? = null,
    val cefr: String? = null
) {
    /** Short one-line gloss, used on chips, cards and the archive list. */
    val shortGloss: String
        get() = meanings.firstOrNull { it.mostCommon }?.persianMeaning
            ?: meanings.firstOrNull()?.persianMeaning
            ?: ""
}

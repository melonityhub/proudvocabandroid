package com.proudvocab.android.core.translate

import com.proudvocab.android.core.dict.DictionaryManager
import com.proudvocab.android.core.settings.TranslationEngine
import com.proudvocab.android.core.util.TextUtils

/**
 * Last-resort translator: it looks every word up in the offline dictionary and
 * glues the glosses back together.
 *
 * The result reads like a gloss rather than a sentence, but it needs no model,
 * no download and no internet — which is exactly the point.
 */
class DictionaryTranslator(private val dictionary: DictionaryManager) {

    suspend fun translate(
        text: String,
        targetIsPersian: Boolean
    ): Result<TranslationResult> {
        return runCatching {
            val tokens = TextUtils.tokenize(text)
            val out = StringBuilder()
            var translatedAny = false
            for (token in tokens) {
                if (!token.isWord) {
                    out.append(token.text)
                    continue
                }
                val languageMatchesTarget = if (targetIsPersian) {
                    TextUtils.isEnglish(token.text)
                } else {
                    !TextUtils.isEnglish(token.text)
                }
                if (!languageMatchesTarget) {
                    out.append(token.text)
                    continue
                }
                val gloss = dictionary.gloss(token.text)
                if (gloss.isBlank()) {
                    out.append(token.text)
                } else {
                    // FastDic stores several meanings separated by "،".
                    val first = gloss.split("،", ",", ";").firstOrNull()?.trim().orEmpty()
                    out.append(if (first.isBlank()) token.text else first)
                    translatedAny = true
                }
            }
            val result = out.toString().replace(Regex("\\s+"), " ").trim()
            if (!translatedAny) error("no word found in the dictionary")
            TranslationResult(
                text = result,
                engine = TranslationEngine.DICTIONARY,
                offline = true
            )
        }
    }
}

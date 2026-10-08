package com.proudvocab.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the resource contract that the compiler cannot check.
 *
 * `stringResource(R.string.x, arg)` is resolved at *runtime*: a `%1$d`
 * placeholder fed a pre-formatted (Persian-digit) string throws
 * `IllegalFormatConversionException` and takes the whole screen down — which is
 * exactly how the saved-words screen used to die. A placeholder that differs
 * between `values/` and `values-fa/` only crashes when the UI language is
 * Persian, so neither of those is caught by a build.
 */
class ResourcesContractTest {

    private val PLACEHOLDER = Regex(
        """%(?:(\d+)\$)?[-#0,+ ]*\d*(?:\.\d+)?([a-zA-Z%])"""
    )

    private val STRING_TAG = Regex(
        """<string\s+name="([^"]+)"[^>]*>(.*?)</string>""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private fun resourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        val found = candidates.firstOrNull { it.isFile }
        assertNotNull("cannot find $relativePath", found)
        return found!!
    }

    private fun strings(relativePath: String): Map<String, String> {
        val text = resourceFile(relativePath).readText()
        val out = LinkedHashMap<String, String>()
        for (match in STRING_TAG.findAll(text)) {
            out[match.groupValues[1]] = match.groupValues[2]
        }
        return out
    }

    /** Positional conversion characters, e.g. `%1$d` -> `1:d`. */
    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value)
            .filter { it.groupValues[2] != "%" }
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .toList()

    private fun kotlinSources(): List<File> {
        val roots = listOf(File("src/main/java"), File("app/src/main/java"))
            .filter { it.isDirectory }
        assertTrue("no Kotlin sources found", roots.isNotEmpty())
        return roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
    }

    @Test
    fun `every string referenced from code exists in the default resources`() {
        val resources = strings("src/main/res/values/strings.xml")
        val referenced = LinkedHashSet<String>()
        val pattern = Regex("""(?:stringResource|getString)\(\s*R\.string\.(\w+)""")
        for (file in kotlinSources()) {
            for (match in pattern.findAll(file.readText())) {
                referenced += match.groupValues[1]
            }
        }
        assertTrue("expected to find string references in the sources", referenced.isNotEmpty())
        val missing = referenced.filter { it !in resources }
        assertEquals("code references string ids that are not defined: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `the persian translation keeps the same placeholders as english`() {
        val english = strings("src/main/res/values/strings.xml")
        val persian = strings("src/main/res/values-fa/strings.xml")
        val mismatches = ArrayList<String>()
        for ((key, value) in english) {
            val translated = persian[key] ?: continue
            val expected = placeholders(value)
            val actual = placeholders(translated)
            if (expected != actual) {
                mismatches += "$key: en=$expected fa=$actual"
            }
        }
        assertTrue("placeholder mismatch between locales:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `counts that are formatted by TextUtils are string placeholders`() {
        // `TextUtils.formatNumber` returns a String (Persian digits when the
        // learner has them on), so any resource it feeds must use %s.
        val english = strings("src/main/res/values/strings.xml")
        val offenders = ArrayList<String>()
        val callPattern = Regex(
            """R\.string\.(\w+)\s*,\s*TextUtils\.formatNumber\(""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        var checked = 0
        for (file in kotlinSources()) {
            for (match in callPattern.findAll(file.readText())) {
                val key = match.groupValues[1]
                checked++
                val types = placeholders(english[key] ?: "").map { it.substringAfter(':') }
                if (types.any { it != "s" }) {
                    offenders += "$key uses $types with a formatted number"
                }
            }
        }
        assertTrue("expected the archive/dictionary counters to be checked", checked > 0)
        assertEquals(offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `no resource still uses an integer placeholder for the word count`() {
        // Regression guard for the crash that killed the saved-words screen.
        val english = strings("src/main/res/values/strings.xml")
        assertEquals(listOf("1:s"), placeholders(english.getValue("archive_count")))
    }
}

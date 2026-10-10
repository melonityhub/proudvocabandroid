package com.proudvocab.android

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proudvocab.android.core.settings.SettingsRepository
import com.proudvocab.android.core.util.LocaleStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Drives the real UI on a device: every bottom tab, every settings page and a
 * dictionary search. A crash anywhere kills the instrumentation process and
 * fails the run, so this is the test that would have caught "the app opens
 * and then dies" before a release.
 *
 * The UI language is taken from the instrumentation argument `pvLocale`
 * (`""` = device default, `"fa"` = Persian). CI runs the suite in both, so the
 * Persian layout (RTL, Persian digits, longer strings) is exercised too.
 */
@RunWith(AndroidJUnit4::class)
class AllScreensUiTest {

    /** Skip onboarding and pick the language before the activity is created. */
    private val seedPreferences = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val language = InstrumentationRegistry.getArguments().getString("pvLocale").orEmpty()
            runBlocking {
                SettingsRepository(context).update { it.copy(onboardingCompleted = true) }
            }
            LocaleStore(context).language = language
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // RuleChain runs the seeding before the activity is launched by composeRule.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedPreferences).around(composeRule)

    private fun text(resId: Int): String = composeRule.activity.getString(resId)

    private fun openTab(route: String) {
        val tag = "tab_$route"
        // The root draws nothing until DataStore has produced the settings
        // (MainActivity/ProudVocabRoot), and waitForIdle() does not wait for
        // that, so the first tab can appear a moment after launch.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun goBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyBottomTabOpensAndTheAppStaysAlive() {
        composeRule.waitForIdle()
        listOf("dictionary", "review", "words", "settings", "watch").forEach { route ->
            openTab(route)
        }
        composeRule.onNodeWithTag("tab_watch").assertExists()
    }

    private fun backArrowShown(label: String): Boolean =
        composeRule.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun everySettingsPageOpensAndReturnsToTheList() {
        openTab("settings")
        val sections = listOf(
            R.string.settings_section_appearance,
            R.string.settings_section_typography,
            R.string.settings_section_subtitles,
            R.string.settings_section_languages,
            R.string.settings_section_translation,
            R.string.settings_section_learning,
            R.string.settings_section_srs,
            R.string.games_title,
            R.string.settings_section_data,
            R.string.settings_section_permissions,
            R.string.settings_section_about
        )
        val backLabel = text(R.string.back)
        for (section in sections) {
            val title = text(section)
            // Later rows can sit below the fold on a small screen; scroll the
            // LazyColumn to the row first, otherwise the tap lands off-target.
            composeRule.onAllNodes(hasText(title) and hasClickAction())
                .onFirst()
                .performScrollTo()
                .performClick()
            // The page change is an AnimatedContent transition; waitForIdle()
            // does not wait for it, so poll for the arrow instead.
            val opened = runCatching {
                composeRule.waitUntil(timeoutMillis = 5_000) { backArrowShown(backLabel) }
            }.isSuccess
            check(opened) {
                "tapping '$title' did not open its page. Tree: " +
                    composeRule.onRoot().printToString().take(6000)
            }
            goBack()
            val closed = runCatching {
                composeRule.waitUntil(timeoutMillis = 5_000) { !backArrowShown(backLabel) }
            }.isSuccess
            check(closed) { "back did not return from '$title' to the settings list" }
            assertTrue(
                "settings list did not come back after '$title'",
                composeRule.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()
            )
        }
    }

    @Test
    fun dictionarySearchRunsWithoutCrashing() {
        openTab("dictionary")
        composeRule.onAllNodes(hasSetTextAction()).onFirst()
            .performClick()
            .performTextInput("hel")
        composeRule.waitForIdle()
        // Lookups run on Dispatchers.IO; give the starter database a moment.
        Thread.sleep(1500)
        composeRule.waitForIdle()
        openTab("watch")
    }
}

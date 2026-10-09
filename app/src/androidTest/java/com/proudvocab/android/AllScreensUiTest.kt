package com.proudvocab.android

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithTag("tab_$route").performClick()
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
            composeRule.onAllNodes(hasText(title) and hasClickAction())
                .onFirst()
                .performClick()
            composeRule.waitForIdle()
            // A sub-page shows the back arrow; the root list does not.
            composeRule.onNode(hasContentDescription(backLabel)).assertExists()
            goBack()
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

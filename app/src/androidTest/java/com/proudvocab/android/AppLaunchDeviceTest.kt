package com.proudvocab.android

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proudvocab.android.ui.screens.player.PlayerViewModel
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke tests for the launch path.
 *
 * 1.0.2 crashed at launch: opening the player screen constructed
 * PlayerViewModel, whose init block touches SubtitleParser; an ICU-invalid
 * regex there threw ExceptionInInitializerError on the main thread and the
 * process died. These tests reproduce that exact path on a device so it can
 * never ship again.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchDeviceTest {

    @Test
    fun mainActivityLaunchesAndStaysAlive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    @Test
    fun playerViewModelInitializesOnDevice() {
        // Exactly what the player screen does on first composition. Run on
        // the main thread so a failure inside the ViewModel's init block
        // (which uses Dispatchers.Main.immediate) propagates to this test
        // instead of escaping into the void.
        val app = ApplicationProvider.getApplicationContext<Application>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            PlayerViewModel(app)
        }
    }
}

package com.proudvocab.android

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proudvocab.android.ui.screens.player.PlayerViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Functional check on a device: a subtitle file goes through the same
 * ViewModel path the player uses (URI -> read -> parse -> cues). The UI
 * smoke test cannot do this because shell-pushed file:// URIs are denied by
 * MediaProvider on Android 12.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSubtitleDeviceTest {

    @Test
    fun subtitleFileFromStorageLoadsCuesOnDevice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "pv-device-test.srt")
        file.writeText(
            "1\n00:00:01,000 --> 00:00:03,000\nHello world\n\n" +
                "2\n00:00:04,000 --> 00:00:06,000\nSecond line\n"
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var viewModel: PlayerViewModel? = null
        instrumentation.runOnMainSync {
            val vm = PlayerViewModel(app)
            viewModel = vm
            vm.loadSubtitle(app, Uri.fromFile(file))
        }
        val vm = requireNotNull(viewModel)

        val deadline = System.currentTimeMillis() + 15_000
        while (vm.uiState.value.cues.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        val cues = vm.uiState.value.cues
        assertEquals("both cues should be parsed", 2, cues.size)
        assertTrue(cues.first().text.contains("Hello world"))
    }
}

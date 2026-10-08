package com.proudvocab.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.util.LocaleHelper
import com.proudvocab.android.core.util.LocaleStore
import com.proudvocab.android.ui.ProudVocabRoot
import com.proudvocab.android.ui.ProvideDependencies
import com.proudvocab.android.ui.theme.ProudVocabTheme

val LocalHostActivity = staticCompositionLocalOf<ComponentActivity> {
    error("No activity provided")
}

class MainActivity : ComponentActivity() {

    private var startIntent by mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        // The chosen UI language has to be in place before the first layout.
        super.attachBaseContext(LocaleHelper.apply(newBase, LocaleStore(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startIntent = intent
        setContent { AppContent(startIntent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startIntent = intent
    }

    @Composable
    private fun AppContent(intent: Intent?) {
        val app = LocalContext.current.applicationContext as ProudVocabApplication
        val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
        ProudVocabTheme(settings) {
            ProvideDependencies {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalHostActivity provides this
                ) {
                    ProudVocabRoot(incomingIntent = intent)
                }
            }
        }
    }
}

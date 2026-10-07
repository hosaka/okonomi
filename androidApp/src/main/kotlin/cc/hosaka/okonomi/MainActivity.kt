package cc.hosaka.okonomi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cc.hosaka.okonomi.feature.navigation.appEntryLinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
        // Only on a fresh start: a recreated activity is handed the same
        // intent again, and its link has already been opened.
        if (savedInstanceState == null) {
            forwardEntryLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Kept as the activity's intent, so a later recreation sees this
        // one rather than whatever first launched it.
        setIntent(intent)
        forwardEntryLink(intent)
    }

    /**
     * Hands an `okonomi://entry/<id>` link to the shell; anything else is
     * ignored there. Not when the task was brought back from Recents:
     * Android then replays the intent that first started it, and a link
     * tapped long ago would open its entry again over whatever the reader
     * has moved on to.
     */
    private fun forwardEntryLink(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val uri = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        appEntryLinks.open(uri.toString())
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
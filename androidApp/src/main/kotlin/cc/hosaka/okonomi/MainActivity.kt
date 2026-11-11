package cc.hosaka.okonomi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cc.hosaka.okonomi.feature.navigation.handOffToShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
        // Only on a fresh start: a recreated activity is handed the same
        // intent again, and its link or lookup has already been opened.
        if (savedInstanceState == null) {
            handOffToShell(intent, startedForResult = callingActivity != null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Kept as the activity's intent, so a later recreation sees this
        // one rather than whatever first launched it.
        setIntent(intent)
        handOffToShell(intent, startedForResult = callingActivity != null)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
package cc.hosaka.okonomi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cc.hosaka.okonomi.feature.home.HomeScreen
import cc.hosaka.okonomi.ui.theme.OkonomiTheme

@Composable
@Preview
fun App() {
    OkonomiTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            HomeScreen()
        }
    }
}

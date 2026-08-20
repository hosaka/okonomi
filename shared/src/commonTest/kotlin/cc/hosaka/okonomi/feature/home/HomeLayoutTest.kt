package cc.hosaka.okonomi.feature.home

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutTest {
    @Test
    fun `wider than tall is horizontal`() {
        assertEquals(HomeLayout.Horizontal, homeLayoutFor(maxWidth = 640.dp, maxHeight = 480.dp))
    }

    @Test
    fun `taller than wide is vertical`() {
        assertEquals(HomeLayout.Vertical, homeLayoutFor(maxWidth = 480.dp, maxHeight = 640.dp))
    }

    @Test
    fun `square is vertical`() {
        assertEquals(HomeLayout.Vertical, homeLayoutFor(maxWidth = 500.dp, maxHeight = 500.dp))
    }
}

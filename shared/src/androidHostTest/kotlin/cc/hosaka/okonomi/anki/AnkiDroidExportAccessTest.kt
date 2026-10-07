package cc.hosaka.okonomi.anki

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AnkiDroidExport.access] against Robolectric's package manager: whether
 * AnkiDroid's provider resolves, and then whether its permission is held.
 * What the real system answers for an AnkiDroid whose API is switched off
 * (a disabled provider component, which does not resolve) is not modelled
 * beyond "does not resolve".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AnkiDroidExportAccessTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val export = AnkiDroidExport { application }

    private fun installAnkiDroid() {
        shadowOf(application.packageManager).addOrUpdateProvider(
            ProviderInfo().apply {
                authority = "com.ichi2.anki.flashcards"
                name = "com.ichi2.anki.provider.CardContentProvider"
                packageName = "com.ichi2.anki"
                applicationInfo = ApplicationInfo().apply { packageName = "com.ichi2.anki" }
            },
        )
    }

    @Test
    fun `no provider is unavailable even with the permission granted`() {
        shadowOf(application).grantPermissions(ANKIDROID_PERMISSION)

        assertEquals(AnkiAccess.Unavailable, export.access())
    }

    @Test
    fun `no provider is unavailable with the permission never defined`() {
        assertEquals(AnkiAccess.Unavailable, export.access())
    }

    @Test
    fun `a provider without the permission needs it asked for`() {
        installAnkiDroid()
        shadowOf(application).denyPermissions(ANKIDROID_PERMISSION)

        assertEquals(AnkiAccess.NeedsPermission, export.access())
    }

    @Test
    fun `a provider with the permission is ready`() {
        installAnkiDroid()
        shadowOf(application).grantPermissions(ANKIDROID_PERMISSION)

        assertEquals(AnkiAccess.Granted, export.access())
    }
}

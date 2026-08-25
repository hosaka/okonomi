package cc.hosaka.okonomi.ui.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Host actual: runs Compose UI tests on the JVM under Robolectric, no emulator.
 *
 * `@Config(sdk = [36])` is mandatory, not a preference. Robolectric 4.16.x ships
 * Android runtimes up to API 36 while `compileSdk` is 37, so without it every
 * test aborts with "Package targetSdkVersion=37 > maxSdkVersion=36". Raise it
 * only alongside a Robolectric that supports the newer API.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
actual abstract class ComposeUiTestBase actual constructor()

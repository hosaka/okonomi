package cc.hosaka.okonomi.ui.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Device actual. We run no device tests, but this file is still required.
 *
 * `withDeviceTestBuilder { sourceSetTreeName = "test" }` in the build script
 * makes `androidDeviceTest` depend on `commonTest`, so the device compilation
 * sees the `expect` declaration and `:shared:compileAndroidDeviceTest` fails
 * with "Expected ComposeUiTestBase has no actual declaration" without it.
 */
@RunWith(AndroidJUnit4::class)
actual abstract class ComposeUiTestBase actual constructor()

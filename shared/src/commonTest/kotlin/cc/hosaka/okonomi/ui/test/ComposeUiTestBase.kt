package cc.hosaka.okonomi.ui.test

/**
 * Base class for Compose UI tests written in `commonTest`.
 *
 * Common code cannot carry a JUnit `@RunWith`, and Compose UI tests need one to
 * get an Android runtime on the JVM. JUnit 4's `@RunWith` is `@Inherited`, so
 * the annotation lives on the Android `actual` and every common subclass picks
 * it up. Extend this from any test that calls `runComposeUiTest`.
 */
expect abstract class ComposeUiTestBase()

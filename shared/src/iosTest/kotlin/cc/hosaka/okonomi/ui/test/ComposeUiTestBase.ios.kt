package cc.hosaka.okonomi.ui.test

/**
 * iOS actual. Kotlin/Native has no JUnit runner to inherit and needs none:
 * `runComposeUiTest` brings its own environment there, so this exists only to
 * satisfy the `expect` that carries the Android runner.
 */
actual abstract class ComposeUiTestBase actual constructor()

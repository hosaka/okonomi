import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.sqldelight)
}

// Two databases, two source directories, and the split is load-bearing.
//
// The default layout puts every `.sq` file under src/commonMain/sqldelight
// and compiles them into whichever database is declared. :tools:dictgen
// points at the dictionary's directory to build the shipped file from the
// same schema the app reads, so a user table left in the default place
// would end up INSIDE okonomi.db and move DICTIONARY_SCHEMA_FINGERPRINT.
// Each database therefore names its own directory; see
// tools/dictgen/build.gradle.kts, which must keep pointing at the
// dictionary's alone.
sqldelight {
    databases {
        create("OkonomiDb") {
            packageName.set("cc.hosaka.okonomi.db")
            srcDirs.setFrom("src/commonMain/sqldelight/dictionary")
            dialect(libs.sqldelight.sqliteDialect)
            // Read-only bundled database: schema is regenerated wholesale, never migrated.
            verifyMigrations.set(false)
            // The androidx driver bridge requires the async generated schema.
            generateAsync.set(true)
        }
        // The user's own data, and the opposite migration policy to the
        // dictionary's. This file can never be regenerated from anything,
        // so it is migrated rather than replaced and the migrations are
        // verified: `verifySqlDelightMigration` replays the checked-in
        // `<version>.db` snapshots through the `.sqm` files and fails if
        // the result is not the schema the `.sq` files describe.
        create("UserDb") {
            packageName.set("cc.hosaka.okonomi.user.db")
            srcDirs.setFrom("src/commonMain/sqldelight/user")
            dialect(libs.sqldelight.sqliteDialect)
            verifyMigrations.set(true)
            generateAsync.set(true)
            // The build directory, NOT the checked-in snapshot directory.
            //
            // Verification replays the `<version>.db` snapshots in
            // src/commonMain/sqldelight/user/databases/, which it finds
            // because they sit under srcDirs. `generateCommonMainUserDbSchema`
            // deliberately cannot reach them: pointed at that directory it
            // overwrites the existing `1.db` in place, which turns the guard
            // into a formality — change the schema, watch verify fail, run
            // the generate task, watch it pass, and ship a DDL change with
            // no migration behind it and every install stranded on the old
            // one. That was not hypothetical; it was done.
            //
            // A snapshot is a record of a version that shipped. Publishing a
            // new one means writing the `.sqm` first (which moves the schema
            // version) and then copying the generated file in under its NEW
            // number. An existing one is never replaced.
            schemaOutputDirectory.set(layout.buildDirectory.dir("generated/userDbSchema"))
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "cc.hosaka.okonomi.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       buildToolsVersion = libs.versions.android.buildTools.get()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
           // Lets host tests run code that logs through android.util.Log.
           isReturnDefaultValues = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.kotlinx.serialization.core)
            // The export file format; see encodeFavourites/decodeFavourites
            // in cc.hosaka.okonomi.user (FavouritesTransfer.kt).
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // The system save/open dialogs behind the Favourites export and
            // import. The Compose artifact initialises itself on Android and
            // uses SAF, so there is no FileKit.init() call and no manifest
            // permission anywhere in this project.
            implementation(libs.filekit.dialogsCompose)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.sqldelight.runtime)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.sqldelight.androidx.driver)
            // The app's only preference storage; see cc.hosaka.okonomi.prefs.
            implementation(libs.androidx.datastore.preferencesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Compose UI tests live in commonTest and execute on the JVM through
            // androidHostTest. Use androidx.compose.ui.test.v2.runComposeUiTest:
            // the v1 entry points are deprecated in Compose Multiplatform 1.11.
            implementation(libs.compose.uiTest)
        }
        getByName("androidHostTest").dependencies {
            // Real org.json so the Android AboutLibraries parser works on the JVM.
            implementation(libs.org.json)
            // JDBC driver for the dictionary read-path test: sqlite-bundled's
            // Android AAR only carries device ABIs, so host tests exercise the
            // async-codegen queries over the synchronous JDBC driver instead.
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.androidx.sqlite.bundledJvm)
            // The JVM variant of the SAME sqlite-bundled the app ships, so
            // host tests can run `openUserDb` itself rather than a
            // hand-assembled stand-in for it. Without it the production
            // opener is executed by nothing: a reviewer changed
            // AndroidxSqliteDatabaseType.File(path) to Memory and the whole
            // suite stayed green. Dictionary tests still use JDBC above —
            // they only need a driver, not the app's own opener.
            // Robolectric supplies the Android runtime that Compose UI tests need
            // on the JVM. Without it runComposeUiTest fails with a bare
            // NullPointerException rather than anything self-explanatory.
            implementation(libs.robolectric)
            // ui-test-manifest merges the <activity> entry for ComponentActivity
            // that runComposeUiTest launches. Android's docs all say this belongs
            // on debugImplementation; that guidance assumes the application
            // plugin. The AGP KMP library plugin has no build types, so there is
            // no debugImplementation and plain implementation here is correct.
            // Removing it fails with "Unable to resolve activity for Intent".
            implementation(libs.androidx.compose.uiTestManifest)
            // AndroidJUnit4 dispatches to Robolectric on the host and to
            // instrumentation on a device, so ComposeUiTestBase needs one runner.
            implementation(libs.androidx.testExt.junit)
        }
        getByName("androidDeviceTest").dependencies {
            // We run no device tests, but androidDeviceTest depends on commonTest
            // (see sourceSetTreeName above), so it must still compile the
            // ComposeUiTestBase actual and therefore needs the runner on its path.
            implementation(libs.androidx.testExt.junit)
        }
        all {
            languageSettings {
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

aboutLibraries {
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
    }
}
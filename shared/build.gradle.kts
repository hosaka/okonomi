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

sqldelight {
    databases {
        create("OkonomiDb") {
            packageName.set("cc.hosaka.okonomi.db")
            dialect(libs.sqldelight.sqliteDialect)
            // Read-only bundled database: schema is regenerated wholesale, never migrated.
            verifyMigrations.set(false)
            // The androidx driver bridge requires the async generated schema.
            generateAsync.set(true)
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.sqldelight.runtime)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.sqldelight.androidx.driver)
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
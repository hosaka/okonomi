import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.sqldelight)
    application
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sqldelight {
    databases {
        create("OkonomiDb") {
            packageName.set("cc.hosaka.okonomi.db")
            // Same schema files the app compiles: single source of truth in :shared.
            srcDirs.setFrom("../../shared/src/commonMain/sqldelight")
            dialect(libs.sqldelight.sqliteDialect)
            verifyMigrations.set(false)
        }
    }
}

dependencies {
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqliteDriver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}

application {
    mainClass.set("cc.hosaka.okonomi.dictgen.MainKt")
}

tasks.named<JavaExec>("run") {
    // Default --data/--out arguments resolve relative to the repository root.
    workingDir = rootDir
}

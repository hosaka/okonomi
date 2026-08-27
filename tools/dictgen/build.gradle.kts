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
            //
            // The DICTIONARY's directory alone, never the whole sqldelight
            // tree: the user database's `.sq` files live beside it, and
            // compiling those in here would create list/list_entry inside
            // the shipped okonomi.db and move
            // DICTIONARY_SCHEMA_FINGERPRINT.
            srcDirs.setFrom("../../shared/src/commonMain/sqldelight/dictionary")
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
    // JVM variant of the bundled SQLite (3.50.x): proves the app schema,
    // FTS5 included, is accepted by the exact SQLite the app ships.
    testImplementation(libs.androidx.sqlite)
    testImplementation(libs.androidx.sqlite.bundled)
}

application {
    mainClass.set("cc.hosaka.okonomi.dictgen.MainKt")
}

tasks.named<JavaExec>("run") {
    // Default --data/--out arguments resolve relative to the repository root.
    workingDir = rootDir
}

val dictionaryOutputDir = layout.buildDirectory.dir("generated/dictionary")

val generateDictionary = tasks.register<JavaExec>("generateDictionary") {
    group = "build"
    description = "Generates the bundled dictionary database and its version sidecar from data/."
    mainClass.set(application.mainClass)
    classpath = sourceSets.main.get().runtimeClasspath
    // Locals only: lambdas below must not capture the build script object
    // (configuration cache cannot serialize script references).
    val sourceNames = listOf(
        "JMdict_e.xml",
        "JMnedict.xml",
        "kanjidic2.xml",
        "radkfile",
        // Tatoeba: the Japanese/English pairs and the word index that
        // links them to entries (the Phrases tab).
        "jpn_sentences.tsv",
        "eng_sentences.tsv",
        "jpn_indices.csv",
    )
    // KanjiVG ships one SVG per character, so this source is a directory
    // rather than a file and needs its own existence check below: the
    // `.isFile` test the files use rejects a directory outright.
    val sourceDirNames = listOf("kanjivg")
    val dataDir = rootDir.resolve("data")
    val outputDir = dictionaryOutputDir
    inputs.files(sourceNames.map { dataDir.resolve(it) })
        .withPropertyName("dictionarySources")
        .withPathSensitivity(PathSensitivity.NONE)
    // Registered as file TREES, not as inputs.dir(...). Gradle validates
    // an input directory before it runs any task action, and does so even
    // when the property is marked optional, so inputs.dir turns a fresh
    // clone into "property 'dictionarySourceDir-kanjivg' specifies
    // directory ... which doesn't exist" and the curated message below --
    // the one that names every source and points at README.md -- is never
    // reached. A tree over a missing directory is simply empty, so the
    // build survives configuration and fails in doFirst with something
    // worth reading. Contents are still tracked when the directory is
    // there, which is what keeps the task up-to-date correctly.
    inputs.files(sourceDirNames.map { fileTree(dataDir.resolve(it)) })
        .withPropertyName("dictionarySourceDirs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir).withPropertyName("dictionaryOutputDir")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--data", dataDir.absolutePath,
                "--out", outputDir.get().asFile.resolve("okonomi.db").absolutePath,
            )
        },
    )
    doFirst {
        val missingFiles = sourceNames.filterNot { dataDir.resolve(it).isFile }
        val missingDirs = sourceDirNames.filterNot { name ->
            val dir = dataDir.resolve(name)
            dir.isDirectory && dir.list()?.isNotEmpty() == true
        }
        val missing = missingFiles + missingDirs.map { "$it/" }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Dictionary sources are missing from ${dataDir}: ${missing.joinToString()}. " +
                    "The app bundles the generated dictionary, so building it requires the " +
                    "JMdict_e.xml, JMnedict.xml, kanjidic2.xml and radkfile sources from EDRDG, " +
                    "plus jpn_sentences.tsv, eng_sentences.tsv and jpn_indices.csv from Tatoeba, " +
                    "and the kanjivg/ directory of per-character SVGs from KanjiVG, " +
                    "in data/. See README.md for where each one is downloaded from.",
            )
        }
    }
}

// Exposes the generated database + sidecar directory to :androidApp (asset packaging).
val dictionaryElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "okonomi-dictionary"))
    }
}

artifacts {
    add(dictionaryElements.name, dictionaryOutputDir) {
        builtBy(generateDictionary)
    }
}

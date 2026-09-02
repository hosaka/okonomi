import org.gradle.api.file.RelativePath
import org.gradle.api.resources.ReadableResource
import org.gradle.api.resources.ResourceException
import org.gradle.api.tasks.options.Option
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

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

/**
 * Downloads the dictionary source archives into `data/archives/`.
 *
 * That directory is a cache, not a build output to be recreated: an archive
 * already sitting there, recorded as having come from the URL currently
 * configured, is never fetched again, so repeat builds -- and `--offline` --
 * make no network request at all. Each transfer is written to a `.part` file,
 * checked against the length the server declared, and moved into place only
 * once it is complete, so a truncated body never appears under the final name.
 */
abstract class FetchDictionarySources : DefaultTask() {
    /** Local archive file name -> the URL it is downloaded from. */
    @get:Input
    abstract val sources: MapProperty<String, String>

    @get:OutputDirectory
    abstract val archiveDir: DirectoryProperty

    /**
     * Whether the build was started with `--offline`. Internal on purpose:
     * toggling the flag must not invalidate an otherwise complete cache.
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    @get:Internal
    @get:Option(
        option = "refresh",
        description = "Re-downloads every source archive, replacing the cached copies.",
    )
    abstract val refresh: Property<Boolean>

    init {
        refresh.convention(false)
        // The up-to-date condition is stated outright rather than left to
        // Gradle's output snapshotting, which considers the task done as soon
        // as it has run once: a deleted archive would then never be re-fetched,
        // and neither would a repointed URL. Capture the properties, never
        // `this`, so the spec stays configuration-cache safe.
        val refreshRequested = refresh
        val expected = sources
        val dir = archiveDir
        outputs.upToDateWhen { !refreshRequested.get() && isCached(dir.get().asFile, expected.get()) }
    }

    @TaskAction
    fun fetch() {
        val dir = archiveDir.get().asFile
        val force = refresh.get()
        val table = sources.get().toSortedMap()
        dir.mkdirs()
        // A killed daemon leaves its half-written transfer behind; nothing
        // else ever reads a .part, so sweep them rather than accumulate them.
        dir.listFiles { _, name -> name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }

        val recorded = readSourceUrls(dir)
        val wanted = table.filter { (name, url) ->
            force || !File(dir, name).isFile || recorded[name] != url
        }
        if (wanted.isEmpty()) {
            didWork = false
            return
        }
        if (offline.get()) {
            throw GradleException(
                "Gradle is offline and these dictionary source archives are not cached in " +
                    "$dir: ${wanted.keys.joinToString()}. Run '$path' once with network " +
                    "access; the archives are kept and never downloaded again.",
            )
        }
        wanted.forEach { (name, url) -> download(name, url, dir) }
        // Records where each cached archive came from, so that repointing a
        // URL -- bumping the pinned KanjiVG release, whose version lives only
        // in the URL -- re-fetches instead of silently reusing the old bytes.
        writeSourceUrls(dir, table.filterKeys { File(dir, it).isFile })
    }

    private fun download(name: String, url: String, dir: File) {
        val target = File(dir, name)
        val part = File(dir, name + PART_SUFFIX)
        logger.lifecycle("Downloading $name from $url")
        try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            // EDRDG is a small community-run host: say who is calling.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            // Ask for the bytes as published: a transport-level re-encoding
            // would leave a body the archive tasks cannot read, and would make
            // the declared length disagree with what is written.
            connection.setRequestProperty("Accept-Encoding", "identity")
            val written: Long
            val declared: Long
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw IOException("HTTP $status ${connection.responseMessage.orEmpty()}".trim())
                }
                declared = connection.contentLengthLong
                written = connection.inputStream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            // Without this a truncated body -- or a 200 carrying an HTML error
            // page -- would be cached permanently under the archive's name.
            if (declared >= 0 && written != declared) {
                throw IOException(
                    "expected $declared bytes but received $written; the transfer was cut short",
                )
            }
            atomicReplace(part, target)
        } catch (e: IOException) {
            throw GradleException("Failed to download $name from $url: ${e.message}", e)
        } finally {
            // A no-op once the move above has succeeded.
            part.delete()
        }
    }

    private fun readSourceUrls(dir: File): Map<String, String> = parseSourceUrls(File(dir, SOURCE_URLS))

    private fun writeSourceUrls(dir: File, entries: Map<String, String>) {
        val manifest = File(dir, SOURCE_URLS)
        val tmp = File(dir, SOURCE_URLS + PART_SUFFIX)
        tmp.writeText(entries.entries.joinToString("\n", postfix = "\n") { "${it.key}\t${it.value}" })
        atomicReplace(tmp, manifest)
    }

    private fun atomicReplace(tmp: File, target: File) {
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val PART_SUFFIX = ".part"
        const val SOURCE_URLS = "source-urls.tsv"
        const val USER_AGENT = "okonomi-dictgen (dictionary build for the okonomi app)"

        fun parseSourceUrls(manifest: File): Map<String, String> {
            if (!manifest.isFile) return emptyMap()
            return manifest.readLines()
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()
        }

        /** Every configured archive present, and fetched from the URL configured now. */
        fun isCached(dir: File, expected: Map<String, String>): Boolean {
            val recorded = parseSourceUrls(File(dir, SOURCE_URLS))
            return expected.all { (name, url) -> File(dir, name).isFile && recorded[name] == url }
        }
    }
}

/**
 * Decompresses `data/archives/` into the plain files `:tools:dictgen` reads
 * from `data/`, using Gradle's own [ArchiveOperations] for all four formats.
 *
 * The extracted files and `data/kanji/` are declared as outputs one by one
 * and `data/` itself never is, so the download cache under `data/archives/`
 * is outside this task's scope and can never be cleaned up by it.
 */
abstract class ExtractDictionarySources : DefaultTask() {
    /** Archive name -> extracted file name, for a single gzipped file. */
    @get:Input
    abstract val gzipped: MapProperty<String, String>

    /** Archive name -> extracted file name, for a single bzip2'd file. */
    @get:Input
    abstract val bzipped: MapProperty<String, String>

    /** Archive name -> the one member to pull out of a bzip2'd tar. */
    @get:Input
    abstract val tarred: MapProperty<String, String>

    /** Archive name -> the zip's top-level directory, synced below `data/`. */
    @get:Input
    abstract val zipped: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    /** Where [archives] live. Tracked through [archives], not as a directory. */
    @get:Internal
    abstract val archiveDir: DirectoryProperty

    /** Tracked through the individual outputs below, never as a directory. */
    @get:Internal
    abstract val dataDir: DirectoryProperty

    @get:OutputFiles
    abstract val extractedFiles: ConfigurableFileCollection

    @get:OutputDirectories
    abstract val extractedDirs: ConfigurableFileCollection

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        val archiveRoot = archiveDir.get().asFile
        val data = dataDir.get().asFile
        data.mkdirs()

        // Copy.from(gzip(...)) does not work: a ReadableResource only feeds
        // tarTree, so the single-file archives are read directly.
        gzipped.get().toSortedMap().forEach { (archive, extracted) ->
            val file = File(archiveRoot, archive)
            extractSingle(file, File(data, extracted)) { archiveOperations.gzip(it) }
        }
        bzipped.get().toSortedMap().forEach { (archive, extracted) ->
            val file = File(archiveRoot, archive)
            extractSingle(file, File(data, extracted)) { archiveOperations.bzip2(it) }
        }
        tarred.get().toSortedMap().forEach { (archive, member) ->
            val file = File(archiveRoot, archive)
            val tree = archiveOperations.tarTree(archiveOperations.bzip2(file)).matching {
                include(member)
            }
            if (isEmptyTree(file, tree)) {
                throw GradleException(
                    "$file holds no entry named '$member'. The Tatoeba index archive is expected " +
                        "to contain exactly that one member; delete that file and build again to " +
                        "download it afresh.",
                )
            }
            copying(file) {
                fileSystemOperations.copy {
                    from(tree)
                    into(data)
                }
            }
        }
        zipped.get().toSortedMap().forEach { (archive, directory) ->
            val file = File(archiveRoot, archive)
            val tree = archiveOperations.zipTree(file).matching { include("$directory/**") }
            // The sync below empties its destination of anything the tree does
            // not carry, so an archive that stopped shipping this directory --
            // a renamed top level in a future release -- would silently wipe
            // data/$directory/ instead of failing.
            if (isEmptyTree(file, tree)) {
                throw GradleException(
                    "$file holds no '$directory/' directory, which is where the per-character " +
                        "sources are expected; delete that file and build again to download it " +
                        "afresh, and check the pinned release still ships '$directory/'.",
                )
            }
            copying(file) {
                fileSystemOperations.sync {
                    from(tree) {
                        // The zip nests everything under its own top-level
                        // directory; drop that segment so the entries land in
                        // data/kanji/ rather than data/kanji/kanji/. Variants
                        // come along as published -- KanjivgParser filters them.
                        eachFile {
                            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                        }
                    }
                    includeEmptyDirs = false
                    into(File(data, directory))
                }
            }
        }
    }

    /**
     * Decompresses via a `.part` beside [target] and moves it into place, so
     * an interrupted extract cannot leave a truncated file that still
     * satisfies the `.isFile` check `generateDictionary` makes.
     */
    private fun extractSingle(archive: File, target: File, open: (File) -> ReadableResource) {
        val part = File(target.parentFile, target.name + PART_SUFFIX)
        try {
            open(archive).read().use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                Files.move(
                    part.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw GradleException(unreadable(archive, e.message), e)
        } catch (e: ResourceException) {
            throw GradleException(unreadable(archive, e.message), e)
        } finally {
            // A no-op once the move above has succeeded.
            part.delete()
        }
    }

    /** Runs [block], relabelling a decompression failure with the fix. */
    private fun copying(archive: File, block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            throw GradleException(unreadable(archive, e.message), e)
        } catch (e: ResourceException) {
            throw GradleException(unreadable(archive, e.message), e)
        }
    }

    private fun isEmptyTree(archive: File, tree: FileTree): Boolean =
        try {
            tree.isEmpty
        } catch (e: IOException) {
            throw GradleException(unreadable(archive, e.message), e)
        } catch (e: ResourceException) {
            throw GradleException(unreadable(archive, e.message), e)
        }

    private fun unreadable(archive: File, cause: String?): String =
        "Could not decompress $archive: ${cause ?: "unreadable archive"}. Delete that file and " +
            "build again to download it afresh."

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}

// Local archive name -> URL. The archive names mirror the names they extract
// to (JMdict_e.xml.gz -> JMdict_e.xml) so data/archives/ reads as a
// compressed mirror of data/; the Tatoeba tar and the KanjiVG zip are the two
// that cannot.
val dictionaryArchiveUrls = mapOf(
    "JMdict_e.xml.gz" to "https://www.edrdg.org/pub/Nihongo/JMdict_e.gz",
    "JMnedict.xml.gz" to "https://www.edrdg.org/pub/Nihongo/JMnedict.xml.gz",
    "kanjidic2.xml.gz" to "https://www.edrdg.org/kanjidic/kanjidic2.xml.gz",
    "radkfile.gz" to "https://www.edrdg.org/pub/Nihongo/radkfile.gz",
    "jpn_sentences.tsv.bz2" to "https://downloads.tatoeba.org/exports/per_language/jpn/jpn_sentences.tsv.bz2",
    "eng_sentences.tsv.bz2" to "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2",
    "jpn_indices.tar.bz2" to "https://downloads.tatoeba.org/exports/jpn_indices.tar.bz2",
    // Pinned release: the stroke-order table is generated from this exact set.
    // The version lives only in the URL, so the fetch task records which URL
    // each cached archive came from and re-downloads when this line moves.
    "kanjivg.zip" to "https://github.com/KanjiVG/kanjivg/releases/download/r20260714/kanjivg-20260714-all.zip",
)

val gzippedArchives = mapOf(
    "JMdict_e.xml.gz" to "JMdict_e.xml",
    "JMnedict.xml.gz" to "JMnedict.xml",
    "kanjidic2.xml.gz" to "kanjidic2.xml",
    "radkfile.gz" to "radkfile",
)
val bzippedArchives = mapOf(
    "jpn_sentences.tsv.bz2" to "jpn_sentences.tsv",
    "eng_sentences.tsv.bz2" to "eng_sentences.tsv",
)
val tarredArchives = mapOf("jpn_indices.tar.bz2" to "jpn_indices.csv")
val zippedArchives = mapOf("kanjivg.zip" to "kanji")

// A download without an extraction rule (or the reverse) would otherwise only
// surface as a missing source at generation time, or as a silently unused
// download. Catch the mismatch while the build is being configured.
val dictionaryExtractionRules = gzippedArchives + bzippedArchives + tarredArchives + zippedArchives
require(dictionaryArchiveUrls.keys == dictionaryExtractionRules.keys) {
    "Every dictionary archive needs both a download URL and an extraction rule. " +
        "No URL for: ${(dictionaryExtractionRules.keys - dictionaryArchiveUrls.keys).joinToString()}; " +
        "no extraction rule for: ${(dictionaryArchiveUrls.keys - dictionaryExtractionRules.keys).joinToString()}."
}

val dictionaryDataDir: File = rootDir.resolve("data")
val dictionaryArchiveDir: File = dictionaryDataDir.resolve("archives")

val fetchDictionarySources = tasks.register<FetchDictionarySources>("fetchDictionarySources") {
    group = "build"
    description = "Downloads the dictionary source archives into data/archives/ (cached, fetched once)."
    sources.set(dictionaryArchiveUrls)
    archiveDir.set(dictionaryArchiveDir)
    offline.set(gradle.startParameter.isOffline)
}

val extractDictionarySources = tasks.register<ExtractDictionarySources>("extractDictionarySources") {
    group = "build"
    description = "Decompresses data/archives/ into the source files :tools:dictgen reads from data/."
    dependsOn(fetchDictionarySources)
    gzipped.set(gzippedArchives)
    bzipped.set(bzippedArchives)
    tarred.set(tarredArchives)
    zipped.set(zippedArchives)
    archiveDir.set(dictionaryArchiveDir)
    dataDir.set(dictionaryDataDir)
    archives.from(dictionaryArchiveUrls.keys.map { dictionaryArchiveDir.resolve(it) })
    extractedFiles.from(
        (gzippedArchives.values + bzippedArchives.values + tarredArchives.values)
            .map { dictionaryDataDir.resolve(it) },
    )
    extractedDirs.from(zippedArchives.values.map { dictionaryDataDir.resolve(it) })
}

tasks.named<JavaExec>("run") {
    // Default --data/--out arguments resolve relative to the repository root.
    workingDir = rootDir
    // The entry point README.md documents, so it has to work on a fresh
    // clone: fetch and decompress the sources first.
    dependsOn(extractDictionarySources)
}

val dictionaryOutputDir = layout.buildDirectory.dir("generated/dictionary")

// Kept out of dictionaryOutputDir on purpose: that directory is published to
// :androidApp and packaged as assets verbatim, and SyncDictionaryAssets fails
// on anything in it that is not the database or its version sidecar.
val posCodesFile = layout.buildDirectory.file("generated/dictionary-meta/pos-codes.tsv")

val generateDictionary = tasks.register<JavaExec>("generateDictionary") {
    group = "build"
    description = "Generates the bundled dictionary database and its version sidecar from data/."
    mainClass.set(application.mainClass)
    classpath = sourceSets.main.get().runtimeClasspath
    dependsOn(extractDictionarySources)
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
    // `.isFile` test the files use rejects a directory outright. The name is
    // the zip's own top-level directory, kept as published.
    val sourceDirNames = listOf("kanji")
    val dataDir = dictionaryDataDir
    val outputDir = dictionaryOutputDir
    val posCodes = posCodesFile
    val fetchTaskPath = "${project.path}:fetchDictionarySources"
    inputs.files(sourceNames.map { dataDir.resolve(it) })
        .withPropertyName("dictionarySources")
        .withPathSensitivity(PathSensitivity.NONE)
    // Registered as file TREES, not as inputs.dir(...). Gradle validates
    // an input directory before it runs any task action, and does so even
    // when the property is marked optional, so inputs.dir turns a fresh
    // clone into "property 'dictionarySourceDir-kanji' specifies
    // directory ... which doesn't exist" and the curated message below --
    // the one that names every source and points at the fetch task -- is
    // never reached. A tree over a missing directory is simply empty, so the
    // build survives configuration and fails in doFirst with something
    // worth reading. Contents are still tracked when the directory is
    // there, which is what keeps the task up-to-date correctly.
    inputs.files(sourceDirNames.map { fileTree(dataDir.resolve(it)) })
        .withPropertyName("dictionarySourceDirs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir).withPropertyName("dictionaryOutputDir")
    outputs.file(posCodes).withPropertyName("posCodesFile")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--data", dataDir.absolutePath,
                "--out", outputDir.get().asFile.resolve("okonomi.db").absolutePath,
                "--pos-codes", posCodes.get().asFile.absolutePath,
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
                    "The build normally downloads and decompresses them itself; run " +
                    "'$fetchTaskPath' once with network access (the archives are cached in " +
                    "data/archives/ and never fetched twice). See README.md for each source " +
                    "and its licence.",
            )
        }
    }
}

/**
 * Refreshes the committed POS code sidecar from the generated dictionary.
 *
 * The file lives in :shared's host-test resources rather than in a build
 * directory because BreakdownPosCodesTest has to hold on a clean checkout:
 * pr-test.yml deliberately never builds the dictionary, and a guard that
 * cannot run in PR checks is a guard that stops guarding.
 *
 * Run this whenever the dictionary is regenerated and commit what changes.
 * A code JMdict has retired shows up here as a diff, which is the point.
 */
tasks.register<Copy>("syncPosCodes") {
    group = "build"
    description = "Refreshes shared/src/androidHostTest/resources/pos-codes.tsv from the generated dictionary."
    dependsOn(generateDictionary)
    from(posCodesFile)
    into(rootProject.layout.projectDirectory.dir("shared/src/androidHostTest/resources"))
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

package cc.hosaka.okonomi.dictgen

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var db = File("tools/dictgen/build/generated/dictionary/okonomi.db")
    var out = File("shared/src/commonTest/kotlin/cc/hosaka/okonomi/lang/ConjugationCorpus.kt")
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--db" -> db = File(args.getOrNull(++i) ?: usage())
            "--out" -> out = File(args.getOrNull(++i) ?: usage())
            "--help", "-h" -> {
                println(USAGE)
                return
            }
            else -> {
                System.err.println("Unknown argument: $arg")
                usage()
            }
        }
        i++
    }
    try {
        println(ConjugationCorpusWriter(db, out).run().report())
    } catch (e: PipelineException) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println("corpusgen failed: $e")
        exitProcess(1)
    }
}

private const val USAGE = "Usage: corpusgen [--db <file>] [--out <file>]"

private fun usage(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}

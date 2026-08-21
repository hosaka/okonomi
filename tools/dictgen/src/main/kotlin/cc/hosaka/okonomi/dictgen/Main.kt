package cc.hosaka.okonomi.dictgen

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var dataDir = File("data")
    var out = File("tools/dictgen/build/okonomi.db")
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--data" -> dataDir = File(args.getOrNull(++i) ?: usage())
            "--out" -> out = File(args.getOrNull(++i) ?: usage())
            "--help", "-h" -> {
                println("Usage: dictgen [--data <dir>] [--out <file>]")
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
        println(Pipeline(dataDir, out).run().report())
    } catch (e: PipelineException) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println("dictgen failed: $e")
        exitProcess(1)
    }
}

private fun usage(): Nothing {
    System.err.println("Usage: dictgen [--data <dir>] [--out <file>]")
    exitProcess(2)
}

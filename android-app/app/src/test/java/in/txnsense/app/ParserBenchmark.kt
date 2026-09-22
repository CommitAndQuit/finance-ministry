package `in`.txnsense.app

import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.parser.ParserRules
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import `in`.txnsense.app.parser.engine.allTemplates
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Throwaway benchmark: cost of the rule engine per SMS, and the cost of running every template
 * against every message (the worst case if the pre-template guards were removed). Also isolates
 * the cost of compiling template regexes per message vs compiling them once.
 */
class ParserBenchmark {

    private fun bodies(file: File): List<Pair<String, String>> {
        val header = Regex("^(?:Received from|Sent to) (\\S+) on (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\s*$")
        fun separator(line: String) = line.matches(Regex("^[-=]{5,}\\s*$")) ||
            line.startsWith("Conversation with") || line.startsWith("Received from") ||
            line.startsWith("Sent to") || line.matches(Regex("^\\d+ messages?\\s*$")) ||
            line.startsWith("[ Exported on")
        val lines = file.readText().split("\n")
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < lines.size) {
            val m = header.find(lines[i])
            if (m == null) { i++; continue }
            val sender = m.groupValues[1]; i++
            if (i < lines.size && lines[i].isBlank()) i++
            val body = StringBuilder()
            while (i < lines.size && !separator(lines[i])) {
                if (body.isNotEmpty()) body.append("\n")
                body.append(lines[i].trim()); i++
            }
            if (body.isNotEmpty()) out.add(sender to body.toString().trim())
        }
        return out
    }

    private inline fun time(warmups: Int, reps: Int, block: () -> Unit): Double {
        repeat(warmups) { block() }
        val start = System.nanoTime()
        repeat(reps) { block() }
        return (System.nanoTime() - start) / 1e6 / reps // ms per rep
    }

    @Test
    fun benchmark() {
        val file = File(System.getProperty("auditFile") ?: "../../sms-history-sep2026.txt")
        assumeTrue("Export not found at ${file.absolutePath}", file.exists())
        val msgs = bodies(file)
        val parser = TemplateEngineParser()
        val templates = allTemplates()
        val n = msgs.size
        println("BENCH messages=$n templates=${templates.size} " +
            "avgBodyChars=${msgs.sumOf { it.second.length } / n}")

        // 1. Real engine, as shipped (guards short-circuit most messages).
        val asShipped = time(20, 100) {
            msgs.forEach { (s, b) -> parser.parse(IncomingSms(s, 0L, b)) }
        }

        // 2. Worst case with guards removed: every template tried against every message,
        //    compiling each pattern per message exactly as the engine does today.
        val allTemplatesCompiledEachTime = time(5, 20) {
            msgs.forEach { (_, b) ->
                val t = ParserRules.transactionText(b)
                templates.forEach { Regex(it.regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(t) }
            }
        }

        // 3. Same work, but patterns compiled once up front.
        val precompiled = templates.map { Regex(it.regexPattern, RegexOption.IGNORE_CASE) }
        val allTemplatesPrecompiled = time(20, 200) {
            msgs.forEach { (_, b) ->
                val t = ParserRules.transactionText(b)
                precompiled.forEach { it.containsMatchIn(t) }
            }
        }

        // 4. Cost of compiling the template set once (paid per message today).
        val compileOnly = time(20, 200) {
            templates.forEach { Regex(it.regexPattern, RegexOption.IGNORE_CASE) }
        }

        fun per(ms: Double) = String.format("%.1f us/sms", ms * 1000 / n)
        println("BENCH as_shipped_total            = ${"%.3f".format(asShipped)} ms  (${per(asShipped)})")
        println("BENCH all_templates_compile_each  = ${"%.3f".format(allTemplatesCompiledEachTime)} ms  (${per(allTemplatesCompiledEachTime)})")
        println("BENCH all_templates_precompiled   = ${"%.3f".format(allTemplatesPrecompiled)} ms  (${per(allTemplatesPrecompiled)})")
        println("BENCH compile_template_set_once   = ${"%.3f".format(compileOnly)} ms")
        println("BENCH extrapolated to 20000 SMS:")
        println("BENCH   as_shipped               = ${"%.2f".format(asShipped / n * 20000 / 1000)} s")
        println("BENCH   compile_each_no_guards   = ${"%.2f".format(allTemplatesCompiledEachTime / n * 20000 / 1000)} s")
        println("BENCH   precompiled_no_guards    = ${"%.2f".format(allTemplatesPrecompiled / n * 20000 / 1000)} s")
    }
}

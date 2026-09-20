package `in`.financeministry.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.IncomingSms
import `in`.financeministry.app.parser.ParserRules
import `in`.financeministry.app.parser.engine.TemplateEngineParser
import `in`.financeministry.app.parser.engine.allTemplates
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Throwaway on-device benchmark: rule-engine cost per SMS on real ART/ARM, versus the worst case
 * of trying every template against every message. Skipped unless the export has been pushed to
 * the app's external files dir.
 */
class ParserBenchmarkDeviceTest {

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
        return (System.nanoTime() - start) / 1e6 / reps
    }

    private fun log(line: String) {
        Log.i("ParserBench", line)
        println(line)
    }

    @Test
    fun benchmarkOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "sms-history.txt")
        assumeTrue("Push the export to ${file.absolutePath}", file.exists())
        val msgs = bodies(file)
        assumeTrue("Export parsed no messages", msgs.isNotEmpty())
        val parser = TemplateEngineParser()
        val templates = allTemplates()
        val n = msgs.size

        log("DBENCH device messages=$n templates=${templates.size} avgChars=${msgs.sumOf { it.second.length } / n}")

        val asShipped = time(20, 100) { msgs.forEach { (s, b) -> parser.parse(IncomingSms(s, 0L, b)) } }
        val compileEach = time(5, 20) {
            msgs.forEach { (_, b) ->
                val t = ParserRules.transactionText(b)
                templates.forEach { Regex(it.regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(t) }
            }
        }
        val pre = templates.map { Regex(it.regexPattern, RegexOption.IGNORE_CASE) }
        val precompiled = time(20, 200) {
            msgs.forEach { (_, b) ->
                val t = ParserRules.transactionText(b)
                pre.forEach { it.containsMatchIn(t) }
            }
        }

        fun per(ms: Double) = String.format("%.1f us/sms", ms * 1000 / n)
        log("DBENCH as_shipped           = ${"%.3f".format(asShipped)} ms (${per(asShipped)})")
        log("DBENCH all_tmpl_compile_each= ${"%.3f".format(compileEach)} ms (${per(compileEach)})")
        log("DBENCH all_tmpl_precompiled = ${"%.3f".format(precompiled)} ms (${per(precompiled)})")
        log("DBENCH 20000sms as_shipped=${"%.2f".format(asShipped / n * 20000 / 1000)}s " +
            "compile_each=${"%.2f".format(compileEach / n * 20000 / 1000)}s " +
            "precompiled=${"%.2f".format(precompiled / n * 20000 / 1000)}s")
    }
}

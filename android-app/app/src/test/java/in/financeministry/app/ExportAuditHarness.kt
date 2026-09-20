package `in`.financeministry.app

import `in`.financeministry.app.core.model.IncomingSms
import `in`.financeministry.app.core.model.ParseDecision
import `in`.financeministry.app.parser.engine.TemplateEngineParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Throwaway audit harness: runs the real rule engine over a local "SMS Exporter" text export and
 * prints one row per message. Not an assertion test; skipped unless the export file is present.
 */
class ExportAuditHarness {

    private class Msg(val sender: String, val date: String, val body: String)

    /** Mirrors FileHistoricalSmsSource.read so the audit sees exactly what the importer sees. */
    private fun readExport(file: File): List<Msg> {
        val header = Regex("^(?:Received from|Sent to) (\\S+) on (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\s*$")
        fun separator(line: String) = line.matches(Regex("^[-=]{5,}\\s*$")) ||
            line.startsWith("Conversation with") || line.startsWith("Received from") ||
            line.startsWith("Sent to") || line.matches(Regex("^\\d+ messages?\\s*$")) ||
            line.startsWith("[ Exported on")
        val lines = file.readText().split("\n")
        val out = mutableListOf<Msg>()
        var i = 0
        while (i < lines.size) {
            val match = header.find(lines[i])
            if (match == null) { i++; continue }
            val sender = match.groupValues[1]
            val dateText = match.groupValues[2]
            i++
            if (i < lines.size && lines[i].isBlank()) i++
            val body = StringBuilder()
            while (i < lines.size && !separator(lines[i])) {
                if (body.isNotEmpty()) body.append("\n")
                body.append(lines[i].trim())
                i++
            }
            val text = body.toString().trim()
            if (text.isNotEmpty()) out.add(Msg(sender, dateText, text))
        }
        return out
    }

    @Test
    fun auditExport() {
        val file = File(System.getProperty("auditFile") ?: "../../sms-history-sep2026.txt")
        assumeTrue("Audit export not found at ${file.absolutePath}", file.exists())
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val parser = TemplateEngineParser()

        println("AUDIT_BEGIN")
        readExport(file).forEach { msg ->
            val millis = runCatching { fmt.parse(msg.date)?.time ?: 0L }.getOrDefault(0L)
            val a = parser.parse(IncomingSms(msg.sender, millis, msg.body))
            val amount = a.amountMinor?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "-"
            println(
                listOf(
                    msg.date,
                    msg.sender,
                    a.decision,
                    amount,
                    a.direction,
                    a.status,
                    a.channel,
                    a.transactionType,
                    a.bankName ?: "-",
                    a.maskedAccountHint ?: "-",
                    a.counterpartyLabel ?: "-",
                    a.confidence,
                    a.ruleId,
                    msg.body.replace("\n", " ⏎ "),
                ).joinToString("\t") { it.toString() }
            )
        }
        println("AUDIT_END")

        val counts = readExport(file).groupingBy {
            parser.parse(IncomingSms(it.sender, 0L, it.body)).decision
        }.eachCount()
        println("SUMMARY total=${readExport(file).size} " +
            ParseDecision.values().joinToString(" ") { "$it=${counts[it] ?: 0}" })
    }
}

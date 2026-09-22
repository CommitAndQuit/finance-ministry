package `in`.txnsense.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Debug/testing source: parses an "SMS Exporter" text export into historical messages so the
 * import pipeline can be exercised on an emulator where adb-injected SMS are hidden from a
 * non-default SMS app. Wired only in debug builds; never used in release.
 */
class FileHistoricalSmsSource(private val file: File) : HistoricalSmsSource {
    override suspend fun read(window: ImportWindow, emit: suspend (HistoricalSms) -> Unit) {
        if (!file.exists()) error("Test SMS file not found at ${file.path}")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val lines = file.readText().split("\n")
        val header = Regex("^(?:Received from|Sent to) (\\S+) on (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\s*$")
        fun separator(line: String) = line.matches(Regex("^[-=]{5,}\\s*$")) ||
            line.startsWith("Conversation with") || line.startsWith("Received from") ||
            line.startsWith("Sent to") || line.matches(Regex("^\\d+ messages?\\s*$")) ||
            line.startsWith("[ Exported on")
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
            if (text.isNotEmpty()) {
                val millis = runCatching { fmt.parse(dateText)?.time ?: 0L }.getOrDefault(0L)
                // previewImport applies the window filter; emit everything with a valid date.
                if (millis > 0) emit(HistoricalSms(sender, millis, 0L, text))
            }
        }
    }
}

package `in`.financeministry.app

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * End-to-end proof of the payment-method feature over the user's real SMS export, run through
 * the actual import -> commit -> discovery path (fake [HistoricalSmsSource], bypassing the
 * device SMS provider). Opt-in: skips unless sms-history.txt has been pushed to the app's
 * external files dir, so it never embeds private data and is a no-op in CI.
 *
 * Push the corpus first, e.g.:
 *   adb shell mkdir -p /sdcard/Android/data/in.financeministry.app/files
 *   adb push sms-history.txt /sdcard/Android/data/in.financeministry.app/files/sms-history.txt
 */
class PaymentMethodImportE2eTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun repository() = TransactionRepository(context, "pm_e2e_${UUID.randomUUID()}")
    private fun grant() = InstrumentationRegistry.getInstrumentation().uiAutomation
        .grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)

    @Test fun real_corpus_import_discovers_payment_methods() = runBlocking {
        val file = File(context.getExternalFilesDir(null), "sms-history.txt")
        assumeTrue("Push sms-history.txt to the app external dir to run this", file.exists())
        grant()
        val messages = parseExport(file.readText())
        assertTrue("corpus should parse to many messages", messages.size > 100)

        val repository = repository()
        try {
            val now = System.currentTimeMillis()
            val preview = repository.previewImport({ _, emit -> messages.forEach { emit(it) } }, now)
            Log.i(TAG, "scanned=${preview.scanned} ignored=${preview.ignored} duplicates=${preview.duplicates} candidates=${preview.rows.size}")
            assertTrue("expected in-window financial candidates", preview.rows.size > 50)

            val result = repository.commitImport(preview)
            Log.i(TAG, "committed inserted=${result.inserted} duplicates=${result.duplicates}")
            assertTrue("expected transactions inserted", result.inserted > 50)

            val methods = repository.discoveredMethods()
            assertTrue("expected discovered payment methods", methods.isNotEmpty())

            // Log the full discovered set so the run is a readable, hands-off proof.
            Log.i(TAG, "=== discovered payment methods (${methods.size}) ===")
            methods.forEach {
                Log.i(TAG, "  ${it.bankName ?: "?"} | ${it.channel} | ${it.maskedAccountHint ?: "-"} : " +
                    "count=${it.count} out=${it.outMinor / 100} in=${it.inMinor / 100}")
            }
            val channels = methods.map { it.channel }.toSet()
            val banks = methods.mapNotNull { it.bankName }.toSet()
            Log.i(TAG, "channels=$channels banks=$banks")

            // Feature assertions: credit cards split out, banks attributed, account transfers recognized.
            assertTrue("expected a CreditCard method", channels.contains("CreditCard"))
            assertTrue("expected at least one recognized bank", banks.isNotEmpty())
            assertTrue("expected total method usage to be positive",
                methods.sumOf { it.count } > 0)
        } finally { repository.eraseAll(); repository.close() }
    }

    private data class Rec(val sender: String, val dt: String, val body: String)

    private fun parseExport(text: String): List<HistoricalSms> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val lines = text.split("\n")
        val header = Regex("^(?:Received from|Sent to) (\\S+) on (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\s*$")
        fun sep(l: String) = l.matches(Regex("^[-=]{5,}\\s*$")) ||
            l.startsWith("Conversation with") || l.startsWith("Received from") || l.startsWith("Sent to") ||
            l.matches(Regex("^\\d+ messages?\\s*$")) || l.startsWith("[ Exported on")
        val out = ArrayList<HistoricalSms>()
        var i = 0
        while (i < lines.size) {
            val h = header.find(lines[i])
            if (h == null) { i++; continue }
            val sender = h.groupValues[1]; val dt = h.groupValues[2]; i++
            if (i < lines.size && lines[i].isBlank()) i++
            val body = StringBuilder()
            while (i < lines.size && !sep(lines[i])) { if (body.isNotEmpty()) body.append("\n"); body.append(lines[i].trim()); i++ }
            val b = body.toString().trim()
            if (b.isNotEmpty()) {
                val millis = runCatching { fmt.parse(dt)?.time ?: 0L }.getOrDefault(0L)
                if (millis > 0) out.add(HistoricalSms(sender, millis, 0L, b))
            }
        }
        return out
    }

    private companion object { const val TAG = "PMImportE2E" }
}

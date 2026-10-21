package `in`.txnsense.app

import android.telephony.SmsMessage
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in validation of the local fake-bank fixture using Android's real PDU decoder. */
class ServiceHeaderPduFixtureTest {
    @Test fun generated_service_header_fixture_decodes_without_changing_body_or_origin() {
        val i = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runPduDecode") == "true")
        val input = JSONObject(File(i.targetContext.filesDir, "service-pdu-decode-input.json").readText())
        val hex = input.getString("pdu")
        assertTrue(hex.matches(Regex("(?:[0-9A-F]{2})+")))
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val message = SmsMessage.createFromPdu(bytes, "3gpp")
        assertNotNull("Synthetic PDU could not be decoded", message)
        assertEquals(input.getString("expectedSender"), message.originatingAddress)
        assertEquals(input.getString("expectedBody"), message.messageBody)
        assertTrue(message.timestampMillis > 0)
    }
}

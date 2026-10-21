package `in`.txnsense.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Invented references and accounts only; SMS bodies are transient test inputs. */
class ReferenceReliabilityIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun grant() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
    }
    private fun body(amount: Int = 42, account: String = "0000", channel: String = "UPI",
        reference: String = "UPI:000000000001", refund: Boolean = false) =
        "INR $amount ${if (refund) "refund credited" else "debited"} via $channel account XX$account $reference"

    @Test fun sender_variations_keep_reference_evidence_without_rewriting_original() = runBlocking {
        grant()
        for (header in listOf("HDFCBK", "ICICIB")) {
            val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
            try {
                repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
                val now = System.currentTimeMillis()
                assertTrue(repo.ingest(IncomingSms("AD-$header", now, body())))
                val original = repo.snapshot().rows.single()
                assertTrue(repo.ingest(IncomingSms("VM-$header", now + 1, body(refund = true))))
                val refund = repo.snapshot().rows.first()
                assertNotNull(original.referenceHash)
                assertArrayEquals(original.referenceHash, refund.referenceHash)
                assertEquals(original.id, refund.linkedOriginalId)
                assertEquals("AutoRecorded", refund.reviewState)
                val unchanged = repo.get(original.id)!!
                assertEquals(original.updatedAt, unchanged.updatedAt)
                assertEquals(original.reviewState, unchanged.reviewState)
                assertEquals(original.amountMinor, unchanged.amountMinor)
                assertFalse(unchanged.isUserCorrected)
                assertArrayEquals(original.referenceHash, unchanged.referenceHash)
                repo.close()
                assertEquals(original.id, repo.get(refund.id)!!.linkedOriginalId)
            } finally { repo.eraseAll(); repo.close() }
        }
    }

    @Test fun reference_does_not_override_conflicting_financial_signals_or_ambiguous_tokens() = runBlocking {
        grant()
        val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
            val now = System.currentTimeMillis()
            repo.ingest(IncomingSms("AD-HDFCBK", now, body()))
            repo.ingest(IncomingSms("OTHER-SYNTHETIC", now + 1, body(refund = true)))
            assertNull(repo.snapshot().rows.first().linkedOriginalId)
            assertEquals("NeedsReview", repo.snapshot().rows.first().reviewState)
            val variants = listOf(body(amount = 41, refund = true), body(account = "9999", refund = true),
                body(channel = "IMPS", reference = "Ref 000000000001", refund = true),
                body(reference = "UPI:000000000001 Ref 000000000002", refund = true),
                body(refund = true).replace("account XX0000", "account 123456789012"),
                body(refund = true).replace("INR 42", "USD 42"))
            variants.forEachIndexed { index, text ->
                assertTrue(repo.ingest(IncomingSms("VM-HDFCBK", now + index + 2, text)))
                val adjustment = repo.snapshot().rows.first()
                assertNull(text, adjustment.linkedOriginalId)
                assertEquals(text, "NeedsReview", adjustment.reviewState)
            }
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun reused_reference_requires_unique_uncorrected_recent_original() = runBlocking {
        grant()
        for (scenario in listOf("old", "future", "corrected", "ambiguous", "duplicate", "review", "source")) {
            val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
            try {
                repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
                val now = System.currentTimeMillis()
                val originalTime = when (scenario) {
                    "old" -> now - 31L * 24 * 60 * 60 * 1000
                    "future" -> now + 1000
                    else -> now - 1000
                }
                val referenceBody = body(reference = "Ref 000000000001")
                repo.ingest(IncomingSms("SYNTHETIC", originalTime, referenceBody))
                val original = repo.withLedger { it.transactions().all().single() }
                when (scenario) {
                    "corrected" -> repo.save(ManualInput("42", Direction.Debit, originalTime,
                        TransactionType.Other, channel = Channel.UPI, accountHint = "0000", notes = "Checked by me"), original.id)
                    "ambiguous" -> repo.ingest(IncomingSms("SYNTHETIC", originalTime + 1, referenceBody))
                    "duplicate" -> repo.withLedger { it.transactions().update(original.copy(duplicateOfId = "older")) }
                    "review" -> repo.withLedger { it.transactions().update(original.copy(reviewState = "NeedsReview")) }
                    "source" -> {
                        val firstSource = repo.addPaymentSource("First account", "Bank account", Channel.UPI)
                        repo.withLedger { it.transactions().update(original.copy(paymentSourceId = firstSource.id)) }
                        repo.addPaymentSource("Other account", "Bank account", Channel.UPI, "SYNTHBANK", "0000")
                    }
                }
                val savedBefore = repo.get(original.id)!!
                repo.ingest(IncomingSms("SYNTHETIC", now, body(reference = "Ref 000000000001", refund = true)))
                val adjustment = repo.withLedger { it.transactions().all().single { row -> row.transactionType == "Refund" } }
                assertNull(scenario, adjustment.linkedOriginalId)
                assertEquals(scenario, "NeedsReview", adjustment.reviewState)
                assertEquals(scenario, savedBefore.updatedAt, repo.get(original.id)!!.updatedAt)
                assertEquals(scenario, savedBefore.isUserCorrected, repo.get(original.id)!!.isUserCorrected)
                assertEquals(scenario, savedBefore.userNotes, repo.get(original.id)!!.userNotes)
                assertEquals(scenario, savedBefore.amountMinor, repo.get(original.id)!!.amountMinor)
            } finally { repo.eraseAll(); repo.close() }
        }
    }

    @Test fun thirty_day_boundary_can_link_but_existing_adjustment_blocks_a_second_link() = runBlocking {
        grant()
        val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
            val now = System.currentTimeMillis()
            val originalTime = now - 30L * 24 * 60 * 60 * 1000
            repo.ingest(IncomingSms("AD-HDFCBK", originalTime, body()))
            val original = repo.withLedger { it.transactions().all().single() }
            repo.ingest(IncomingSms("VM-HDFCBK", now, body(refund = true)))
            assertEquals(original.id, repo.snapshot().rows.first().linkedOriginalId)
            // Keep this second adjustment inside the boundary as well: the existing
            // link, not an expired time window, must prevent double-linking.
            var savedId: String? = null
            assertTrue(repo.ingest(IncomingSms("AD-HDFCBK", now, body(refund = true))) { savedId = it.id })
            val second = repo.get(requireNotNull(savedId))!!
            assertNull(second.linkedOriginalId)
            assertEquals("NeedsReview", second.reviewState)
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun history_import_preserves_reference_for_later_capture_without_reclassifying_adjustments() = runBlocking {
        grant()
        val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
            val now = System.currentTimeMillis()
            val history = listOf(HistoricalSms("AD-HDFCBK", now - 1000, 0, body()),
                HistoricalSms("VM-HDFCBK", now - 500, 0, body(reference = "UPI:000000000002", refund = true)))
            val source = HistoricalSmsSource { _, emit -> history.forEach { emit(it) } }
            val preview = repo.previewImport(source, now)
            assertTrue(preview.rows.all { it.referenceHash?.size == 32 })
            assertTrue(repo.snapshot().rows.isEmpty())
            assertEquals(2, repo.commitImport(preview).inserted)
            val original = repo.snapshot().rows.single { it.transactionType != "Refund" }
            val historicalRefund = repo.snapshot().rows.single { it.transactionType == "Refund" }
            assertNotNull(original.referenceHash)
            assertEquals("NeedsReview", historicalRefund.reviewState)
            assertNull(historicalRefund.linkedOriginalId)
            repo.close()
            repo.ingest(IncomingSms("VM-HDFCBK", now, body(refund = true)))
            val liveRefund = repo.snapshot().rows.first()
            assertEquals(original.id, liveRefund.linkedOriginalId)
            assertArrayEquals(original.referenceHash, liveRefund.referenceHash)
            assertEquals("NeedsReview", repo.get(historicalRefund.id)!!.reviewState)
            assertEquals(2, repo.previewImport(source, now).duplicates)
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun legacy_reference_is_lookup_only_and_is_not_silently_rehashed() = runBlocking {
        grant()
        val namespace = "reference_${UUID.randomUUID()}"
        val repo = TransactionRepository(context, namespace)
        try {
            repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
            val now = System.currentTimeMillis()
            repo.ingest(IncomingSms("SYNTHETIC", now, body()))
            val original = repo.snapshot().rows.single()
            val legacyHash = DeviceSecrets(context, namespace).hmacSource("transaction-reference-v1:synthetic", 0, "000000000001")
            repo.withLedger { it.transactions().update(original.copy(referenceHash = legacyHash)) }
            repo.ingest(IncomingSms("SYNTHETIC", now + 1, body(refund = true)))
            assertEquals(original.id, repo.snapshot().rows.first().linkedOriginalId)
            assertArrayEquals(legacyHash, repo.get(original.id)!!.referenceHash)
            assertFalse(legacyHash.contentEquals(repo.snapshot().rows.first().referenceHash))
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun unrelated_or_numeric_headers_are_not_assumed_route_aliases() = runBlocking {
        grant()
        for ((first, second) in listOf("12345" to "67890", "SYNTHETIC" to "PREFIX-SYNTHETIC",
            "VM-123456" to "AD-123456", "VM-SYNTHBANK-2" to "AD-SYNTHBANK-2",
            "VM-SYNTHBANK" to "AD-SYNTHBANK")) {
            val repo = TransactionRepository(context, "reference_${UUID.randomUUID()}")
            try {
                repo.preferences.edit().putBoolean("sms_disclosure", true).commit()
                val now = System.currentTimeMillis()
                repo.ingest(IncomingSms(first, now, body()))
                repo.ingest(IncomingSms(second, now + 1, body(refund = true)))
                assertNull("$first / $second", repo.snapshot().rows.first().linkedOriginalId)
                assertEquals("NeedsReview", repo.snapshot().rows.first().reviewState)
            } finally { repo.eraseAll(); repo.close() }
        }
    }
}

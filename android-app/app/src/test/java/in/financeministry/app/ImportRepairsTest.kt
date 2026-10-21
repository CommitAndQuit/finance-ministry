package `in`.financeministry.app

import `in`.financeministry.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ImportRepairsTest {
    private fun old() = TransactionEntity("existing", byteArrayOf(1, 2), "SMS", 10, 11, null, null,
        "Debit", "Successful", "UPI", "Unknown", maskedAccountHint = "••••0000", confidence = 60,
        reviewState = "NeedsReview", parserVersion = 12, createdAt = 20, updatedAt = 20,
        importBatchId = "original-batch", category = "Food", paymentSourceId = "my-source")
    private fun parsed() = old().copy(id = "new", sourceTimestamp = 99, effectiveTimestamp = 99,
        amountMinor = 4250, currency = "INR", counterpartyLabel = "SAMPLE SHOP", confidence = 96,
        reviewState = "AutoRecorded", parserVersion = 13, referenceHash = byteArrayOf(3, 4))

    @Test fun known_amount_field_repair_keeps_counted_state_identity_and_existing_label() {
        val existing = old().copy(amountMinor = 4250, currency = "INR", channel = "Unknown",
            reviewState = "AutoRecorded", counterpartyLabel = "Existing label")
        val result = requireNotNull(ImportRepairs.propose(existing, parsed()))
        assertEquals(4250L, result.amountMinor)
        assertEquals("AutoRecorded", result.reviewState)
        assertEquals("UPI", result.channel)
        assertEquals("Existing label", result.counterpartyLabel)
        assertEquals("my-source", result.paymentSourceId)
    }

    @Test fun known_amount_missing_label_can_be_repaired_for_both_upi_and_card() {
        for (channel in listOf("UPI", "Card")) {
            val existing = old().copy(amountMinor = 4250, currency = "INR", channel = channel, reviewState = "AutoRecorded")
            val detected = parsed().copy(channel = channel, transactionType = if (channel == "Card") "MerchantPayment" else "Unknown")
            val result = requireNotNull(ImportRepairs.propose(existing, detected))
            assertEquals("SAMPLE SHOP", result.counterpartyLabel)
            assertEquals("AutoRecorded", result.reviewState)
            assertEquals(existing.effectiveTimestamp, result.effectiveTimestamp)
        }
    }

    @Test fun complete_records_or_conflicting_known_fields_are_not_repaired() {
        val complete = old().copy(amountMinor = 4250, currency = "INR", counterpartyLabel = "SAMPLE SHOP", reviewState = "AutoRecorded")
        assertNull(ImportRepairs.propose(complete, parsed()))
        assertNull(ImportRepairs.propose(complete.copy(amountMinor = 9900), parsed()))
        assertNull(ImportRepairs.propose(complete.copy(channel = "Card"), parsed()))
        assertNull(ImportRepairs.propose(complete.copy(currency = "USD"), parsed()))
    }

    @Test fun repair_fills_missing_evidence_without_replacing_identity_dates_source_or_category() {
        val result = requireNotNull(ImportRepairs.propose(old(), parsed()))
        assertEquals("existing", result.id)
        assertEquals(10L, result.sourceTimestamp)
        assertEquals(11L, result.effectiveTimestamp)
        assertArrayEquals(byteArrayOf(1, 2), result.sourceFingerprint)
        assertEquals("original-batch", result.importBatchId)
        assertEquals("my-source", result.paymentSourceId)
        assertEquals("Food", result.category)
        assertEquals(4250L, result.amountMinor)
        assertEquals("SAMPLE SHOP", result.counterpartyLabel)
        assertArrayEquals(byteArrayOf(3, 4), result.referenceHash)
        assertEquals("NeedsReview", result.reviewState)
        assertFalse(result.isUserCorrected)
    }

    @Test fun repair_requires_a_newer_parser_and_compatible_positive_successful_inr_debit() {
        for (invalid in listOf(parsed().copy(parserVersion = 12), parsed().copy(amountMinor = null),
            parsed().copy(amountMinor = 0), parsed().copy(currency = "USD"), parsed().copy(direction = "Credit"),
            parsed().copy(status = "Failed"), parsed().copy(status = "Pending"), parsed().copy(channel = "Card"),
            parsed().copy(maskedAccountHint = "••••9999"), parsed().copy(transactionType = "Refund"))) {
            assertNull(ImportRepairs.propose(old(), invalid))
        }
    }

    @Test fun repair_does_not_touch_nonimported_edited_confirmed_or_financially_linked_state() {
        for (protected in listOf(old().copy(sourceType = "Manual"), old().copy(importBatchId = null),
            old().copy(sourceFingerprint = null), old().copy(amountMinor = 9900), old().copy(currency = "USD"),
            old().copy(isUserCorrected = true), old().copy(reviewState = "Confirmed"),
            old().copy(reviewState = "Dismissed"), old().copy(updatedAt = 21), old().copy(userNotes = "My note"),
            old().copy(direction = "Unknown"), old().copy(status = "Pending"), old().copy(channel = "Card"),
            old().copy(ownership = "Group"), old().copy(personalShareMinor = 100), old().copy(repaidMinor = 100),
            old().copy(duplicateOfId = "other"), old().copy(linkedOriginalId = "other"))) {
            assertNull(ImportRepairs.propose(protected, parsed()))
        }
    }
}

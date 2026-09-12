package `in`.financeministry.app

import `in`.financeministry.app.core.model.Channel
import `in`.financeministry.app.core.model.Direction
import `in`.financeministry.app.core.model.SpendingOwnership
import `in`.financeministry.app.core.model.TransactionType
import `in`.financeministry.app.data.ManualInput
import `in`.financeministry.app.data.PaymentSourceEntity
import `in`.financeministry.app.data.PaymentSourceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationAndSourceRulesTest {
    private fun source(id: String, bank: String? = null, last4: String? = null,
        kind: String = "Bank account", channel: String = "UPI") = PaymentSourceEntity(
        id = id, nickname = id, kind = kind, channel = channel,
        bankName = bank, last4 = last4, createdAt = 1,
    )

    @Test fun contradictory_last_four_never_falls_back_to_the_only_source() {
        assertNull(PaymentSourceMatcher.resolve("UPI", "••••2222", "HDFCBK", listOf(source("hdfc", "HDFC", "1111"))))
    }

    @Test fun recognized_bank_sender_disqualifies_a_different_bank() {
        assertNull(PaymentSourceMatcher.resolve("UPI", null, "ICICIB", listOf(source("hdfc", "HDFC"))))
    }

    @Test fun unique_positive_evidence_maps_but_ambiguous_evidence_does_not() {
        val sources = listOf(source("first", "HDFC", "1111"), source("second", "HDFC", "2222"))
        assertEquals("second", PaymentSourceMatcher.resolve("UPI", "••••2222", "HDFCBK", sources))
        assertNull(PaymentSourceMatcher.resolve("UPI", null, "HDFCBK", sources))
    }

    @Test fun bank_evidence_disambiguates_identical_last_four_across_banks() {
        val sources = listOf(source("hdfc", "HDFC", "7111"), source("icici", "ICICI", "7111"))
        assertEquals("hdfc", PaymentSourceMatcher.resolve("UPI", "XX7111", "HDFCBK", sources))
    }

    @Test fun same_bank_without_last_four_remains_unresolved() {
        val sources = listOf(source("primary", "HDFC", "7111"), source("secondary", "HDFC", "9222"))
        assertNull(PaymentSourceMatcher.resolve("UPI", null, "HDFCBK", sources))
    }

    @Test fun a_sole_compatible_source_maps_when_the_message_has_no_identifier() {
        assertEquals("only", PaymentSourceMatcher.resolve("UPI", null, "BANKALERT", listOf(source("only"))))
    }

    @Test fun upi_backed_credit_card_is_a_valid_registered_upi_source() {
        val card = source("rupay-card", "BOB", "0318", kind = "Credit card")
        assertEquals("rupay-card", PaymentSourceMatcher.resolve("UPI", "ending 0318", "BOBSMS", listOf(card)))
    }

    @Test fun either_legacy_type_or_current_ownership_normalizes_to_self_transfer() {
        val input = ManualInput("100", Direction.Debit, 1, TransactionType.SelfTransfer,
            ownership = SpendingOwnership.Personal)
        assertEquals(TransactionType.SelfTransfer, input.normalizedType())
        assertEquals(SpendingOwnership.SelfTransfer, input.normalizedOwnership())
        assertEquals(TransactionType.SelfTransfer, input.copy(ownership = SpendingOwnership.SelfTransfer).normalizedType())
        assertEquals(SpendingOwnership.SelfTransfer, input.copy(ownership = SpendingOwnership.SelfTransfer).normalizedOwnership())
    }

    @Test fun irrelevant_hidden_group_values_are_normalized_after_reclassification() {
        val personal = ManualInput("300", Direction.Debit, 1, TransactionType.Other,
            ownership = SpendingOwnership.Personal, groupLabel = "Old group", personalShare = "100", repaid = "50")
        personal.validate()
        assertEquals(30000L, personal.personalShareMinor())
        assertEquals(0L, personal.repaidMinor())
    }
}

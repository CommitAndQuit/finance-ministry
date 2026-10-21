package `in`.txnsense.app.parser

import `in`.txnsense.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic merchant, amount and URL; preserves the reported issuer's grammar only. */
class ReceivedCardRefundTest {
    private val receipt = "Refund of Rs.42.19 from DEMO.ae received on your Federal Bank  One Credit Card."
    private val adjustment = " This will be adjusted against your outstanding dues. Check details: https://example.com/refund"
    private fun parse(body: String, sender: String = "AD-EXBANK") =
        RuleBasedFinancialSmsParser().parse(IncomingSms(sender, 0L, body))

    @Test fun received_card_refund_is_not_discarded_as_a_future_dues_reminder() {
        val result = parse(receipt + adjustment)
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(4219L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
        assertEquals(Channel.Card, result.channel)
        assertEquals(TransactionType.Refund, result.transactionType)
        assertEquals("DEMO.ae", result.counterpartyLabel)
        assertNull(result.maskedAccountHint)
    }

    @Test fun completed_receipt_without_adjustment_footer_keeps_its_refund_identity() {
        val result = parse(receipt.replace("Rs.42.19", "INR 1,234.50").replace("Bank  One", "Bank One"))
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(123450L, result.amountMinor)
        assertEquals(TransactionType.Refund, result.transactionType)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
    }

    @Test fun unreceived_or_future_refunds_are_not_successful_records() {
        listOf("will be received", "not received", "not yet received", "has not been received", "may be received", "pending receipt").forEach { wording ->
            val result = parse(receipt.replace("received", wording) + adjustment)
            assertFalse(wording, result.decision == ParseDecision.Record && result.status == TransactionStatus.Successful)
        }
        val pending = parse(receipt + " Refund processing is pending.")
        assertFalse(pending.decision == ParseDecision.Record && pending.status == TransactionStatus.Successful)
    }

    @Test fun receipt_keeps_otp_origin_and_multiple_amount_safeguards() {
        assertEquals(ParseDecision.Reject, parse(receipt + adjustment + " OTP 123456").decision)
        assertEquals(ParseDecision.NeedsReview, parse(receipt + adjustment, "999990001").decision)
        val multiple = parse(receipt + adjustment + " INR 50 also credited.")
        assertEquals(ParseDecision.NeedsReview, multiple.decision)
        assertNull(multiple.amountMinor)
    }
}

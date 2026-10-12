package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SecondaryAndIncompleteLayoutsTest {
    private fun parse(body: String) = RuleBasedFinancialSmsParser().parse(IncomingSms("AD-EXBANK", 1L, body))

    // A merchant receipt can describe the same purchase as a bank debit.
    @Test fun paid_merchant_order_is_saved_for_review_not_automatically_counted_twice() {
        val result = parse("Payment Confirmed! Your Sample Cafe Order #DEMO2468 of Rs.415.5 was successfully paid on 19/09/2026 at 10:38 PM. Txn ID: 123450009876.")
        assertEquals(ParseDecision.NeedsReview, result.decision)
        assertEquals(41550L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
    }

    // Funding a wallet from the user's bank could be an own-account transfer.
    @Test fun wallet_funding_retains_amount_but_requires_confirmation_before_money_in() {
        val result = parse("Rs.819.00 received in your Paytm wallet from A/c XX2468. Wallet balance Rs.2,700.00.")
        assertEquals(ParseDecision.NeedsReview, result.decision)
        assertEquals(81900L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
    }

    @Test fun a_currencyless_reversal_is_preserved_without_assuming_an_inr_amount() {
        val result = parse("Your UPI transaction of 91.00 has been reversed in your account due to a technical problem. UPI Ref no. 123450009876")
        assertEquals(ParseDecision.NeedsReview, result.decision)
        assertNull(result.amountMinor)
        assertNull(result.currency)
        assertEquals(TransactionStatus.Reversed, result.status)
        assertEquals(TransactionType.Reversal, result.transactionType)
    }

    @Test fun proposed_and_incomplete_sent_payment_text_does_not_become_a_successful_debit() {
        listOf(
            "Sent Rs.219.40 from Kotak Bank AC X2468 to samplecafe@ybl",
            "Debit INR 319.25 A/c no. XX2468. Available balance INR 8,100.00",
            "Your A/C XXXXX912468 has a debit request by transfer of Rs 218.70 on 19/09/26.",
            "Thanks for paying Rs.2,160.00 for SAMPLE TAX. Please make the transfer from A/c XXXX2468."
        ).forEach { body ->
            val result = parse(body)
            assertFalse("Incomplete layout must not count as a completed payment", result.decision == ParseDecision.Record && result.status == TransactionStatus.Successful)
        }
    }
}

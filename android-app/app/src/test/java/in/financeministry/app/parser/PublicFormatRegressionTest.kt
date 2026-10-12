package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Invented amounts/identifiers; public-format research is credited in the notices. */
class PublicFormatRegressionTest {
    private fun parse(body: String, sender: String = "EXBANK") = RuleBasedFinancialSmsParser().parse(IncomingSms(sender, 1L, body))

    @Test fun credited_payment_to_an_own_credit_card_is_a_bill_confirmation_not_income() {
        val result = parse("Your payment of INR 782.40 has been credited to your Example Bank Credit Card ending with 2468. Available limit INR 22,111.00.")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(78240L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
        assertEquals(TransactionType.CardRepayment, result.transactionType)
    }

    @Test fun numeric_missing_and_implausible_origins_require_review_without_losing_reliable_fields() {
        val results = listOf("9999900001", "+919999900001", "", "UNKNOWN_PHONE").map {
            parse("INR 641.25 debited from A/c XX2468 via UPI.", it)
        }
        assertEquals(listOf(ParseDecision.NeedsReview, ParseDecision.NeedsReview, ParseDecision.NeedsReview, ParseDecision.NeedsReview), results.map { it.decision })
        results.forEach {
            assertEquals(64125L, it.amountMinor)
            assertEquals(Direction.Debit, it.direction)
            assertEquals(TransactionStatus.Successful, it.status)
        }
    }

    @Test fun a_plausible_service_header_keeps_clear_completed_bank_movements_supported() {
        val result = parse("INR 641.25 debited from A/c XX2468 via UPI.", "AD-EXBANK")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(64125L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
    }

    @Test fun actually_credited_cashback_is_not_discarded_or_reclassified_as_a_card_bill() {
        val result = parse("INR 49.00 cashback credited to your sample wallet. Balance INR 4,099.00.", "WALLET")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(4900L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertNotEquals(TransactionType.CardRepayment, result.transactionType)
    }

    @Test fun valid_large_amounts_do_not_inherit_an_external_projects_arbitrary_cap() {
        val result = parse("INR 5,00,00,000 debited from A/c XX2468 via NEFT.")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(5_000_000_000L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
    }

    @Test fun a_completed_card_refund_has_incoming_direction_and_successful_status() {
        val result = parse("INR 413.20 has been refunded to your Example Bank Card x2468 by SYNTHETIC SHOP.")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(41320L, result.amountMinor)
        assertEquals(Direction.Credit, result.direction)
        assertEquals(TransactionStatus.Successful, result.status)
        assertEquals(TransactionType.Refund, result.transactionType)
    }

    @Test fun current_outstanding_abbreviation_is_not_a_second_payment_amount() {
        val result = parse("INR 892.75 spent on HDFC Bank CREDIT Card ending 2468 at SYNTHETIC SHOP on 2026-09-18 17:20:31. Avl bal INR 31,070.00, curr o/s INR 1,922.00.", "HDFCCC")
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(89275L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
    }

    @Test fun an_otp_from_a_numeric_origin_still_stays_out_of_the_ledger() {
        val result = parse("OTP 000000 for a payment of INR 641.25. Never share this code.", "9999900001")
        assertEquals(ParseDecision.Reject, result.decision)
        assertNull(result.amountMinor)
    }
}

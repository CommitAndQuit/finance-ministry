package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic values only; retain the reported grammar, not the tester's financial data. */
class CurrencylessUpiDebitTest {
    private val body = "Dear UPI user A/C X0000 debited by 42.50 on date 18Sep26 trf to SAMPLE SHOP Refno 000000000001"
    private fun parse(text: String, sender: String = "AD-EXBANK") =
        RuleBasedFinancialSmsParser().parse(IncomingSms(sender, 1L, text))

    @Test fun complete_currencyless_upi_grammar_is_not_specific_to_one_bank() {
        for (sender in listOf("AD-SBIUPI", "VM-EXBANK", "EXOTHER")) {
            for (suffix in listOf("", " If not u? call-1800000000", " If not u? call-1800000000 for other services-18000000-SBI")) {
                val result = parse(" $body$suffix ", sender)
                assertEquals(ParseDecision.Record, result.decision)
                assertEquals(4250L, result.amountMinor)
                assertEquals("INR", result.currency)
                assertEquals(Direction.Debit, result.direction)
                assertEquals(TransactionStatus.Successful, result.status)
                assertEquals(Channel.UPI, result.channel)
                assertEquals("••••0000", result.maskedAccountHint)
                assertEquals("SAMPLE SHOP", result.counterpartyLabel)
            }
        }
    }

    @Test fun unmarked_amount_variants_are_exact_and_recipient_ends_before_reference() {
        for ((amount, expected) in listOf("7.00" to 700L, "1,234.56" to 123456L, "1,23,456.78" to 12345678L)) {
            val result = parse(body.replace("42.50", amount).replace("SAMPLE SHOP", "Sample Shop Ltd"))
            assertEquals(ParseDecision.Record, result.decision)
            assertEquals(expected, result.amountMinor)
            assertEquals("Sample Shop Ltd", result.counterpartyLabel)
        }
    }

    @Test fun unknown_origins_still_need_review_even_with_complete_amount_and_recipient() {
        for (sender in listOf("", "unknown", "9999900000")) {
            val result = parse(body, sender)
            assertEquals(ParseDecision.NeedsReview, result.decision)
            assertEquals(4250L, result.amountMinor)
            assertEquals("SAMPLE SHOP", result.counterpartyLabel)
        }
    }

    @Test fun currencyless_template_does_not_guess_from_balances_or_other_events() {
        for (text in listOf(
            "$body. INR 99 also debited",
            "$body Another account debited by 99.00",
            "$body Available balance INR 900.00",
            body.replace("SAMPLE SHOP", "OTHER ACCOUNT credited"),
            body.replace("on date 18Sep26 ", ""),
            body.replace(" Refno 000000000001", ""),
            body.replace("42.50", "USD 42.50"),
            body.replace("42.50", "0.00"),
            body.replace("42.50", "-42.50"),
            body.replace("42.50", "42.501"),
            body.replace("42.50", "1,23.00"),
            body.replace("42.50", "92233720368547758.08"),
            body.replace("000000000001", "0000000000019")
        )) assertFalse("Unsafe or incomplete alert was auto-recorded: $text", parse(text).decision == ParseDecision.Record)
        assertNull(parse("Your account was debited by 42.50 through transfer").amountMinor)
    }

    @Test fun otp_negation_and_scheduled_movement_guards_are_preserved() {
        for (text in listOf("OTP 000000 $body", body.replace("debited by", "not debited by"),
            body.replace("debited by", "will be debited by"))) {
            assertEquals(ParseDecision.Reject, parse(text).decision)
        }
    }

    @Test fun canara_debits_do_not_use_balances_or_fraud_help_numbers_as_amounts() {
        val card = parse("A/c XXXX0000 linked to card  debited INR 42.50 on 19/09/26 POS txn. Avl Bal INR 9,876.54. To stop further debit SMS SUSPECT to 56161, Dial 1930 for cyber fraud. -Canara Bank")
        assertEquals(ParseDecision.Record, card.decision)
        assertEquals(4250L, card.amountMinor)
        assertEquals(Direction.Debit, card.direction)
        assertEquals(Channel.Card, card.channel)
        val debit = parse("An amount of INR 7.00 has been DEBITED to your account XXXX0000 on 05/09/2026. Total Avail.bal INR 9,876.54.. Dial 1930 to report cyber fraud- Canara Bank")
        assertEquals(ParseDecision.Record, debit.decision)
        assertEquals(700L, debit.amountMinor)
        assertEquals(Direction.Debit, debit.direction)
        assertEquals(Channel.Unknown, debit.channel)
    }

    @Test fun compact_reference_label_keeps_reference_evidence_without_phone_numbers() {
        assertEquals("000000000001", TransactionReference.extract(body + " If not u? call-1800000000"))
        assertNull(TransactionReference.extract(body.replace("000000000001", "000000000001X")))
    }
}

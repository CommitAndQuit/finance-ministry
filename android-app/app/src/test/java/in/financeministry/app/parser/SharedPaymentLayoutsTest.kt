package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Invented recipients, accounts, amounts and references; shared issuer-independent grammar. */
class SharedPaymentLayoutsTest {
    private fun parse(body: String, sender: String = "AD-EXBANK") =
        RuleBasedFinancialSmsParser().parse(IncomingSms(sender, 1L, body))
    private val sent = "Sent Rs.42.50 from Example Bank A/c X0000 to Sample Cafe on 21-10-26. UPI Ref 000000000001."
    private val multiline = "Sent Rs.42.50\nFrom Example Bank A/C *0000\nTo sample.pay-1@okexample\nOn 18/10/26\nRef 000000000001"
    private val drCr = "Rs.42.50 Dr. from A/C XXXXXX0000 and Cr. to sample.pay-1@hdfcbank. Ref:000000000001. AvlBal:Rs9876.54(2026:10:20 01:53:41)."
    private val card = "Spent INR 42.50 \nusing Example Bank Credit Card no. XX0000 on 20-10-26 19:12:42 IST at \nSAMPLE SHOP. \nAvl limit: INR 9876.54."

    @Test fun dated_single_line_sent_accepts_named_recipients_across_issuers() {
        for (bank in listOf("Kotak Bank", "Example Bank", "Other Bank")) {
            for (recipient in listOf("Sample Cafe", "Demo Play", "Sample Health Co", "Shop 42 & Sons")) {
                val result = parse(sent.replace("Example Bank", bank).replace("Sample Cafe", recipient))
                assertEquals(ParseDecision.Record, result.decision)
                assertEquals(4250L, result.amountMinor)
                assertEquals(Direction.Debit, result.direction)
                assertEquals(TransactionStatus.Successful, result.status)
                assertEquals(Channel.UPI, result.channel)
                assertEquals("••••0000", result.maskedAccountHint)
                assertEquals(recipient, result.counterpartyLabel)
            }
        }
    }

    @Test fun multiline_sent_extracts_punctuated_vpa_without_reading_the_security_footer_as_method() {
        for (bank in listOf("HDFC Bank", "Example Bank")) {
            val result = parse(multiline.replace("Example Bank", bank))
            assertEquals(ParseDecision.Record, result.decision)
            assertEquals(4250L, result.amountMinor)
            assertEquals(Channel.UPI, result.channel)
            assertEquals("sample.pay-1", result.counterpartyLabel)
            val named = parse(multiline.replace("Example Bank", bank).replace("sample.pay-1@okexample", "Sample Box") +
                "\nNot You?\nSMS BLOCK UPI to 7000000000")
            assertEquals(ParseDecision.Record, named.decision)
            assertEquals("Sample Box", named.counterpartyLabel)
            assertEquals(Channel.Unknown, named.channel)
        }
    }

    @Test fun dr_cr_vpa_alert_identifies_upi_and_recipient_but_not_the_balance() {
        for (suffix in listOf("", " Not you? Call 1800000000-BOB", " Not you? Call 1800000000-OTHER BANK")) {
            val result = parse(drCr + suffix)
            assertEquals(ParseDecision.Record, result.decision)
            assertEquals(4250L, result.amountMinor)
            assertEquals(Direction.Debit, result.direction)
            assertEquals(Channel.UPI, result.channel)
            assertEquals("sample.pay-1", result.counterpartyLabel)
        }
    }

    @Test fun card_no_and_using_grammar_tolerates_spacing_line_breaks_and_bank_names() {
        for (bank in listOf("Axis Bank", "Example Bank", "Other Bank")) {
            for (layout in listOf(card, card.replace(" \n", " "), card.replace("\n", "\r\n"))) {
                val result = parse(layout.replace("Example Bank", bank))
                assertEquals(ParseDecision.Record, result.decision)
                assertEquals(4250L, result.amountMinor)
                assertEquals(Direction.Debit, result.direction)
                assertEquals(Channel.Card, result.channel)
                assertEquals(TransactionType.MerchantPayment, result.transactionType)
                assertEquals("••••0000", result.maskedAccountHint)
                assertEquals("SAMPLE SHOP", result.counterpartyLabel)
            }
        }
    }

    @Test fun not_done_by_you_footer_is_not_additional_movement_or_reference_evidence() {
        val text = sent + " Not done by you? Tap https://example.com/fraud. UPI failed INR 900 Ref 000000000002"
        val result = parse(text)
        assertEquals(ParseDecision.Record, result.decision)
        assertEquals(4250L, result.amountMinor)
        assertEquals(TransactionStatus.Successful, result.status)
        assertEquals("000000000001", TransactionReference.extract(text))
        assertEquals(ParseDecision.Reject, parse(text + " OTP 000000").decision)
    }

    @Test fun new_layouts_keep_otp_future_negation_ambiguity_and_origin_guards() {
        for (text in listOf(sent, multiline, drCr, card)) {
            assertEquals(ParseDecision.NeedsReview, parse(text, "9999900000").decision)
            assertEquals(ParseDecision.Reject, parse("OTP 000000 $text").decision)
            assertFalse(parse("If you $text").decision == ParseDecision.Record)
            assertFalse(parse("You have not $text").decision == ParseDecision.Record)
            assertFalse(parse("$text INR 90 also debited").decision == ParseDecision.Record)
            assertFalse(parse("$text Your account will be debited tomorrow").decision == ParseDecision.Record)
        }
    }

    @Test fun sent_requires_a_complete_account_date_reference_and_recipient_layout() {
        for (text in listOf(sent.replace(". UPI Ref 000000000001.", ""),
            sent.replace(" on 21-10-26", ""), sent.replace("A/c X0000", "A/c 123456789012"),
            sent.replace("000000000001", "000000000001X"), sent.replace("000000000001", "000000000001.25"),
            sent.replace("Sample Cafe", "your account credited"), sent.replace("Sample Cafe", ""),
            "Sent Rs.42.50 voucher to your email", multiline.substringBefore("\nOn"))) {
            assertFalse("Incomplete or unsafe message counted as a payment: $text", parse(text).decision == ParseDecision.Record)
        }
    }

    @Test fun foreign_currency_card_purchase_does_not_use_inr_available_limit_as_amount() {
        val result = parse(card.replace("Spent INR 42.50", "Spent USD 42.50"))
        assertEquals(ParseDecision.NeedsReview, result.decision)
        assertNull(result.amountMinor)
        assertEquals(Channel.Card, result.channel)
    }
}

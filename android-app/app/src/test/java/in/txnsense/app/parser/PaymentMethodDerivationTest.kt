package `in`.txnsense.app.parser

import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import org.junit.Assert.*
import org.junit.Test

/** Invented values only. Covers method classification and bank derivation. */
class PaymentMethodDerivationTest {
    private val parser = TemplateEngineParser()
    private fun parse(body: String, sender: String = "TEST") = parser.parse(IncomingSms(sender, 0L, body))

    @Test fun pluxee_spend_is_classified_as_wallet() {
        val result = parse("Rs.42 spent from Pluxee Meal wallet, card no. on 01-01-2026 1:2:3 at TEST SHOP. Avl bal Rs.900.")
        assertEquals(Channel.Wallet, result.channel)
    }

    @Test fun credit_card_repayment_is_classified_as_credit_card() {
        val result = parse("Payment of Rs 500 has been received towards your ICICI Bank Credit Card XX1234")
        assertEquals(Channel.CreditCard, result.channel)
        assertEquals(TransactionType.CardRepayment, result.transactionType)
    }

    @Test fun explicit_credit_card_spend_falls_back_to_credit_card() {
        val result = parse("Rs. 500 spent on your credit card ending 1234")
        assertEquals(Channel.CreditCard, result.channel)
    }

    @Test fun explicit_debit_card_spend_falls_back_to_debit_card() {
        val result = parse("Rs. 500 spent on your debit card ending 1234")
        assertEquals(Channel.DebitCard, result.channel)
    }

    @Test fun net_banking_movement_is_classified_as_net_banking() {
        val result = parse("Rs. 500 debited via Net Banking from your account 1234")
        assertEquals(Channel.NetBanking, result.channel)
    }

    @Test fun generic_card_alert_stays_generic_card() {
        val result = parse("Spent Rs.500 on your Card ending 1234 at TEST SHOP on 2026-01-01")
        assertEquals(Channel.Card, result.channel)
    }

    @Test fun bank_is_derived_from_sender_id() {
        val result = parse("Spent Rs.500 on your Card ending 1234 at TEST SHOP on 2026-01-01", sender = "VM-HDFCBK")
        assertEquals("HDFC", result.bankName)
    }

    @Test fun bank_is_derived_from_body_when_sender_is_opaque() {
        val result = parse("Payment of Rs 500 has been received towards your ICICI Bank Credit Card XX1234")
        assertEquals("ICICI", result.bankName)
    }

    @Test fun generic_card_from_card_only_issuer_is_promoted_to_credit_card() {
        val result = parse("Spent Rs.500 on your SBI Card ending 8613 at TEST SHOP on 2026-01-01", sender = "VM-SBICRD-T")
        assertEquals(Channel.CreditCard, result.channel)
        assertEquals("SBI", result.bankName)
    }

    @Test fun icici_account_statement_debit_is_a_bank_transfer() {
        val result = parse("ICICI Bank Acc XX900 debited Rs. 29.50 on 22-Apr-26 VAT*EDPT2033*. Avb Bal Rs. 900.00.")
        assertEquals(Channel.BankTransfer, result.channel)
        assertEquals(Direction.Debit, result.direction)
    }

    @Test fun rtgs_rail_tag_is_detected_from_info_field() {
        val result = parse("ICICI Bank Acc XX900 debited Rs. 200000.00 on 30-May-26 InfoRTGS*ICICR120. Avl Bal Rs. 900.00.")
        assertEquals(Channel.RTGS, result.channel)
    }

    @Test fun apay_balance_receipt_is_a_wallet() {
        val result = parse("Payment of Rs 172.00 using Apay balance is successful at A.in. Updated balance is Rs 4046.68.")
        assertEquals(Channel.Wallet, result.channel)
    }

    @Test fun rejected_messages_carry_no_bank() {
        val result = parse("Your OTP is 123456", sender = "VM-HDFCBK")
        assertEquals(ParseDecision.Reject, result.decision)
        assertNull(result.bankName)
    }
}

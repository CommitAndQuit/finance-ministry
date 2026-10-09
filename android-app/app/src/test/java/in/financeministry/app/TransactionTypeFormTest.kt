package `in`.financeministry.app

import `in`.financeministry.app.core.model.TransactionType
import `in`.financeministry.app.feature.transactionTypeForSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionTypeFormTest {
    @Test fun ordinary_unknown_payment_does_not_require_a_technical_type() {
        assertEquals(TransactionType.Other, transactionTypeForSave("Unknown", "Debit", "UPI", false))
        assertEquals(TransactionType.Other, transactionTypeForSave("Unknown", "Credit", "NEFT", false))
    }

    @Test fun existing_accounting_types_are_preserved() {
        assertEquals(TransactionType.Refund, transactionTypeForSave("Refund", "Credit", "Card", false))
        assertEquals(TransactionType.Reversal, transactionTypeForSave("Reversal", "Credit", "UPI", false))
        assertEquals(TransactionType.CardRepayment, transactionTypeForSave("CardRepayment", "Credit", "Card", false))
        assertEquals(TransactionType.SelfTransfer, transactionTypeForSave("SelfTransfer", "Debit", "UPI", false))
        assertEquals(TransactionType.SalaryIncome, transactionTypeForSave("SalaryIncome", "Credit", "NEFT", false))
    }

    @Test fun an_unidentified_card_credit_requires_an_accounting_decision() {
        assertThrows(IllegalArgumentException::class.java) { transactionTypeForSave("Unknown", "Credit", "Card", false) }
        assertThrows(IllegalArgumentException::class.java) { transactionTypeForSave("Unknown", "Credit", "UPI", true) }
    }

    @Test fun explicit_other_credit_is_not_silently_changed_to_bill_payment() {
        assertEquals(TransactionType.Other, transactionTypeForSave("Other", "Credit", "Card", true))
    }
}

package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticLabellerTest {

    private val labeller = HeuristicLabeller()

    private fun label(vararg words: String) = labeller.label(words.toList())

    @Test fun a_spend_layout_is_a_settled_debit() {
        val semantics = label("spent", "on", "your", "card", "at", "on")

        assertEquals(Direction.Debit, semantics?.direction)
        assertEquals(TransactionStatus.Successful, semantics?.status)
    }

    @Test fun a_card_name_does_not_reverse_the_direction_of_a_spend() {
        // "Credit card" says which card, not which way the money went.
        assertEquals(Direction.Debit, label("spent", "on", "your", "credit", "card", "at")?.direction)
        assertEquals(Channel.CreditCard, label("spent", "on", "your", "credit", "card", "at")?.channel)
        assertEquals(Channel.DebitCard, label("spent", "on", "your", "debit", "card", "at")?.channel)
    }

    @Test fun an_unqualified_card_alert_keeps_the_generic_channel() {
        assertEquals(Channel.Card, label("spent", "on", "card", "ending", "at")?.channel)
    }

    @Test fun the_named_channel_wins_over_the_card_noun() {
        assertEquals(Channel.UPI, label("debited", "from", "a/c", "via", "upi", "card")?.channel)
        assertEquals(Channel.ATM, label("withdrawn", "at", "atm", "from", "a/c")?.channel)
    }

    @Test fun a_layout_that_says_nothing_about_movement_is_refused() {
        assertNull(label("your", "a/c", "bal", "is", "on"))
    }

    @Test fun a_layout_that_says_both_directions_is_refused() {
        assertNull(label("debited", "and", "credited", "to", "your", "a/c"))
    }

    @Test fun layouts_that_only_talk_about_money_are_refused() {
        assertNull(label("payment", "of", "is", "due", "on"))
        assertNull(label("otp", "for", "payment", "at"))
        assertNull(label("you", "are", "eligible", "for", "a", "loan", "of"))
        assertNull(label("statement", "generated", "for", "your", "card"))
        assertNull(label("mandate", "for", "will", "be", "debited", "on"))
    }

    @Test fun an_unsettled_movement_is_refused_because_a_later_message_decides_it() {
        assertNull(label("debited", "from", "a/c", "initiated"))
    }

    @Test fun a_failed_or_reversed_movement_keeps_its_outcome() {
        assertEquals(TransactionStatus.Failed, label("spent", "on", "card", "declined")?.status)
        assertEquals(TransactionStatus.Reversed, label("debited", "transaction", "reversed", "on")?.status)
        assertEquals(TransactionType.Reversal, label("debited", "transaction", "reversed", "on")?.transactionType)
    }

    @Test fun recognizable_kinds_of_movement_are_named() {
        assertEquals(TransactionType.CashWithdrawal, label("withdrawn", "at", "atm", "from")?.transactionType)
        assertEquals(TransactionType.SalaryIncome, label("salary", "credited", "to", "a/c")?.transactionType)
        assertEquals(TransactionType.Refund, label("refund", "credited", "to", "a/c")?.transactionType)
        assertEquals(TransactionType.FeeCharge, label("fee", "debited", "from", "a/c")?.transactionType)
    }
}

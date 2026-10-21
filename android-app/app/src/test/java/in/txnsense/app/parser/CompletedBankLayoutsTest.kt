package `in`.txnsense.app.parser

import `in`.txnsense.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Independently expected invented fixtures for researched completed-payment grammar. */
@RunWith(Parameterized::class)
class CompletedBankLayoutsTest(private val case: Fixture) {
    data class Fixture(val name: String, val body: String, val amount: Long, val channel: Channel,
        val last4: String, val merchant: String? = null) {
        override fun toString() = name
    }

    @Test fun completed_payment_retains_the_transaction_amount_and_usable_source_evidence() {
        val parsed = RuleBasedFinancialSmsParser().parse(IncomingSms("AD-EXBANK", 1L, case.body))
        assertEquals(ParseDecision.Record, parsed.decision)
        assertEquals(case.amount, parsed.amountMinor)
        assertEquals(Direction.Debit, parsed.direction)
        assertEquals(TransactionStatus.Successful, parsed.status)
        assertEquals(case.channel, parsed.channel)
        assertEquals("••••${case.last4}", parsed.maskedAccountHint)
        case.merchant?.let { assertEquals(it, parsed.counterpartyLabel) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun fixtures(): List<Array<Fixture>> = listOf(
            Fixture("debit by transfer", "Dear Customer, Your A/C XXXXX912468 has a debit by transfer of Rs 218.70 on 19/09/26. Avl Bal Rs 900.00.-SBI", 21870, Channel.Unknown, "2468"),
            Fixture("UPI debit prefix", "Debit INR 319.25 A/c no. XX2468 on 19-09-26. Info- UPI/P2M/123450009876/SAMPLE CAFE. Avl Bal- INR 8,100.00", 31925, Channel.UPI, "2468", "SAMPLE CAFE"),
            Fixture("single-line sent", "Sent Rs.219.40 from Kotak Bank AC X2468 to samplecafe@ybl on 19-09-26. UPI Ref 123450009876.", 21940, Channel.UPI, "2468", "samplecafe"),
            Fixture("colon currency", "A/c *2468 Debited for Rs:61.25 on 19-09-2026 19:44:40 by Mob Bk ref no 123450009876 Avl Bal Rs:2810.55. -Union Bank", 6125, Channel.Unknown, "2468"),
            Fixture("account deducted", "An amount of Rs 619 has been deducted from your account ending with 2468 on 19-09-26.", 61900, Channel.Unknown, "2468"),
            Fixture("NetBanking paid", "Thanks for paying Rs.2,160.00 from A/c XXXX2468 to SAMPLE TAX via HDFC Bank NetBanking. Please contact the bank if this was not you.", 216000, Channel.BankTransfer, "2468", "SAMPLE TAX"),
            Fixture("debit card used", "Thank you for using your HDFC Bank Debit Card ending 2468 for INR 819.25 towards SAMPLE MART in Bengaluru at 2026-09-19 18:20:00.", 81925, Channel.Card, "2468", "SAMPLE MART"),
            Fixture("verb-first card", "Spent Card no. XX2468 INR 591.40 19-09-26 20:15:32 SAMPLE CAFE Avl Lmt INR 65,000. Not you? Report it through the bank app.", 59140, Channel.Card, "2468", "SAMPLE CAFE"),
            Fixture("card has been spent", "Rs.651.50 has been spent on your Card XX2468 at SAMPLE MART.", 65150, Channel.Card, "2468", "SAMPLE MART"),
            Fixture("UPI paid prefix", "Rs.517.25 paid to samplecafe@paytm from A/c XX2468 on 19-09-26. UPI Ref No 123450009876.", 51725, Channel.UPI, "2468", "samplecafe")
        ).map { arrayOf(it) }
    }
}

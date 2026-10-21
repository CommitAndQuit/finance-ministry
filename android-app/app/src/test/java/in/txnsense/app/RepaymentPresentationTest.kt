package `in`.txnsense.app

import `in`.txnsense.app.data.*
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepaymentPresentationTest {
    private fun row(status: String = "Successful", review: String = "Confirmed", direction: String = "Debit") =
        TransactionEntity(id = "payment", sourceType = "Manual", sourceTimestamp = 1, effectiveTimestamp = 1,
            amountMinor = 30_000, direction = direction, status = status, channel = "UPI", transactionType = "Other",
            reviewState = review, createdAt = 1, updatedAt = 1, ownership = "Group", personalShareMinor = 10_000)

    @Test fun debt_wording_matches_financial_eligibility() {
        assertEquals("Still owed" to 20_000L, repaymentSummary(row()))
        assertEquals("Potentially owed after review" to 20_000L, repaymentSummary(row(review = "NeedsReview")))
        assertEquals("Potentially owed after review" to 20_000L, repaymentSummary(row(status = "Pending")))
        assertNull(repaymentSummary(row(status = "Failed")))
        assertNull(repaymentSummary(row(status = "Reversed")))
        assertNull(repaymentSummary(row(direction = "Credit")))
        assertNull(repaymentSummary(row(), setOf("payment")))
    }

    // Losing a settled share from a person's detail would hide received money.
    @Test fun person_balance_keeps_settled_expenses_in_outstanding_person_drilldown() {
        val dinner = row().copy(id = "dinner", repaidMinor = 5_000)
        val lunch = row().copy(id = "lunch", repaidMinor = 20_000)
        val repayments = listOf(
            RepaymentEntity("partial", "dinner", "credit1", 5_000, 1, "Alice", "Linked", 1, "alice"),
            RepaymentEntity("settled", "lunch", "credit2", 20_000, 1, "Alice", "Linked", 1, "alice"))
        val summary = RepaymentSummary(BigInteger.valueOf(15_000), BigInteger.valueOf(40_000),
            BigInteger.valueOf(25_000), BigInteger.valueOf(15_000), listOf(dinner, lunch), repayments,
            listOf(GroupShareEntity("s1", "dinner", "alice", 20_000), GroupShareEntity("s2", "lunch", "alice", 20_000)),
            listOf(LocalMemberEntity("alice", "Alice", 1)))
        val alice = RepaymentAccounting.people(summary).single()
        assertEquals("Alice", alice.name)
        assertEquals("15000", alice.outstanding.toString())
        assertEquals("25000", alice.received.toString())
        assertEquals(2, alice.expenses.size)
    }

    // Dropping legacy rows once any named split exists would understate all-date balances.
    @Test fun unnamed_legacy_balances_remain_visible_beside_named_people() {
        val legacy = row().copy(id = "legacy", groupLabel = null, repaidMinor = 1_001)
        val summary = RepaymentSummary(BigInteger.valueOf(18_999), BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.ZERO, listOf(legacy), emptyList())
        val balance = RepaymentAccounting.people(summary).single()
        assertEquals("Unassigned", balance.name)
        assertEquals("18999", balance.outstanding.toString())
        assertEquals("1001", balance.received.toString())
    }

    // Ranking by amount alone would choose an unrelated receipt over corroborating label/date evidence.
    @Test fun possible_matches_rank_name_amount_and_date_without_settling_anything() {
        val expense = row().copy(effectiveTimestamp = 86_400_000, counterpartyLabel = "Dinner")
        fun credit(id: String, name: String, amount: Long, time: Long) = expense.copy(id = id,
            direction = "Credit", ownership = "Personal", counterpartyLabel = name,
            amountMinor = amount, effectiveTimestamp = time)
        val best = credit("best", "Alice repayment", 20_000, 172_800_000)
        val sameAmount = credit("unrelated", "Salary", 20_000, 90L * 86_400_000)
        val cashPartial = credit("partial", "Alice", 10_000, 172_800_000)
        val matches = RepaymentAccounting.rankCredits(expense, "Alice", 20_000,
            listOf(sameAmount to 20_000, cashPartial to 10_000, best to 20_000))
        assertEquals(listOf("best", "partial", "unrelated"), matches.map { it.row.id })
        assertEquals(listOf("Name matches Alice", "Exact unpaid amount", "Received within 30 days of this expense"), matches.first().reasons)
        assertEquals(0L, expense.repaidMinor)
    }

    // A refund, uncertain receipt or zero available paise must never be offered as a settlement candidate.
    @Test fun possible_matches_exclude_ineligible_receipts_and_use_unallocated_paise() {
        val expense = row()
        val credit = row(direction = "Credit").copy(ownership = "Personal", counterpartyLabel = "Alice", amountMinor = 30_000)
        val matches = RepaymentAccounting.rankCredits(expense, "Alice", 20_000, listOf(
            credit.copy(id = "allocated") to 20_000,
            credit.copy(id = "exhausted") to 0,
            credit.copy(id = "review", reviewState = "NeedsReview") to 20_000,
            credit.copy(id = "refund", transactionType = "Refund") to 20_000))
        assertEquals(listOf("allocated"), matches.map { it.row.id })
        assertEquals(20_000L, matches.single().availableMinor)
        assertEquals(true, "Exact unpaid amount" in matches.single().reasons)
    }
}

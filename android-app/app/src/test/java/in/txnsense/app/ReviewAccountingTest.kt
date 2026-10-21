package `in`.txnsense.app

import `in`.txnsense.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReviewAccountingTest {
    private val month=LocalDate.of(2026,10,1)
    private fun row(id:String="payment", review:String="Confirmed", direction:String="Debit") =
        TransactionEntity(id=id,sourceType="SMS",sourceTimestamp=month.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            effectiveTimestamp=month.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),amountMinor=4200,
            direction=direction,status="Successful",channel="UPI",transactionType="Other",reviewState=review,createdAt=1,updatedAt=1)

    @Test fun dismissed_records_never_create_spending_debt_or_settlement_suggestions() {
        val expense=row(review="Dismissed").copy(ownership="ForOther",personalShareMinor=0)
        assertFalse(RepaymentAccounting.eligible(expense))
        assertEquals(0L,RepaymentAccounting.principal(expense))
        assertEquals(0L,RepaymentAccounting.owed(expense))
        val credit=row("receipt","Dismissed","Credit")
        assertTrue(RepaymentAccounting.rankCredits(row(),"Alice",4200,listOf(credit to 4200L)).isEmpty())
        assertNull(repaymentSummary(expense))
    }

    @Test fun dismissed_reversal_and_refund_cannot_change_a_confirmed_originals_breakdown() {
        val original=row()
        val reversal=row("reversal","Dismissed").copy(status="Reversed",transactionType="Reversal",linkedOriginalId=original.id)
        val refund=row("refund","Dismissed","Credit").copy(transactionType="Refund")
        val insight=SpendingInsights.calculate(listOf(original,reversal,refund),month,month.plusDays(10),ZoneOffset.UTC)
        assertEquals("4200",insight.total.toString())
        assertEquals("0",insight.refundsReceived.toString())
        assertEquals(listOf("payment"),insight.transactions.map {it.id})
    }
}

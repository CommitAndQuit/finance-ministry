package `in`.financeministry.app

import `in`.financeministry.app.data.TransactionEntity
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
}

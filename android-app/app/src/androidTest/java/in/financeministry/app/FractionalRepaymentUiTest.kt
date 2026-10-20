package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.RepaymentEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FractionalRepaymentUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cash_repayment_without_leading_zero_settles_eighty_paise() = settle(linked = false)

    @Test fun linked_fractional_repayment_and_edits_keep_existing_receipt_unchanged() = settle(linked = true)

    private fun settle(linked: Boolean) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "fractional_repay_${UUID.randomUUID()}")
        try {
            val now = System.currentTimeMillis()
            val expenseId = runBlocking { repo.save(ManualInput("0.80", Direction.Debit, now,
                TransactionType.Other, label = "Demo expense", ownership = SpendingOwnership.ForOther, groupLabel = "Friend")) }
            val creditId = if (linked) runBlocking { repo.save(ManualInput("1.00", Direction.Credit, now,
                TransactionType.Other, label = "Demo repayment")) } else null
            val expense = runBlocking { repo.get(expenseId)!! }
            val open = mutableStateOf(true)
            rule.setContent { FinanceMinistryTheme { Surface {
                if (open.value) RepaymentEditor(repo, expense, null, onDismiss = { open.value = false })
            } } }
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Received cash") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            if (linked) rule.onNodeWithText("Demo repayment").performScrollTo().performClick()
            else rule.onNodeWithText("Received cash").performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performScrollTo().performTextReplacement(".80")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Still owed after saving: ₹0.00").assertExists()
            rule.onNodeWithText(if (linked) "Confirm & link repayment" else "Record cash repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { !open.value }
            val repayment = runBlocking { repo.repaymentsFor(expenseId).single() }
            assertEquals(80L, repayment.amountMinor)
            assertEquals(0L, runBlocking { RepaymentAccounting.owed(repo.get(expenseId)!!) })
            assertEquals(if (linked) 100L else 80L, runBlocking { repo.snapshot().credit.toLong() })
            if (linked) {
                assertEquals(creditId, repayment.incomingId)
                runBlocking { repo.editRepayment(repayment.id, ".40", repayment.receivedAt, repayment.payer) }
                assertEquals(40L, runBlocking { repo.repaymentsFor(expenseId).single().amountMinor })
                assertEquals(40L, runBlocking { RepaymentAccounting.owed(repo.get(expenseId)!!) })
                assertEquals(100L, runBlocking { repo.get(creditId!!)!!.amountMinor })
                runBlocking { repo.editRepayment(repayment.id, ".8", repayment.receivedAt, repayment.payer) }
                assertEquals(0L, runBlocking { RepaymentAccounting.owed(repo.get(expenseId)!!) })
                assertEquals(100L, runBlocking { repo.snapshot().credit.toLong() })
            } else {
                assertNotNull(repayment.incomingId)
                assertEquals(80L, runBlocking { repo.get(repayment.incomingId!!)!!.amountMinor })
            }
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}

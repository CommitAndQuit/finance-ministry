package `in`.txnsense.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import `in`.txnsense.app.ui.LocalLedgerTheme
import `in`.txnsense.app.ui.MonthlyMathSheet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real encrypted DB read failure, never the main ledger and no mocked loader. */
class MonthlyBreakdownFailureUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // An uncaught refunds-load exception or a Retry that does not really reload
    // the repository must fail; the stored records must survive unchanged.
    @Test fun failed_refund_load_keeps_breakdown_open_and_retry_reads_recovered_storage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefix = "math_fault_${UUID.randomUUID()}"
        val r = TransactionRepository(app, prefix)
        val databaseFile = app.getDatabasePath("$prefix.db")
        var originalBytes: ByteArray? = null
        try {
            runBlocking {
                r.save(ManualInput("100", Direction.Debit, System.currentTimeMillis(), TransactionType.Other, label = "Synthetic expense", category = "Food"))
                r.save(ManualInput("15", Direction.Credit, System.currentTimeMillis(), TransactionType.Refund, label = "Synthetic refund", category = "Food"))
            }
            val snapshot = runBlocking { r.snapshot() }
            assertEquals(10000L, snapshot.personalSpend.toLong())
            assertEquals(1500L, snapshot.credit.toLong())
            runBlocking { r.close() }
            assertEquals("$prefix.db", databaseFile.name)
            originalBytes = databaseFile.readBytes()
            assertTrue(originalBytes.size > 512)
            // Only this test's closed, randomly namespaced synthetic file is
            // replaced. The encrypted original is retained in memory to restore.
            databaseFile.writeBytes("synthetic unreadable database".toByteArray())
            var dismissed = false
            rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
            rule.setContent { LocalLedgerTheme(r.preferences) {
                MonthlyMathSheet(r, snapshot, onDismiss = { dismissed = true }, onCategories = {})
            } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Could not load refunds. Your other totals are unchanged.").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Could not load refunds. Your other totals are unchanged.").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Got it").assertIsDisplayed()
            assertFalse(dismissed)

            runBlocking { r.close() }
            databaseFile.writeBytes(originalBytes)
            rule.onNodeWithText("Retry refunds").performScrollTo().performClick()
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Refunds received") and hasText("₹15")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("Refunds received") and hasText("₹15")).assertIsDisplayed()
            assertEquals(snapshot.rows, runBlocking { r.snapshot().rows })
            assertEquals(10000L, runBlocking { r.snapshot().personalSpend.toLong() })
            assertEquals(1500L, runBlocking { r.snapshot().credit.toLong() })
            rule.onNodeWithText("Got it").performClick()
            assertTrue(dismissed)
        } finally {
            runBlocking { r.close() }
            originalBytes?.let { databaseFile.writeBytes(it) }
            runBlocking { r.eraseAll(); r.close() }
        }
    }
}

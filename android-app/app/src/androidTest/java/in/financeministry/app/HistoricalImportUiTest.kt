package `in`.financeministry.app

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.HistoricalImportPanel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class HistoricalImportUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun repair_requires_confirmation_and_updates_existing_record_without_counting_it() = repairFlow(false)

    @Test fun field_only_repair_requires_confirmation_and_preserves_counted_totals() = repairFlow(true)

    private fun repairFlow(fieldOnly: Boolean) {
        val context = rule.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val repository = TransactionRepository(context, "import_repair_ui_${UUID.randomUUID()}")
        val now = System.currentTimeMillis()
        val body = if (fieldOnly) "Sent Rs.42.50\nFrom Example Bank A/C *0000\nTo sample.pay-1@okexample\nOn 18/10/26\nRef 000000000001"
            else "Dear UPI user A/C X0000 debited by 42.50 on date 18Sep26 trf to SAMPLE SHOP Refno 000000000001"
        val sms = HistoricalSms("AD-EXBANK", now - 1000, 0, body)
        val source = HistoricalSmsSource { _, emit -> emit(sms) }
        try {
            val id = runBlocking {
                repository.commitImport(repository.previewImport(source, now))
                val old = repository.snapshot().rows.single()
                repository.withLedger { it.transactions().update(old.copy(amountMinor = if (fieldOnly) 4250L else null,
                    currency = if (fieldOnly) "INR" else null, channel = "Unknown", counterpartyLabel = null,
                    reviewState = if (fieldOnly) "AutoRecorded" else "NeedsReview", parserVersion = 12)) }
                old.id
            }
            rule.activity.runOnUiThread {
                rule.activity.setContent { MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { HistoricalImportPanel(repository, source) }
                } }
            }
            rule.onNodeWithText("Import last 3 months").performScrollTo().performClick()
            rule.onNodeWithText("I understand — scan").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Import 0 and repair 1").fetchSemanticsNodes().isNotEmpty() }
            assertNull(runBlocking { repository.get(id) }!!.counterpartyLabel)
            assertEquals(if (fieldOnly) 4250L else null, runBlocking { repository.get(id) }!!.amountMinor)
            rule.onNodeWithText("Import 0 and repair 1").performScrollTo().performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Repaired 1 existing records", substring = true).fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                assertEquals(4250L, repository.get(id)!!.amountMinor)
                assertEquals(if (fieldOnly) "AutoRecorded" else "NeedsReview", repository.get(id)!!.reviewState)
                assertEquals(if (fieldOnly) "sample.pay-1" else "SAMPLE SHOP", repository.get(id)!!.counterpartyLabel)
                assertEquals(1, repository.snapshot().rows.size)
                assertEquals(if (fieldOnly) "4250" else "0", repository.snapshot().debit.toString())
            }
        } finally { runBlocking { repository.eraseAll(); repository.close() } }
    }

    @Test fun erase_clears_unconfirmed_preview() {
        val context = rule.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val repository = TransactionRepository(context, "import_ui_${UUID.randomUUID()}")
        try {
            rule.activity.runOnUiThread {
                rule.activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            HistoricalImportPanel(repository, HistoricalSmsSource { _, emit ->
                                emit(HistoricalSms("TEST", System.currentTimeMillis() - 1000, 0, "INR 42 debited via UPI"))
                            })
                        }
                    }
                }
            }
            rule.onNodeWithText("Import last 3 months").performClick()
            rule.onNodeWithText("I understand — scan").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Import preview").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { repository.eraseAll() }
            rule.waitUntil(5000) { rule.onAllNodesWithText("Import preview").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("Import 1 transactions").assertDoesNotExist()
        } finally { runBlocking { repository.eraseAll(); repository.close() } }
    }
}

package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Pending records never enter totals; dismissal retains evidence and can be reversed. */
class PublicReviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun seed(): Pair<TransactionRepository, TransactionEntity> {
        val r=TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "public_review_${UUID.randomUUID()}")
        val now=System.currentTimeMillis()
        val row=TransactionEntity("uncertain", sourceType="SMS", sourceTimestamp=now, effectiveTimestamp=now,
            amountMinor=47000, direction="Debit", status="Successful", channel="Card", transactionType="MerchantPayment",
            counterpartyLabel="Card payment", reviewState="NeedsReview", createdAt=now, updatedAt=now)
        runBlocking {r.withLedger {it.transactions().insert(row)}}
        r.preferences.edit().putBoolean("onboarding_complete",true).putInt("app_tour_seen_version",BuildConfig.VERSION_CODE).commit()
        rule.runOnUiThread {rule.activity.enableEdgeToEdge()}
        rule.setContent {FinanceMinistryTheme {LedgerApp(r,null,consumeRequest={})}}
        rule.waitUntil(15000) {rule.onAllNodesWithContentDescription("Open review tab").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("Open review tab").performClick()
        return r to row
    }
    @Test fun actionable_review_confirms_one_payment_and_updates_totals_once() {
        val (r,row)=seed()
        try {
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            rule.onNodeWithText("Details need checking").assertIsDisplayed()
            rule.onNodeWithText("₹470.00").assertIsDisplayed()
            capture("review-pending-light.png")
            rule.onNodeWithText("Check & confirm").performClick()
            rule.onNodeWithText("Check this payment").assertIsDisplayed()
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="Confirmed"}}
            assertEquals(47000L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals(47000L,runBlocking {r.snapshot().personalSpend.toLong()})
            assertEquals(0,runBlocking {r.reviewCount()})
            assertEquals(1,runBlocking {r.snapshot().rows.size})
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    @Test fun not_a_transaction_preserves_record_and_can_return_to_review() {
        val (r,row)=seed()
        try {
            rule.onNodeWithText("Not a transaction").performClick()
            rule.onNodeWithText("Exclude this alert?").assertIsDisplayed()
            rule.onNodeWithText("Keep in review").performClick()
            assertEquals("NeedsReview",runBlocking {r.get(row.id)!!.reviewState})
            rule.onNodeWithText("Not a transaction").performClick()
            rule.onNodeWithText("Exclude alert").performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="Dismissed"}}
            assertEquals(0,runBlocking {r.reviewCount()})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals(0L,runBlocking {r.snapshot().resultDebit.toLong()})
            assertEquals(0L,runBlocking {r.snapshot().personalSpend.toLong()})
            assertEquals(1,runBlocking {r.snapshot().rows.size})
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Card payment").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Card payment").performTouchInput {longClick()}
            rule.onNodeWithText("Edit / confirm").assertDoesNotExist()
            rule.onNodeWithText("Category & purpose").assertDoesNotExist()
            rule.onNodeWithText("Mark as card bill payment").assertDoesNotExist()
            rule.onNodeWithText("Restore to review").performScrollTo().performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="NeedsReview"}}
            assertEquals(1,runBlocking {r.reviewCount()})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals("Successful",runBlocking {r.get(row.id)!!.status})
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    @Test fun old_editor_cannot_confirm_after_an_alert_is_excluded_then_restored() {
        val (r,row)=seed()
        try {
            rule.onNodeWithText("Check & confirm").performClick()
            runBlocking {
                r.dismissReview(row.id,row.updatedAt)
                val excluded=r.get(row.id)!!
                r.restoreDismissedReview(row.id,excluded.updatedAt)
            }
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) {
                runBlocking {r.get(row.id)?.reviewState=="Confirmed"} ||
                    rule.onAllNodesWithText("This record changed. Reopen it before saving.").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("NeedsReview",runBlocking {r.get(row.id)!!.reviewState})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            rule.onNodeWithText("This record changed. Reopen it before saving.").assertIsDisplayed()
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    private fun capture(name:String) {
        rule.waitForIdle();rule.mainClock.advanceTimeBy(500);rule.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation()
        val variant=InstrumentationRegistry.getArguments().getString("visualVariant")
            ?.takeIf {it in setOf("light","dark","compact-light","compact-dark","large-text-light","large-text-dark")} ?: "light"
        val f=File(i.targetContext.getExternalFilesDir("public-ui"),name.replace("-light.png","-$variant.png"))
        f.outputStream().use {check(i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
}

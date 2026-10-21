package `in`.txnsense.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.data.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real encrypted ingestion and totals; no personal inbox reads or mock parser. */
class OriginAndCardReceiptIntegrationTest {
    private fun repository(): TransactionRepository {
        val app = ApplicationProvider.getApplicationContext<Application>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECEIVE_SMS)
        return TransactionRepository(app, "origin_card_${UUID.randomUUID()}").also {
            it.preferences.edit().putBoolean("sms_disclosure", true).commit()
        }
    }

    @Test fun card_confirmation_is_excluded_while_genuine_cashback_is_money_in() = runBlocking {
        val r = repository()
        try {
            val now = System.currentTimeMillis()
            assertTrue(r.ingest(IncomingSms("AD-EXBANK", now,
                "Your payment of INR 782.40 has been credited to your Example Bank Credit Card ending with 2468. Available limit INR 22,111.00.")))
            val bill = r.snapshot().rows.single()
            assertEquals(78240L, bill.amountMinor)
            assertEquals("CardRepayment", bill.transactionType)
            assertEquals("Credit", bill.direction)
            assertEquals("AutoRecorded", bill.reviewState)
            assertEquals(0L, r.snapshot().credit.toLong())
            assertEquals(0L, r.snapshot().debit.toLong())
            assertEquals(0L, r.snapshot().personalSpend.toLong())
            assertTrue(r.ingest(IncomingSms("WALLET", now + 1,
                "INR 49.00 cashback credited to your sample wallet. Balance INR 4,099.00.")))
            assertEquals(2, r.snapshot().rows.size)
            assertEquals(4900L, r.snapshot().credit.toLong())
            assertEquals(0L, r.snapshot().personalSpend.toLong())
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun numeric_origin_keeps_reliable_fields_for_review_without_adding_to_totals() = runBlocking {
        val r = repository()
        try {
            val now = System.currentTimeMillis()
            assertTrue(r.ingest(IncomingSms("9999900001", now, "INR 641.25 debited from A/c XX2468 via UPI.")))
            val row = r.snapshot().rows.single()
            assertEquals("SMS", row.sourceType)
            assertEquals(64125L, row.amountMinor)
            assertEquals("Debit", row.direction)
            assertEquals("Successful", row.status)
            assertEquals("UPI", row.channel)
            assertEquals("NeedsReview", row.reviewState)
            assertEquals(0L, r.snapshot().debit.toLong())
            assertEquals(0L, r.snapshot().credit.toLong())
            assertEquals(0L, r.snapshot().personalSpend.toLong())
            assertFalse(row.isUserCorrected)
        } finally { r.eraseAll(); r.close() }
    }
}

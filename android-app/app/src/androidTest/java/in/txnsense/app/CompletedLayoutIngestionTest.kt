package `in`.txnsense.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Parser-only recall must not be used as proof of stored totals or source mapping. */
class CompletedLayoutIngestionTest {
    private fun withLedger(check: suspend (TransactionRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        val repo = TransactionRepository(context, "completed_layout_${UUID.randomUUID()}")
        try { repo.preferences.edit().putBoolean("sms_disclosure", true).commit(); check(repo) }
        finally { repo.eraseAll(); repo.close() }
    }

    @Test fun completed_upi_prefix_maps_registered_source_and_counts_the_payment_once() = withLedger { repo ->
        val source = repo.addPaymentSource("Everyday UPI", "UPI", Channel.UPI, "Axis", "2468")
        val sms = IncomingSms("AD-AXISBK", System.currentTimeMillis(),
            "Debit INR 39.25 A/c no. XX2468 on 19-09-26. Info- UPI/P2M/123450009876/SAMPLE CAFE. Avl Bal- INR 8,100.00")
        assertTrue(repo.ingest(sms))
        val row = repo.snapshot().rows.single()
        assertEquals("AutoRecorded", row.reviewState)
        assertEquals("••••2468", row.maskedAccountHint)
        assertEquals(source.id, row.paymentSourceId)
        assertEquals("SAMPLE CAFE", row.counterpartyLabel)
        assertEquals(3925L, row.amountMinor)
        assertFalse(repo.ingest(sms))
        assertEquals(1L, repo.snapshot().resultCount)
        assertEquals(3925L, repo.snapshot().debit.toLong())
        assertEquals(3925L, repo.snapshot().personalSpend.toLong())
        assertEquals(0L, repo.snapshot().credit.toLong())
    }

    @Test fun a_new_card_layout_maps_the_card_without_counting_its_available_limit() = withLedger { repo ->
        val source = repo.addPaymentSource("Everyday card", "Credit card", Channel.Card, "Axis", "2468")
        assertTrue(repo.ingest(IncomingSms("AD-AXISBK", System.currentTimeMillis(),
            "Spent Card no. XX2468 INR 78.50 19-09-26 20:15:32 SAMPLE CAFE Avl Lmt INR 65,000. Not you? Report it through the bank app.")))
        val row = repo.snapshot().rows.single()
        assertEquals(source.id, row.paymentSourceId)
        assertEquals("MerchantPayment", row.transactionType)
        assertEquals("AutoRecorded", row.reviewState)
        assertEquals("SAMPLE CAFE", row.counterpartyLabel)
        assertEquals(7850L, repo.snapshot().debit.toLong())
        assertEquals(7850L, repo.snapshot().personalSpend.toLong())
    }

    @Test fun received_one_card_refund_is_saved_for_review_without_inventing_a_purchase_link() = withLedger { repo ->
        val sms = IncomingSms("AD-EXBANK", System.currentTimeMillis(),
            "Refund of Rs.42.19 from DEMO.ae received on your Federal Bank  One Credit Card. This will be adjusted against your outstanding dues. Check details: https://example.com/refund")
        assertTrue(repo.ingest(sms))
        val row = repo.snapshot().rows.single()
        assertEquals(4219L, row.amountMinor)
        assertEquals("Credit", row.direction)
        assertEquals("Successful", row.status)
        assertEquals("Card", row.channel)
        assertEquals("Refund", row.transactionType)
        assertEquals("DEMO.ae", row.counterpartyLabel)
        assertEquals("NeedsReview", row.reviewState)
        assertNull(row.linkedOriginalId)
        assertNull(row.maskedAccountHint)
        assertFalse(repo.ingest(sms))
        assertEquals(1, repo.reviewCount())
        assertEquals(0L, repo.snapshot().credit.toLong())
        assertEquals(0L, repo.snapshot().personalSpend.toLong())
    }

    @Test fun merchant_confirmation_and_wallet_funding_are_stored_for_review_without_changing_totals() = withLedger { repo ->
        val time = System.currentTimeMillis()
        assertTrue(repo.ingest(IncomingSms("AD-EXBANK", time,
            "Payment Confirmed! Your Sample Cafe Order #DEMO2468 of Rs.415.5 was successfully paid on 19/09/2026 at 10:38 PM. Txn ID: 123450009876.")))
        assertTrue(repo.ingest(IncomingSms("AD-EXBANK", time + 1,
            "Rs.819.00 received in your Paytm wallet from A/c XX2468. Wallet balance Rs.2,700.00.")))
        val snapshot = repo.snapshot()
        assertEquals(2, snapshot.rows.size)
        assertEquals(setOf(41550L, 81900L), snapshot.rows.map { it.amountMinor }.toSet())
        assertEquals(setOf("NeedsReview"), snapshot.rows.map { it.reviewState }.toSet())
        assertEquals(0L, snapshot.debit.toLong())
        assertEquals(0L, snapshot.credit.toLong())
        assertEquals(0L, snapshot.personalSpend.toLong())
        assertEquals(2, repo.reviewCount())
    }
}

package `in`.financeministry.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.util.UUID

class ReviewRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Application>()
    private fun repo()=TransactionRepository(context,"review_repository_${UUID.randomUUID()}")
    private suspend fun pending(r:TransactionRepository):Pair<IncomingSms,TransactionEntity> {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName,Manifest.permission.RECEIVE_SMS)
        automation.grantRuntimePermission(context.packageName,Manifest.permission.READ_SMS)
        r.preferences.edit().putBoolean("sms_disclosure",true).commit()
        // An unmatched refund genuinely enters Review. A complete debit alert is
        // intentionally auto-recorded, even with an unfamiliar sender header.
        val sms=IncomingSms("AD-HDFCBK",System.currentTimeMillis()-100,"INR 42 refund credited via UPI account XX0000 UPI:000000000001")
        assertTrue(r.ingest(sms))
        val row=r.snapshot().rows.single()
        assertEquals("NeedsReview",row.reviewState)
        return sms to row
    }
    private suspend fun rejected(block:suspend ()->Unit) {
        try {block();fail("Expected stale or invalid Review transition to be rejected")}
        catch(_:IllegalArgumentException) {}
    }
    @Test fun dismissal_retains_evidence_audit_and_deduplication_after_reopening()=runBlocking {
        val r=repo()
        try {
            val (sms,old)=pending(r)
            r.dismissReview(old.id,old.updatedAt)
            r.close()
            val excluded=r.get(old.id)!!
            assertEquals("Dismissed",excluded.reviewState)
            assertEquals("Successful",excluded.status)
            assertArrayEquals(old.sourceFingerprint,excluded.sourceFingerprint)
            assertArrayEquals(old.referenceHash,excluded.referenceHash)
            assertFalse(r.ingest(sms))
            assertEquals(1,r.snapshot().rows.size)
            assertEquals(0L,r.snapshot().debit.toLong())
            assertEquals(0L,r.snapshot().resultDebit.toLong())
            assertEquals(0L,r.snapshot().credit.toLong())
            assertEquals(0L,r.snapshot().resultCredit.toLong())
            assertEquals(0,r.reviewCount())
            assertTrue(r.csvReport(1,Long.MAX_VALUE).contains("Not a transaction"))
            r.restoreDismissedReview(old.id,excluded.updatedAt)
            assertEquals(1,r.reviewCount())
            assertEquals(0L,r.snapshot().debit.toLong())
            assertEquals(listOf("Dismissed","NeedsReview"),r.withLedger {it.transactions().corrections(old.id)}.sortedBy {it.correctedAt}.map {it.newValue})
        } finally {r.eraseAll();r.close()}
    }
    @Test fun stale_drafts_cannot_dismiss_or_restore_a_later_decision()=runBlocking {
        val r=repo()
        try {
            val (_,old)=pending(r)
            r.classify(old.id,"Travel",SpendingOwnership.Personal,"","","",true)
            val edited=r.get(old.id)!!
            assertTrue(edited.updatedAt>old.updatedAt)
            rejected {r.dismissReview(old.id,old.updatedAt)}
            assertEquals("NeedsReview",r.get(old.id)!!.reviewState)
            r.dismissReview(old.id,edited.updatedAt)
            rejected {r.restoreDismissedReview(old.id,edited.updatedAt)}
            assertEquals("Dismissed",r.get(old.id)!!.reviewState)
        } finally {r.eraseAll();r.close()}
    }
    @Test fun confirmed_money_cannot_be_dismissed_through_pending_review_actions()=runBlocking {
        val r=repo()
        try {
            val id=r.save(ManualInput("42",Direction.Debit,System.currentTimeMillis(),TransactionType.Other))
            val old=r.get(id)!!
            rejected {r.dismissReview(id,old.updatedAt)}
            rejected {r.restoreDismissedReview(id,old.updatedAt)}
            assertEquals("Confirmed",r.get(id)!!.reviewState)
            assertEquals(4200L,r.snapshot().debit.toLong())
        } finally {r.eraseAll();r.close()}
    }
    @Test fun full_editor_cannot_confirm_an_alert_excluded_after_the_editor_opened()=runBlocking {
        val r=repo()
        try {
            val (_,draft)=pending(r)
            r.dismissReview(draft.id,draft.updatedAt)
            rejected {r.save(ManualInput("42",Direction.Credit,draft.effectiveTimestamp,TransactionType.Other),draft.id)}
            assertEquals("Dismissed",r.get(draft.id)!!.reviewState)
            assertEquals(0L,r.snapshot().credit.toLong())
            assertEquals(0,r.reviewCount())
        } finally {r.eraseAll();r.close()}
    }
    @Test fun classification_cannot_change_an_excluded_alert()=runBlocking {
        val r=repo()
        try {
            val (_,draft)=pending(r)
            r.dismissReview(draft.id,draft.updatedAt)
            val excluded=r.get(draft.id)!!
            val corrections=r.withLedger {it.transactions().corrections(draft.id)}
            rejected {r.classify(draft.id,"Travel",SpendingOwnership.Personal)}
            val after=r.get(draft.id)!!
            assertEquals(excluded.copy(sourceFingerprint=null,referenceHash=null),after.copy(sourceFingerprint=null,referenceHash=null))
            assertArrayEquals(excluded.sourceFingerprint,after.sourceFingerprint)
            assertArrayEquals(excluded.referenceHash,after.referenceHash)
            assertEquals(corrections,r.withLedger {it.transactions().corrections(draft.id)})
            assertEquals(0L,r.snapshot().credit.toLong())
        } finally {r.eraseAll();r.close()}
    }
    @Test fun backup_rejects_excluded_expenses_with_retained_repayment_links()=runBlocking {
        val r=repo();val restored=repo()
        try {
            val id=r.save(ManualInput("42",Direction.Debit,System.currentTimeMillis(),TransactionType.Other,
                ownership=SpendingOwnership.ForOther,groupLabel="Alice"))
            r.recordRepayment(id,"10",System.currentTimeMillis(),"Alice")
            val password="long-test-only-password".toCharArray()
            val plain=BackupCipher.decrypt(r.createEncryptedBackup(password),password)
            val root=try {JSONObject(String(plain,Charsets.UTF_8))} finally {plain.fill(0)}
            val rows=root.getJSONObject("data").getJSONObject("tables").getJSONArray("transactions")
            for(i in 0 until rows.length()) if(rows.getJSONObject(i).getString("id")==id) rows.getJSONObject(i).put("reviewState","Dismissed")
            val invalid=BackupCipher.encrypt(root.toString().toByteArray(Charsets.UTF_8),password)
            rejected {restored.previewBackup(invalid,password)}
            assertTrue(restored.snapshot().rows.isEmpty())
            assertEquals(1000L,r.snapshot().credit.toLong())
            assertEquals(1,r.repaymentsFor(id).size)
        } finally {r.eraseAll();r.close();restored.eraseAll();restored.close()}
    }
    @Test fun encrypted_backup_keeps_dismissal_excluded_and_reversible_on_another_installation()=runBlocking {
        val r=repo();val restored=repo()
        try {
            val (_,old)=pending(r)
            r.dismissReview(old.id,old.updatedAt)
            val bytes=r.createEncryptedBackup("long-test-only-password".toCharArray())
            restored.restoreBackup(restored.previewBackup(bytes,"long-test-only-password".toCharArray()),false)
            val row=restored.get(old.id)!!
            assertEquals("Dismissed",row.reviewState)
            assertEquals(0,restored.reviewCount())
            assertEquals(0L,restored.snapshot().debit.toLong())
            restored.restoreDismissedReview(row.id,row.updatedAt)
            assertEquals(1,restored.reviewCount())
            assertEquals(0L,restored.snapshot().personalSpend.toLong())
        } finally {r.eraseAll();r.close();restored.eraseAll();restored.close()}
    }
}

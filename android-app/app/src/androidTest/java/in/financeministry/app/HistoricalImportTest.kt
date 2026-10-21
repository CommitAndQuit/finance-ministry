package `in`.financeministry.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HistoricalImportTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun repository() = TransactionRepository(context, "import_test_${UUID.randomUUID()}")
    private fun grant() = InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
    private fun source(vararg rows: HistoricalSms) = HistoricalSmsSource { _, emit -> rows.forEach { emit(it) } }

    // Same grammatical shapes as tester feedback, with invented private details.
    private val sharedLayouts = listOf(
        "Rs.42.50 Dr. from A/C XXXXXX0000 and Cr. to sample.pay@hdfcbank. Ref:000000000001. AvlBal:Rs9876.54(2026:10:20 01:53:41). Not you? Call 1800000000-BOB",
        "Sent Rs.42.50\nFrom HDFC Bank A/C *0000\nTo Sample Box\nOn 16/10/26\nRef 000000000002\nNot You?\nSMS BLOCK UPI to 7000000000",
        "Sent Rs.42.50\nFrom HDFC Bank A/C *0000\nTo sample.pay-1@okexample\nOn 18/10/26\nRef 000000000003",
        "Sent Rs.42.50 from Kotak Bank A/c X0000 to Sample Cafe on 21-10-26. UPI Ref 000000000004. Not done by you? Tap https://example.com/fraud",
        "Sent Rs.42.50 from Other Bank A/c X0000 to Demo Play on 21-10-26. UPI Ref 000000000005.",
        "Sent Rs.42.50 from Example Bank A/c X0000 to Sample Health Co on 21-10-26. UPI Ref 000000000006.",
        "Spent INR 42.50 \nusing Axis Bank Credit Card no. XX0000 on 20-10-26 19:12:42 IST at \nSAMPLE SHOP. \nAvl limit: INR 9876.54."
    )

    @Test fun shared_payment_layouts_import_with_labels_dates_methods_and_repeat_deduplication() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        val messages = sharedLayouts.mapIndexed { i, body -> HistoricalSms("AD-EXBANK", now - 1000 - i, now - 2000 - i, body) }
        try {
            val preview = r.previewImport(source(*messages.toTypedArray()), now)
            assertEquals(7, preview.ready)
            assertEquals(0, preview.ignored)
            assertEquals(listOf("sample.pay", "Sample Box", "sample.pay-1", "Sample Cafe", "Demo Play", "Sample Health Co", "SAMPLE SHOP"),
                preview.rows.map { it.counterpartyLabel })
            assertEquals(listOf("UPI", "Unknown", "UPI", "UPI", "UPI", "UPI", "Card"), preview.rows.map { it.channel })
            assertTrue(preview.rows.all { it.amountMinor == 4250L && it.direction == "Debit" && it.status == "Successful" })
            assertEquals(messages.map { it.date }, preview.rows.map { it.effectiveTimestamp })
            assertTrue(r.snapshot().rows.isEmpty())
            assertEquals(7, r.commitImport(preview).inserted)
            assertEquals("29750", r.snapshot().debit.toString())
            assertEquals(7, r.previewImport(source(*messages.toTypedArray()), now).duplicates)
            assertEquals(0, r.commitImport(preview).inserted)
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun field_only_import_repairs_preserve_counted_totals_sources_categories_and_identity() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        val messages = listOf(0, 2, 6).map { i -> HistoricalSms("AD-EXBANK", now - 1000 - i, 0, sharedLayouts[i]) }
        try {
            val paymentSource = r.addPaymentSource("Demo UPI", "Bank account", Channel.UPI, "", "0000")
            r.commitImport(r.previewImport(source(*messages.toTypedArray()), now))
            val originals = r.snapshot().rows
            r.withLedger { db -> originals.forEach { row ->
                db.transactions().update(row.copy(counterpartyLabel = null, channel = "Unknown", transactionType = "Unknown",
                    parserVersion = 12, category = "Food"))
            } }
            val oldTotal = r.snapshot().debit
            val preview = r.previewImport(source(*messages.toTypedArray()), now)
            assertEquals(3, preview.repairs)
            assertEquals(0, preview.newCount)
            assertTrue(r.snapshot().rows.all { it.counterpartyLabel == null })
            assertEquals(3, r.commitImport(preview).repaired)
            val updated = r.snapshot()
            assertEquals(oldTotal, updated.debit)
            assertEquals(3, updated.rows.size)
            for (original in originals) {
                val row = r.get(original.id)!!
                assertEquals(original.effectiveTimestamp, row.effectiveTimestamp)
                assertEquals(original.importBatchId, row.importBatchId)
                assertEquals(original.paymentSourceId, row.paymentSourceId)
                if (original.channel == "UPI") assertEquals(paymentSource.id, row.paymentSourceId)
                assertEquals("Food", row.category)
                assertEquals("AutoRecorded", row.reviewState)
                assertEquals(original.counterpartyLabel, row.counterpartyLabel)
                assertEquals(original.channel, row.channel)
            }
            assertEquals(0, r.commitImport(preview).repaired)
            assertEquals(3, r.previewImport(source(*messages.toTypedArray()), now).duplicates)
            assertEquals(0, r.undoImport(originals.first().importBatchId!!))
        } finally { r.eraseAll(); r.close() }
    }

    private suspend fun legacyIncomplete(r: TransactionRepository, now: Long): Pair<HistoricalSms, TransactionEntity> {
        val sms = HistoricalSms("AD-EXBANK", now - 1000, now - 2000,
            "Dear UPI user A/C X0000 debited by 42.50 on date 18Sep26 trf to SAMPLE SHOP Refno 000000000001")
        r.commitImport(r.previewImport(source(sms), now))
        val original = r.snapshot().rows.single()
        val incomplete = original.copy(amountMinor = null, currency = null, counterpartyLabel = null,
            referenceHash = null, confidence = 60, parserVersion = 12, reviewState = "NeedsReview")
        r.withLedger { it.transactions().update(incomplete) }
        return sms to incomplete
    }

    @Test fun rescan_previews_and_repairs_missing_amount_once_without_creating_another_transaction() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        try {
            val (sms, old) = legacyIncomplete(r, now)
            val originalBatch = r.latestImport()!!.id
            val preview = r.previewImport(source(sms, sms), now)
            assertEquals(1, preview.rows.size)
            assertEquals(1, preview.repairs)
            assertEquals(0, preview.newCount)
            assertEquals(1, preview.duplicates)
            assertEquals(old.id, preview.rows.single().id)
            assertEquals(4250L, preview.rows.single().amountMinor)
            assertEquals("SAMPLE SHOP", preview.rows.single().counterpartyLabel)
            assertEquals("NeedsReview", preview.rows.single().reviewState)
            assertNull(r.get(old.id)!!.amountMinor) // Preview never writes.
            val result = r.commitImport(preview)
            assertEquals(0, result.inserted)
            assertEquals(1, result.repaired)
            assertEquals(1, r.snapshot().rows.size)
            val repaired = r.get(old.id)!!
            assertEquals(4250L, repaired.amountMinor)
            assertEquals(old.effectiveTimestamp, repaired.effectiveTimestamp)
            assertEquals(old.importBatchId, repaired.importBatchId)
            assertNotNull(repaired.referenceHash)
            assertEquals("NeedsReview", repaired.reviewState)
            assertEquals("0", r.snapshot().debit.toString())
            val repeated = r.commitImport(preview)
            assertEquals(0, repeated.inserted)
            assertEquals(0, repeated.repaired)
            assertEquals(0, r.previewImport(source(sms), now).rows.size)
            assertEquals(1, r.previewImport(source(sms), now).duplicates)
            assertEquals(originalBatch, r.latestImport()!!.id)
            assertEquals(0, r.undoImport(originalBatch)) // Do not erase a pre-existing repaired row.
            assertEquals(4250L, r.get(old.id)!!.amountMinor)
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun repair_preview_cannot_overwrite_a_later_user_correction() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        try {
            val (sms, old) = legacyIncomplete(r, now)
            val preview = r.previewImport(source(sms), now)
            assertEquals(1, preview.rows.size)
            r.save(ManualInput("99", Direction.Debit, old.effectiveTimestamp, TransactionType.Other,
                label = "My correction", notes = "Keep this"), old.id)
            assertEquals(0, r.commitImport(preview).inserted)
            val preserved = r.get(old.id)!!
            assertEquals(9900L, preserved.amountMinor)
            assertEquals("My correction", preserved.counterpartyLabel)
            assertEquals("Keep this", preserved.userNotes)
            assertTrue(preserved.isUserCorrected)
            assertEquals(1, r.snapshot().rows.size)
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun rescan_preserves_confirmed_dismissed_audited_changed_and_complete_records() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        try {
            val (sms, old) = legacyIncomplete(r, now)
            val protected = listOf(old.copy(isUserCorrected = true), old.copy(reviewState = "Confirmed"),
                old.copy(reviewState = "Dismissed"), old.copy(updatedAt = old.updatedAt + 1),
                old.copy(amountMinor = 9900, currency = "INR"), old.copy(userNotes = "Keep this"),
                old.copy(ownership = "Group", groupLabel = "Trip"), old.copy(linkedOriginalId = "original"),
                old.copy(duplicateOfId = "duplicate"))
            for (row in protected) {
                r.withLedger { it.transactions().update(row) }
                val preview = r.previewImport(source(sms), now)
                assertTrue("Protected record offered for repair: $row", preview.rows.isEmpty())
                assertEquals(1, preview.duplicates)
                r.commitImport(preview)
                assertEquals(row.amountMinor, r.get(old.id)!!.amountMinor)
            }
            r.withLedger {
                it.transactions().update(old)
                it.transactions().audit(listOf(CorrectionEntity(UUID.randomUUID().toString(), old.id,
                    old.createdAt, "category", "Other", "Food")))
            }
            assertTrue(r.previewImport(source(sms), now).rows.isEmpty())
            assertNull(r.get(old.id)!!.amountMinor)
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun portable_restore_requires_review_before_reimport_can_change_totals() = runBlocking {
        grant()
        val first = repository()
        val second = repository()
        val now = System.currentTimeMillis()
        val sms = HistoricalSms("TEST", now - 1000, 0, "INR 42 debited via UPI")
        val password = "test-only-password-long".toCharArray()
        try {
            first.commitImport(first.previewImport(source(sms), now))
            val backup = first.createEncryptedBackup(password)
            second.restoreBackup(second.previewBackup(backup, password), protectCurrent = false)
            assertTrue(second.restoredHistoryNeedsReview())
            val preview = second.previewImport(source(sms), now)
            assertEquals(0, preview.ready)
            assertEquals(1, preview.needsReview)
            second.commitImport(preview)
            assertEquals("4200", second.snapshot().debit.toString())
            assertEquals(1, second.previewImport(source(sms), now).duplicates)
            val onward = BackupCipher.decrypt(second.createEncryptedBackup(password), password)
            assertTrue(org.json.JSONObject(onward.toString(Charsets.UTF_8)).getBoolean("fingerprintHistoryRequiresReview"))
            onward.fill(0)
        } finally { password.fill('\u0000'); first.eraseAll(); first.close(); second.eraseAll(); second.close() }
    }

    @Test fun undo_import_keeps_an_incoming_payment_used_by_repayment_history() = runBlocking {
        grant()
        val r = repository()
        val now = System.currentTimeMillis()
        try {
            val result = r.commitImport(r.previewImport(source(HistoricalSms("TEST", now - 1000, 0, "INR 500 credited via UPI")), now))
            val credit = r.snapshot().rows.single()
            val expense = r.save(ManualInput("1000", Direction.Debit, now, TransactionType.Other, ownership = SpendingOwnership.ForOther))
            r.recordRepayment(expense, "250", credit.effectiveTimestamp, "Dinner", credit.id)
            assertEquals(0, r.undoImport(result.batchId))
            assertNotNull(r.get(credit.id))
            assertEquals(25000L, r.get(expense)!!.repaidMinor)
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun permission_denial_never_reads_source() = runBlocking {
        val denied = object : android.content.ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_DENIED
        }
        val repository = TransactionRepository(denied, "denied_import_${UUID.randomUUID()}")
        try {
            try {
                repository.previewImport(HistoricalSmsSource { _, _ -> fail("Read without permission") })
                fail("Permission denial accepted")
            } catch (_: IllegalStateException) { }
            assertTrue(repository.snapshot().rows.isEmpty())
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun actual_inbox_provider_imports_host_injected_sms() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runImportProvider") == "true")
        grant()
        val repository = repository()
        try {
            val preview = repository.previewImport()
            assertTrue("Expected synthetic inbox SMS", preview.rows.any { it.amountMinor == 876543L })
            val payment = preview.rows.single { it.amountMinor == 876543L }
            assertEquals("Sample Import Cafe", payment.counterpartyLabel)
            assertEquals("UPI", payment.channel)
            assertEquals("AutoRecorded", payment.reviewState)
            repository.commitImport(preview)
            assertEquals(1, repository.snapshot().rows.count { it.amountMinor == 876543L })
            assertEquals(0, repository.previewImport().rows.size)
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun preview_repeat_import_original_dates_and_undo_preserve_edits() = runBlocking {
        grant()
        val repository = repository()
        val now = System.currentTimeMillis()
        val dayAgo = now - 86400000
        val first = HistoricalSms("TEST", dayAgo, dayAgo - 2000, "INR 42 debited via UPI")
        val second = HistoricalSms("TEST", dayAgo + 1, 0, "INR 43 credited via UPI")
        try {
            val preview = repository.previewImport(source(first, first, second, HistoricalSms("TEST", now, 0, "OTP 000000")), now)
            assertEquals(2, preview.ready)
            assertEquals(1, preview.duplicates)
            assertTrue(repository.snapshot().rows.isEmpty())
            val result = repository.commitImport(preview)
            assertEquals(2, result.inserted)
            assertEquals(2, repository.snapshot().rows.size)
            val row = repository.snapshot().rows.single { it.amountMinor == 4200L }
            assertEquals(dayAgo, row.effectiveTimestamp)
            repository.save(ManualInput("44", Direction.Debit, dayAgo, TransactionType.Other), row.id)
            val again = repository.previewImport(source(first, second), now)
            assertEquals(0, again.ready)
            assertEquals(2, again.duplicates)
            assertEquals(1, repository.undoImport(result.batchId))
            assertEquals(4400L, repository.get(row.id)!!.amountMinor)
            assertEquals(1, repository.snapshot().rows.size)
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun stale_preview_after_erasure_cannot_recreate_the_ledger() = runBlocking {
        grant()
        val repository = repository()
        try {
            val now = System.currentTimeMillis()
            val preview = repository.previewImport(source(HistoricalSms("TEST", now, 0, "INR 42 debited")), now)
            repository.eraseAll()
            try { repository.commitImport(preview); fail("Stale preview accepted") } catch (_: IllegalStateException) { }
            assertTrue(repository.snapshot().rows.isEmpty())
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun cancellation_during_scan_has_no_partial_import() = runBlocking {
        grant()
        val repository = repository()
        val ready = CompletableDeferred<Unit>()
        try {
            val now = System.currentTimeMillis()
            val scan = launch {
                repository.previewImport(HistoricalSmsSource { _, emit ->
                    emit(HistoricalSms("TEST", now, 0, "INR 42 debited"))
                    ready.complete(Unit)
                    awaitCancellation()
                }, now)
            }
            ready.await(); scan.cancel(); scan.join()
            assertTrue(repository.snapshot().rows.isEmpty())
            assertNull(repository.latestImport())
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun live_capture_between_preview_and_commit_is_not_duplicated() = runBlocking {
        grant()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        val repository = repository()
        try {
            val now = System.currentTimeMillis()
            val sent = now - 2000
            val body = "INR 42 debited via UPI"
            val preview = repository.previewImport(source(HistoricalSms("TEST", now, sent, body)), now)
            repository.preferences.edit().putBoolean("sms_disclosure", true).commit()
            assertTrue(repository.ingest(IncomingSms("TEST", sent, body)))
            val existing = repository.snapshot().rows.single()
            repository.save(ManualInput("45", Direction.Debit, sent, TransactionType.Other), existing.id)
            assertEquals(0, repository.commitImport(preview).inserted)
            assertEquals(4500L, repository.snapshot().rows.single().amountMinor)
            assertNull(repository.latestImport())
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun repeated_commit_reopen_and_undo_are_idempotent() = runBlocking {
        grant()
        val repository = repository()
        try {
            val now = System.currentTimeMillis()
            val preview = repository.previewImport(source(HistoricalSms("TEST", now, 0, "INR 42 debited")), now)
            val result = repository.commitImport(preview)
            assertEquals(0, repository.commitImport(preview).inserted)
            repository.close()
            assertEquals(result.batchId, repository.latestImport()!!.id)
            assertEquals(1, repository.undoImport(result.batchId))
            assertEquals(0, repository.undoImport(result.batchId))
            assertTrue(repository.snapshot().rows.isEmpty())
        } finally { repository.eraseAll(); repository.close() }
    }

    @Test fun imported_and_live_messages_use_the_same_conservative_source_resolution() = runBlocking {
        grant()
        val repository = repository()
        val now = System.currentTimeMillis()
        try {
            val source = repository.addPaymentSource("Personal UPI", "Bank account", Channel.UPI, "HDFC", "7111")
            val body = "INR 42 debited via UPI account XX7111"
            val preview = repository.previewImport(source(HistoricalSms("HDFCBK", now, 0, body)), now)
            repository.commitImport(preview)
            assertEquals(source.id, repository.snapshot().rows.single().paymentSourceId)
        } finally { repository.eraseAll(); repository.close() }
    }
}

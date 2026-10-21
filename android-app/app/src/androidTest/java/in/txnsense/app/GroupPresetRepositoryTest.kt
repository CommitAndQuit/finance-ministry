package `in`.txnsense.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GroupPresetRepositoryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun repo() = TransactionRepository(app, "preset_${UUID.randomUUID()}")

    @Test fun presets_survive_restart_and_deletion_does_not_change_recorded_shares() = runBlocking {
        val name = "preset_${UUID.randomUUID()}"
        var r = TransactionRepository(app, name)
        try {
            val alice = r.addLocalMember("Alice"); val bob = r.addLocalMember("Bob")
            val preset = r.saveGroupPreset("Dinner", false, 100, listOf(MemberShareInput(alice.id, 200), MemberShareInput(bob.id, 100)))
            val id = r.save(ManualInput("10.01", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                ownership = SpendingOwnership.Group, groupLabel = "Dinner", personalShare = "2.50", groupShares = preset.preview(1001).others))
            r.close(); r = TransactionRepository(app, name)
            val loaded = r.groupPresets().single()
            assertEquals(preset.id, loaded.id)
            assertEquals(listOf(alice.id, bob.id), loaded.members.map { it.memberId })
            assertEquals(250L, loaded.preview(1001).personalMinor)
            assertEquals(listOf(501L, 250L), loaded.preview(1001).others.map { it.amountMinor })
            r.renameLocalMember(alice.id, "Alice home")
            r.deleteGroupPreset(loaded.id)
            assertTrue(r.groupPresets().isEmpty())
            assertEquals(2, r.groupSharesFor(id).size)
            assertEquals(250L, r.get(id)!!.personalShareMinor)
            assertEquals(751L, r.snapshot().outstandingRepayments.toLong())
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun encrypted_backup_restores_equal_and_custom_presets_with_local_people() = runBlocking {
        val r = repo(); val restored = repo()
        try {
            val alice = r.addLocalMember("Alice"); val bob = r.addLocalMember("Bob")
            r.saveGroupPreset("Dinner", true, 100, listOf(MemberShareInput(alice.id, 100), MemberShareInput(bob.id, 100)))
            r.saveGroupPreset("Home", false, 100, listOf(MemberShareInput(bob.id, 200), MemberShareInput(alice.id, 100)))
            val bytes = r.createEncryptedBackup("long-test-only-password".toCharArray())
            val preview = restored.previewBackup(bytes, "long-test-only-password".toCharArray())
            assertEquals(2, preview.groupPresetCount)
            restored.restoreBackup(preview, false)
            assertEquals(2, restored.groupPresets().size)
            assertEquals(listOf(334L, 334L, 333L), restored.groupPresets().single { it.name == "Dinner" }.preview(1001).let {
                listOf(it.personalMinor) + it.others.map { share -> share.amountMinor }
            })
            assertEquals(listOf(bob.id, alice.id), restored.groupPresets().single { it.name == "Home" }.preview(1001).others.map { it.memberId })
        } finally { r.eraseAll(); r.close(); restored.eraseAll(); restored.close() }
    }

    @Test fun updating_and_renaming_a_default_keeps_member_order_and_existing_expenses() = runBlocking {
        val r = repo()
        try {
            val alice = r.addLocalMember("Alice"); val bob = r.addLocalMember("Bob")
            val old = r.saveGroupPreset("Dinner", true, 100, listOf(MemberShareInput(alice.id, 100), MemberShareInput(bob.id, 100)))
            val id = r.save(ManualInput("9", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                ownership = SpendingOwnership.Group, groupLabel = "Dinner", personalShare = "3", groupShares = old.preview(900).others))
            r.saveGroupPreset("Dinner", false, 100, listOf(MemberShareInput(bob.id, 200), MemberShareInput(alice.id, 100)), old.id)
            r.renameGroupPreset(old.id, "Home dinner")
            val next = r.groupPresets().single()
            assertEquals(old.id, next.id); assertEquals("Home dinner", next.name)
            assertEquals(listOf(bob.id, alice.id), next.members.map { it.memberId })
            assertEquals(listOf(250L, 501L, 250L), next.preview(1001).let { listOf(it.personalMinor) + it.others.map { share -> share.amountMinor } })
            assertEquals(300L, r.get(id)!!.personalShareMinor)
            assertEquals(listOf(300L, 300L), r.groupSharesFor(id).map { it.amountMinor })
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun schema_eight_backups_restore_without_inventing_presets() = runBlocking {
        val r = repo(); val restored = repo()
        try {
            r.addLocalMember("Alice")
            val bytes = r.createEncryptedBackup("long-test-only-password".toCharArray())
            val plain = BackupCipher.decrypt(bytes, "long-test-only-password".toCharArray())
            val root = try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
            root.put("schema", 8)
            val tables = root.getJSONObject("data").getJSONObject("tables")
            tables.remove("group_presets"); tables.remove("group_preset_members")
            val legacy = BackupCipher.encrypt(root.toString().toByteArray(), "long-test-only-password".toCharArray())
            restored.restoreBackup(restored.previewBackup(legacy, "long-test-only-password".toCharArray()), false)
            assertEquals("Alice", restored.localMembers().single().name)
            assertTrue(restored.groupPresets().isEmpty())
        } finally { r.eraseAll(); r.close(); restored.eraseAll(); restored.close() }
    }

    @Test fun invalid_members_duplicate_names_and_malformed_backup_presets_are_rejected() = runBlocking {
        val r = repo()
        try {
            val alice = r.addLocalMember("Alice")
            r.saveGroupPreset("Dinner", true, 100, listOf(MemberShareInput(alice.id, 100)))
            suspend fun rejected(block: suspend () -> Unit) {
                try { block(); fail("Expected invalid preset rejection") } catch (_: IllegalArgumentException) { }
            }
            rejected { r.saveGroupPreset(" dinner ", true, 1, listOf(MemberShareInput(alice.id, 1))) }
            rejected { r.saveGroupPreset("Missing", true, 1, listOf(MemberShareInput("missing", 1))) }
            rejected { r.saveGroupPreset("Empty", false, 0, listOf(MemberShareInput(alice.id, 0))) }
            val bytes = r.createEncryptedBackup("long-test-only-password".toCharArray())
            val plain = BackupCipher.decrypt(bytes, "long-test-only-password".toCharArray())
            val root = try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
            root.getJSONObject("data").getJSONObject("tables").getJSONArray("group_preset_members").getJSONObject(0).put("memberId", "missing")
            val invalid = BackupCipher.encrypt(root.toString().toByteArray(), "long-test-only-password".toCharArray())
            rejected { r.previewBackup(invalid, "long-test-only-password".toCharArray()) }
            assertEquals(1, r.groupPresets().size)
        } finally { r.eraseAll(); r.close() }
    }
}

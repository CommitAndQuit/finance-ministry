package `in`.financeministry.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AppearanceBackupTest {
    private fun repository()=TransactionRepository(ApplicationProvider.getApplicationContext<Application>(),"appearance_backup_${UUID.randomUUID()}")
    private val password get()="appearance-test-password".toCharArray()
    @Test fun encrypted_backup_preserves_each_explicit_appearance_without_changing_ledger_values() = runBlocking {
        val source=repository()
        try {
            val id=source.save(ManualInput("42",Direction.Debit,System.currentTimeMillis(),TransactionType.Other))
            for(mode in listOf("System","Light","Dark")) {
                val target=repository()
                try {
                    source.preferences.edit().putString("appearance",mode).commit()
                    val bytes=source.createEncryptedBackup(password)
                    target.restoreBackup(target.previewBackup(bytes,password),false)
                    assertEquals(mode,target.preferences.getString("appearance",null))
                    assertEquals(4200L,target.get(id)!!.amountMinor)
                    assertFalse(target.preferences.getBoolean("sms_disclosure",false))
                } finally {target.eraseAll();target.close()}
            }
        } finally {source.eraseAll();source.close()}
    }
    @Test fun an_older_backup_without_appearance_resets_the_destination_to_system_default()=runBlocking {
        val source=repository();val target=repository()
        try {
            source.save(ManualInput("42",Direction.Debit,System.currentTimeMillis(),TransactionType.Other))
            val bytes=source.createEncryptedBackup(password)
            target.preferences.edit().putString("appearance","Dark").commit()
            target.restoreBackup(target.previewBackup(bytes,password),false)
            assertFalse(target.preferences.contains("appearance"))
            assertEquals(4200L,target.snapshot().personalSpend.toLong())
        } finally {source.eraseAll();source.close();target.eraseAll();target.close()}
    }
    @Test fun invalid_appearance_preferences_are_rejected_before_any_restore()=runBlocking {
        val r=repository()
        try {
            r.save(ManualInput("42",Direction.Debit,System.currentTimeMillis(),TransactionType.Other))
            val plain=BackupCipher.decrypt(r.createEncryptedBackup(password),password)
            val original=try {plain.toString(Charsets.UTF_8)} finally {plain.fill(0)}
            for(value in listOf<Any>("unknown",true,42)) {
                val root=JSONObject(original)
                root.getJSONObject("data").getJSONObject("preferences").put("appearance",value)
                try {r.previewBackup(BackupCipher.encrypt(root.toString().toByteArray(),password),password);fail("Invalid appearance accepted: $value")}
                catch(_:IllegalArgumentException) {}
                assertEquals(4200L,r.snapshot().personalSpend.toLong())
            }
        } finally {r.eraseAll();r.close()}
    }
}

package `in`.txnsense.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One SMS layout the parser could not read, stored as a token skeleton and never as a message.
 *
 * [encoded] is a [`in`.txnsense.app.parser.induction.SkeletonCodec] string: banking vocabulary plus
 * typed blanks. Every merchant, payee, amount, account number, reference and link is already a blank
 * before this row exists, so the corpus cannot leak what it never held.
 *
 * The primary key is a hash of the layout, so repeated sightings of one layout increment [sightings]
 * rather than adding rows. That caps how much the corpus can ever grow and makes ["how many times have
 * I seen this?"] answerable without keeping the messages.
 */
@Entity(tableName = "skeleton_sightings", indices = [Index("bankId"), Index("lastSeen")])
data class SkeletonSightingEntity(
    @PrimaryKey val layoutKey: String,
    val bankId: String,
    val encoded: String,
    val sightings: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)

/**
 * A layout the app induced for itself, and how much it has earned.
 *
 * [state] is the whole safety story. `Shadow` means the template is evaluated against live messages
 * but books nothing; `Active` means it may create a transaction, always for review; `Retired` means it
 * once disagreed with a curated template and will never be used or relearned again.
 */
@Entity(tableName = "learned_templates", indices = [Index("bankId"), Index("state")])
data class LearnedTemplateEntity(
    @PrimaryKey val templateId: String,
    val bankId: String,
    val regexPattern: String,
    val direction: String,
    val status: String,
    val channel: String,
    val transactionType: String,
    /** Distinct layouts the pattern was grown from. */
    val sampleCount: Int,
    val literalChars: Int,
    val anchorCount: Int,
    val state: String,
    /** Live messages the pattern read cleanly while in shadow. */
    val agreements: Int = 0,
    /** Times it read a message differently from the curated template that owns it. One is fatal. */
    val contradictions: Int = 0,
    val createdAt: Long,
    val lastMatchedAt: Long? = null,
    val promotedAt: Long? = null,
    val retiredAt: Long? = null,
)

/** What a learned template is allowed to do. */
enum class LearnedState { Shadow, Active, Retired }

@Dao
interface LearningDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun addSighting(row: SkeletonSightingEntity): Long

    @Query("UPDATE skeleton_sightings SET sightings = sightings + 1, lastSeen = :at WHERE layoutKey = :layoutKey")
    fun touchSighting(layoutKey: String, at: Long)

    @Query("SELECT * FROM skeleton_sightings ORDER BY lastSeen DESC LIMIT :limit")
    fun sightings(limit: Int): List<SkeletonSightingEntity>

    @Query("SELECT COUNT(*) FROM skeleton_sightings") fun sightingCount(): Int

    @Query("DELETE FROM skeleton_sightings WHERE lastSeen < :before") fun expireSightings(before: Long): Int

    /** Drops the least recently seen layouts, keeping the corpus bounded however long capture runs. */
    @Query("DELETE FROM skeleton_sightings WHERE layoutKey NOT IN " +
        "(SELECT layoutKey FROM skeleton_sightings ORDER BY lastSeen DESC LIMIT :keep)")
    fun trimSightings(keep: Int): Int

    @Query("DELETE FROM skeleton_sightings") fun clearSightings()

    @Insert(onConflict = OnConflictStrategy.IGNORE) fun addTemplate(row: LearnedTemplateEntity): Long

    @Query("SELECT * FROM learned_templates WHERE state = :state ORDER BY createdAt ASC")
    fun templates(state: String): List<LearnedTemplateEntity>

    @Query("SELECT * FROM learned_templates ORDER BY createdAt DESC") fun allTemplates(): List<LearnedTemplateEntity>

    @Query("SELECT * FROM learned_templates WHERE templateId = :templateId")
    fun template(templateId: String): LearnedTemplateEntity?

    @Query("UPDATE learned_templates SET agreements = agreements + 1, lastMatchedAt = :at WHERE templateId = :templateId")
    fun recordAgreement(templateId: String, at: Long)

    @Query("UPDATE learned_templates SET state = 'Active', promotedAt = :at WHERE templateId = :templateId AND state = 'Shadow'")
    fun promote(templateId: String, at: Long)

    @Query("UPDATE learned_templates SET state = 'Retired', contradictions = contradictions + 1, " +
        "retiredAt = :at WHERE templateId = :templateId AND state != 'Retired'")
    fun retire(templateId: String, at: Long)

    @Query("DELETE FROM learned_templates WHERE templateId = :templateId") fun forget(templateId: String)

    @Query("DELETE FROM learned_templates") fun clearTemplates()
}

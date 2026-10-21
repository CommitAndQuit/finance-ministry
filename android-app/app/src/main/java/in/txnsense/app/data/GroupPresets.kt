package `in`.txnsense.app.data

import androidx.room.*
import java.math.BigInteger

@Entity(tableName = "group_presets")
data class GroupPresetEntity(@PrimaryKey val id: String, val name: String, val splitMode: String,
    val personalWeight: Long, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "group_preset_members", foreignKeys = [
    ForeignKey(entity = GroupPresetEntity::class, parentColumns = ["id"], childColumns = ["presetId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = LocalMemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("presetId"), Index("memberId"), Index(value = ["presetId", "memberId"], unique = true),
    Index(value = ["presetId", "position"], unique = true)])
data class GroupPresetMemberEntity(@PrimaryKey val id: String, val presetId: String, val memberId: String,
    val position: Int, val weight: Long)

data class GroupPreset(val record: GroupPresetEntity, val members: List<GroupPresetMemberEntity>) {
    val id get() = record.id
    val name get() = record.name
    val equal get() = record.splitMode == "Equal"
    /** Re-saving an unchanged preview must not replace its ratio with rounded paise. */
    fun weightsForDefault(total: Long, personal: Long, others: List<MemberShareInput>): List<Long> {
        val current = preview(total)
        return if (!equal && personal == current.personalMinor && others == current.others)
            listOf(record.personalWeight) + members.map { it.weight }
        else listOf(personal) + others.map { it.amountMinor }
    }
    fun preview(total: Long): GroupPresetPreview {
        require(record.splitMode in setOf("Equal", "Custom") && members.size in 1..20 &&
            members.map { it.memberId }.distinct().size == members.size) { "Check this saved group's local people and split." }
        val amounts = if (equal) GroupSplitAccounting.equal(total, members.size + 1)
            else GroupPresetAccounting.custom(total, listOf(record.personalWeight) + members.map { it.weight })
        return GroupPresetPreview(amounts[0], members.mapIndexed { i, member -> MemberShareInput(member.memberId, amounts[i + 1]) })
    }
}
data class GroupPresetPreview(val personalMinor: Long, val others: List<MemberShareInput>)

/** Custom defaults are ratios of exact paise, rescaled without floating-point arithmetic. */
object GroupPresetAccounting {
    fun custom(total: Long, weights: List<Long>): List<Long> {
        require(total > 0 && weights.size in 2..21 && weights.all { it >= 0 }) { "Check the saved group's shares." }
        val denominator = weights.fold(BigInteger.ZERO) { sum, value -> sum + BigInteger.valueOf(value) }
        require(denominator > BigInteger.ZERO) { "A custom default needs a positive share." }
        val divisions = weights.map { BigInteger.valueOf(total).multiply(BigInteger.valueOf(it)).divideAndRemainder(denominator) }
        // Each nonnegative weight is <= their sum, so every quotient is in 0..total.
        // total is a positive Long: narrowing is exact, including on Android 26–30,
        // where BigInteger.longValueExact() is not available.
        val values = divisions.map { it[0].toLong() }.toMutableList()
        val remaining = total - values.fold(0L, Math::addExact)
        val order = divisions.indices.sortedWith(compareByDescending<Int> { divisions[it][1] }.thenBy { it })
        repeat(remaining.toInt()) { values[order[it]]++ }
        return values
    }
}

@Dao
interface GroupPresetDao {
    @Query("SELECT * FROM group_presets ORDER BY name COLLATE NOCASE, id") fun presets(): List<GroupPresetEntity>
    @Query("SELECT * FROM group_presets WHERE id = :id") fun preset(id: String): GroupPresetEntity?
    @Query("SELECT * FROM group_preset_members WHERE presetId = :id ORDER BY position") fun members(id: String): List<GroupPresetMemberEntity>
    @Insert fun insert(row: GroupPresetEntity)
    @Update fun update(row: GroupPresetEntity)
    @Insert fun insertMembers(rows: List<GroupPresetMemberEntity>)
    @Query("DELETE FROM group_preset_members WHERE presetId = :id") fun deleteMembers(id: String)
    @Query("DELETE FROM group_presets WHERE id = :id") fun delete(id: String)
}

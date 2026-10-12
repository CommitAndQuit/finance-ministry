package `in`.financeministry.app.data

import java.util.UUID

suspend fun TransactionRepository.groupPresets(): List<GroupPreset> = withLedger { db ->
    db.groupPresets().let { dao -> dao.presets().map { GroupPreset(it, dao.members(it.id)) } }
}

internal fun validateGroupPresetName(name: String) {
    validateMemberName(name)
}

/** Saving a default never edits any recorded expense, allocation or repayment. */
suspend fun TransactionRepository.saveGroupPreset(name: String, equal: Boolean, personalMinor: Long,
    others: List<MemberShareInput>, id: String? = null): GroupPreset = withLedger { db ->
    validateGroupPresetName(name)
    require(others.size in 1..20 && others.map { it.memberId }.distinct().size == others.size &&
        others.all { it.amountMinor >= 0 && db.transactions().member(it.memberId) != null }) { "Choose existing local people once each." }
    GroupPresetAccounting.custom(1, listOf(personalMinor) + others.map { it.amountMinor })
    val dao = db.groupPresets()
    val old = id?.let { requireNotNull(dao.preset(it)) { "Saved group no longer exists." } }
    require(dao.presets().none { it.id != id && it.name.equals(name.trim(), true) }) { "That saved group name already exists." }
    val now = maxOf(System.currentTimeMillis(), (old?.updatedAt ?: 0) + 1)
    val row = GroupPresetEntity(old?.id ?: UUID.randomUUID().toString(), name.trim(), if (equal) "Equal" else "Custom",
        if (equal) 1 else personalMinor, old?.createdAt ?: now, now)
    val members = others.mapIndexed { i, share -> GroupPresetMemberEntity(UUID.randomUUID().toString(), row.id,
        share.memberId, i, if (equal) 1 else share.amountMinor) }
    db.runInTransaction {
        if (old == null) dao.insert(row) else { dao.update(row); dao.deleteMembers(row.id) }
        dao.insertMembers(members)
    }
    revision.value++
    GroupPreset(row, members)
}

suspend fun TransactionRepository.renameGroupPreset(id: String, name: String) = withLedger { db ->
    validateGroupPresetName(name)
    val dao = db.groupPresets()
    val old = requireNotNull(dao.preset(id)) { "Saved group no longer exists." }
    require(dao.presets().none { it.id != id && it.name.equals(name.trim(), true) }) { "That saved group name already exists." }
    dao.update(old.copy(name = name.trim(), updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1)))
    revision.value++
}

suspend fun TransactionRepository.deleteGroupPreset(id: String) = withLedger { db ->
    db.groupPresets().delete(id)
    revision.value++
}

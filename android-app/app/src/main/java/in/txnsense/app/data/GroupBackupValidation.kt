package `in`.txnsense.app.data

import org.json.JSONObject
import java.math.BigInteger
import java.util.Locale

/** Validate the complete allocation graph before any restore deletes the current ledger. */
internal fun validateGroupBackup(tables: Map<String, Map<String, JSONObject>>) {
    val members = tables.getValue("local_members")
    val names = mutableSetOf<String>()
    members.values.forEach {
        val name = it.getString("name")
        validateMemberName(name)
        require(name == name.trim() && names.add(name.lowercase(Locale.ROOT)) && it.getLong("createdAt") > 0) { "Invalid or duplicate local person." }
    }
    val presets = tables.getValue("group_presets")
    val presetMembers = tables.getValue("group_preset_members").values.groupBy { it.getString("presetId") }
    require(presetMembers.keys.all { it in presets }) { "Saved group is missing." }
    val presetNames = mutableSetOf<String>()
    presets.forEach { (id, preset) ->
        val name = preset.getString("name")
        validateGroupPresetName(name)
        require(name == name.trim() && presetNames.add(name.lowercase(Locale.ROOT)) &&
            preset.getString("splitMode") in setOf("Equal", "Custom") && preset.getLong("createdAt") > 0 &&
            preset.getLong("updatedAt") >= preset.getLong("createdAt")) { "Invalid saved group." }
        val rows = presetMembers[id].orEmpty().sortedBy { it.getLong("position") }
        require(rows.size in 1..20 && rows.map { it.getString("memberId") }.distinct().size == rows.size &&
            rows.map { it.getLong("position") } == rows.indices.map { it.toLong() } &&
            rows.all { it.getString("memberId") in members && it.getLong("weight") >= 0 }) { "Invalid saved group people." }
        val weights = listOf(preset.getLong("personalWeight")) + rows.map { it.getLong("weight") }
        GroupPresetAccounting.custom(1, weights)
        if (preset.getString("splitMode") == "Equal") require(weights.all { it == 1L }) { "Invalid equal split default." }
    }
    val transactions = tables.getValue("transactions")
    val shares = tables.getValue("group_shares").values.groupBy { it.getString("expenseId") }
    shares.forEach { (id, rows) ->
        val expense = requireNotNull(transactions[id]) { "Split expense is missing." }
        require(expense.getString("ownership") == "Group" && expense.getString("direction") == "Debit" &&
            expense.getLong("repaymentExpected") == 1L && expense.getString("status") == "Successful" &&
            expense.getString("transactionType") !in setOf("SelfTransfer", "CardRepayment")) { "Invalid individual split expense." }
        val inputs = rows.map {
            val member = it.getString("memberId")
            require(member in members) { "Split person is missing." }
            MemberShareInput(member, it.getLong("amountMinor"))
        }
        require(!expense.isNull("amountMinor") && !expense.isNull("personalShareMinor")) { "Split amounts are missing." }
        GroupSplitAccounting.validate(expense.getLong("amountMinor"), expense.getLong("personalShareMinor"), inputs)
    }
    val received = mutableMapOf<Pair<String, String>, BigInteger>()
    tables.getValue("repayments").values.forEach { receipt ->
        val expense = receipt.getString("expenseId")
        val allocations = shares[expense].orEmpty()
        if (allocations.isEmpty()) require(receipt.isNull("memberId")) { "Repayment has no individual split." }
        else {
            require(!receipt.isNull("memberId") && receipt.getString("method") != "Legacy") { "Individual repayment needs a person and receipt." }
            val member = receipt.getString("memberId")
            val share = requireNotNull(allocations.firstOrNull { it.getString("memberId") == member }) { "Repayment person is not in this expense." }
            val key = expense to member
            val total = (received[key] ?: BigInteger.ZERO) + BigInteger.valueOf(receipt.getLong("amountMinor"))
            require(total <= BigInteger.valueOf(share.getLong("amountMinor"))) { "Repayment exceeds this person's share." }
            received[key] = total
        }
    }
}

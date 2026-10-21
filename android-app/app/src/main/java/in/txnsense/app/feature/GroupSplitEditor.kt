package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import `in`.txnsense.app.ui.LedgerTextInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.txnsense.app.data.*
import `in`.txnsense.app.money
import `in`.txnsense.app.ui.LedgerIcon
import `in`.txnsense.app.ui.LedgerChoiceChip
import `in`.txnsense.app.ui.compactMoney
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger

internal fun encodeShares(shares: List<MemberShareInput>): String = JSONArray().apply {
    shares.forEach { put(JSONObject().put("person", it.memberId).put("amount", it.amountMinor)) }
}.toString()
internal fun decodeShares(value: String): List<MemberShareInput> = JSONArray(value).let { array ->
    List(array.length()) { i -> array.getJSONObject(i).let { MemberShareInput(it.getString("person"), it.getLong("amount")) } }
}
internal fun shareDecimal(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
private fun parseShare(value: String): Long {
    require(value.matches(Regex("[0-9]{1,16}(\\.[0-9]{1,2})?"))) { "Enter shares with up to two decimal places." }
    return BigDecimal(value).movePointRight(2).longValueExact()
}

/** Draft-only until Apply. Member nicknames are reusable local records, never contacts. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GroupSplitEditor(repository: TransactionRepository, total: Long, personal: Long,
    initial: List<MemberShareInput>, onApply: suspend (Long, List<MemberShareInput>) -> Unit, onDismiss: () -> Unit,
    onPresetApplied: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<LocalMemberEntity>>(emptyList()) }
    var presets by remember { mutableStateOf<List<GroupPreset>>(emptyList()) }
    var selectedPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosePreset by rememberSaveable { mutableStateOf(false) }
    var savePreset by rememberSaveable { mutableStateOf(false) }
    var updatePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var renamePresetId by remember { mutableStateOf<String?>(null) }
    var deletePreset by remember { mutableStateOf<GroupPreset?>(null) }
    var presetMessage by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var ids by rememberSaveable { mutableStateOf(initial.map { it.memberId }) }
    val initiallyEqual = remember(total, personal, initial) {
        initial.isEmpty() || listOf(personal) + initial.map { it.amountMinor } == GroupSplitAccounting.equal(total, initial.size + 1)
    }
    var equal by rememberSaveable { mutableStateOf(initiallyEqual) }
    var byParts by rememberSaveable { mutableStateOf(false) }
    var parts by rememberSaveable { mutableStateOf("{}") }
    val initialAmounts = remember { JSONObject().put("you", shareDecimal(personal)).apply {
        initial.forEach { put(it.memberId, shareDecimal(it.amountMinor)) }
    }.toString() }
    var amounts by rememberSaveable { mutableStateOf(initialAmounts) }
    var newName by rememberSaveable { mutableStateOf("") }
    var choose by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var rename by rememberSaveable { mutableStateOf("") }
    var moreOptions by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val dirty = ids != initial.map { it.memberId } || amounts != initialAmounts || equal != initiallyEqual || byParts || parts != "{}"
    val close = { if (!busy) { if (dirty) discard = true else onDismiss() }; Unit }
    BackHandler { close() }
    LaunchedEffect(Unit) {
        try { members = repository.localMembers(); presets = repository.groupPresets(); ready = true }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load people. Close and retry." }
    }
    fun setAmount(id: String, value: String) { amounts = JSONObject(amounts).put(id, value).toString(); error = null }
    // A modal owns a separate composition. Capture one immutable render snapshot:
    // its child must not observe new IDs while using an older calculated list.
    val selectedIds = ids
    val equalMode = equal
    val partsMode = byParts
    val amountTexts = JSONObject(amounts)
    val partTexts = JSONObject(parts)
    val equalAmounts = if (selectedIds.isEmpty()) listOf(total) else GroupSplitAccounting.equal(total, selectedIds.size + 1)
    val custom = try { listOf(parseShare(amountTexts.optString("you"))) + selectedIds.map { parseShare(amountTexts.optString(it)) } }
        catch (_: Exception) { null }
    val weights = try { GroupSplitAccounting.partWeights((listOf("you") + selectedIds).map { partTexts.optString(it, "1") }) }
        catch (_: IllegalArgumentException) { null }
    val values = when {
        partsMode -> weights?.let { GroupPresetAccounting.custom(total, it) }
        equalMode -> equalAmounts
        else -> custom
    }
    val sum = values?.fold(BigInteger.ZERO) { n, v -> n + BigInteger.valueOf(v) }
    val valid = ready && selectedIds.isNotEmpty() && selectedIds.size <= 20 && sum == BigInteger.valueOf(total)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !dirty && !busy })
    ModalBottomSheet(onDismissRequest = close, sheetState = sheet, sheetGesturesEnabled = false, dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 9.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Split with people", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = close, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Close group split" }) { LedgerIcon("close") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("You paid ${compactMoney(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (presets.isNotEmpty()) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { choosePreset = true }, enabled = ready && !busy, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Choose saved group", style = MaterialTheme.typography.labelLarge)
                            Text(presets.firstOrNull { it.id == selectedPresetId }?.let { "${it.name} · ${it.members.size + 1} people" }
                                ?: "Reusable people & shares", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LedgerIcon("people")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LedgerChoiceChip(equalMode, { equal = true; byParts = false; error = null }, label = { Text("Split equally") }, enabled = !busy,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                    LedgerChoiceChip(!equalMode && !partsMode, {
                        if (equalMode || partsMode) {
                            val current = values ?: equalAmounts
                            amounts = JSONObject().put("you", shareDecimal(current[0])).apply {
                                selectedIds.forEachIndexed { i, id -> put(id, shareDecimal(current[i + 1])) }
                            }.toString()
                        }
                        equal = false; byParts = false; error = null
                    }, label = { Text("Custom amounts") }, enabled = !busy,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                    LedgerChoiceChip(partsMode, { equal = false; byParts = true; error = null }, label = { Text("By parts") }, enabled = !busy,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                }
                if (partsMode) Text("Enter each person's parts. For ₹900, 1 part and 2 parts become ₹300 and ₹600. Zero parts is allowed; at least one must be positive. Extra paise go to the largest remainder, then the order below.", style = MaterialTheme.typography.bodySmall)
                if (equalMode && total % (selectedIds.size + 1) != 0L) Text("Extra paise go to You, then people in the order below.", style = MaterialTheme.typography.bodySmall)
                if (partsMode) {
                    (listOf("you") + selectedIds).forEachIndexed { i, id ->
                        val name = if (id == "you") "You" else members.firstOrNull { it.id == id }?.name ?: "Loading person…"
                        SplitPartRow(name, partTexts.optString(id, "1"), values?.get(i), !busy) {
                            parts = JSONObject(parts).put(id, it.take(12)).toString(); error = null
                        }
                    }
                } else {
                SplitShareRow("You", if (equalMode) equalAmounts[0] else null, amountTexts.optString("you"), !busy,
                    { setAmount("you", it) })
                selectedIds.forEachIndexed { i, id ->
                    val name = members.firstOrNull { it.id == id }?.name ?: "Loading person…"
                    SplitShareRow(name, if (equalMode) equalAmounts[i + 1] else null, amountTexts.optString(id), !busy,
                        { setAmount(id, it) })
                }
                }
                TextButton(onClick = { focus.clearFocus(); choose = true }, enabled = ready && !busy, contentPadding = PaddingValues(0.dp)) {
                    LedgerIcon("plus", Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Add or manage people")
                }
                Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                        SplitPreviewRow("Your spending", values?.firstOrNull()?.let { compactMoney(it) } ?: "—")
                        SplitPreviewRow("Owed back to you", values?.drop(1)?.fold(BigInteger.ZERO) { n, v -> n + BigInteger.valueOf(v) }?.let { compactMoney(it) } ?: "—")
                    }
                }
                Text("Names stay on your phone. You can change the split for each payment. No contacts or invitations.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { moreOptions = !moreOptions }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("More split options") }
                if (moreOptions) {
                    if (initial.isNotEmpty()) TextButton(onClick = { remove = true }, enabled = !busy) { Text("Use an unnamed split instead") }
                    TextButton(onClick = { presetName = ""; updatePresetId = null; savePreset = true }, enabled = valid && !busy) { Text("Save as group default") }
                    if (selectedPresetId != null && presets.any { it.id == selectedPresetId }) {
                        TextButton(onClick = {
                            val preset = presets.first { it.id == selectedPresetId }
                            presetName = preset.name; updatePresetId = preset.id; renamePresetId = null; savePreset = true
                        }, enabled = valid && !busy) { Text("Update saved default") }
                    }
                }
                presetMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    when {
                        selectedIds.isEmpty() -> Text("Add at least one person besides you.", style = MaterialTheme.typography.bodySmall)
                        sum == null -> Text(if (partsMode) "Enter valid parts for everyone; at least one must be greater than zero." else "Enter a valid amount for every share.", style = MaterialTheme.typography.bodySmall)
                        sum < BigInteger.valueOf(total) -> Text("Remaining to assign: ${money(BigInteger.valueOf(total) - sum)}", style = MaterialTheme.typography.bodySmall)
                        sum > BigInteger.valueOf(total) -> Text("Shares exceed the expense by ${money(sum - BigInteger.valueOf(total))}", color = MaterialTheme.colorScheme.error)
                        else -> Text("${selectedIds.size + 1} people · Total ${money(total)}", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = {
                            if (busy) return@Button
                            busy = true
                            scope.launch {
                                try {
                                    onApply(values!![0], selectedIds.mapIndexed { i, id -> MemberShareInput(id, values[i + 1]) })
                                    presets.firstOrNull { it.id == selectedPresetId }?.let { onPresetApplied(it.name) }
                                    onDismiss()
                                }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (e: IllegalArgumentException) { error = e.message }
                                catch (_: Exception) { error = "Could not apply the split. Close and check the expense before retrying." }
                                finally { busy = false }
                            }
                        }, enabled = valid && !busy, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (busy) "Saving…" else "Apply split") }
                }
            }
        }
    }
    if (choosePreset) AlertDialog(onDismissRequest = { if (!busy) choosePreset = false }, title = { Text("Saved groups") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a local default, then check the preview before applying.", style = MaterialTheme.typography.bodySmall)
            presets.forEach { preset ->
                Column {
                    TextButton(enabled = !busy, onClick = {
                        try {
                            val preview = preset.preview(total)
                            ids = preview.others.map { it.memberId }; equal = preset.equal; byParts = false; parts = "{}"
                            amounts = JSONObject().put("you", shareDecimal(preview.personalMinor)).apply {
                                preview.others.forEach { put(it.memberId, shareDecimal(it.amountMinor)) }
                            }.toString()
                            selectedPresetId = preset.id; presetMessage = null; error = null; choosePreset = false
                        } catch (e: IllegalArgumentException) { error = e.message }
                    }) { Text("Use ${preset.name}") }
                    Text("${preset.members.size + 1} people · ${if (preset.equal) "Equal" else "Custom ratio"}", style = MaterialTheme.typography.bodySmall)
                    Text((listOf("You") + preset.members.map { member -> members.firstOrNull { it.id == member.memberId }?.name ?: "Missing person" }).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(enabled = !busy, onClick = { renamePresetId = preset.id; presetName = preset.name }) { Text("Rename") }
                        TextButton(enabled = !busy, onClick = { deletePreset = preset }) { Text("Delete") }
                    }
                }
            }
            if (presets.isEmpty()) Text("No saved groups. Build a split and save it as a default.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { choosePreset = false }, enabled = !busy) { Text("Done") } })
    if (savePreset || renamePresetId != null) {
        AlertDialog(onDismissRequest = { if (!busy) { savePreset = false; renamePresetId = null } },
            title = { Text(if (renamePresetId != null) "Rename saved group" else "Save group default") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerTextInput(presetName, { presetName = it.take(40); error = null }, label = "Saved group name",
                    singleLine = true, enabled = !busy)
                Text(if (renamePresetId != null) "Recorded shares and repayments do not change."
                    else if (equalMode) "Keep these people and an equal split for next time."
                    else "Keep these people and custom proportions. Amounts scale exactly to each expense; check the preview next time.",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(enabled = !busy && presetName.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        if (renamePresetId != null) repository.renameGroupPreset(renamePresetId!!, presetName)
                        else {
                            require(valid) { "Check the split before saving a default." }
                            // Preserve the entered ratio, not its rounded paise allocation.
                            val defaults = if (partsMode) weights!! else presets.firstOrNull { it.id == selectedPresetId }
                                ?.weightsForDefault(total, values!![0], selectedIds.mapIndexed { i, id -> MemberShareInput(id, values[i + 1]) }) ?: values!!
                            selectedPresetId = repository.saveGroupPreset(presetName, equalMode, defaults[0],
                                selectedIds.mapIndexed { i, id -> MemberShareInput(id, defaults[i + 1]) }, updatePresetId).id
                        }
                        presets = repository.groupPresets(); presetMessage = "Saved group default. Recorded expenses are unchanged."
                        savePreset = false; renamePresetId = null; error = null
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: IllegalArgumentException) { error = e.message }
                    catch (_: Exception) { error = "Could not save this group. Check saved groups before retrying." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Saving…" else "Save default") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { savePreset = false; renamePresetId = null }) { Text("Cancel") } })
    }
    deletePreset?.let { preset -> AlertDialog(onDismissRequest = { if (!busy) deletePreset = null }, title = { Text("Delete ${preset.name}?") },
        text = { Text("Only the reusable default is removed. Local people, recorded shares and repayments stay unchanged.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try {
                    repository.deleteGroupPreset(preset.id); presets = repository.groupPresets()
                    if (selectedPresetId == preset.id) selectedPresetId = null
                    deletePreset = null; error = null; presetMessage = "Saved default deleted. This split is unchanged."
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Could not delete the default. Check saved groups before retrying." }
                finally { busy = false }
            }
        }) { Text("Delete default") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deletePreset = null }) { Text("Cancel") } }) }
    if (choose) AlertDialog(onDismissRequest = { if (!busy) { focus.clearFocus(); choose = false } }, title = { Text("Choose people") },
        text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            LedgerTextInput(search, { search = it.take(40) }, label = "Search local names", singleLine = true, enabled = !busy)
            members.filter { it.name.contains(search, true) }.forEach { person ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(person.id in selectedIds, { checked ->
                        if (!checked) ids = ids - person.id
                        else if (person.id !in ids && ids.size < 20) { ids = ids + person.id; setAmount(person.id, "0") }
                    }, enabled = !busy && (person.id in selectedIds || selectedIds.size < 20), modifier = Modifier.semantics { contentDescription = "Include ${person.name} in split" })
                    Text(person.name, Modifier.weight(1f))
                    TextButton(onClick = { renameId = person.id; rename = person.name }, enabled = !busy) { Text("Rename") }
                }
            }
            if (members.isEmpty()) Text("No saved people yet. Add a local name below.")
            if (selectedIds.size < 20) {
                LedgerTextInput(newName, { newName = it.take(40); error = null }, label = "New local name",
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = ready && !busy)
                TextButton(onClick = {
                    if (busy || ids.size >= 20) return@TextButton
                    busy = true
                    scope.launch {
                        try {
                            val person = repository.addLocalMember(newName)
                            members = repository.localMembers()
                            if (person.id !in ids) { ids = ids + person.id; setAmount(person.id, "0") }
                            newName = ""; search = ""; focus.clearFocus()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (e: IllegalArgumentException) { error = e.message }
                        catch (_: Exception) { error = "Could not add this name. Check the people list before retrying." }
                        finally { busy = false }
                    }
                }, enabled = ready && !busy && newName.isNotBlank()) { Text("Add person") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { focus.clearFocus(); choose = false }, enabled = !busy) { Text("Done") } })
    if (renameId != null) AlertDialog(onDismissRequest = { if (!busy) renameId = null }, title = { Text("Rename local person") },
        text = { Column {
            LedgerTextInput(rename, { rename = it.take(40) }, label = "Local name", singleLine = true)
            Text("Their shares and repayments stay linked.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try { repository.renameLocalMember(renameId!!, rename); members = repository.localMembers(); renameId = null; error = null }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (e: IllegalArgumentException) { error = e.message }
                catch (_: Exception) { error = "Could not rename. Close and retry." }
                finally { busy = false }
            }
        }) { Text("Save name") } }, dismissButton = { TextButton(onClick = { renameId = null }, enabled = !busy) { Text("Cancel") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard split changes?") },
        text = { Text("The expense has not changed. New local names and saved group defaults remain available for future splits.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    if (remove) AlertDialog(onDismissRequest = { if (!busy) remove = false }, title = { Text("Remove individual shares?") },
        text = { Text("Your share and the total owed stay the same. Unlink any existing repayments first; incoming payments are kept.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try { onApply(personal, emptyList()); onDismiss() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (e: IllegalArgumentException) { error = e.message; remove = false }
                catch (_: Exception) { error = "Could not remove the split. Close and check the expense."; remove = false }
                finally { busy = false }
            }
        }) { Text("Remove shares") } }, dismissButton = { TextButton(onClick = { remove = false }, enabled = !busy) { Text("Cancel") } })
}

@Composable private fun SplitPartRow(name: String, value: String, calculated: Long?, enabled: Boolean, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(calculated?.let(::money) ?: "—", style = MaterialTheme.typography.titleSmall)
        }
        LedgerTextInput(value, onChange, label = "Parts for $name", singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(130.dp), enabled = enabled)
    }
}

@Composable private fun SplitShareRow(name: String, fixed: Long?, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val avatarColor = if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFF332E43) else Color(0xFFECE6F7)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(35.dp).background(avatarColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (fixed != null) Text(money(fixed), style = MaterialTheme.typography.titleSmall)
        else LedgerTextInput(value, { onChange(it.take(19)) }, label = "Share for $name (INR)", showLabel = false, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(113.dp), enabled = enabled)
    }
}

@Composable private fun SplitPreviewRow(label: String, amount: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(amount, style = MaterialTheme.typography.titleSmall)
    }
}

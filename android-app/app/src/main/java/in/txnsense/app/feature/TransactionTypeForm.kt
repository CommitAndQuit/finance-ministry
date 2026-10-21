package `in`.txnsense.app.feature

import `in`.txnsense.app.core.model.TransactionType
import `in`.txnsense.app.data.PaymentSourceEntity
import `in`.txnsense.app.data.TransactionRepository
import androidx.compose.runtime.*

internal data class FormPaymentSources(val sources: List<PaymentSourceEntity>, val ready: Boolean,
    val failed: Boolean, val retry: () -> Unit)

/** Keep asynchronous lookup/error state separate from the editable financial fields. */
@Composable
internal fun paymentSourcesForForm(repository: TransactionRepository, recordId: String?): FormPaymentSources {
    var sources by remember(repository, recordId) { mutableStateOf<List<PaymentSourceEntity>>(emptyList()) }
    var ready by remember(repository, recordId) { mutableStateOf(false) }
    var failed by remember(repository, recordId) { mutableStateOf(false) }
    var attempt by remember(repository, recordId) { mutableStateOf(0) }
    LaunchedEffect(repository, recordId, attempt) {
        failed = false
        try { sources = repository.paymentSources(); ready = true }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    return FormPaymentSources(sources, ready, failed) { attempt++ }
}

/** Resolve only a user-confirmed ordinary payment, never an ambiguous card credit. */
internal fun transactionTypeForSave(type: String, direction: String, channel: String, creditCardSource: Boolean): TransactionType {
    val identified = TransactionType.valueOf(type)
    if (identified != TransactionType.Unknown) return identified
    require(direction != "Credit" || (channel != "Card" && !creditCardSource)) {
        "Choose card bill payment, refund, or other money in."
    }
    return TransactionType.Other
}

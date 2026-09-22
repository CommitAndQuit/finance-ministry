package `in`.txnsense.app.data

import `in`.txnsense.app.core.BankRegistry

/** Conservative source resolution. Missing evidence is allowed; contradictory evidence is not. */
object PaymentSourceMatcher {
    /** Card variants are interchangeable for matching: an SMS may not state credit vs debit. */
    private val cardFamily = setOf("Card", "CreditCard", "DebitCard")

    private fun channelMatches(sourceChannel: String, txChannel: String): Boolean =
        sourceChannel == txChannel || (sourceChannel in cardFamily && txChannel in cardFamily)

    fun resolve(channel: String, maskedHint: String?, sender: String, sources: List<PaymentSourceEntity>): String? {
        val last4 = maskedHint?.takeLast(4)?.takeIf { it.matches(Regex("[0-9]{4}")) }
        val recognizedSenderBanks = BankRegistry.detect(sender)?.let { setOf(it) } ?: emptySet()
        val eligible = sources.asSequence().filter { it.active && channelMatches(it.channel, channel) }.filter { source ->
            (last4 == null || source.last4 == null || source.last4 == last4) &&
                (recognizedSenderBanks.isEmpty() || source.bankName == null || BankRegistry.key(source.bankName) in recognizedSenderBanks)
        }.toList()
        if (eligible.isEmpty()) return null

        val positiveLast4 = last4?.let { digits -> eligible.filter { it.last4 == digits } }.orEmpty()
        if (positiveLast4.size == 1) return positiveLast4.single().id
        if (positiveLast4.size > 1) return null

        val positiveBank = if (recognizedSenderBanks.isEmpty()) emptyList() else eligible.filter {
            it.bankName?.let(BankRegistry::key) in recognizedSenderBanks
        }
        if (positiveBank.size == 1) return positiveBank.single().id
        if (positiveBank.size > 1) return null

        // A sole source is safe only when the message contains no source identifier that
        // contradicts it. Identifiers absent on the registered source remain unresolved.
        return eligible.singleOrNull()?.takeIf { source ->
            (last4 == null || source.last4 == last4) &&
                (recognizedSenderBanks.isEmpty() || source.bankName?.let(BankRegistry::key) in recognizedSenderBanks)
        }?.id
    }
}

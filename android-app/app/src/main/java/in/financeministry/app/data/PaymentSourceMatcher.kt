package `in`.financeministry.app.data

/** Conservative source resolution. Missing evidence is allowed; contradictory evidence is not. */
object PaymentSourceMatcher {
    private val bankAliases = mapOf(
        "HDFC" to listOf("HDFC"),
        "ICICI" to listOf("ICICI"),
        "SBI" to listOf("SBI", "STATEBANKOFINDIA"),
        "BOB" to listOf("BOB", "BANKOFBARODA"),
        "KOTAK" to listOf("KOTAK"),
        "AXIS" to listOf("AXIS"),
        "PNB" to listOf("PNB", "PUNJABNATIONALBANK"),
        "YES" to listOf("YESBANK"),
        "IDFC" to listOf("IDFC"),
        "FEDERAL" to listOf("FEDERAL"),
        "INDUSIND" to listOf("INDUSIND"),
        "CANARA" to listOf("CANARA"),
        "UNION" to listOf("UNIONBANK"),
        "RBL" to listOf("RBL"),
    )

    fun resolve(channel: String, maskedHint: String?, sender: String, sources: List<PaymentSourceEntity>): String? {
        val last4 = maskedHint?.takeLast(4)?.takeIf { it.matches(Regex("[0-9]{4}")) }
        val senderKey = normalize(sender)
        val recognizedSenderBanks = bankAliases.filterValues { aliases -> aliases.any(senderKey::contains) }.keys
        val eligible = sources.asSequence().filter { it.active && it.channel == channel }.filter { source ->
            (last4 == null || source.last4 == null || source.last4 == last4) &&
                (recognizedSenderBanks.isEmpty() || source.bankName == null || bankKey(source.bankName) in recognizedSenderBanks)
        }.toList()
        if (eligible.isEmpty()) return null

        val positiveLast4 = last4?.let { digits -> eligible.filter { it.last4 == digits } }.orEmpty()
        if (positiveLast4.size == 1) return positiveLast4.single().id
        if (positiveLast4.size > 1) return null

        val positiveBank = if (recognizedSenderBanks.isEmpty()) emptyList() else eligible.filter {
            it.bankName?.let(::bankKey) in recognizedSenderBanks
        }
        if (positiveBank.size == 1) return positiveBank.single().id
        if (positiveBank.size > 1) return null

        // A sole source is safe only when the message contains no source identifier that
        // contradicts it. Identifiers absent on the registered source remain unresolved.
        return eligible.singleOrNull()?.takeIf { source ->
            (last4 == null || source.last4 == last4) &&
                (recognizedSenderBanks.isEmpty() || source.bankName?.let(::bankKey) in recognizedSenderBanks)
        }?.id
    }

    private fun bankKey(name: String): String {
        val normalized = normalize(name)
        return bankAliases.entries.firstOrNull { (_, aliases) -> aliases.any(normalized::contains) }?.key ?: normalized
    }

    private fun normalize(value: String): String = value.uppercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit)
}

package `in`.txnsense.app.core

import java.util.Locale

/**
 * Shared registry of bank aliases used to infer the issuing institution from an SMS
 * sender id or body. Kept in one place so the parser and the payment-source matcher
 * agree on what counts as "the same bank".
 */
object BankRegistry {
    val aliases: Map<String, List<String>> = mapOf(
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

    /**
     * Senders that only ever issue credit cards (distinct entities from the parent bank),
     * so a generic "Card" alert from them is a credit card. Debit-card alerts arrive from
     * ordinary bank senders, which are deliberately excluded here.
     */
    private val creditCardIssuers = listOf("SBICRD", "SBICARD", "ONECARD", "ONECRD", "AMEX")

    fun normalize(value: String): String = value.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    /** True when [sender] is a credit-card-only issuer id (e.g. "VM-SBICRD-T"). */
    fun isCreditCardIssuer(sender: String): Boolean {
        val normalized = normalize(sender)
        return creditCardIssuers.any(normalized::contains)
    }

    /** Canonical bank key contained in [text] (an SMS sender id or body), or null when none match. */
    fun detect(text: String): String? {
        val normalized = normalize(text)
        return aliases.entries.firstOrNull { (_, matches) -> matches.any(normalized::contains) }?.key
    }

    /** Canonical bank key for a stored bank name, falling back to its normalized form. */
    fun key(name: String): String {
        val normalized = normalize(name)
        return aliases.entries.firstOrNull { (_, matches) -> matches.any(normalized::contains) }?.key ?: normalized
    }
}

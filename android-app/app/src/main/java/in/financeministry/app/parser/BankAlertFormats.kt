package `in`.financeministry.app.parser

/** Completed account-payment grammar, independently authored from public-format research.
 * A payment verb alone is insufficient: these layouts require a source and dated movement.
 */
internal object BankAlertFormats {
    private const val money = """(?:rs\.?|inr|₹)\s*:?[ \t]*[\d,.]+"""
    private const val date = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-/](?:\d{1,2}|[a-z]{3})[-/]\d{2,4})(?!\d)"""
    private const val masked = """[x*•]+\d{0,6}(?<last4>\d{4})(?![a-z0-9])"""
    private const val account = """(?:a/c|ac|acct\.?|account)\s+(?:no\.?\s*)?$masked"""
    private const val vpa = """(?<merchant>[a-z0-9._-]{1,60})@[a-z0-9.-]{1,40}"""
    private val transferDebit = Regex("""^(?:dear customer,\s*)?your\s+$account\s+has a debit by transfer of\s+$money\s+on\s+$date""", RegexOption.IGNORE_CASE)
    private val upiDebit = Regex("""^debit\s+$money\s+$account\s+on\s+$date\.\s*info-\s*upi/p[12][mp]/\d{12}/(?<merchant>[^.\r\n]{1,80})\.""", RegexOption.IGNORE_CASE)
    // Same completed-payment grammar whether the issuer wraps it over lines or
    // names a shop rather than a VPA. Do not infer a debit from "sent" alone.
    private val sentContext = Regex("""^sent\s+$money\s+from\b""", RegexOption.IGNORE_CASE)
    private val sent = Regex("""^sent\s+$money\s+from\s+[a-z][a-z .&-]{0,59}\b$account\s+to\s+(?<merchant>[a-z0-9][a-z0-9 .&'+/_@()-]{0,79}?)\s+on\s+$date\.?\s+(?:upi\s+)?ref\s*(?:no\.?\s*)?:?\s*\d{12}(?=\.?(?:\s|$))""", RegexOption.IGNORE_CASE)
    private val drCrVpa = Regex("""^$money\s+dr\.\s+from\s+$account\s+and\s+cr\.\s+to\s+$vpa\.?\s+ref:\s*\d{8,24}(?=\.?(?:\s|$))""", RegexOption.IGNORE_CASE)
    private val deducted = Regex("""^an amount of\s+$money\s+has been deducted from your account ending\s+(?:with\s+)?(?<last4>\d{4})(?!\d)\s+on\s+$date""", RegexOption.IGNORE_CASE)
    val netBanking = Regex("""^thanks for paying\s+$money\s+from\s+$account\s+to\s+(?<merchant>[^\r\n]{1,80}?)\s+via\s+[a-z ]{1,60}\bnetbanking\b""", RegexOption.IGNORE_CASE)
    private val paid = Regex("""^$money\s+paid to\s+$vpa\s+from\s+$account\s+on\s+$date\.\s*upi\s+ref\s+(?:no\.?\s*)?\d{12}(?!\d)""", RegexOption.IGNORE_CASE)
    // Some Indian UPI alerts omit the currency marker. Accept only this complete
    // account/debit/date/recipient/reference grammar, irrespective of issuer name.
    private val unmarkedUpiContext = Regex("""^dear\s+upi\s+user\s+$account\s+debited\s+by\s+(?!rs\b|inr\b|₹)""", RegexOption.IGNORE_CASE)
    private val unmarkedUpi = Regex(
        """^dear\s+upi\s+user\s+$account\s+debited\s+by\s+(?<amount>[+-]?[\d,.]+)\s+on\s+date\s+\d{1,2}(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\d{2}(?!\d)\s+trf\s+to\s+(?<merchant>[a-z0-9][a-z0-9 .&'/_@()-]{0,79}?)\s+refno\s+\d{12}(?![a-z0-9])""" +
            """(?:\s+if\s+not\s+(?:u|you)\?\s*call\s*[-:]?\s*\d{8,15}(?:\s+for\s+other\s+services\s*[-:]?\s*\d{5,15})?(?:\s*-\s*[a-z][a-z ]{0,39})?)?\s*\.?$""",
        RegexOption.IGNORE_CASE,
    )
    private val unsafeRecipient = Regex("""\b(?:debited|credited|account|pending|refund|reversed|inr|rs|usd|eur|gbp)\b""", RegexOption.IGNORE_CASE)
    fun currencylessUpiContext(text: String): Boolean = unmarkedUpiContext.containsMatchIn(text)
    fun currencylessUpi(text: String): MatchResult? = unmarkedUpi.matchEntire(text)?.takeUnless {
        unsafeRecipient.containsMatchIn(it.groups["merchant"]!!.value)
    }
    private fun sentMatch(text: String) = sent.find(text)?.takeUnless {
        unsafeRecipient.containsMatchIn(it.groups["merchant"]!!.value)
    }
    fun incompleteSent(text: String): Boolean = sentContext.containsMatchIn(text) && sentMatch(text) == null
    fun hasUpiEvidence(text: String): Boolean = drCrVpa.containsMatchIn(text) ||
        sentMatch(text)?.groups?.get("merchant")?.value?.contains('@') == true
    private val namedLayouts = listOf(upiDebit, drCrVpa, netBanking, paid)
    fun match(text: String): MatchResult? = (listOf(transferDebit, deducted) + namedLayouts).firstNotNullOfOrNull { it.find(text) }
        ?: sentMatch(text) ?: currencylessUpi(text)
    fun counterparty(text: String): String? = sentMatch(text)?.groups?.get("merchant")?.value?.trim()?.substringBefore('@')
        ?: namedLayouts.firstNotNullOfOrNull { it.find(text)?.groups?.get("merchant")?.value?.trim() }
        ?: currencylessUpi(text)?.groups?.get("merchant")?.value?.trim()
}

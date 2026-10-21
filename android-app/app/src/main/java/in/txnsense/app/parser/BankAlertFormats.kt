package `in`.txnsense.app.parser

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
    private val sent = Regex("""^sent\s+$money\s+from\s+[a-z ]{1,60}\b$account\s+to\s+$vpa\s+on\s+$date\.\s*upi\s+ref\s+\d{12}(?!\d)""", RegexOption.IGNORE_CASE)
    private val deducted = Regex("""^an amount of\s+$money\s+has been deducted from your account ending\s+(?:with\s+)?(?<last4>\d{4})(?!\d)\s+on\s+$date""", RegexOption.IGNORE_CASE)
    val netBanking = Regex("""^thanks for paying\s+$money\s+from\s+$account\s+to\s+(?<merchant>[^\r\n]{1,80}?)\s+via\s+[a-z ]{1,60}\bnetbanking\b""", RegexOption.IGNORE_CASE)
    private val paid = Regex("""^$money\s+paid to\s+$vpa\s+from\s+$account\s+on\s+$date\.\s*upi\s+ref\s+(?:no\.?\s*)?\d{12}(?!\d)""", RegexOption.IGNORE_CASE)
    private val namedLayouts = listOf(upiDebit, sent, netBanking, paid)
    fun match(text: String): MatchResult? = (listOf(transferDebit, deducted) + namedLayouts).firstNotNullOfOrNull { it.find(text) }
    fun counterparty(text: String): String? = namedLayouts.firstNotNullOfOrNull { it.find(text)?.groups?.get("merchant")?.value?.trim() }
}

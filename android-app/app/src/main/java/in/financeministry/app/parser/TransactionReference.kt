package `in`.financeministry.app.parser

/** Transient only: callers must HMAC this value before persisting it. */
object TransactionReference {
    private val label = Regex(
        """(?<![\w@])(?:(?<reference>ref(?:erence)?|rrn|utr)(?:\s*no\.?)?\b\s*[:#.-]?|(?<upi>upi)\s*:)\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val numericReference = Regex("""^(\d{8,24})(?=$|\s|[.;,)\]])(?!\.\d)""")
    private val numericUpiReference = Regex("""^(\d{12})(?=$|\s|[.;,)\]]|-[A-Za-z])(?!\.\d)""")

    fun extract(body: String): String? {
        // A support/security footer does not identify the payment. Keep leading zeroes,
        // and never shorten malformed, alphanumeric or decimal tokens into evidence.
        val text = ParserRules.transactionText(body)
        val references = label.findAll(text).map { match ->
            val format = if (match.groups["upi"] != null) numericUpiReference else numericReference
            format.find(text.substring(match.range.last + 1))?.groupValues?.get(1)
        }.toList()
        if (references.any { it == null }) return null
        return references.distinct().singleOrNull()
    }
}

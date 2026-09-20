package `in`.financeministry.app.parser

internal object ParserRules {
    // Banks append a fraud-reporting footer ("Not you? OTP 123456 to block ...") that carries its
    // own codes and amounts. It is not transaction evidence, so templates never see it.
    private val securityFooter = Regex("""(?:^|[\r\n.!?])\s*not you\?""", RegexOption.IGNORE_CASE)

    fun transactionText(body: String): String =
        securityFooter.find(body)?.let { body.substring(0, it.range.first) }?.trim() ?: body.trim()
}

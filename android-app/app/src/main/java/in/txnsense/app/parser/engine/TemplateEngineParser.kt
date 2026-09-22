package `in`.txnsense.app.parser.engine

import `in`.txnsense.app.core.BankRegistry
import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseAssessment
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.core.model.TransactionType
import `in`.txnsense.app.parser.ParserRules
import `in`.txnsense.app.parser.induction.RegexSafety
import java.math.BigInteger

/**
 * Template-only SMS classifier.
 *
 * One pass: strip any security footer, then try each template in [TemplateRepository] in order. The
 * first pattern that matches decides the transaction and returns immediately; a message no template
 * recognizes is rejected. There are no heuristics and no pre-filters — coverage and accuracy come
 * from adding templates.
 *
 * [learned] holds layouts the app induced for itself. They are tried only once every curated
 * template has declined, so learning can add coverage but can never change an answer the curated
 * templates already give, and each is time-boxed while matching because nothing hand-audited it.
 */
class TemplateEngineParser(private val learned: List<ParsingTemplate> = emptyList()) {

    fun parse(input: IncomingSms): ParseAssessment {
        val assessment = assess(ParserRules.transactionText(input.body))
        if (assessment.decision == ParseDecision.Reject) return assessment
        // Sender id is the most reliable bank signal; fall back to the body when it is opaque.
        val bank = BankRegistry.detect(input.sender) ?: BankRegistry.detect(input.body)
        var result = if (bank == null) assessment else assessment.copy(bankName = bank)
        // A generic card alert from a credit-card-only issuer (e.g. SBI Cards) is a credit card.
        if (result.channel == Channel.Card && BankRegistry.isCreditCardIssuer(input.sender)) {
            result = result.copy(channel = Channel.CreditCard)
        }
        return result
    }

    private fun assess(text: String): ParseAssessment {
        for (template in TemplateRepository.templates) {
            val match = template.regex.find(text) ?: continue
            if (template.accept?.invoke(match) == false) continue
            return matched(template, match)
        }
        for (template in learned) {
            val match = RegexSafety.findWithin(template.regex, text) ?: continue
            if (template.accept?.invoke(match) == false) continue
            return matched(template, match)
        }
        return rejected("no_template_match")
    }

    private fun matched(template: ParsingTemplate, match: MatchResult): ParseAssessment {
        val amount = match.namedOrNull("amount")?.let(::parseMinorUnits)
        // A recognized layout whose amount cannot be read safely is still worth keeping, for review.
        val decision = if (amount == null) ParseDecision.NeedsReview else template.decision
        val ruleId = when {
            amount == null && template.decision == ParseDecision.Record -> "decisive_missing_safe_amount"
            else -> template.ruleId
        }
        return ParseAssessment(
            decision = decision,
            amountMinor = amount,
            currency = if (amount == null) null else "INR",
            direction = template.direction,
            status = template.status,
            channel = template.channel,
            transactionType = template.transactionType,
            maskedAccountHint = match.namedOrNull("account")?.let { "••••$it" },
            counterpartyLabel = match.namedOrNull("merchant")?.trim()?.ifBlank { null },
            confidence = if (decision == ParseDecision.Record) 96 else 70,
            ruleId = ruleId,
            parserVersion = 6,
        )
    }

    /** Rupees-and-paise string to minor units; null when the text is not a safe single amount. */
    private fun parseMinorUnits(raw: String): Long? {
        if (raw.startsWith("+") || raw.startsWith("-")) return null
        val parts = raw.split('.')
        if (parts.size > 2) return null
        val integer = parts[0]
        if (!isValidInteger(integer)) return null
        val fraction = parts.getOrElse(1) { "" }
        if (fraction.length > 2 || !fraction.all(Char::isDigit)) return null

        val whole = BigInteger(integer.replace(",", ""))
        val minor = whole.multiply(BigInteger.valueOf(100)).add(BigInteger((fraction + "00").take(2)))
        return if (minor > BigInteger.ZERO && minor <= BigInteger.valueOf(Long.MAX_VALUE)) minor.toLong() else null
    }

    /** Plain digits, or Indian digit grouping such as "1,25,000". */
    private fun isValidInteger(value: String): Boolean =
        value.matches(Regex("""\d+""")) || value.matches(Regex("""\d{1,3}(?:,\d{2})*,\d{3}"""))

    private fun rejected(ruleId: String): ParseAssessment = ParseAssessment(
        decision = ParseDecision.Reject,
        amountMinor = null,
        currency = null,
        direction = Direction.Unknown,
        status = TransactionStatus.Unknown,
        channel = Channel.Unknown,
        transactionType = TransactionType.Unknown,
        maskedAccountHint = null,
        counterpartyLabel = null,
        confidence = 100,
        ruleId = ruleId,
        parserVersion = 6,
    )
}

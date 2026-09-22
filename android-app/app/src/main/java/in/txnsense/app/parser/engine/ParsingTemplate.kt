package `in`.txnsense.app.parser.engine

import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.core.model.TransactionType

/**
 * One SMS layout the parser recognizes.
 *
 * A template is the only thing that can classify a message: the regex decides *whether* the layout
 * matched, and the fixed fields decide *what* it means. Named groups supply the variable parts —
 * `amount` (required for a recorded transaction), `account` (masked last-4) and `merchant`.
 *
 * The pattern is compiled once, when the template is constructed.
 */
data class ParsingTemplate(
    val templateId: String,
    val regexPattern: String,
    val direction: Direction,
    val status: TransactionStatus,
    val channel: Channel,
    val transactionType: TransactionType,
    val decision: ParseDecision = ParseDecision.Record,
    val ruleId: String = "template_match",
    /**
     * Optional sanity check on the captured groups, for layouts a regex cannot fully express.
     * Returning false makes the engine keep looking at later templates.
     */
    val accept: ((MatchResult) -> Boolean)? = null,
) {
    val regex: Regex = Regex(regexPattern, RegexOption.IGNORE_CASE)
}

/** Named group value, or null when this pattern has no such group. */
internal fun MatchResult.namedOrNull(name: String): String? =
    runCatching { groups[name]?.value }.getOrNull()

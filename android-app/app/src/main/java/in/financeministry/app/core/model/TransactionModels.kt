package `in`.financeministry.app.core.model

/** Transient parser input. Its diagnostic form intentionally excludes private SMS content. */
class IncomingSms(
    val sender: String,
    val receivedAtMillis: Long,
    val body: String,
) {
    override fun toString(): String = "IncomingSms(redacted)"
}

enum class ParseDecision { Record, NeedsReview, Reject }
enum class Direction { Debit, Credit, Transfer, Unknown }
enum class TransactionStatus { Successful, Failed, Reversed, Pending, Unknown }
/**
 * How money moved. [Card] stays as a generic value for card alerts that do not state
 * credit vs debit; [CreditCard]/[DebitCard]/[Wallet]/[NetBanking] are used only when the
 * SMS is explicit, so existing ledgers that stored [Card] remain valid.
 */
enum class Channel { UPI, Card, CreditCard, DebitCard, Wallet, NetBanking, ATM, IMPS, NEFT, RTGS, BankTransfer, CashManual, Other, Unknown }
enum class TransactionType { MerchantPayment, P2PTransfer, SelfTransfer, CardRepayment, SalaryIncome, Refund, Reversal, CashWithdrawal, Deposit, FeeCharge, Other, Unknown }
enum class SourceType { SMS, Manual }
enum class ReviewState { AutoRecorded, NeedsReview, Confirmed }
/** Who ultimately bears this cost. Kept separate from the payment channel and category. */
enum class SpendingOwnership { Personal, ForOther, Group, SelfTransfer }

data class ParseAssessment(
    val decision: ParseDecision,
    val amountMinor: Long?,
    val currency: String?,
    val direction: Direction,
    val status: TransactionStatus,
    val channel: Channel,
    val transactionType: TransactionType,
    val maskedAccountHint: String?,
    val counterpartyLabel: String?,
    val confidence: Int,
    val ruleId: String,
    val parserVersion: Int = 1,
    /** Issuing bank inferred from the SMS sender or body, when recognized. */
    val bankName: String? = null,
)

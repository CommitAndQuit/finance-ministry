package `in`.txnsense.app.parser.induction

/**
 * The fixed set of words a skeleton is allowed to keep.
 *
 * This list does two jobs at once, which is why the induction approach works without a language
 * model in the loop:
 *
 * 1. **Redaction.** Any word outside this set is replaced by a [Slot.Word] blank. Merchant names,
 *    payee names, people, cities and product names are out of vocabulary by construction, so a
 *    stored skeleton cannot carry them.
 * 2. **Slot discovery.** The words that *are* kept are exactly the invariant parts of a bank's
 *    layout — that is what makes a layout a template. So the surviving words form the literal spine
 *    the inducer aligns on, and the redacted positions are precisely the fields worth capturing.
 *
 * [disqualifiers] are deliberately in vocabulary even though they never appear in a transaction
 * layout: the labeller can only refuse an OTP, a promotion or a due-date reminder if it can see
 * those words in the spine. A word removed from here becomes invisible, not harmless.
 */
object BankVocabulary {

    /** Movement verbs and their inflections. A layout with none of these is not money moving. */
    val movement: Set<String> = setOf(
        "debited", "debit", "credited", "credit", "spent", "spend", "paid", "pay", "payment",
        "withdrawn", "withdrawal", "transferred", "transfer", "sent", "received", "charged",
        "charge", "deducted", "refunded", "refund", "reversed", "reversal", "purchase",
        "deposited", "deposit", "repaid", "repayment", "settled", "dr", "cr",
    )

    /** Words that describe how the money moved. */
    val channels: Set<String> = setOf(
        "upi", "vpa", "imps", "neft", "rtgs", "atm", "wallet", "card", "cards", "cardmember",
        "netbanking", "pos", "ecom", "online", "cash", "cheque", "chq", "nach", "ach", "w/d",
    )

    /** Words that describe the outcome. */
    val outcomes: Set<String> = setOf(
        "successful", "success", "successfully", "completed", "processed", "posted", "done",
        "declined", "failed", "unsuccessful", "rejected", "pending", "initiated", "reversed",
    )

    /**
     * Words that mean this message is not a completed money movement. Kept in vocabulary so the
     * labeller can see them and refuse the cluster.
     */
    val disqualifiers: Set<String> = setOf(
        "otp", "password", "code", "verification", "verify", "offer", "offers", "congratulations",
        "congrats", "apply", "eligible", "pre-approved", "preapproved", "upgrade", "reward",
        "rewards", "points", "win", "free", "click", "download", "register", "activate",
        "expire", "expires", "expiring", "renewal", "reminder", "overdue", "due", "generated",
        "statement", "mandate", "autopay", "emi", "will", "shortly", "requested", "request",
        "processing", "scheduled", "quote", "premium", "policy", "insurance", "loan", "invest",
        "nav", "units", "folio", "subscription", "cashback", "voucher", "coupon", "discount",
    )

    /** Structural nouns, glue words and bank/issuer tokens that make up the rest of a spine. */
    private val structure: Set<String> = setOf(
        "a/c", "ac", "acc", "acct", "account", "accounts", "bank", "txn", "transaction",
        "transactions", "ref", "reference", "no", "number", "balance", "bal", "avl", "available",
        "limit", "lmt", "amt", "amount", "rs", "inr", "date", "dt", "time", "ist", "info",
        "ending", "last", "digits", "xx", "towards", "against", "beneficiary", "payee",
        "recipient", "merchant", "self", "own", "linked", "updated", "total", "utr", "rrn",
        "mmid", "trf", "alert", "bill", "fee", "fees", "charges", "interest", "salary",
        "on", "at", "to", "from", "for", "in", "of", "by", "with", "via", "thru", "through",
        "using", "and", "or", "is", "was", "has", "have", "been", "be", "your", "you", "ur",
        "the", "a", "an", "not", "this", "that", "it", "if", "as", "now", "new", "dear",
        "customer", "call", "sms", "dispute", "block", "report", "avlbl", "clear",
    )

    /** Issuer and wallet names. A layout is bank-specific, so its brand word belongs in the spine. */
    private val issuers: Set<String> = setOf(
        "hdfc", "icici", "sbi", "bob", "bobcard", "kotak", "axis", "pnb", "yes", "idfc",
        "federal", "indusind", "canara", "union", "rbl", "onecard", "sbicard", "amex", "hsbc",
        "pluxee", "sodexo", "paytm", "phonepe", "gpay", "amazon", "apay", "cred", "meal",
    )

    /** Every word a skeleton may keep. */
    val words: Set<String> =
        movement + channels + outcomes + disqualifiers + structure + issuers

    /**
     * Literals that precede a masked account number, used to recognize a bare four-digit group as
     * an account hint rather than an ordinary number.
     */
    val accountLeadIns: Set<String> = setOf(
        "a/c", "ac", "acc", "acct", "account", "card", "ending", "no", "xx", "bobcard",
    )

    /** Literals after which an out-of-vocabulary run names a counterparty. */
    val counterpartyLeadIns: Set<String> = setOf("at", "to", "from", "vpa", "towards", "payee", "info")

    /** Literals that mark a nearby amount as a balance or a limit rather than the transacted value. */
    val balanceMarkers: Set<String> = setOf("balance", "bal", "avl", "available", "limit", "lmt", "avlbl", "total")
}

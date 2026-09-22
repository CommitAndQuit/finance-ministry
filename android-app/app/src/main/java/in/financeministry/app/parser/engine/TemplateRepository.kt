package `in`.financeministry.app.parser.engine

import `in`.financeministry.app.core.model.Channel
import `in`.financeministry.app.core.model.Direction
import `in`.financeministry.app.core.model.ParseDecision
import `in`.financeministry.app.core.model.TransactionStatus
import `in`.financeministry.app.core.model.TransactionType

/** INR currency prefix. Foreign currencies are handled by the two `foreign_currency_*` templates. */
private const val CUR = """(?:rs\.?|inr|₹)"""
private const val MONEY = """$CUR\s*(?<amount>[\d,.]+)"""
private const val DATE = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-/](?:\d{1,2}|[a-z]{3})[-/]\d{2,4})(?!\d)"""
private const val FOREIGN = """\b(?:usd|eur|gbp|aed|sgd|aud|cad|jpy|chf|sar|hkd|cny)\b"""

/** Optional "… account XX1234" tail on otherwise bare movement lines. */
private const val ACCT_TAIL = """(?:\s+(?:a/c|acc(?:ount|t)?)\s+[x*•]*(?<account>\d{4})(?!\d))?"""

// Card-alert building blocks. Three amount phrasings × three merchant/date tails; each combination
// is its own template so every pattern can use the same `amount`/`account`/`merchant` group names.
private const val CARD_SPENT = """^(?:(?:alert:|transaction successful!)\s*)?spent\s+$MONEY\s+on\s+"""
private const val CARD_IS_SPENT =
    """^(?:(?:alert:|transaction successful!)\s*)?$MONEY\s+(?:(?:is|was)\s+)?spent\s+(?:on|using)\s+"""
private const val CARD_CHARGED =
    """^(?:(?:alert:|transaction successful!)\s*)?$MONEY\s+has been charged to\s+"""
private const val CARD_MASK =
    """(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account>\d{4})(?!\d)"""
private const val TAIL_AT_ON = """(?:\s+for\s+[^\r\n]{1,50}?)?\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+$DATE"""
private const val TAIL_ON_AT = """\s+on\s+$DATE\s+at\s+(?<merchant>[^\r\n]{1,80}?)(?=\.\s|$)"""
private const val TAIL_AT_SIGN = """\s+@(?<merchant>[^\r\n]{1,80}?)\s+$DATE"""

/**
 * UPI payees that settle a credit-card bill. A debit to one of them repays card spends the ledger
 * has already recorded, so it is a `CardRepayment` and must never be counted as a spend again.
 */
private const val CARD_BILL_PAYEE = """cred(?:\s+club)?"""

/** Any payee name, for the same layouts when the counterparty is an ordinary merchant or person. */
private const val ANY_PAYEE = """[a-z][a-z0-9 .&'-]{0,79}?"""

/** ICICI account debit naming who was credited; [payee] decides which counterparties match. */
private fun iciciRecipientCredit(payee: String) =
    """^icici bank acct\s+[x*]+(?<account>\d{3,4})\s+debited for\s+$MONEY\s+on\s+""" +
        """\d{1,2}-[a-z]{3}-\d{2,4};\s*(?<merchant>$payee)\s+credited\.\s*upi:\d{9,14}\.""" +
        """(?:\s*(?:call|sms|to dispute)[\s\S]*)?$"""

/** PNB account debit naming who was paid; [payee] decides which counterparties match. */
private fun pnbUpiDebit(payee: String) =
    """^a/c\s+[x*]+(?<account>\d{4})\s+debited\s+$MONEY\s+dt\s+\d{1,2}-\d{1,2}-\d{2,4}\s+[\d:]+""" +
        """\s+to\s+(?<merchant>$payee)\s+thru\s+upi\b"""

/**
 * Every SMS layout the parser knows, in match order: first match wins, so specific bank layouts come
 * before generic ones. Patterns are compiled once, when this object is first touched.
 *
 * Accuracy is grown by adding templates here — there are no heuristics behind them.
 */
object TemplateRepository {

    val templates: List<ParsingTemplate> = buildList {
        addAll(currencyGuards())
        addAll(bankAndCardLayouts())
        addAll(cardAlerts())
        addAll(accountLayouts())
        addAll(genericMovements())
    }

    /**
     * A non-INR amount must never be booked as rupees, so a foreign-currency spend is flagged for
     * review before any INR template can capture its digits. This is the one template pair that
     * exists to stop a wrong reading rather than to recognize a layout.
     */
    private fun currencyGuards(): List<ParsingTemplate> = listOf(
        ParsingTemplate(
            templateId = "foreign_currency_spend",
            regexPattern = """\b(?:spent|charged|debited|paid)\b[^\r\n]{0,40}?$FOREIGN\s*[\d,.]+""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "unsupported_currency"
        ),
        ParsingTemplate(
            templateId = "foreign_currency_spend_reversed",
            regexPattern = """$FOREIGN\s*[\d,.]+[^\r\n]{0,60}?\b(?:spent|charged|debited|paid)\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "unsupported_currency"
        ),
    )

    private fun bankAndCardLayouts(): List<ParsingTemplate> = listOf(
        ParsingTemplate(
            templateId = "bob_debit",
            regexPattern = """^$MONEY\s+dr\.\s+from\s+a/c\s+[x*]+(?<account>\d{3,4})\s+and\s+cr\.\s+to\s+(?<merchant>\S+)\s+ref:\d{8,24}\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "axis_card",
            regexPattern = """^spent\s+$MONEY\s*\r?\naxis bank card no\.\s+[x*]+(?<account>\d{4})\s*\r?\n\d{2}-\d{2}-\d{2,4}\s+\d{2}:\d{2}:\d{2}\s+ist\s*\r?\n(?<merchant>[^\r\n]+)\r?\navl limit:""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Card,
            transactionType = TransactionType.MerchantPayment
        ),
        // Axis multi-line card spend. Two layouts:
        //   A: "Spent\n Card no. XXnnnn\n INR amt\n date time\n merchant\n Avl Lmt ..."
        //   B: "Spent\n Axis Bank Card no. XXnnnn\n INR amt date time IST\n merchant\n Avl Lmt ..."
        ParsingTemplate(
            templateId = "axis_card_multiline",
            regexPattern = """^spent\s*\r?\n\s*(?:axis bank )?card no\.\s+[x*]+(?<account>\d{4})\s*\r?\n\s*$MONEY\s*(?:\r?\n\s*)?\d{2}-\d{2}-\d{2,4}\s+\d{2}:\d{2}:\d{2}(?:\s+ist)?\s*\r?\n\s*(?<merchant>[^\r\n]+?)\s*\r?\n\s*avl lmt\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Card,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "hdfc_card_upi",
            regexPattern = """^txn\s+$MONEY\s*\r?\non hdfc bank card\s+(?<account>\d{4})\s*\r?\nat (?<merchant>[^\r\n]+)\r?\nby upi\s+\d{12}\s*\r?\non\s+\d{2}-\d{2}\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Card,
            transactionType = TransactionType.MerchantPayment
        ),
        // Pluxee meal-benefit wallet spend — covers "Meal wallet" and "Meal Card wallet" phrasings.
        ParsingTemplate(
            templateId = "pluxee_spend",
            regexPattern = """^$MONEY\s+spent from pluxee\s+meal(?:\s+card)?\s+wallet,\s*card no\.\s*(?:[x*]+(?<account>\d{4}))?,?\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\s*\.\s*avl bal""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "pluxee_spend_linked",
            regexPattern = """^$MONEY\s+was spent from meal card wallet linked to your pluxee card\s*(?:[x*]+(?<account>\d{4}))?\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\.\s*txn no\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.MerchantPayment
        ),
        // Sodexo meal card spend (predecessor brand to Pluxee); no card number in this layout.
        ParsingTemplate(
            templateId = "sodexo_spend",
            regexPattern = """^$MONEY\s+was spent from your sodexo meal card a/c\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\.\s*txn no\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "pluxee_fee",
            regexPattern = """^$MONEY\s+deducted from your pluxee card\s+[x*]+(?<account>\d{4})\s+towards online convenience fee\.\s*pluxee\s*$""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.FeeCharge
        ),
        // Pluxee wallet top-up by the employer.
        ParsingTemplate(
            templateId = "pluxee_wallet_credit",
            regexPattern = """^your pluxee card has been successfully credited with $MONEY\s+towards\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.Deposit
        ),
        // HSBC credit-card spend: "HSBC Credit Card xx7681 used at MERCHANT for INR amt on dd/mm/yy".
        ParsingTemplate(
            templateId = "hsbc_used_at",
            regexPattern = """^hsbc credit\s?card\s+[x*]+(?<account>\d{4})\s+used at\s+(?<merchant>.+?)\s+for\s+$MONEY\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "icici_card",
            regexPattern = """^$MONEY\s+spent using icici bank card\s+[x*]+(?<account>\d{4})\s+on\s+\d{2}-[a-z]{3}-\d{2,4}\s+on\s+(?<merchant>.+?)\.\s*avl limit:""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Card,
            transactionType = TransactionType.MerchantPayment
        ),
        // ICICI credit-card spend routed through UPI or a merchant terminal.
        ParsingTemplate(
            templateId = "icici_credit_card_debit",
            regexPattern = """^icici bank credit card\s+[x*]+(?<account>\d{3,4})\s+debited for\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+for\s+(?<merchant>[^\r\n]{1,60}?)\.?\s*(?:to dispute|$)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.MerchantPayment
        ),
        // Paytm Payments Bank UPI spend: "Rs.169.00 sent to <vpa> from PPBL a/c 91XX6831. UPI Ref:...".
        ParsingTemplate(
            templateId = "paytm_upi_sent",
            regexPattern = """^rs\.?\s*(?<amount>[\d,.]+)\s+sent to\s+(?<merchant>\S+)\s+from\s+ppbl\s+a/c\s+[0-9x]+(?<account>\d{4})\.\s*upi ref:""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "mandate",
            regexPattern = """^upi mandate:\s*\r?\nsent\s+$MONEY\s*\r?\nfrom hdfc bank a/c\s+[*x]*(?<account>\d{4})\s*\r?\nto (?<merchant>[^\r\n]+)\r?\n\d{2}/\d{2}/\d{2,4}\s*\r?\nref\s+\d{8,24}\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "autopay",
            regexPattern = """^autopay \(e-mandate\) success!\s*\r?\nfor (?<merchant>[^\r\n]+)\r?\ntxn amt:\s*$MONEY\s*\r?\ndt:\d{2}/\d{2}/\d{2,4}\s*\r?\nvia:hdfc bank cc\s+(?<account>\d{4})\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard, // "cc" = credit card
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview, // the card debit usually also arrives on its own
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "received",
            regexPattern = """^received!\s*\r?\n$MONEY\s+in hdfc bank a/c\s+[x*]+(?<account>\d{4})\s*\r?\non\s+\d{2}-\d{2}-\d{2,4}\s*\r?\nfor imps\s*-""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "deposited",
            regexPattern = """^update!\s*$MONEY\s+deposited in hdfc bank a/c\s+[x*]+(?<account>\d{4})\s+on\s+\d{2}-[a-z]{3}-\d{2,4}\s+for neft\s+cr-""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.NEFT,
            transactionType = TransactionType.Deposit
        ),
        ParsingTemplate(
            templateId = "posted_refund",
            regexPattern = """^alert!\s*$MONEY\s+refunded by\s+(?<merchant>.+)\s+on\s+\d{2}/[a-z]{3}/\d{2,4}\s*& adjusted against hdfc bank credit card\s+(?<account>\d{4})\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.Refund
        ),
        ParsingTemplate(
            templateId = "initiated_refund",
            regexPattern = """^(?:dear customer,\s*)?refund of\s+$MONEY\s+for your\s+(?<merchant>.{1,100})\border\s*#?\w+\s+is initiated\.""",
            direction = Direction.Credit,
            status = TransactionStatus.Pending,
            channel = Channel.Unknown,
            transactionType = TransactionType.Refund
        ),
        ParsingTemplate(
            templateId = "repayment_1",
            regexPattern = """^payment of $MONEY has been received (?:on|towards) your (?:icici|axis) bank credit card [x*]+(?<account>\d{4})\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.CardRepayment
        ),
        ParsingTemplate(
            templateId = "repayment_2",
            regexPattern = """^dear hdfcbank cardmember,\s*payment of $MONEY received towards your credit card ending with (?<account>\d{4})\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.CardRepayment
        ),
        ParsingTemplate(
            templateId = "repayment_3",
            regexPattern = """^hdfc bank cardmember, online payment of $MONEY vide ref# [^\r\n]{1,80} was credited to your card ending (?<account>\d{4})\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.CardRepayment
        ),
        // Credit-card bill payment received via a biller/BBPS route (no card number in the text).
        ParsingTemplate(
            templateId = "repayment_bbps",
            regexPattern = """^we have received payment of $MONEY via \S+\s*&\s*the same has been credited to your [a-z ]{0,30}credit card\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.CardRepayment
        ),
        // Monthly card cashback posted to the card account.
        ParsingTemplate(
            templateId = "card_cashback_credit",
            regexPattern = """^[^\r\n]{0,60}?cashback[^\r\n]{0,80}?$MONEY\s+has been credited to your [^\r\n]{0,40}?card account\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.Other
        ),
        // Amazon Pay wallet spend (primary wallet debit, so it auto-records).
        ParsingTemplate(
            templateId = "amazon_receipt",
            regexPattern = """^payment of $MONEY using apay balance is successful at a\.in\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "amazon_receipt_dotin",
            regexPattern = """^payment of $MONEY using amazon pay balance is successful at amazon\.in\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Wallet,
            transactionType = TransactionType.MerchantPayment
        ),
        // HP PAY fuel purchase confirmation. Review: the card/UPI debit arrives as its own SMS.
        ParsingTemplate(
            templateId = "hp_pay_receipt",
            regexPattern = """^transaction of $MONEY for purchase of (?<merchant>[^\r\n]+?) is successful""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "tax_receipt",
            regexPattern = """^dear user,\s*challan payment of $MONEY against pan/tan \S+ for assessment year \d{4} has been successfully paid\.""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "airtel_receipt",
            regexPattern = """^hi (?<merchant>[^,\r\n]{1,80}), we have received a payment of $MONEY for your airtel wi-fi id \S+""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "lpg_receipt",
            regexPattern = """^we confirm receipt of online payment made via bbpay for (?<amount>[\d,.]+) against lpg refill booking no:\s*\d+\.your delivery authentication code is \d+\s*- hpcl\s*$""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        // Federal OneCard spend, phrasing A: "... You've spent Rs X at MERCHANT with your Federal One Credit Card ending in XXnnnn".
        ParsingTemplate(
            templateId = "onecard_spent_1",
            regexPattern = """^[^\r\n]*?you've spent $MONEY at (?<merchant>[^\r\n]+?) with your federal (?:bank\s+)?one credit card ending in [x*]+(?<account>\d{4})\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.MerchantPayment
        ),
        // Federal OneCard spend, phrasing B: "... Rs X spent at MERCHANT on your Federal Bank One Credit Card xxXXnnnn".
        ParsingTemplate(
            templateId = "onecard_spent_2",
            regexPattern = """^[^\r\n]*?$MONEY spent at (?<merchant>[^\r\n]+?) on your federal bank\s+one credit card\s+[x*]+(?<account>\d{4})\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.MerchantPayment
        ),
        // Mutual-fund purchase (KFIN "processed @ NAV"). Review: a separate bank debit usually arrives.
        ParsingTemplate(
            templateId = "mf_purchase_processed",
            regexPattern = """^dear investor,\s*your purchase request dt \d{2}/\d{2}/\d{4} for $MONEY in scheme (?<merchant>[^\r\n]+?) is processed\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "mf_new_purchase",
            regexPattern = """^[^\r\n]*?request for (?:new )?purchase of $MONEY\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "bescom_payment",
            regexPattern = """^[^\r\n]*?\(bescom\) payment for \d+ with $MONEY on \d{1,2}-\d{1,2}-\d{4} is successful\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "bigbasket_payment",
            regexPattern = """^[\s\S]*?your payment of $MONEY is successful for your bbnow order\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        ParsingTemplate(
            templateId = "airtel_recharge",
            regexPattern = """^[^\r\n]*?recharge of $MONEY is successful for your airtel mobile\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.MerchantPayment,
            decision = ParseDecision.NeedsReview,
            ruleId = "secondary_payment_confirmation"
        ),
        // Credit/debit card spend with no merchant or date in the text.
        ParsingTemplate(
            templateId = "credit_card_spend_plain",
            regexPattern = """^$MONEY\s+spent on your credit card\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account>\d{4})(?!\d)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.CreditCard,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "debit_card_spend_plain",
            regexPattern = """^$MONEY\s+spent on your debit card\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account>\d{4})(?!\d)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.DebitCard,
            transactionType = TransactionType.MerchantPayment
        ),
        // "Sent Rs X\nFrom <bank> A/C *nnnn\nTo <payee>" (HDFC/NPCI style UPI payment).
        ParsingTemplate(
            templateId = "sent_payment",
            regexPattern = """^sent\s+$MONEY\s*\r?\nfrom [^\r\n]{1,60}\b(?:a/c|account)\s+[*x•]+(?<account>\d{4})\s*\r?\nto (?<merchant>[^\r\n]{1,60})(?:\r?\n|$)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown
        ),
    )

    /**
     * Card alerts from issuers that share one of three amount phrasings and one of three tails.
     * The trailing low-precision pair records an unrecognized card layout for review rather than
     * dropping it, but keeps no merchant.
     */
    private fun cardAlerts(): List<ParsingTemplate> {
        val prefixes = listOf("1" to CARD_SPENT, "2" to CARD_IS_SPENT, "3" to CARD_CHARGED)
        val tails = listOf("a" to TAIL_AT_ON, "b" to TAIL_ON_AT, "c" to TAIL_AT_SIGN)
        val complete = prefixes.flatMap { (p, prefix) ->
            tails.map { (t, tail) ->
                ParsingTemplate(
                    templateId = "card_alert_$p$t",
                    regexPattern = prefix + CARD_MASK + tail,
                    direction = Direction.Debit,
                    status = TransactionStatus.Successful,
                    channel = Channel.Card,
                    transactionType = TransactionType.MerchantPayment
                )
            }
        }
        // "Spent Rs X on <any> Card nnnn at MERCHANT on yyyy-mm-dd[:hh:mm:ss]"
        val isoCardSpend = listOf(
            ParsingTemplate(
                templateId = "card_spend_iso_date",
                regexPattern = CARD_SPENT + CARD_MASK +
                    """\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+\d{4}-\d{2}-\d{2}(?::\d{2}:\d{2}:\d{2})?(?!\d)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_spend_iso_date_amount_first",
                regexPattern = CARD_IS_SPENT + CARD_MASK +
                    """\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+\d{4}-\d{2}-\d{2}(?::\d{2}:\d{2}:\d{2})?(?!\d)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
        )
        val unfamiliar = listOf(
            ParsingTemplate(
                templateId = "unfamiliar_card_layout_spent_first",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?spent\s+$MONEY\b[^\r\n]{0,80}?\bcard\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account>\d{4})(?!\d)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "unfamiliar_card_layout"
            ),
            ParsingTemplate(
                templateId = "unfamiliar_card_layout",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?$MONEY\s+(?:(?:is|was)\s+)?(?:spent|charged|debited)\b[^\r\n]{0,80}?\bcard\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account>\d{4})(?!\d)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "unfamiliar_card_layout"
            ),
        )
        return complete + isoCardSpend + unfamiliar
    }

    /** Bank-account statement layouts: one masked account, sometimes a rail tag or a counterparty. */
    private fun accountLayouts(): List<ParsingTemplate> = listOf(
        // A UPI debit to a credit-card bill payee repays spends already in the ledger. It comes
        // before the generic recipient layout so a repayment is never booked as a fresh spend.
        ParsingTemplate(
            templateId = "icici_card_bill_repayment",
            regexPattern = iciciRecipientCredit(CARD_BILL_PAYEE),
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.CardRepayment
        ),
        // ICICI account debit naming the recipient of the payment. Anchored end-to-end (only a
        // dispute/contact tail may follow) so an OTP prefix or a second movement stops matching.
        ParsingTemplate(
            templateId = "icici_debit_recipient_credit",
            regexPattern = iciciRecipientCredit(ANY_PAYEE),
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown,
            // The recipient must read like a payee name, not another movement clause.
            accept = { match ->
                val recipient = match.namedOrNull("merchant").orEmpty()
                !Regex("""\b(?:accounts?|acct|a/c|debited|credited|transferred|not)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(recipient)
            }
        ),
        // ICICI own-account transfer: one account debited, another credited, in one message.
        ParsingTemplate(
            templateId = "icici_self_transfer",
            regexPattern = """^icici bank acct\s+[x*]+\d{3,4}\s+debited with\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s*&\s*acct\s+[x*]+\d{3,4}\s+credited\.""",
            direction = Direction.Transfer,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.SelfTransfer
        ),
        // ICICI account debit with an "Info" tag instead of a payee. The tag carries the rail.
        ParsingTemplate(
            templateId = "icici_account_debit_rtgs",
            regexPattern = """^icici bank acc\s+[x*]+(?<account>\d{3,4})\s+debited\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+(?:info)?rtgs\*""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.RTGS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "icici_account_debit_neft",
            regexPattern = """^icici bank acc\s+[x*]+(?<account>\d{3,4})\s+debited\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+(?:info)?neft\*""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.NEFT,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "icici_account_debit_imps",
            regexPattern = """^icici bank acc\s+[x*]+(?<account>\d{3,4})\s+debited\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+(?:info)?imps\*""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "icici_account_debit_info",
            regexPattern = """^icici bank acc\s+[x*]+(?<account>\d{3,4})\s+debited\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+[^\r\n]{1,60}?\.?\s*(?:avb|avl|available)\s*bal""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "icici_account_credit_info",
            regexPattern = """^icici bank account\s+[x*]+(?<account>\d{3,4})\s+credited:\s*$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        // "Acct XXnnn is credited with Rs X on dd-Mon-yy from PAYEE. UPI:nnn"
        ParsingTemplate(
            templateId = "icici_acct_credited_with",
            regexPattern = """^(?:dear customer,\s*)?acct\s+[x*]+(?<account>\d{3,4})\s+is credited with\s+$MONEY\s+on\s+\d{1,2}-[a-z]{3}-\d{2,4}(?:\s+from\s+(?<merchant>[a-z][a-z .&'-]{0,79}?))?\.\s*upi:""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown
        ),
        // PNB UPI statement lines.
        // Same layout, card-bill payee first: repaying a card is not a new spend.
        ParsingTemplate(
            templateId = "pnb_card_bill_repayment",
            regexPattern = pnbUpiDebit(CARD_BILL_PAYEE),
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.CardRepayment
        ),
        ParsingTemplate(
            templateId = "pnb_upi_debit",
            regexPattern = pnbUpiDebit("""[^\r\n]{1,60}?"""),
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "pnb_upi_credit",
            regexPattern = """^a/c\s+[x*]+(?<account>\d{4})\s+credited for\s+$MONEY\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+[\d:]+\s+by\s+(?<merchant>[^\r\n]{1,60}?)\s+thru\s+upi\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.P2PTransfer
        ),
        // A debit and a credit in one message: which side is "ours" is unclear, so review it.
        ParsingTemplate(
            templateId = "mixed_debit_and_credit",
            regexPattern = """^$MONEY\s+debited\s+a/?c\s*[x*]+\d{4}\s+and credited to\s+[^\r\n]{1,60}?\s+via\s+upi\b""",
            direction = Direction.Unknown,
            status = TransactionStatus.Unknown,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown,
            decision = ParseDecision.NeedsReview,
            ruleId = "mixed_movement"
        ),
        ParsingTemplate(
            templateId = "acct_debited_with_amount",
            regexPattern = """^a/?c\s+[x*]+(?<account>\d{4})\s+debited with\s+$MONEY\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "acct_is_debited_by",
            regexPattern = """^your a/c\s+[x*]+(?<account>\d{4})\s+is debited by\s+$MONEY\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "acct_is_credited_by",
            regexPattern = """^your a/c\s+[x*]+(?<account>\d{4})\s+is credited by\s+$MONEY\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credited_to_acct_via_imps",
            regexPattern = """^$MONEY\s+credited to your a/c\s+[x*]+(?<account>\d{4})\s+via imps\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credited_to_acct_by_vpa",
            regexPattern = """^$MONEY\s+credited to a/c\s+[x*]+(?<account>\d{4})\s+on\s+$DATE\s+by a/c linked to vpa\s+(?<merchant>\S+?)\s*\(upi""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.P2PTransfer
        ),
        ParsingTemplate(
            templateId = "indusind_acct_debited",
            regexPattern = """^indusind a/c debited;\s*$MONEY\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "debited_via_upi_to_vpa",
            regexPattern = """^$MONEY\s+debited via upi on\s+$DATE\s+[\d:]+\s+to vpa\s+(?<merchant>\S+?)[.\s]""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "acct_debit_masked",
            regexPattern = """^$MONEY\s+debited from\s+(?:a/c|acc(?:ount|t)?)\s+[x*•]+(?<account>\d{4})(?!\d)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        // "… debited from your <Bank Name> account" — a named account with no masked digits.
        ParsingTemplate(
            templateId = "named_account_debit",
            regexPattern = """^$MONEY\s+debited from your\s+[a-z .&'-]{1,40}\s+account\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "named_account_credit",
            regexPattern = """^$MONEY\s+credited to your\s+[a-z .&'-]{1,40}\s+account\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.Unknown
        ),
    )

    /**
     * Rail- and wording-generic movements. These are the least specific patterns, so they come last:
     * they only fire when no bank layout above recognized the message.
     */
    private fun genericMovements(): List<ParsingTemplate> = listOf(
        ParsingTemplate(
            templateId = "refund_credit_upi",
            regexPattern = """^$MONEY\s+refund credited via upi$ACCT_TAIL""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Refund
        ),
        ParsingTemplate(
            templateId = "refund_credit",
            regexPattern = """^$MONEY\s+refund credited\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Refund
        ),
        ParsingTemplate(
            templateId = "reversal_debit_upi",
            regexPattern = """^$MONEY\s+debit transaction reversed via upi$ACCT_TAIL""",
            direction = Direction.Debit,
            status = TransactionStatus.Reversed,
            channel = Channel.UPI,
            transactionType = TransactionType.Reversal
        ),
        ParsingTemplate(
            templateId = "reversal_debit",
            regexPattern = """^$MONEY\s+debit transaction reversed\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Reversed,
            channel = Channel.Unknown,
            transactionType = TransactionType.Reversal
        ),
        ParsingTemplate(
            templateId = "card_debit_declined",
            regexPattern = """^$MONEY\s+card debit transaction declined\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Failed,
            channel = Channel.Card,
            transactionType = TransactionType.MerchantPayment
        ),
        ParsingTemplate(
            templateId = "atm_withdrawal",
            regexPattern = """^$MONEY\s+(?:cash\s+)?withdrawn (?:at|from) atm\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.ATM,
            transactionType = TransactionType.CashWithdrawal
        ),
        ParsingTemplate(
            templateId = "salary_credit",
            regexPattern = """^$MONEY\s+salary credited\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.SalaryIncome
        ),
        ParsingTemplate(
            templateId = "fee_debit",
            regexPattern = """^$MONEY\s+(?:fee|charge) debited\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.FeeCharge
        ),
        ParsingTemplate(
            templateId = "own_account_transfer",
            regexPattern = """^$MONEY\s+transferred (?:between|to) your own accounts?\b""",
            direction = Direction.Transfer,
            status = TransactionStatus.Successful,
            channel = Channel.BankTransfer,
            transactionType = TransactionType.SelfTransfer
        ),
        ParsingTemplate(
            templateId = "debit_via_netbanking",
            regexPattern = """^$MONEY\s+debited via net ?banking\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.NetBanking,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "debit_via_imps",
            regexPattern = """^$MONEY\s+debited (?:via|through) imps\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "debit_via_neft",
            regexPattern = """^$MONEY\s+debited (?:via|through) neft\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.NEFT,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "debit_via_rtgs",
            regexPattern = """^$MONEY\s+debited (?:via|through) rtgs\b""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.RTGS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credit_via_imps",
            regexPattern = """^$MONEY\s+credited (?:via|through) imps\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.IMPS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credit_via_neft",
            regexPattern = """^$MONEY\s+credited (?:via|through) neft\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.NEFT,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credit_via_rtgs",
            regexPattern = """^$MONEY\s+credited (?:via|through) rtgs\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.RTGS,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "debit_via_upi",
            regexPattern = """^$MONEY\s+debited(?: from your account)? via upi$ACCT_TAIL""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "credit_via_upi",
            regexPattern = """^$MONEY\s+credited(?: to your account)? via upi$ACCT_TAIL""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "account_credited_with_by_upi",
            regexPattern = """^your account is credited with\s+$MONEY\s+by upi\b""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.UPI,
            transactionType = TransactionType.Unknown
        ),
        // Bare movements: "<amount> debited" with nothing but a balance or end-of-message after it.
        ParsingTemplate(
            templateId = "account_debit",
            regexPattern = """^$MONEY\s+debited(?:\s+from your account\b|\s*[;.]|\s*$)""",
            direction = Direction.Debit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown
        ),
        ParsingTemplate(
            templateId = "account_credit",
            regexPattern = """^$MONEY\s+credited(?:\s+to your account\b|\s*[;.]|\s*$)""",
            direction = Direction.Credit,
            status = TransactionStatus.Successful,
            channel = Channel.Unknown,
            transactionType = TransactionType.Unknown
        ),
    )
}

/** All templates, in match order. Kept as a function for the benchmark harnesses. */
fun allTemplates(): List<ParsingTemplate> = TemplateRepository.templates

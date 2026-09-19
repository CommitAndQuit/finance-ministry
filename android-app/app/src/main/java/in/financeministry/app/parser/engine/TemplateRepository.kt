package `in`.financeministry.app.parser.engine

import `in`.financeministry.app.core.model.Channel
import `in`.financeministry.app.core.model.Direction
import `in`.financeministry.app.core.model.ParseDecision
import `in`.financeministry.app.core.model.TransactionStatus
import `in`.financeministry.app.core.model.TransactionType

interface TemplateRepository {
    fun getTemplates(): List<ParsingTemplate>
}

class InMemoryTemplateRepository : TemplateRepository {
    override fun getTemplates(): List<ParsingTemplate> {
        val money = """(?:rs\.?|inr|₹)\s*(?<amount>[\d,.]+)"""
        val date = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-/](?:\d{1,2}|[a-z]{3})[-/]\d{2,4})(?!\d)"""

        return listOf(
            // MovementTemplates.bobDebit
            ParsingTemplate(
                templateId = "bob_debit",
                regexPattern = """^$money\s+dr\.\s+from\s+a/c\s+[x*]+(?<account>\d{3,4})\s+and\s+cr\.\s+to\s+(?<merchant>\S+)\s+ref:(?<ref>\d{8,24})\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.Unknown
            ),
            // MovementTemplates.axisCard
            ParsingTemplate(
                templateId = "axis_card",
                regexPattern = """^spent\s+$money\s*\r?\naxis bank card no\.\s+[x*]+(?<account>\d{4})\s*\r?\n\d{2}-\d{2}-\d{2,4}\s+\d{2}:\d{2}:\d{2}\s+ist\s*\r?\n(?<merchant>[^\r\n]+)\r?\navl limit:""",
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
                regexPattern = """^spent\s*\r?\n\s*(?:axis bank )?card no\.\s+[x*]+(?<account>\d{4})\s*\r?\n\s*(?:inr|rs\.?|₹)\s*(?<amount>[\d,.]+)\s*(?:\r?\n\s*)?\d{2}-\d{2}-\d{2,4}\s+\d{2}:\d{2}:\d{2}(?:\s+ist)?\s*\r?\n\s*(?<merchant>[^\r\n]+?)\s*\r?\n\s*avl lmt\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // MovementTemplates.hdfcCardUpi
            ParsingTemplate(
                templateId = "hdfc_card_upi",
                regexPattern = """^txn\s+$money\s*\r?\non hdfc bank card\s+(?<account>\d{4})\s*\r?\nat (?<merchant>[^\r\n]+)\r?\nby upi\s+(?<ref>\d{12})\s*\r?\non\s+\d{2}-\d{2}\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // MovementTemplates.pluxeeSpend — covers "Meal wallet" and "Meal Card wallet" phrasings.
            ParsingTemplate(
                templateId = "pluxee_spend",
                regexPattern = """^$money\s+spent from pluxee\s+meal(?:\s+card)?\s+wallet,\s*card no\.\s*(?:[x*]+(?<account>\d{4}))?,?\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\s*\.\s*avl bal""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet, // Pluxee is a meal-benefit wallet
                transactionType = TransactionType.MerchantPayment
            ),
            // Pluxee alternate phrasing: "was spent from Meal Card Wallet linked to your Pluxee Card ..."
            ParsingTemplate(
                templateId = "pluxee_spend_linked",
                regexPattern = """^$money\s+was spent from meal card wallet linked to your pluxee card\s*(?:[x*]+(?<account>\d{4}))?\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\.\s*txn no\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet,
                transactionType = TransactionType.MerchantPayment
            ),
            // Sodexo meal card spend (predecessor brand to Pluxee); no card number in this layout.
            ParsingTemplate(
                templateId = "sodexo_spend",
                regexPattern = """^$money\s+was spent from your sodexo meal card a/c\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+\d{1,2}:\d{1,2}:\d{1,2}\s+at\s+(?<merchant>.+?)\.\s*txn no\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet,
                transactionType = TransactionType.MerchantPayment
            ),
            // MovementTemplates.pluxeeFee
            ParsingTemplate(
                templateId = "pluxee_fee",
                regexPattern = """^$money\s+deducted from your pluxee card\s+[x*]+(?<account>\d{4})\s+towards online convenience fee\.\s*pluxee\s*$""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet,
                transactionType = TransactionType.FeeCharge
            ),
            // MovementTemplates.iciciCard
            ParsingTemplate(
                templateId = "icici_card",
                regexPattern = """^$money\s+spent using icici bank card\s+[x*]+(?<account>\d{4})\s+on\s+\d{2}-[a-z]{3}-\d{2,4}\s+on\s+(?<merchant>.+?)\.\s*avl limit:""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // MovementTemplates.mandate
            ParsingTemplate(
                templateId = "mandate",
                regexPattern = """^upi mandate:\s*\r?\nsent\s+$money\s*\r?\nfrom hdfc bank a/c\s+[*x]*(?<account>\d{4})\s*\r?\nto (?<merchant>[^\r\n]+)\r?\n\d{2}/\d{2}/\d{2,4}\s*\r?\nref\s+(?<ref>\d{8,24})\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.UPI,
                transactionType = TransactionType.MerchantPayment
            ),
            // MovementTemplates.autopay
            ParsingTemplate(
                templateId = "autopay",
                regexPattern = """^autopay \(e-mandate\) success!\s*\r?\nfor (?<merchant>[^\r\n]+)\r?\ntxn amt:\s*$money\s*\r?\ndt:\d{2}/\d{2}/\d{2,4}\s*\r?\nvia:hdfc bank cc\s+(?<account>\d{4})\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard, // "cc" = credit card
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview, // secondary confirmation
                ruleId = "secondary_payment_confirmation"
            ),
            // MovementTemplates.received
            ParsingTemplate(
                templateId = "received",
                regexPattern = """^received!\s*\r?\n$money\s+in hdfc bank a/c\s+[x*]+(?<account>\d{4})\s*\r?\non\s+\d{2}-\d{2}-\d{2,4}\s*\r?\nfor imps\s*-""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.IMPS,
                transactionType = TransactionType.Unknown
            ),
            // MovementTemplates.deposited
            ParsingTemplate(
                templateId = "deposited",
                regexPattern = """^update!\s*$money\s+deposited in hdfc bank a/c\s+[x*]+(?<account>\d{4})\s+on\s+\d{2}-[a-z]{3}-\d{2,4}\s+for neft\s+cr-""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.NEFT,
                transactionType = TransactionType.Deposit
            ),
            // MovementTemplates.postedRefund
            ParsingTemplate(
                templateId = "posted_refund",
                regexPattern = """^alert!\s*$money\s+refunded by\s+(?<merchant>.+)\s+on\s+\d{2}/[a-z]{3}/\d{2,4}\s*& adjusted against hdfc bank credit card\s+(?<account>\d{4})\b""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.Refund
            ),
            // MovementTemplates.initiatedRefund
            ParsingTemplate(
                templateId = "initiated_refund",
                regexPattern = """^(?:dear customer,\s*)?refund of\s+$money\s+for your\s+(?<merchant>.{1,100})\border\s*#?\w+\s+is initiated\.""",
                direction = Direction.Credit,
                status = TransactionStatus.Pending,
                channel = Channel.Unknown,
                transactionType = TransactionType.Refund
            ),
            // MovementTemplates.repayment 1
            ParsingTemplate(
                templateId = "repayment_1",
                regexPattern = """^payment of $money has been received (?:on|towards) your (?:icici|axis) bank credit card [x*]+(?<account>\d{4})\b""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.CardRepayment
            ),
            // MovementTemplates.repayment 2
            ParsingTemplate(
                templateId = "repayment_2",
                regexPattern = """^dear hdfcbank cardmember,\s*payment of $money received towards your credit card ending with (?<account>\d{4})\b""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.CardRepayment
            ),
            // MovementTemplates.repayment 3
            ParsingTemplate(
                templateId = "repayment_3",
                regexPattern = """^hdfc bank cardmember, online payment of $money vide ref# [^\r\n]{1,80} was credited to your card ending (?<account>\d{4})\b""",
                direction = Direction.Credit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.CardRepayment
            ),
            // Receipt: amazonReceipt — Amazon Pay wallet spend is a primary wallet debit, so auto-record.
            ParsingTemplate(
                templateId = "amazon_receipt",
                regexPattern = """^payment of $money using apay balance is successful at a\.in\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet,
                transactionType = TransactionType.MerchantPayment
            ),
            // Receipt: hpPayReceipt — HP PAY fuel purchase confirmation. Flagged for review because
            // the bank card/UPI debit for the same purchase usually arrives as its own SMS.
            ParsingTemplate(
                templateId = "hp_pay_receipt",
                regexPattern = """^transaction of $money for purchase of (?<merchant>[^\r\n]+?) is successful""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // Receipt: taxReceipt
            ParsingTemplate(
                templateId = "tax_receipt",
                regexPattern = """^dear user,\s*challan payment of $money against pan/tan \S+ for assessment year \d{4} has been successfully paid\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.Unknown,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // Receipt: airtelReceipt
            ParsingTemplate(
                templateId = "airtel_receipt",
                regexPattern = """^hi (?<merchant>[^,\r\n]{1,80}), we have received a payment of $money for your airtel wi-fi id \S+""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.Unknown,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // Receipt: lpgReceipt (NOTE: using standard match instead of group(1))
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

            // Amazon Pay wallet spend, full "Amazon.in" phrasing (primary wallet debit → auto-record).
            ParsingTemplate(
                templateId = "amazon_receipt_dotin",
                regexPattern = """^payment of $money using amazon pay balance is successful at amazon\.in\.""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Wallet,
                transactionType = TransactionType.MerchantPayment
            ),
            // Federal OneCard credit-card spend, phrasing A: "... You've spent Rs X at MERCHANT with your Federal One Credit Card ending in XXnnnn".
            ParsingTemplate(
                templateId = "onecard_spent_1",
                regexPattern = """^[^\r\n]*?you've spent $money at (?<merchant>[^\r\n]+?) with your federal (?:bank\s+)?one credit card ending in [x*]+(?<account>\d{4})\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.MerchantPayment
            ),
            // Federal OneCard credit-card spend, phrasing B: "... Rs X spent at MERCHANT on your Federal Bank One Credit Card xxXXnnnn".
            ParsingTemplate(
                templateId = "onecard_spent_2",
                regexPattern = """^[^\r\n]*?$money spent at (?<merchant>[^\r\n]+?) on your federal bank\s+one credit card\s+[x*]+(?<account>\d{4})\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.CreditCard,
                transactionType = TransactionType.MerchantPayment
            ),
            // Mutual-fund purchase (KFIN "processed @ NAV"). Review: a separate bank debit usually arrives.
            ParsingTemplate(
                templateId = "mf_purchase_processed",
                regexPattern = """^dear investor,\s*your purchase request dt \d{2}/\d{2}/\d{4} for $money in scheme (?<merchant>[^\r\n]+?) is processed\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // Mutual-fund new-purchase confirmation ("request for New Purchase of Rs X").
            ParsingTemplate(
                templateId = "mf_new_purchase",
                regexPattern = """^[^\r\n]*?request for (?:new )?purchase of $money\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // BESCOM electricity bill paid via Airtel Payments Bank. Review (secondary to bank debit).
            ParsingTemplate(
                templateId = "bescom_payment",
                regexPattern = """^[^\r\n]*?\(bescom\) payment for \d+ with $money on \d{1,2}-\d{1,2}-\d{4} is successful\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // bigbasket bbnow order payment receipt. Review (secondary to bank/UPI debit).
            ParsingTemplate(
                templateId = "bigbasket_payment",
                regexPattern = """^[\s\S]*?your payment of $money is successful for your bbnow order\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),
            // Airtel prepaid recharge receipt. Review (secondary to bank/UPI debit).
            ParsingTemplate(
                templateId = "airtel_recharge",
                regexPattern = """^[^\r\n]*?recharge of $money is successful for your airtel mobile\b""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.MerchantPayment,
                decision = ParseDecision.NeedsReview,
                ruleId = "secondary_payment_confirmation"
            ),

            // CardAlertFormats HDFC/SBI/BOBCARD/Federal
            ParsingTemplate(
                templateId = "card_alert_1a",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?spent\s+(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount1>[\d,.]+)\s+on\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account1>\d{4})(?!\d)(?:\s+for\s+[^\r\n]{1,50}?)?\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_1b",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount2>[\d,.]+)\s+(?:(?:is|was)\s+)?spent\s+(?:on|using)\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account2>\d{4})(?!\d)(?:\s+for\s+[^\r\n]{1,50}?)?\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_1c",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount3>[\d,.]+)\s+has been charged to\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account3>\d{4})(?!\d)(?:\s+for\s+[^\r\n]{1,50}?)?\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // CardAlertFormats Kotak
            ParsingTemplate(
                templateId = "card_alert_2a",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?spent\s+(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount1>[\d,.]+)\s+on\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account1>\d{4})(?!\d)\s+on\s+$date\s+at\s+(?<merchant>[^\r\n]{1,80}?)(?=\.\s|$)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_2b",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount2>[\d,.]+)\s+(?:(?:is|was)\s+)?spent\s+(?:on|using)\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account2>\d{4})(?!\d)\s+on\s+$date\s+at\s+(?<merchant>[^\r\n]{1,80}?)(?=\.\s|$)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_2c",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount3>[\d,.]+)\s+has been charged to\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account3>\d{4})(?!\d)\s+on\s+$date\s+at\s+(?<merchant>[^\r\n]{1,80}?)(?=\.\s|$)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // CardAlertFormats YES Bank
            ParsingTemplate(
                templateId = "card_alert_3a",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?spent\s+(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount1>[\d,.]+)\s+on\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account1>\d{4})(?!\d)\s+@(?<merchant>[^\r\n]{1,80}?)\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_3b",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount2>[\d,.]+)\s+(?:(?:is|was)\s+)?spent\s+(?:on|using)\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account2>\d{4})(?!\d)\s+@(?<merchant>[^\r\n]{1,80}?)\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            ParsingTemplate(
                templateId = "card_alert_3c",
                regexPattern = """^(?:(?:alert:|transaction successful!)\s*)?(?:inr|rs\.?|₹|usd|eur|gbp|aed|sgd|aud|cad|jpy)\s*(?<amount3>[\d,.]+)\s+has been charged to\s+(?:your\s+)?[a-z ]{0,60}?(?:\bcard|\bbobcard)\s+(?:ending\s+(?:with\s+)?)?[*x•]*(?<account3>\d{4})(?!\d)\s+@(?<merchant>[^\r\n]{1,80}?)\s+$date""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // ParserRules cardSpend
            ParsingTemplate(
                templateId = "parser_rules_card_spend",
                regexPattern = """^(?:spent\s+$money|(?:rs\.?|inr|₹)\s*(?<amount2>[\d,.]+)\s+spent)\s+on\s+[^\r\n]{1,60}?\bcard\s+[*xX•]*(?<account>\d{4})(?!\d)\s+at\s+(?<merchant>[^\r\n]{1,80}?)\s+on\s+\d{4}-\d{2}-\d{2}(?::\d{2}:\d{2}:\d{2})?(?!\d)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Card,
                transactionType = TransactionType.MerchantPayment
            ),
            // ParserRules sentPayment
            ParsingTemplate(
                templateId = "sent_payment",
                regexPattern = """^sent\s+$money\s*\r?\nfrom [^\r\n]{1,60}\b(?:a/c|account)\s+[*x•]+(?<account>\d{4})\s*\r?\nto (?<merchant>[^\r\n]{1,60})(?:\r?\n|$)""",
                direction = Direction.Debit,
                status = TransactionStatus.Successful,
                channel = Channel.Unknown,
                transactionType = TransactionType.Unknown
            )
        )
    }
}

// Combining standard templates and fallback templates
fun allTemplates(): List<ParsingTemplate> {
    return InMemoryTemplateRepository().getTemplates() + getFallbackTemplates()
}

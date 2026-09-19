package `in`.financeministry.app.parser

import `in`.financeministry.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Invented values only. No private SMS, names, references or balances in the repo. */
class ParserCoverageTest {
    private fun parse(body: String) = `in`.financeministry.app.parser.engine.TemplateEngineParser().parse(IncomingSms("TEST", 0L, body))

    @Test fun product_names_and_administration_are_not_money_movements() {
        listOf(
            "Request to link your HDFC Bank Credit Card 0000 to UPI.",
            "Your Debit Card was issued today.",
            "PIN Change successful! For HDFC Bank Debit Card XX0000.",
            "Successfully modified limits for your Credit Card.",
            "Statement Generated: For HDFC Bank Credit Card XX0000.",
            "Credit Card bill generated for Rs.42.00. Pay now.",
            "Enjoy cashless claim processing at hospitals.",
            "FREE tickets with your Credit Card. Apply today.",
            "Your transfer request is processing.",
            "UPI Mandate Set for Rs.42 to TEST SHOP.",
            "AutoPay Active! For TEST SHOP Starting:01/01/26 On HDFC Bank Card 0000"
        ).forEach { assertEquals(it, ParseDecision.Reject, parse(it).decision) }
    }

    @Test fun wallet_single_digit_time_and_balance_is_are_supported() {
        val wallet = parse("Rs.42 spent from Pluxee Meal wallet, card no. on 01-01-2026 1:2:3 at TEST SHOP. Avl bal Rs.900.")
        assertEquals(ParseDecision.Record, wallet.decision)
        assertEquals(4200L, wallet.amountMinor)
        // Amazon Pay wallet spend is a primary wallet debit and auto-records.
        val receipt = parse("Payment of Rs 42 using Apay balance is successful at A.in. Updated balance is Rs 900. If not u? call 1800000000 - SMS via Pine Labs")
        assertEquals(ParseDecision.Record, receipt.decision)
        assertEquals(4200L, receipt.amountMinor)
        assertEquals(Direction.Debit, receipt.direction)
    }

    @Test fun wallet_unpadded_calendar_components_do_not_drop_payments() {
        for (date in listOf("4-08-2026", "04-8-2026", "4-8-26")) {
            val result = parse("Rs.42 spent from Pluxee Meal wallet, card no. on $date 11:13:4 at TEST SHOP. Avl bal Rs.900.")
            assertEquals(date, ParseDecision.Record, result.decision)
            assertEquals(4200L, result.amountMinor)
            assertEquals(Direction.Debit, result.direction)
        }
    }

    @Test fun autopay_secondary_confirmation_does_not_automatically_duplicate_card_spend() {
        val result = parse("AutoPay (E-mandate) Success!\nFor TEST SHOP\nTxn Amt:INR42.00\nDt:01/01/26\nVia:HDFC Bank CC 0000\nSI Hub ID: TEST\nTnC")
        assertEquals(ParseDecision.NeedsReview, result.decision)
        assertEquals(4200L, result.amountMinor)
        assertEquals(Direction.Debit, result.direction)
    }

    @Test fun common_outgoing_templates_extract_payment_not_balance() {
        listOf(
            "Rs.42.00 Dr. from A/C XXXXXX0000 and Cr. to test@upi. Ref:000000000000. AvlBal:Rs900.00(01:01:26 12:00:00). Not you? Call 1800000000-BOB",
            "Spent INR 42\nAxis Bank Card no. XX0000\n01-01-26 12:00:00 IST\nTEST SHOP\nAvl Limit: INR 900\nNot you? SMS BLOCK 0000 to 7000000000",
            "Txn Rs.42.00\nOn HDFC Bank Card 0000\nAt test@upi\nby UPI 000000000000\nOn 01-01\nNot You?\nCall 1800000000",
            "Rs.42.00 spent from Pluxee Meal wallet, card no. on 01-01-26 12:00:00 at TEST SHOP. Avl bal Rs.900. Not you call 1800000000",
            "Rs.42.00 spent from Pluxee  Meal wallet, card no.xx0000 on 01-01-26 12:00:00 at TEST SHOP. Avl bal Rs.900.",
            "Rs.42.00 deducted from your Pluxee Card xxxx0000 towards ONLINE CONVENIENCE FEE. Pluxee",
            "INR 42.00 spent using ICICI Bank Card XX0000 on 01-Jan-26 on TEST SHOP. Avl Limit: INR 900. If not you, call 1800000000."
        ).forEach {
            val result = parse(it)
            assertEquals(it, ParseDecision.Record, result.decision)
            assertEquals(it, 4200L, result.amountMinor)
            assertEquals(it, Direction.Debit, result.direction)
            assertEquals(it, TransactionStatus.Successful, result.status)
        }
    }

    @Test fun deposits_and_executed_mandates_are_movements() {
        listOf(
            "Received!\nINR 42.00 in HDFC Bank A/c xx0000\nOn 01-01-26\nFor IMPS -TEST BANK- 000000000000\nAvl bal INR 900",
            "Update! INR 42.00 deposited in HDFC Bank A/c XX0000 on 01-JAN-26 for NEFT Cr-TEST.Av l bal INR 900. Cheque deposits in A/C are subject to clearing".replace("Av l", "Avl")
        ).forEach {
            assertEquals(it, ParseDecision.Record, parse(it).decision)
            assertEquals(4200L, parse(it).amountMinor)
            assertEquals(Direction.Credit, parse(it).direction)
        }
        listOf(
            "UPI Mandate:\nSent Rs.42.00\nfrom HDFC Bank A/c 0000\nTo TEST SHOP\n01/01/26\nRef 000000000000\nNot You? Call 1800000000"
        ).forEach {
            assertEquals(it, ParseDecision.Record, parse(it).decision)
            assertEquals(4200L, parse(it).amountMinor)
            assertEquals(Direction.Debit, parse(it).direction)
        }
    }

    @Test fun refunds_distinguish_posted_from_initiated() {
        val posted = parse("Alert! Rs.42 refunded by TEST SHOP on 01/JAN/26 & adjusted against HDFC Bank Credit Card 0000 View updated balance here: https://example.com")
        assertEquals(ParseDecision.Record, posted.decision)
        assertEquals(4200L, posted.amountMinor)
        assertEquals(Direction.Credit, posted.direction)
        assertEquals(TransactionStatus.Successful, posted.status)
        assertEquals(TransactionType.Refund, posted.transactionType)
        val pending = parse("Dear customer, refund of ₹42 for your TEST Order #000 is initiated. It should reflect in 3–5 days - TEST")
        assertEquals(ParseDecision.Record, pending.decision)
        assertEquals(Direction.Credit, pending.direction)
        assertEquals(TransactionStatus.Pending, pending.status)
    }

    @Test fun repayments_are_not_income_and_secondary_receipts_require_review() {
        listOf(
            "Payment of Rs 42 has been received on your ICICI Bank Credit Card XX0000 through Bharat Bill Payment System on 01-JAN-26.",
            "Payment of INR 42 has been received towards your Axis Bank Credit Card XX0000 on 01-01-26 - Axis Bank",
            "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 42 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 0000 ON 01-01-26.YOUR AVAILABLE LIMIT IS RS. 900",
            "HDFC Bank Cardmember, Online Payment of Rs.42 vide Ref# TEST was credited to your card ending 0000 On 01/JAN/26_value Date 01/JAN/26"
        ).forEach {
            val result = parse(it)
            assertEquals(it, ParseDecision.Record, result.decision)
            assertEquals(4200L, result.amountMinor)
            assertEquals(Direction.Credit, result.direction)
            assertEquals("CardRepayment", result.transactionType.name)
        }
        listOf(
            "Dear User, Challan payment of Rs. 42 against PAN/TAN XXXXX0000X for Assessment Year 2026 has been successfully paid. e-Filing, ITD.",
            "Hi TEST, we have received a payment of Rs. 42 for your Airtel Wi-Fi ID 0000_dsl. To download the payment receipt, click https://example.com",
            "We confirm receipt of online payment made via BBPAY for 42 against LPG Refill Booking No: 0000.Your Delivery Authentication Code is 0000 - HPCL"
        ).forEach {
            val result = parse(it)
            assertEquals(it, ParseDecision.NeedsReview, result.decision)
            assertEquals(it, 4200L, result.amountMinor)
            assertEquals(Direction.Debit, result.direction)
            assertEquals(TransactionStatus.Successful, result.status)
        }
    }

    @Test fun new_templates_keep_otp_future_negation_and_multi_amount_guards() {
        val card = "Spent INR 42\nAxis Bank Card no. XX0000\n01-01-26 12:00:00 IST\nTEST SHOP\nAvl Limit: INR 900"
        listOf("OTP 123456 $card", "If you $card", "You have not $card", "Tomorrow $card").forEach {
            assertEquals(it, ParseDecision.Reject, parse(it).decision)
        }
        assertEquals(ParseDecision.NeedsReview, parse("$card\nINR 50 also debited").decision)
        // These still match the anchored layout: rejection must come from the guards.
        assertEquals(ParseDecision.Reject, parse("$card\nAmount will be debited tomorrow").decision)
        assertEquals(ParseDecision.Reject, parse("$card\nYour account was not debited").decision)
        assertEquals(ParseDecision.Reject, parse("$card\nOTP 123456").decision)
        assertEquals(ParseDecision.Reject, parse("Rs.42 deducted from your reward points offer").decision)
    }

    @Test fun pluxee_meal_card_wallet_phrasing_records_despite_avl_bal() {
        val r = parse("Rs. 42.00 spent from Pluxee  Meal Card wallet, card no.xx0000 on 01-01-2026 12:00:00 at TEST SHOP . Avl bal Rs.900.00. Not you call 1800000000")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(4200L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
        assertEquals(Channel.Wallet, r.channel)
    }

    @Test fun pluxee_linked_wallet_phrasing_records() {
        val r = parse("Rs. 42.00 was spent from Meal Card Wallet linked to your Pluxee Card xx0000 on 01-01-2026 12:00:00 at TEST SHOP. Txn no. 000000000000. Avl bal is Rs. 900.00. Not you? Call 1800000000. Pluxee")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(4200L, r.amountMinor)
        assertEquals(Channel.Wallet, r.channel)
    }

    @Test fun sodexo_meal_card_spend_records() {
        val r = parse("Rs.42.00 was spent from your Sodexo Meal Card A/c  on 01-01-2022 12:00:00 at TEST SHOP. Txn no. 000000000000. Avl bal is Rs.900.00. Not you? Call 1800000000. Sodexo")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(4200L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
        assertEquals(Channel.Wallet, r.channel)
    }

    @Test fun axis_multiline_spent_card_records() {
        val r = parse("Spent \n Card no. XX0000 \n INR 42 \n 01-01-26 12:00:00 \n TEST SHOP \n Avl Lmt INR 900.00 \n SMS BLOCK 0000 to 910000000000, if not you - Axis Bank")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(4200L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
        assertEquals(Channel.Card, r.channel)
    }

    @Test fun hp_pay_fuel_receipt_needs_review() {
        val r = parse("Transaction of Rs.42.00 for purchase of Petrol is successful .  Regards, HP PAY Team")
        assertEquals(ParseDecision.NeedsReview, r.decision)
        assertEquals(4200L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
    }

    @Test fun icici_own_account_transfer_records_as_transfer_but_upi_recipient_stays_review() {
        val transfer = parse("ICICI Bank Acct XX000 debited with Rs 42.00 on 01-Jan-26 & Acct XX111 credited.IMPS:000000000000. Call 18002662 for dispute or SMS BLOCK 000 to 9210000000")
        assertEquals(ParseDecision.Record, transfer.decision)
        assertEquals(Direction.Transfer, transfer.direction)
        assertEquals(TransactionType.SelfTransfer, transfer.transactionType)
        // A credit to a named UPI recipient is genuinely ambiguous (may be a real payment) → review.
        val ambiguous = parse("ICICI Bank Acct XX000 debited for Rs 42.00 on 01-Jan-26; somepayee00 credited. UPI:000000000000. Call 18002662 for dispute. SMS BLOCK 000 to 9210000000.")
        assertEquals(ParseDecision.NeedsReview, ambiguous.decision)
    }

    @Test fun hsbc_used_at_credit_card_spend_records_despite_limit_and_due() {
        val r = parse("HSBC creditcard xxxxx7681 used at zepto marketplace private for INR 189.00 on 20/06/26.Limit Rs 1281754.10 Due Rs -1754.10.Report fraud on +914061268002")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(18900L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
        assertEquals(Channel.CreditCard, r.channel)
        assertEquals("••••7681", r.maskedAccountHint)
    }

    @Test fun paytm_upi_sent_records_despite_balance_link() {
        val r = parse("Rs.169.00 sent to credpay.zepto@axisb from PPBL a/c 91XX6831. UPI Ref:232926156001. Balance:https://m.paytm.me/pbCheckBal. Query:http://m.p-y.tm/care")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(16900L, r.amountMinor)
        assertEquals(Direction.Debit, r.direction)
        assertEquals(Channel.UPI, r.channel)
        assertEquals("credpay.zepto@axisb", r.counterpartyLabel)
        assertEquals("••••6831", r.maskedAccountHint)
    }

    @Test fun onecard_inr_spend_records_but_foreign_currency_goes_to_review() {
        val inr = parse("Fresh picks! You've spent Rs. 227.00 at Swiggy Limited with your Federal One Credit Card ending in XX0000. Reward points are now in your basket. To dispute this payment, click: m.1crd.in/OneCrd/shcut")
        assertEquals(ParseDecision.Record, inr.decision)
        assertEquals(22700L, inr.amountMinor)
        assertEquals(Channel.CreditCard, inr.channel)
        val inr2 = parse("Superb choice! Rs. 1,544.90 spent at Fpl Technologies Pvt on your Federal Bank  One Credit Card xxXX0000. Reward points added.")
        assertEquals(ParseDecision.Record, inr2.decision)
        assertEquals(154490L, inr2.amountMinor)
        // Foreign-currency spend must NOT be recorded as INR.
        val usd = parse("All set to go! You've spent USD 10.00 at Rtc, Las Vegas with your Federal One Credit Card ending in XX0000. Reward points are all packed.")
        assertEquals(ParseDecision.NeedsReview, usd.decision)
    }

    @Test fun amazon_pay_full_phrasing_records() {
        val r = parse("Payment of Rs 154.00 using Amazon Pay balance is successful at Amazon.in. Updated Balance: 0.00. For help/stmt: https://www.amazon.in/cstxn")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(15400L, r.amountMinor)
        assertEquals(Channel.Wallet, r.channel)
    }

    @Test fun axis_multiline_inline_amount_variant_records() {
        val r = parse("Spent \n Axis Bank Card no. XX0000 \n INR 233 09-01-24 21:06:52 IST \n BUNDL TECHN \n Avl Lmt INR 94243.42 \n SMS BLOCK 0000 to 910000000000, if not you.")
        assertEquals(ParseDecision.Record, r.decision)
        assertEquals(23300L, r.amountMinor)
        assertEquals(Channel.Card, r.channel)
    }

    @Test fun mutual_fund_and_vendor_receipts_go_to_review() {
        listOf(
            "Dear Investor,Your purchase request Dt 06/02/2024 for Rs. 49997.50 in scheme quant Small Cap Fund - Regular Plan is processed @ NAV of Rs. 240.3974 and 207.979 units are allotted in Folio:XXXXXXXX332 . Rgds, quant Mutual Fund" to 4999750L,
            "We confirm receipt of your request for New Purchase of Rs.50000.00 in Folio-XXXXXXX0000 under quant Small Cap Fund on 06/02/2024 vide 000000000." to 5000000L,
            "Bangalore Electricity Supply Co. Ltd (BESCOM) payment for 0000000000 with Rs.298 on 08-02-2026 is successful, Txn ID -000000000000000. Not You?Call 180023400 - Airtel Payments Bank" to 29800L,
            "Dear Bhaskar, \n  \n Your payment of Rs 108.0 is successful for your bbnow order. \n  \n Regards, \n Team bigbasket" to 10800L,
            "1/2 Recharge of INR 349.00 is successful for your Airtel Mobile on 10-07-2026 05:43 PM, TransID: 0000000000." to 34900L,
        ).forEach { (body, amt) ->
            val r = parse(body)
            assertEquals(body, ParseDecision.NeedsReview, r.decision)
            assertEquals(body, amt, r.amountMinor)
            assertEquals(body, Direction.Debit, r.direction)
        }
    }
}

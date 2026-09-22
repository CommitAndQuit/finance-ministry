package `in`.txnsense.app.parser.induction

/**
 * Messages a learned pattern must never match.
 *
 * Induction's failure mode is over-generalization: a pattern grown from three genuine spends that
 * also happens to fit an OTP or a promotion would silently invent transactions. Every candidate is
 * therefore replayed against this list and discarded if it matches any entry, which bounds the
 * damage a loose pattern can do before a human ever sees it.
 *
 * All values are invented. Nothing here comes from a received message, and the list is shipped so
 * the check works on a device that has never seen one of these categories.
 */
object NegativeCorpus {

    val bodies: List<String> = listOf(
        // One-time passwords and verification, which carry amounts and merchant names.
        "OTP 123456 for INR 250 UPI transaction. Do not share with anyone.",
        "123456 is your one time password to pay Rs.500 at TEST SHOP. Valid for 10 minutes.",
        "Use verification code 987654 to authorize Rs.1,200 on your Card XX0000.",
        // Promotions and offers.
        "Increase your credit limit to INR 50,000 today. Apply now.",
        "Congratulations! You are eligible for a pre-approved loan of Rs.2,00,000.",
        "FREE tickets with your Credit Card. Apply today and win rewards.",
        "Get 10% cashback up to Rs.500 on your next purchase at TEST SHOP.",
        "Your reward points 1234 are expiring soon. Redeem before 01-01-2026.",
        // Bills, dues and statements: real amounts, no movement.
        "Credit Card bill generated for Rs.42.00. Pay now to avoid charges.",
        "Statement Generated: For HDFC Bank Credit Card XX0000. Total due Rs.9,000.",
        "INR 500 payment due tomorrow for your Card ending 0000.",
        "Your EMI of Rs.3,500 will be debited on 01-01-2026 from a/c XX0000.",
        "Minimum amount due Rs.900 on Card XX0000 by 01-01-2026.",
        // Mandates and scheduled instructions.
        "UPI Mandate Set for Rs.42 to TEST SHOP. It will be debited monthly.",
        "AutoPay Active! For TEST SHOP Starting:01/01/26 On HDFC Bank Card 0000",
        "A mandate of Rs.1,000 has been requested by TEST SHOP for your a/c XX0000.",
        // Balance and limit notices with no transaction.
        "Available balance: INR 900 in a/c XX0000 as on 01-01-2026.",
        "Avl bal in your a/c XX0000 is Rs.9,000.00. Avl limit Rs.50,000.",
        // Administration that mentions cards and accounts but moves nothing.
        "Request to link your HDFC Bank Credit Card 0000 to UPI.",
        "PIN Change successful! For HDFC Bank Debit Card XX0000.",
        "Successfully modified limits for your Credit Card XX0000.",
        "Your Debit Card XX0000 was issued today and will be delivered shortly.",
        "Your Card XX0000 has been blocked as requested on 01-01-2026.",
        // In-flight and negated movements, which must not be learned as settled ones.
        "Your transfer request of Rs.500 is processing. You will be notified.",
        "No transaction happened; account XX0000 not debited for Rs.500.",
        "Rs.500 transfer to TEST SHOP has been initiated from a/c XX0000.",
        // Investment and insurance statements.
        "Your policy premium of Rs.12,000 is due on 01-01-2026. Renew now.",
        "NAV for 01-01-2026 is Rs.42.0000. Your folio holds 123.456 units.",
        // Delivery and service notifications that carry an amount.
        "Your order of Rs.999 has been shipped. Track at http://example.invalid/r",
        "Recharge plan of Rs.299 is expiring. Renew to avoid service interruption.",
    )
}

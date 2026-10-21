package `in`.financeministry.app.data

/** Enrich untouched imports only. Recovering an amount never approves money. */
internal object ImportRepairs {
    fun propose(old: TransactionEntity, parsed: TransactionEntity): TransactionEntity? {
        if (old.sourceType != "SMS" || old.importBatchId == null || old.sourceFingerprint == null ||
            old.currency !in listOf(null, "INR") ||
            old.reviewState !in listOf("NeedsReview", "AutoRecorded") || old.isUserCorrected || old.updatedAt != old.createdAt ||
            old.parserVersion >= parsed.parserVersion || !old.userNotes.isNullOrBlank() ||
            old.ownership != "Personal" || old.groupLabel != null || old.personalShareMinor != null ||
            old.repaidMinor != 0L || old.duplicateOfId != null || old.linkedOriginalId != null ||
            old.direction != "Debit" || old.status != "Successful" || old.channel !in listOf("Unknown", "UPI", "Card") ||
            old.transactionType !in listOf("Unknown", "Other", "MerchantPayment", "P2PTransfer") ||
            parsed.amountMinor == null || parsed.amountMinor <= 0 || parsed.currency != "INR" ||
            parsed.direction != "Debit" || parsed.status != "Successful" || parsed.channel !in listOf("Unknown", "UPI", "Card") ||
            parsed.transactionType !in listOf("Unknown", "Other", "MerchantPayment", "P2PTransfer") ||
            (old.maskedAccountHint != null && old.maskedAccountHint != parsed.maskedAccountHint)) return null
        val recoveringAmount = old.amountMinor == null
        if (recoveringAmount) {
            // The earlier recovery contract stays deliberately narrow: missing
            // amounts in successful UPI imports must be reviewed before counting.
            if (old.reviewState != "NeedsReview" || old.channel !in listOf("Unknown", "UPI") ||
                parsed.channel != "UPI" || old.transactionType !in listOf("Unknown", "Other")) return null
        } else {
            // Field-only enrichment must never alter money, method or counted state.
            if (old.amountMinor != parsed.amountMinor || old.currency != parsed.currency ||
                (old.channel != "Unknown" && old.channel != parsed.channel) ||
                (old.transactionType !in listOf("Unknown", "Other") && old.transactionType != parsed.transactionType)) return null
            val improvesMethod = old.channel == "Unknown" && parsed.channel != "Unknown"
            val improvesLabel = old.counterpartyLabel.isNullOrBlank() && !parsed.counterpartyLabel.isNullOrBlank()
            val improvesType = old.transactionType == "Unknown" && parsed.transactionType !in listOf("Unknown", "Other")
            if (!improvesMethod && !improvesLabel && !improvesType) return null
        }
        return old.copy(amountMinor = parsed.amountMinor, currency = parsed.currency, channel = parsed.channel,
            transactionType = if (old.transactionType in listOf("Unknown", "Other")) parsed.transactionType else old.transactionType,
            counterpartyLabel = old.counterpartyLabel?.takeIf { it.isNotBlank() } ?: parsed.counterpartyLabel,
            maskedAccountHint = old.maskedAccountHint ?: parsed.maskedAccountHint,
            referenceHash = old.referenceHash ?: parsed.referenceHash,
            confidence = parsed.confidence, parserVersion = parsed.parserVersion,
            reviewState = if (recoveringAmount) "NeedsReview" else old.reviewState)
    }
}

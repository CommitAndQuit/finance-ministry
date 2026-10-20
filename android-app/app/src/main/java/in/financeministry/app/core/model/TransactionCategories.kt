package `in`.financeministry.app.core.model

// Other remains the unclassified fallback; named defaults stay intentionally small.
val transactionCategories = listOf("Other", "Grocery", "Food", "Entertainment", "Bills", "Transport", "Investment")

// Retired suggestions are still valid in existing ledgers, rules and backups.
internal val supportedTransactionCategories = transactionCategories +
    listOf("Travel", "Shopping", "Flat expenses", "Health", "Education", "Cash", "Income")

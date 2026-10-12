package `in`.financeministry.app

import androidx.compose.ui.test.*

/** A native field may name itself by caption or accessibility label; never select just the caption. */
internal fun editableLedgerField(label: String): SemanticsMatcher = hasSetTextAction() and
    (hasContentDescription(label) or hasText(label))

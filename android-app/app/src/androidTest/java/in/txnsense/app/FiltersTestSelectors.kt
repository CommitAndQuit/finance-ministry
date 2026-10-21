package `in`.txnsense.app

import androidx.compose.ui.test.*

/** Follow the visible filter controls rather than invoking hidden semantics actions. */
internal fun SemanticsNodeInteractionsProvider.openAdvancedFilters() {
    if (onAllNodesWithText("Fewer filters").fetchSemanticsNodes().isEmpty()) {
        onNodeWithText("More filters").performScrollTo().performClick()
    }
}

internal fun SemanticsNodeInteractionsProvider.openFilterCategories() {
    onNodeWithContentDescription("Choose filter categories").performScrollTo().performClick()
}

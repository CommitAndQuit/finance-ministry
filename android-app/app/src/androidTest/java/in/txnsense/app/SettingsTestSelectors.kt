package `in`.txnsense.app

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Follow the real progressive-disclosure path; no hidden semantics actions or operation retries. */
internal fun SemanticsNodeInteractionsProvider.openSettingsTool(title: String) {
    if (onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()) {
        onNodeWithText("More settings").performScrollTo().performClick()
    }
    onNodeWithText(title).performScrollTo().performClick()
}

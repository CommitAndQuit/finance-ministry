package `in`.txnsense.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText

/** Headers and primary actions share wording; only select the editor's actionable save. */
internal fun publicEditorSaveButton(): SemanticsMatcher = hasClickAction() and
    (hasText("Add payment") or hasText("Confirm payment") or hasText("Save changes"))

/** A choice's heading and outlined value are separate nodes in the approved form. */
internal fun publicEditorChoice(label: String, value: String): SemanticsMatcher =
    hasText(value) and hasClickAction() and hasAnyAncestor(hasAnyChild(hasText(label)))

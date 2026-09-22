@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.txnsense.app.feature

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Scopes for the top-level navigation shared-element transition, provided once in MainActivity so
 * any screen can opt a composable into the morph without threading the scopes through every call.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Tags a composable as one end of a shared-bounds morph (e.g. a transaction row expanding into its
 * detail view). Both ends use the same [key]. A no-op when the scopes are absent (e.g. previews).
 */
@Composable
fun Modifier.expandFromRow(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    return if (shared != null && animated != null) {
        with(shared) { this@expandFromRow.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = animated) }
    } else this
}

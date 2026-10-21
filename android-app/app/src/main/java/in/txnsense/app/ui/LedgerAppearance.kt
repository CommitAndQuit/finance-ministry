package `in`.txnsense.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat

internal const val APPEARANCE_KEY = "appearance"
internal enum class LedgerAppearance { System, Light, Dark }
internal val LocalLedgerAppearance = compositionLocalOf { LedgerAppearance.System }

/** Invalid/older local preferences fall back safely; backup input is validated separately. */
internal fun ledgerAppearance(value: Any?): LedgerAppearance =
    LedgerAppearance.entries.firstOrNull { it.name == value } ?: LedgerAppearance.System

/** Native date/time dialogs use the same resolved appearance as their Compose owner. */
@Composable
internal fun ledgerPickerTheme(): Int =
    if (MaterialTheme.colorScheme.background.luminance() < .5f) `in`.txnsense.app.R.style.Theme_TxnSense_Picker_Dark
    else `in`.txnsense.app.R.style.Theme_TxnSense_Picker_Light

@Composable
internal fun LocalLedgerTheme(preferences: SharedPreferences, content: @Composable () -> Unit) {
    var appearance by remember(preferences) { mutableStateOf(ledgerAppearance(preferences.all[APPEARANCE_KEY])) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == APPEARANCE_KEY) appearance = ledgerAppearance(prefs.all[APPEARANCE_KEY])
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        LedgerAppearance.System -> systemDark
        LedgerAppearance.Light -> false
        LedgerAppearance.Dark -> true
    }
    val context = LocalContext.current
    val activity = remember(context) {
        var owner: Context = context
        while (owner is ContextWrapper && owner !is ComponentActivity) owner = owner.baseContext
        owner as? ComponentActivity
    }
    // Apply only when the theme changes, never on transaction-field recomposition.
    LaunchedEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
        )
    }
    CompositionLocalProvider(LocalLedgerAppearance provides appearance) {
        FinanceLedgerTheme(dark = dark, content = content)
    }
}

/** A live appearance change must also update the modal's independent Android window. */
@Composable
internal fun LedgerSheetSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    LaunchedEffect(window, dark) {
        if (window != null) WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

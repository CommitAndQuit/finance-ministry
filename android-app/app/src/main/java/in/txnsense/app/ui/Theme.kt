package `in`.txnsense.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.R

/**
 * Single source of truth for the app's palette. Material roles (primary/secondary/background/
 * surface/…) drive every standard component and screen; [Brand] carries the extra fintech accents
 * (lime/yellow/navy/teal) that don't map onto a Material role. Read colors via
 * `MaterialTheme.colorScheme` and `LocalBrand.current` — never hard-code Color(...) in screens.
 */

// ---- Raw brand palette ----
private val Cream = Color(0xFFF5F5EE)
private val Lime = Color(0xFFC8E63C)
private val Yellow = Color(0xFFF5D90A)
private val Navy = Color(0xFF1A2332)
private val Teal = Color(0xFF2EC4B6)
private val Ink = Color(0xFF1A1A2E)
private val Muted = Color(0xFF888888)
private val Hairline = Color(0xFFD8D8CE)
private val White = Color(0xFFFFFFFF)

private val DarkBg = Color(0xFF14171F)
private val DarkSurface = Color(0xFF1E2430)
private val DarkMuted = Color(0xFFA8A8A0)
private val DarkHairline = Color(0xFF2A2F3A)

// Home background gradient — light green (bottom) to a lighter variant (top).
private val GreenTop = Color(0xFFEEF4DF)
private val GreenBottom = Color(0xFFD4E7B4)
private val DarkGreenTop = Color(0xFF1B241C)
private val DarkGreenBottom = Color(0xFF10160F)

/** Extra accents beyond Material roles, plus the correct text color to place on each accent. */
data class Brand(
    val lime: Color,
    val yellow: Color,
    val navy: Color,
    val teal: Color,
    val onAccentDark: Color,   // text/icons on light accents (lime/yellow/teal)
    val onAccentLight: Color,  // text/icons on dark accents (navy)
    val textSecondary: Color,
    val hairline: Color,
    val bgTop: Color,          // screen gradient top
    val bgBottom: Color,       // screen gradient bottom
    val glassTop: Color,       // frosted-glass fill (top)
    val glassBottom: Color,    // frosted-glass fill (bottom)
    val glassBorder: Color,    // frosted-glass edge highlight
)

private val LightBrand = Brand(
    lime = Lime, yellow = Yellow, navy = Navy, teal = Teal,
    onAccentDark = Ink, onAccentLight = Cream, textSecondary = Muted, hairline = Hairline,
    bgTop = GreenTop, bgBottom = GreenBottom,
    glassTop = White.copy(alpha = 0.55f), glassBottom = White.copy(alpha = 0.28f), glassBorder = White.copy(alpha = 0.6f),
)
private val DarkBrand = LightBrand.copy(
    textSecondary = DarkMuted, hairline = DarkHairline, bgTop = DarkGreenTop, bgBottom = DarkGreenBottom,
    glassTop = White.copy(alpha = 0.10f), glassBottom = White.copy(alpha = 0.04f), glassBorder = White.copy(alpha = 0.16f),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** Bundled Montserrat (res/font) — the app's single typeface, used for every text style. */
val Montserrat: FontFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

/** Every Material text style rendered in Montserrat. H1 = [Typography.headlineLarge]. */
private val Base = Typography()
private val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Montserrat),
    displayMedium = Base.displayMedium.copy(fontFamily = Montserrat),
    displaySmall = Base.displaySmall.copy(fontFamily = Montserrat),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontFamily = Montserrat),
    titleSmall = Base.titleSmall.copy(fontFamily = Montserrat),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Montserrat),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Montserrat),
    bodySmall = Base.bodySmall.copy(fontFamily = Montserrat),
    labelLarge = Base.labelLarge.copy(fontFamily = Montserrat),
    labelMedium = Base.labelMedium.copy(fontFamily = Montserrat),
    labelSmall = Base.labelSmall.copy(fontFamily = Montserrat),
)

private val LightColors = lightColorScheme(
    primary = Navy, onPrimary = White,
    primaryContainer = Color(0xFFE8F3B8), onPrimaryContainer = Navy,
    secondary = Teal, onSecondary = Navy,
    secondaryContainer = Color(0xFFD4EEE6), onSecondaryContainer = Navy,
    tertiary = Lime, onTertiary = Navy,
    background = Cream, onBackground = Ink,
    surface = White, onSurface = Ink,
    surfaceVariant = Color(0xFFECECE3), onSurfaceVariant = Muted,
    outline = Hairline, outlineVariant = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = Teal, onPrimary = Navy,
    primaryContainer = Color(0xFF224D45), onPrimaryContainer = Cream,
    secondary = Lime, onSecondary = Navy,
    secondaryContainer = Color(0xFF3A4A1E), onSecondaryContainer = Cream,
    tertiary = Lime, onTertiary = Navy,
    background = DarkBg, onBackground = Cream,
    surface = DarkSurface, onSurface = Cream,
    surfaceVariant = DarkHairline, onSurfaceVariant = DarkMuted,
    outline = DarkHairline, outlineVariant = DarkHairline,
)

@Composable
fun TxnSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBrand provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography) {
            // Make Montserrat the default for ad-hoc Text() calls that don't reference a typography style.
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Montserrat), content = content)
        }
    }
}

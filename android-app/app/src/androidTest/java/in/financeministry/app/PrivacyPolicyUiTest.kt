package `in`.financeministry.app

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.feature.PrivacyPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyPolicyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun online_policy_opens_public_https_page_without_financial_payload() {
        var launched: Intent? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                launched = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            showPolicy()
            rule.onNodeWithText("Open online privacy policy").performScrollTo().performClick()
            rule.runOnIdle {
                val intent = requireNotNull(launched)
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals("https://hk121902-stack.github.io/finance-ministry/privacy.html", intent.data.toString())
                assertNull(intent.extras)
                assertNull(intent.clipData)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun missing_browser_keeps_offline_policy_and_shows_public_url_and_support() {
        val noBrowser = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        rule.setContent { CompositionLocalProvider(LocalContext provides noBrowser) {
            FinanceMinistryTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
                PrivacyPanel(onErase = {}, busy = false)
            } } }
        } }
        rule.onNodeWithText("Open online privacy policy").performScrollTo().performClick()
        rule.onNode(hasText("https://hk121902-stack.github.io/finance-ministry/privacy.html", substring = true)
            and hasText("financeministrysupport@gmail.com", substring = true))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Stored data").performScrollTo().assertIsDisplayed()
    }

    private fun showPolicy() {
        rule.setContent { FinanceMinistryTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) { PrivacyPanel(onErase = {}, busy = false) }
        } } }
    }
}

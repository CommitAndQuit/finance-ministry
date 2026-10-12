package `in`.financeministry.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val request = mutableStateOf<Pair<String, Boolean>?>(null)
    private val quickRequest = mutableStateOf(false)
    private val reviewRequestGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    private val addRequestGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    private val optionalToolRequest = mutableStateOf<String?>(null)
    private val resumeGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readRequest(intent)
        setContent {
            FinanceMinistryTheme {
                LedgerApp((application as FinanceMinistryApp).container.repository, request.value, resumeGeneration.intValue,
                    reviewRequestGeneration.intValue, addRequestGeneration.intValue,
                    optionalToolRequest = optionalToolRequest.value, quickRequest = quickRequest.value,
                    consumeOptionalToolRequest = { optionalToolRequest.value = null }) {
                    request.value = null
                    intent.removeExtra("transaction_id"); intent.removeExtra("edit"); intent.removeExtra("quick_classify")
                }
            }
        }
    }
    override fun onResume() { super.onResume(); resumeGeneration.intValue++ }
    public override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readRequest(intent) }
    fun requestReview() { reviewRequestGeneration.intValue++ }
    private fun readRequest(intent: Intent) {
        quickRequest.value = intent.getBooleanExtra("quick_classify", false)
        request.value = intent.getStringExtra("transaction_id")?.let { it to intent.getBooleanExtra("edit", false) }
        if (intent.getBooleanExtra("open_review", false)) {
            requestReview()
            intent.removeExtra("open_review")
        }
        if (intent.getBooleanExtra("open_add", false)) {
            addRequestGeneration.intValue++
            intent.removeExtra("open_add")
        }
        intent.getStringExtra("open_optional_tools")?.let {
            optionalToolRequest.value = it
            intent.removeExtra("open_optional_tools")
        }
    }
}

@Composable
fun FinanceMinistryTheme(content: @Composable () -> Unit) {
    `in`.financeministry.app.ui.FinanceLedgerTheme(content = content)
}

@Composable
fun FinanceMinistryNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "shell") {
        composable("shell") {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Finance Ministry", style = MaterialTheme.typography.headlineMedium)
                    Text(text = "Private alpha shell")
                }
            }
        }
    }
}

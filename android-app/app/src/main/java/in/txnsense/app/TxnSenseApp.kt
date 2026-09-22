package `in`.txnsense.app

import android.app.Application

class TxnSenseApp : Application() {
    val container by lazy { AppContainer(this) }
}

package `in`.txnsense.app

import android.app.Application

class AppContainer(
    val application: Application,
) {
    val repository by lazy { `in`.txnsense.app.data.TransactionRepository(application) }
}

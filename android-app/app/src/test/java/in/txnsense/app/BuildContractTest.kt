package `in`.txnsense.app

import org.junit.Assert.assertSame
import org.junit.Test

class BuildContractTest {
    @Test
    fun applicationRetainsOneContainerBoundToIt() {
        val application = TxnSenseApp()

        val firstContainer = application.container
        val secondContainer = application.container

        assertSame(application, firstContainer.application)
        assertSame(firstContainer, secondContainer)
    }
}

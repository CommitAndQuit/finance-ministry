package `in`.financeministry.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LauncherIconTest {
    @Test fun launcher_resolves_adaptive_layers_and_a_nonempty_themed_mark() {
        val context = ApplicationProvider.getApplicationContext<FinanceMinistryApp>()
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("Launcher must use an adaptive asset", icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(icon.monochrome)
        val mark = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.ic_launcher_monochrome)!!.apply {
            setBounds(0, 0, 108, 108); draw(Canvas(mark))
        }
        var opaque = 0
        for (y in 0 until 108) for (x in 0 until 108) {
            if (Color.alpha(mark.getPixel(x, y)) > 128) {
                opaque++
                assertTrue("Mark clipped by launcher safe-zone circle", (x - 54)*(x - 54)+(y - 54)*(y - 54) < 33*33)
            }
        }
        assertTrue("Themed icon must not be empty", opaque > 500)
        if (InstrumentationRegistry.getArguments().getString("captureIcon") == "true") {
            val bitmap = Bitmap.createBitmap(1200, 760, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(247, 246, 242))
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(23, 36, 78); textSize = 52f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            canvas.drawText("Finance Ministry", 72f, 102f, ink)
            ink.textSize = 24f; ink.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("A quieter, sharper identity.", 72f, 144f, ink)
            val previous = context.getDrawable(R.drawable.ic_finance_ministry)!!
            previous.setBounds(85, 240, 285, 440); previous.draw(canvas)
            icon.setBounds(410, 215, 690, 495); icon.draw(canvas)
            val themed = AdaptiveIconDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(221, 228, 248)),
                context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate().apply { setTint(Color.rgb(23, 36, 78)) })
            themed.setBounds(825, 240, 1025, 440); themed.draw(canvas)
            canvas.drawText("BEFORE", 125f, 555f, ink)
            canvas.drawText("NEW · INK BLUE", 452f, 555f, ink)
            canvas.drawText("THEMED", 865f, 555f, ink)
            icon.setBounds(500, 623, 548, 671); icon.draw(canvas)
            icon.setBounds(572, 615, 636, 679); icon.draw(canvas)
            ink.textSize = 19f
            canvas.drawText("Small-size check", 492f, 716f, ink)
            File(context.getExternalFilesDir(null), "launcher-icon-preview.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}

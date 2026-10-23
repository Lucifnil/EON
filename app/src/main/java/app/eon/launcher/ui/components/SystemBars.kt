package app.eon.launcher.ui.components

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
fun Window.applyEonNavigationBar(view: View = decorView) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    navigationBarColor = Color.TRANSPARENT
    WindowCompat.getInsetsController(this, view).isAppearanceLightNavigationBars = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}

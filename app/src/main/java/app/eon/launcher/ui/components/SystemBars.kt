package app.eon.launcher.ui.components

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Suppress("DEPRECATION")
fun Window.applyEonNavigationBar(view: View = decorView) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    WindowCompat.getInsetsController(this, view).apply {
        isAppearanceLightNavigationBars = true
        isAppearanceLightStatusBars = true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}

@Composable
fun EonDialogSystemBars() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.applyEonNavigationBar(view)
    }
}

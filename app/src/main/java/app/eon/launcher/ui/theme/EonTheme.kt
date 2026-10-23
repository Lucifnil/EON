package app.eon.launcher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EonBlue = Color(0xFF007AFF)
val EonBackground = Color(0xFFF7F7F9)
val EonInk = Color(0xFF111116)
val EonSecondary = Color(0xFF6E6E73)
val EonSeparator = Color(0xFFE5E5EA)
val EonGreen = Color(0xFF22A447)
val EonRed = Color(0xFFFF3B30)

private val EonColors: ColorScheme = lightColorScheme(
    primary = EonBlue,
    onPrimary = Color.White,
    background = EonBackground,
    onBackground = EonInk,
    surface = Color.White,
    onSurface = EonInk,
    surfaceVariant = Color(0xFFF0F0F4),
    onSurfaceVariant = EonSecondary,
    outline = EonSeparator,
    error = EonRed,
)

@Composable
fun EonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EonColors,
        typography = Typography(),
        content = content,
    )
}

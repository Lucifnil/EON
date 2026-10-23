package app.eon.launcher.ui.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Immutable
data class EonGlassState(
    val backdrop: LayerBackdrop,
    val haze: HazeState,
)

@Composable
fun rememberEonGlassState(): EonGlassState {
    val backdrop = rememberLayerBackdrop()
    return EonGlassState(backdrop, rememberHazeState())
}

fun Modifier.eonBackdropSource(state: EonGlassState): Modifier =
    layerBackdrop(state.backdrop).hazeSource(state.haze)

fun Modifier.eonGlass(
    state: EonGlassState,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 16.dp,
    strong: Boolean = false,
    refractionHeight: Dp = if (strong) 24.dp else 16.dp,
    refractionAmount: Dp = if (strong) 24.dp else 18.dp,
    surfaceAlpha: Float = if (strong) 0.30f else 0.22f,
): Modifier {
    val effectiveBlur = blurRadius.value.coerceAtLeast(0f).dp
    val base = this
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.drawBackdrop(
            backdrop = state.backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(effectiveBlur.toPx())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    lens(
                        refractionHeight = refractionHeight.toPx(),
                        refractionAmount = (refractionAmount * 1.15f).toPx(),
                        depthEffect = false,
                    )
                }
            },
            onDrawSurface = {
                drawRect(Color(0xFFFAFAFA).copy(alpha = surfaceAlpha))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (strong) 0.20f else 0.15f),
                        0.48f to Color.Transparent,
                        1f to Color.White.copy(alpha = if (strong) 0.07f else 0.05f),
                    ),
                )
            },
        )
    } else {
        base.hazeEffect(state.haze) {
            this.blurRadius = effectiveBlur
            backgroundColor = Color(0xFFF7F7F9)
            tints = listOf(HazeTint(Color.White.copy(alpha = surfaceAlpha + 0.08f)))
            fallbackTint = HazeTint(Color.White.copy(alpha = 0.76f))
            noiseFactor = 0.035f
        }
    }
    return glass.border(0.8.dp, Color.White.copy(alpha = 0.82f), shape)
}

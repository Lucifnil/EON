package app.eon.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Press/drag physics adapted from Kyant0/AndroidLiquidGlass' catalog sample
 * (Apache-2.0). It keeps the selected lens enlarged while pressed and adds a
 * velocity-dependent stretch while it travels between tabs.
 */
class LiquidTabMotion(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val onDragStarted: LiquidTabMotion.(position: Offset) -> Unit,
    private val onDragStopped: LiquidTabMotion.() -> Unit,
    private val onDrag: LiquidTabMotion.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private var requestedValue = initialValue

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectLiquidDragGestures(
            onDragStart = {
                onDragStarted(it.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        requestedValue = target
        animationScope.launch {
            valueAnimation.animateTo(target, spring(1f, 1000f, 0.001f)) {
                updateVelocity()
            }
        }
    }

    fun animateToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                val target = requestedValue
                launch { valueAnimation.animateTo(target, spring(1f, 1000f, 0.001f)) }
                launch { velocityAnimation.animateTo(0f, spring(0.5f, 300f, 0.01f)) }
            }
        }
    }

    private fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(78f / 56f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(78f / 56f, spring(0.7f, 250f, 0.001f)) }
        }
    }

    private fun release() {
        animationScope.launch {
            if (value != targetValue) {
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < 0.05f }
                    .first()
            }
            launch { pressAnimation.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(1f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(1f, spring(0.7f, 250f, 0.001f)) }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val range = valueRange.endInclusive - valueRange.start
        val targetVelocity = velocityTracker.calculateVelocity().x / range
        animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, spring(0.5f, 300f, 0.01f))
        }
    }
}

private suspend fun PointerInputScope.inspectLiquidDragGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                onDragEnd(change)
                break
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                onDrag(change, delta)
                change.consume()
            }
        }
    }
}

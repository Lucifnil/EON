package app.eon.launcher.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.eon.launcher.EonViewModel
import app.eon.launcher.model.SoFile
import app.eon.launcher.ui.components.eonBackdropSource
import app.eon.launcher.ui.components.LiquidTabMotion
import app.eon.launcher.ui.components.rememberEonGlassState
import app.eon.launcher.ui.theme.EonBackground
import app.eon.launcher.ui.theme.EonBlue
import app.eon.launcher.ui.theme.EonSecondary
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.ui.util.lerp

private data class EonTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    EonTab("文件", Icons.Outlined.Folder),
    EonTab("密钥", Icons.Outlined.Key),
    EonTab("日志", Icons.Outlined.Description),
)

internal fun pagerIndicatorPosition(currentPage: Int, offsetFraction: Float, pageCount: Int): Float =
    (currentPage + offsetFraction).coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())

@Composable
fun EonApp(viewModel: EonViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val glassState = rememberEonGlassState()
    val launchSheetGlassState = rememberEonGlassState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedFile by remember { mutableStateOf<SoFile?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsBackProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    PredictiveBackHandler(enabled = showSettings) { events ->
        try {
            events.collect { settingsBackProgress = it.progress }
            showSettings = false
            settingsBackProgress = 0f
        } catch (_: CancellationException) {
            settingsBackProgress = 0f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(EonBackground),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .eonBackdropSource(launchSheetGlassState),
        ) {
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    scanPaths = state.scanPaths,
                    scanErrors = state.scanErrors,
                    onBrowseDirectory = viewModel::browseDirectory,
                    onAddScanPath = viewModel::addScanPath,
                    onRemoveScanPath = viewModel::removeScanPath,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = size.width * 0.14f * settingsBackProgress
                            alpha = 1f - 0.12f * settingsBackProgress
                        }
                        .background(EonBackground)
                        .eonBackdropSource(glassState),
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(EonBackground)
                        .eonBackdropSource(glassState),
                ) { page ->
                    when (page) {
                        0 -> FilesScreen(
                            state = state,
                            onQueryChange = viewModel::setQuery,
                            onRefresh = viewModel::scan,
                            onRootRetry = viewModel::checkRoot,
                            onSettings = { showSettings = true },
                            onFileClick = { viewModel.clearMessage(); selectedFile = it },
                            onTipSeen = viewModel::markScanTipSeen,
                            onDismissTip = viewModel::dismissScanTip,
                            isActive = pagerState.settledPage == page,
                        )
                        1 -> KeysScreen(
                            keys = state.keys,
                            onReadKey = viewModel::readKey,
                            onSave = viewModel::saveKey,
                            onDelete = viewModel::deleteKey,
                        )
                        else -> LogsScreen(
                            run = state.run,
                            onStop = viewModel::stop,
                            onClear = viewModel::clearLogs,
                        )
                    }
                }

                if (selectedFile == null) {
                    EonBottomBar(
                        pagePosition = { pagerIndicatorPosition(pagerState.currentPage, pagerState.currentPageOffsetFraction, tabs.size) },
                        onPageSelected = { index ->
                            scope.launch { pagerState.animateScrollToPage(index, animationSpec = spring()) }
                        },
                        glassState = glassState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (showSettings) 20.dp else 104.dp),
            )
        }

        selectedFile?.let { file ->
            LaunchSheet(
                file = file,
                keys = state.keys,
                lastKeyId = viewModel.lastKeyId(file.path),
                message = state.message,
                glassState = launchSheetGlassState,
                onReadKey = viewModel::readKey,
                onDismiss = { selectedFile = null },
                onLaunch = { secret, selectedKeyId, args, workDir ->
                    viewModel.launch(file, secret, selectedKeyId, args, workDir).also { accepted ->
                        if (accepted) {
                            selectedFile = null
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun EonBottomBar(
    pagePosition: () -> Float,
    onPageSelected: (Int) -> Unit,
    glassState: app.eon.launcher.ui.components.EonGlassState,
    modifier: Modifier = Modifier,
) {
    val barBackdrop = rememberLayerBackdrop()
    val tabsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(glassState.backdrop, barBackdrop, tabsBackdrop)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val horizontalInset = 4.dp
        val horizontalInsetPx = with(density) { horizontalInset.toPx() }
        val tabWidth = (maxWidth - horizontalInset * 2) / tabs.size
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val animationScope = rememberCoroutineScope()
        val panelDrag = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (panelDrag.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val motion = remember(animationScope, tabWidthPx) {
            LiquidTabMotion(
                animationScope = animationScope,
                initialValue = pagePosition(),
                valueRange = 0f..tabs.lastIndex.toFloat(),
                onDragStarted = { position ->
                    val pressedIndex = ((position.x - horizontalInsetPx) / tabWidthPx)
                        .toInt()
                        .coerceIn(0, tabs.lastIndex)
                    updateValue(pressedIndex.toFloat())
                },
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(0, tabs.lastIndex)
                    animateToValue(target.toFloat())
                    onPageSelected(target)
                    animationScope.launch { panelDrag.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx)
                            .coerceIn(0f, tabs.lastIndex.toFloat()),
                    )
                    animationScope.launch { panelDrag.snapTo(panelDrag.value + dragAmount.x) }
                },
            )
        }

        LaunchedEffect(motion) {
            snapshotFlow { pagePosition() }
                .collectLatest { position ->
                    if (motion.pressProgress < 0.01f) motion.animateToValue(position)
                }
        }

        // Base glass is deliberately content-free. Labels are rendered once at
        // the very top, so the magnifier cannot reveal a second copy of them.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffset }
                .layerBackdrop(barBackdrop)
                .drawBackdrop(
                    backdrop = glassState.backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, motion.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(Color(0xFFFAFAFA).copy(alpha = 0.34f))
                    },
                )
                .padding(horizontal = horizontalInset),
        )

        // Capture a second, blue copy of the labels for the lens only. The
        // visible copy is faded exactly where the enlarged lens overlaps it,
        // so refraction stays strong without drawing two glyphs at once.
        Row(
            Modifier
                .clearAndSetSemantics { }
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = horizontalInset)
                .graphicsLayer(colorFilter = ColorFilter.tint(EonBlue)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EonTabItems(
                tint = { Color.White },
                scale = { lerp(1f, 1.2f, motion.pressProgress) },
            )
        }

        // The selected item magnifies and refracts the page backdrop. Labels
        // are drawn later, once, to avoid doubled glyphs inside the lens.
        Box(
            Modifier
                .padding(horizontal = horizontalInset)
                .graphicsLayer {
                    translationX = motion.value * tabWidthPx + panelOffset
                }
                .width(tabWidth)
                .height(56.dp)
                .drawBackdrop(
                    backdrop = lensBackdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = {
                        lens(
                            10.dp.toPx() * motion.pressProgress,
                            14.dp.toPx() * motion.pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = motion.pressProgress) },
                    shadow = { Shadow(alpha = motion.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * motion.pressProgress,
                            alpha = motion.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = motion.scaleX
                        scaleY = motion.scaleY
                        val velocity = motion.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        val magnification = lerp(1f, 1.18f, motion.pressProgress)
                        withTransform(
                            { scale(magnification, magnification) },
                            drawBackdrop,
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.10f), alpha = 1f - motion.pressProgress)
                        drawRect(Color.Black.copy(alpha = 0.03f * motion.pressProgress))
                    },
                ),
        )

        // One visual copy of every label sits above both glass layers. The
        // selected label itself scales and changes tint, so magnification never
        // exposes an unscaled grey duplicate underneath it.
        Row(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .graphicsLayer { translationX = panelOffset }
                .padding(horizontal = horizontalInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EonTabItems(
                tint = { index ->
                    val proximity = (1f - abs(index - motion.value)).coerceIn(0f, 1f)
                    lerpColor(EonSecondary, EonBlue, proximity)
                },
                scale = { index ->
                    val proximity = (1f - abs(index - motion.value)).coerceIn(0f, 1f)
                    lerp(1f, 1.2f, motion.pressProgress * proximity)
                },
                alpha = { index ->
                    val distance = abs(index - motion.value)
                    val lensCoverage = ((0.82f - distance) / 0.24f).coerceIn(0f, 1f)
                    1f - motion.pressProgress * lensCoverage
                },
            )
        }

        // One pointer handler owns all touch gestures. Semantics and keyboard
        // activation share the page callback without adding a second tap handler.
        Row(
            Modifier
                .fillMaxSize()
                .then(motion.modifier)
                .selectableGroup()
                .padding(horizontal = horizontalInset),
        ) {
            tabs.forEachIndexed { index, tab ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics {
                            contentDescription = tab.label
                            role = Role.Tab
                            selected = pagePosition().roundToInt() == index
                            onClick {
                                onPageSelected(index)
                                true
                            }
                        }
                        .onKeyEvent { event ->
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                                    if (event.type == KeyEventType.KeyUp) onPageSelected(index)
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EonTabItems(
    tint: (Int) -> Color,
    scale: (Int) -> Float = { 1f },
    alpha: (Int) -> Float = { 1f },
) {
    tabs.forEachIndexed { index, tab ->
        Column(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .graphicsLayer {
                    scaleX = scale(index)
                    scaleY = scale(index)
                    this.alpha = alpha(index)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = tint(index),
                modifier = Modifier.size(23.dp),
            )
            Text(
                tab.label,
                color = tint(index),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

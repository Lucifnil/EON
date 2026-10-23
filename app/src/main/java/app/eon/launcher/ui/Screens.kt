package app.eon.launcher.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eon.launcher.BuildConfig
import app.eon.launcher.model.EonUiState
import app.eon.launcher.model.KeySummary
import app.eon.launcher.model.LogLine
import app.eon.launcher.model.ProcessStatus
import app.eon.launcher.model.RootState
import app.eon.launcher.model.RunState
import app.eon.launcher.model.SoFile
import app.eon.launcher.model.DirectoryListing
import app.eon.launcher.data.ScanPathPolicy
import app.eon.launcher.ui.components.EonGlassState
import app.eon.launcher.ui.components.EonDialog
import app.eon.launcher.ui.components.KeyVaultRow
import app.eon.launcher.ui.components.ScanPathsCard
import app.eon.launcher.ui.components.DirectoryPicker
import app.eon.launcher.ui.components.EonDialogSystemBars
import app.eon.launcher.ui.components.eonGlass
import app.eon.launcher.ui.theme.EonBackground
import app.eon.launcher.ui.theme.EonBlue
import app.eon.launcher.ui.theme.EonGreen
import app.eon.launcher.ui.theme.EonInk
import app.eon.launcher.ui.theme.EonRed
import app.eon.launcher.ui.theme.EonSecondary
import app.eon.launcher.ui.theme.EonSeparator
import kotlinx.coroutines.launch

// Matches the expanded large-title navigation rhythm used by native iOS apps.
private val MainPageTopInset = 36.dp
private val MainPageSectionSpacing = 16.dp
private val MainHeaderHeight = 72.dp

@Composable
fun FilesScreen(
    state: EonUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRootRetry: () -> Unit,
    onSettings: () -> Unit,
    onFileClick: (SoFile) -> Unit,
    onTipSeen: () -> Unit,
    onDismissTip: () -> Unit,
    isActive: Boolean,
) {
    val showTip = isActive && state.showScanPathTip && state.rootState == RootState.READY && state.scanPaths.isNotEmpty()
    LaunchedEffect(showTip) { if (showTip) onTipSeen() }
    val visibleFiles = remember(state.files, state.query) {
        state.files.filter {
            state.query.isBlank() || it.name.contains(state.query, true) || it.path.contains(state.query, true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = MainPageTopInset, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(MainPageSectionSpacing),
    ) {
        item {
            EonHeader(
                title = "EON",
                subtitle = when (state.rootState) {
                    RootState.CHECKING -> "正在检查 Root…"
                    RootState.READY -> "Root 已连接"
                    RootState.DENIED -> "Root 未授权，轻触重试"
                    RootState.UNAVAILABLE -> "设备未提供 Root"
                },
                statusColor = when (state.rootState) {
                    RootState.READY -> EonGreen
                    RootState.CHECKING -> EonSecondary
                    else -> EonRed
                },
                onSubtitleClick = if (state.rootState == RootState.READY) null else onRootRetry,
                action = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "设置", tint = EonSecondary)
                    }
                },
            )
        }
        item {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索 .so 文件") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "重新扫描") }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFEEEEF2),
                    unfocusedContainerColor = Color(0xFFEEEEF2),
                    disabledContainerColor = Color(0xFFEEEEF2),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = EonBlue,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showTip) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("默认扫描 ${ScanPathPolicy.DEFAULT_PATH}，可在设置中添加目录。", color = EonSecondary, fontSize = 13.sp)
                    TextButton(onClick = { onDismissTip(); onSettings() }, contentPadding = PaddingValues(0.dp)) { Text("管理路径", color = EonBlue) }
                }
                IconButton(onClick = onDismissTip) { Icon(Icons.Default.Close, "关闭提示", tint = EonSecondary) }
            }
        }
        if (state.scanErrors.isNotEmpty()) item {
            TextButton(onClick = onSettings) { Text("${state.scanErrors.size} 个路径无法访问 · 管理路径", color = EonRed, fontSize = 13.sp) }
        }
        if (!state.isScanning && visibleFiles.isEmpty()) {
            item {
                EmptyState(
                    title = when {
                        state.scanPaths.isEmpty() -> "尚未设置扫描路径"
                        state.query.isNotBlank() -> "没有匹配结果"
                        state.scanErrors.size == state.scanPaths.size -> "扫描路径暂时无法访问"
                        else -> "没有找到 .so 文件"
                    },
                    body = when {
                        state.scanPaths.isEmpty() -> "添加目录后即可扫描 .so 文件。"
                        state.query.isNotBlank() -> "尝试搜索其他名称或路径"
                        else -> "检查 Root 授权与扫描路径。"
                    },
                    action = if (state.scanPaths.isEmpty()) ({ TextButton(onClick = onSettings) { Text("设置扫描路径", color = EonBlue) } }) else null,
                )
            }
        }
        items(visibleFiles, key = { it.path }) { file ->
            SoFileRow(file, onClick = { onFileClick(file) })
        }
    }
}

@Composable
private fun SoFileRow(file: SoFile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(EonBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, null, tint = EonBlue, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(file.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = EonInk)
            Text(file.path, fontSize = 12.sp, color = EonSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (file.executable) EonGreen else EonSecondary))
                Text(if (file.executable) "可执行" else "不可执行", fontSize = 12.sp, color = if (file.executable) EonGreen else EonSecondary)
                file.lastExitCode?.let { Text("上次退出 $it", fontSize = 12.sp, color = EonSecondary) }
            }
        }
        Icon(Icons.Default.PlayArrow, "打开启动面板", tint = if (file.executable) EonBlue else EonSecondary)
    }
}

@Composable
fun KeysScreen(
    keys: List<KeySummary>,
    onReadKey: suspend (String) -> CharArray?,
    onSave: (String?, String, CharArray) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editor by remember { mutableStateOf<KeyEditor?>(null) }
    var deleting by remember { mutableStateOf<KeySummary?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = MainPageTopInset, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(MainPageSectionSpacing),
    ) {
        item {
            EonHeader(
                title = "密钥库",
                subtitle = "可用于任意 SO 文件",
                statusColor = EonSecondary,
                action = {
                    FilledIconButton(
                        onClick = { editor = KeyEditor() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EonBlue),
                    ) { Icon(Icons.Default.Add, "新增密钥", tint = Color.White) }
                },
            )
        }
        if (keys.isEmpty()) {
            item { EmptyState("还没有保存的密钥", "新增密钥后，可以在启动任意 .so 时复用。") }
        }
        items(keys, key = { it.id }) { key ->
            KeyVaultRow(
                key = key,
                onEdit = {
                    scope.launch {
                        val secret = onReadKey(key.id) ?: return@launch
                        try {
                            editor = KeyEditor(key.id, key.name, secret.concatToString())
                        } finally {
                            secret.fill('\u0000')
                        }
                    }
                },
                onDelete = { deleting = key },
            )
        }
    }
    editor?.let { value ->
        KeyEditorDialog(
            initial = value,
            onDismiss = { editor = null },
            onSave = { name, secret ->
                onSave(value.id, name, secret.toCharArray())
                editor = null
            },
        )
    }
    deleting?.let { key ->
        EonDialog(
            title = "删除密钥？",
            onDismiss = { deleting = null },
            confirmLabel = "删除",
            onConfirm = { onDelete(key.id); deleting = null },
            destructive = true,
        ) {
            Text("将删除“${key.name}”。", maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("此操作无法撤销。", color = EonSecondary, fontSize = 13.sp)
        }
    }
}

private data class KeyEditor(val id: String? = null, val name: String = "", val secret: String = "")

@Composable
private fun KeyEditorDialog(initial: KeyEditor, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var secret by remember(initial) { mutableStateOf(initial.secret) }
    var visible by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && secret.isNotEmpty()
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = EonBackground,
        unfocusedContainerColor = EonBackground,
        disabledContainerColor = EonBackground,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = EonBlue,
    )

    EonDialog(
        title = if (initial.id == null) "新增密钥" else "编辑密钥",
        onDismiss = onDismiss,
        confirmLabel = "保存",
        onConfirm = { onSave(name.trim(), secret) },
        confirmEnabled = canSave,
    ) {
        Spacer(Modifier.height(2.dp))
        TextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("密钥名称", color = EonSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = secret,
            onValueChange = { secret = it },
            placeholder = { Text("密钥", color = EonSecondary) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "隐藏密钥" else "显示密钥",
                        tint = EonSecondary,
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LogsScreen(run: RunState, onStop: () -> Unit, onClear: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = MainPageTopInset, end = 20.dp, bottom = 112.dp)) {
        EonHeader(
            title = "运行日志",
            subtitle = runSubtitle(run),
            statusColor = when (run.status) {
                ProcessStatus.RUNNING, ProcessStatus.STARTING -> EonGreen
                ProcessStatus.FAILED -> EonRed
                else -> EonSecondary
            },
            action = {
                if (run.status == ProcessStatus.RUNNING || run.status == ProcessStatus.STARTING) {
                    IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "停止进程", tint = EonRed) }
                } else if (run.status != ProcessStatus.STOPPING && run.lines.isNotEmpty()) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.widthIn(min = 48.dp).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("清空", maxLines = 1)
                    }
                }
            },
        )
        Spacer(Modifier.height(MainPageSectionSpacing))
        if (run.lines.isEmpty()) {
            EmptyState("尚未运行", "运行输出将在这里显示。")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        val text = run.lines.joinToString("\n") { it.text }
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("日志", text)))
                        }
                    },
                ) { Text("复制日志") }
            }
            LazyColumn(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFF17171C)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(run.lines) { line ->
                    SelectionContainer {
                        Text(
                            text = line.text,
                            color = when (line.stream) {
                                LogLine.Stream.SYSTEM -> Color(0xFF7EB7FF)
                                LogLine.Stream.STDERR -> Color(0xFFFF8A80)
                                LogLine.Stream.STDOUT -> Color(0xFFE5E5EA)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    scanPaths: List<String>,
    scanErrors: Map<String, String>,
    onBrowseDirectory: suspend (String) -> DirectoryListing,
    onAddScanPath: suspend (String) -> Boolean,
    onRemoveScanPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectingDirectory by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("设置", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        item {
            ScanPathsCard(scanPaths, scanErrors, onAdd = { selectingDirectory = true }, onRemove = onRemoveScanPath)
        }
        item {
            SettingsCard("关于") {
                Text("EON ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text("EON 需要设备提供 su Root 环境。普通共享库无法直接启动，目标必须是可执行 ELF。应用不会自动 chmod、移动或修改目标文件。", color = EonSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
    if (selectingDirectory) DirectoryPicker(
        initialPath = scanPaths.firstOrNull() ?: "/",
        existingPaths = scanPaths,
        onBrowse = onBrowseDirectory,
        onAdd = onAddScanPath,
        onDismiss = { selectingDirectory = false },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = EonBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchSheet(
    file: SoFile,
    keys: List<KeySummary>,
    lastKeyId: String?,
    message: String?,
    glassState: EonGlassState,
    onReadKey: suspend (String) -> CharArray?,
    onDismiss: () -> Unit,
    onLaunch: (CharArray, String?, String, String) -> Boolean,
) {
    val controlShape = RoundedCornerShape(16.dp)
    val initialKey = remember(file.path, lastKeyId, keys) { keys.firstOrNull { it.id == lastKeyId } }
    var keyMode by remember(file.path) {
        mutableStateOf(if (initialKey != null) LaunchKeyMode.SAVED else LaunchKeyMode.MANUAL)
    }
    var selectedKeyId by remember(file.path) {
        mutableStateOf(initialKey?.id ?: keys.firstOrNull()?.id)
    }
    var keyMenu by remember { mutableStateOf(false) }
    var manualSecret by remember { mutableStateOf("") }
    var readingKey by remember { mutableStateOf(false) }
    var secretVisible by remember { mutableStateOf(false) }
    var arguments by remember { mutableStateOf("") }
    var workDirectory by remember(file) { mutableStateOf(file.directory) }
    val selectedKey = keys.firstOrNull { it.id == selectedKeyId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(keys) {
        if (keys.none { it.id == selectedKeyId }) {
            selectedKeyId = keys.firstOrNull()?.id
            keyMenu = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.22f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        EonDialogSystemBars()
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .eonGlass(
                    glassState,
                    RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                    blurRadius = 48.dp,
                    strong = true,
                    surfaceAlpha = 0.72f,
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(42.dp, 5.dp).clip(CircleShape).background(EonSeparator).align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启动 ${file.name}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(file.path, color = EonSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
            }
            LaunchKeyModeSelector(
                mode = keyMode,
                onModeChange = { mode ->
                    keyMode = mode
                    keyMenu = false
                    secretVisible = false
                    if (mode == LaunchKeyMode.SAVED && selectedKeyId == null) {
                        selectedKeyId = keys.firstOrNull()?.id
                    }
                },
            )
            if (keyMode == LaunchKeyMode.SAVED) ExposedDropdownMenuBox(
                expanded = keyMenu,
                onExpandedChange = { if (keys.isNotEmpty()) keyMenu = !keyMenu },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedKey?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("已保存密钥") },
                    placeholder = { Text("暂无已保存密钥") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyMenu) },
                    shape = controlShape,
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = keyMenu,
                    onDismissRequest = { keyMenu = false },
                    shape = controlShape,
                    containerColor = Color.White,
                ) {
                    keys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key.name) },
                            onClick = { selectedKeyId = key.id; keyMenu = false },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = manualSecret,
                    onValueChange = { manualSecret = it },
                    label = { Text("运行密钥") },
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { secretVisible = !secretVisible }) { Icon(if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                    shape = controlShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(arguments, { arguments = it }, label = { Text("启动参数（可选）") }, singleLine = true, shape = controlShape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(workDirectory, { workDirectory = it }, label = { Text("工作目录") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Folder, null) }, shape = controlShape, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (keyMode == LaunchKeyMode.MANUAL) {
                        if (onLaunch(manualSecret.toCharArray(), null, arguments, workDirectory)) manualSecret = ""
                    } else {
                        val id = selectedKey?.id ?: return@Button
                        val args = arguments
                        val directory = workDirectory
                        readingKey = true
                        scope.launch {
                            try {
                                val chars = onReadKey(id) ?: return@launch
                                try {
                                    onLaunch(chars.copyOf(), id, args, directory)
                                } finally {
                                    chars.fill('\u0000')
                                }
                            } finally {
                                readingKey = false
                            }
                        }
                    }
                },
                enabled = !readingKey && file.executable && workDirectory.startsWith('/') &&
                    (if (keyMode == LaunchKeyMode.MANUAL) manualSecret.isNotEmpty() else selectedKey != null),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EonBlue),
            ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text("启动", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            if (message != null) Text(message, color = EonRed, fontSize = 13.sp)
            if (!file.executable) Text("目标文件不可执行，EON 不会自动修改权限。", color = EonRed, fontSize = 13.sp)
        }
    }
}

private enum class LaunchKeyMode { MANUAL, SAVED }

@Composable
private fun LaunchKeyModeSelector(
    mode: LaunchKeyMode,
    onModeChange: (LaunchKeyMode) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.48f))
            .border(1.dp, EonSeparator.copy(alpha = 0.8f), shape),
    ) {
        LaunchKeyMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) EonBlue else Color.Transparent)
                    .clickable { onModeChange(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (item == LaunchKeyMode.MANUAL) "手动输入" else "已保存密钥",
                    color = if (selected) Color.White else EonSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EonHeader(
    title: String,
    subtitle: String,
    statusColor: Color,
    onSubtitleClick: (() -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    // Chinese and Latin fallback fonts report different line metrics. Give every
    // title the same scalable slot so all three subtitle rows share one baseline.
    val titleHeight = with(LocalDensity.current) { 40.sp.toDp() }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = MainHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().height(titleHeight),
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Text(
                    subtitle,
                    color = statusColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.widthIn(min = 48.dp).height(48.dp), contentAlignment = Alignment.Center) { action() }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = EonSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        action?.invoke()
    }
}

private fun runSubtitle(run: RunState): String = when (run.status) {
    ProcessStatus.IDLE -> "等待启动"
    ProcessStatus.STARTING -> "正在启动 ${run.fileName.orEmpty()}"
    ProcessStatus.RUNNING -> "${run.fileName.orEmpty()} 正在运行"
    ProcessStatus.STOPPING -> "正在停止"
    ProcessStatus.FINISHED -> "已结束 · 退出码 ${run.exitCode ?: "-"}"
    ProcessStatus.FAILED -> "启动失败"
}

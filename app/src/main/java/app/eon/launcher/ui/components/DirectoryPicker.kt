package app.eon.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eon.launcher.data.ScanPathPolicy
import app.eon.launcher.model.DirectoryListing
import app.eon.launcher.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
fun DirectoryPicker(
    initialPath: String,
    existingPaths: List<String>,
    onBrowse: suspend (String) -> DirectoryListing,
    onAdd: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var requestedPath by remember { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentPath = listing?.path ?: requestedPath
    val currentListing = listing

    LaunchedEffect(requestedPath, reload) {
        loading = true
        error = null
        listing = null
        try {
            listing = onBrowse(requestedPath)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "目录读取失败"
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    fun back() {
        if (currentPath == "/") onDismiss() else requestedPath = ScanPathPolicy.parent(currentPath)
    }

    Dialog(
        onDismissRequest = { if (!adding) back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        EonDialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = EonBackground) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !adding, contentPadding = PaddingValues(0.dp), modifier = Modifier.widthIn(min = 52.dp)) { Text("取消", color = EonBlue, fontSize = 17.sp) }
                    Text("选择扫描目录", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.width(52.dp))
                }
                DirectoryBreadcrumbs(currentPath, enabled = !adding) { requestedPath = it }
                if (currentPath != "/") {
                    TextButton(onClick = { requestedPath = ScanPathPolicy.parent(currentPath) }, enabled = !adding) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = EonBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("上级目录", color = EonBlue)
                    }
                } else Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = EonBlue)
                        currentListing == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "无法访问此目录", Modifier.padding(horizontal = 16.dp), color = EonSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = { reload++ }) { Text("重试", color = EonBlue) }
                        }
                        else -> LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.White)) {
                            if (currentListing.children.isEmpty()) item {
                                Text("没有子文件夹", Modifier.fillMaxWidth().padding(24.dp), color = EonSecondary)
                            }
                            items(currentListing.children, key = { it }) { child ->
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !adding) { requestedPath = child }.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Default.Folder, null, tint = EonBlue, modifier = Modifier.size(28.dp))
                                    Text(child.substringAfterLast('/'), Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = EonSecondary)
                                }
                                HorizontalDivider(Modifier.padding(start = 56.dp), color = EonSeparator)
                            }
                        }
                    }
                }
                if (error != null && listing != null) Text(error!!, Modifier.padding(top = 12.dp), color = EonRed, fontSize = 13.sp)
                val alreadyAdded = currentPath in existingPaths
                Button(
                    enabled = !loading && !adding && listing != null && !alreadyAdded,
                    onClick = {
                        val path = listing?.path ?: return@Button
                        adding = true
                        scope.launch {
                            try {
                                if (onAdd(path)) onDismiss() else error = "此目录已添加"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "添加目录失败"
                            } finally {
                                adding = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EonBlue),
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (adding) "正在添加…" else if (alreadyAdded) "已添加此目录" else "添加此目录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun DirectoryBreadcrumbs(path: String, enabled: Boolean, onNavigate: (String) -> Unit) {
    val parts = remember(path) { path.split('/').filter { it.isNotEmpty() } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onNavigate("/") }, enabled = enabled) { Text("根目录", color = EonBlue) }
        parts.forEachIndexed { index, part ->
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = EonSecondary, modifier = Modifier.size(16.dp))
            TextButton(onClick = { onNavigate("/" + parts.take(index + 1).joinToString("/")) }, enabled = enabled) { Text(part, color = EonBlue, maxLines = 1) }
        }
    }
}

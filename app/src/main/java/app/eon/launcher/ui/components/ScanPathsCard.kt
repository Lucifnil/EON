package app.eon.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import app.eon.launcher.ui.theme.*

@Composable
fun ScanPathsCard(paths: List<String>, errors: Map<String, String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)) {
        Text("扫描路径", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), color = EonBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (paths.isEmpty()) {
            Text("尚未设置扫描路径", Modifier.padding(16.dp), color = EonSecondary, fontSize = 15.sp)
        }
        paths.forEach { path ->
            Row(
                Modifier.fillMaxWidth().clickable { expanded = if (expanded == path) null else path }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = EonBlue)
                Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    Text(path, fontSize = 15.sp, maxLines = if (expanded == path) Int.MAX_VALUE else 1, overflow = TextOverflow.MiddleEllipsis)
                    if (path in errors) Text("无法访问", color = EonRed, fontSize = 12.sp)
                }
                IconButton(onClick = { onRemove(path) }) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "移除 $path", tint = EonRed)
                }
            }
            HorizontalDivider(Modifier.padding(start = 52.dp), color = EonSeparator)
        }
        TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = EonBlue)
            Spacer(Modifier.size(8.dp))
            Text("添加扫描路径", color = EonBlue, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
        }
        Text("仅扫描所选目录，不包含子目录。", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = EonSecondary, fontSize = 12.sp)
    }
}

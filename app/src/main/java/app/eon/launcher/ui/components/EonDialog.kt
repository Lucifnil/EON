package app.eon.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eon.launcher.ui.theme.EonBlue
import app.eon.launcher.ui.theme.EonRed
import app.eon.launcher.ui.theme.EonSecondary
import app.eon.launcher.ui.theme.EonSeparator

@Composable
fun EonDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 340.dp).fillMaxWidth().imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 18.dp,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    content()
                }
                HorizontalDivider(color = EonSeparator)
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("取消", color = EonBlue, fontSize = 17.sp, maxLines = 1)
                    }
                    VerticalDivider(color = EonSeparator)
                    TextButton(
                        enabled = confirmEnabled,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(
                            confirmLabel,
                            color = when {
                                !confirmEnabled -> EonSecondary.copy(alpha = 0.45f)
                                destructive -> EonRed
                                else -> EonBlue
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

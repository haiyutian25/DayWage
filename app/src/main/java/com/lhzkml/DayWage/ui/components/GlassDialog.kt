package com.lhzkml.DayWage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhzkml.DayWage.ui.theme.LocalAppColors

/**
 * 玻璃弹窗：遮罩 + 近乎不透明的玻璃卡片。
 * 弹窗内部把 [LocalInOverlay] 设为 true，卡片不再折射背景照片。
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalInOverlay provides true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    // 点遮罩关闭；卡片自己吃掉点击
                    .pressable({ onDismiss() }, pressedScale = 1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .pressable({}, pressedScale = 1f),
                ) {
                    Glass(
                        strong = true,
                        shape = RoundedCornerShape(28.dp),
                        tint = if (c.isDark) Color(0xFF1C1C24) else Color.White,
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Spacer(Modifier.height(16.dp))
                            content()
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SecondaryButton("取消", onDismiss, Modifier.weight(1f))
                                PrimaryButton(
                                    confirmText,
                                    onConfirm,
                                    Modifier.weight(1f),
                                    enabled = confirmEnabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.AppSettings
import com.lhzkml.DayWage.data.SettingsRepository
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.DockClearance
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.IconTile
import com.lhzkml.DayWage.ui.components.ListRow
import com.lhzkml.DayWage.ui.components.RowDivider
import com.lhzkml.DayWage.ui.components.SectionHeader
import com.lhzkml.DayWage.ui.components.appear
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.ui.theme.accentChoices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MineViewModel(
    private val repo: WorkRepository,
    private val settings: SettingsRepository,
    private val reset: suspend () -> Unit,
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun set(kind: String, value: Boolean) {
        viewModelScope.launch {
            when (kind) {
                "glass" -> settings.setGlass(value)
                "pureBlack" -> settings.setPureBlack(value)
                "dark" -> {
                    settings.setFollowSystem(false)
                    settings.setDark(value)
                }
                "night" -> settings.setNightShift(value)
                "autoPay" -> settings.setAutoDayPay(value)
                "weekMonday" -> settings.setWeekMonday(value)
                "byHour" -> settings.setRecordByHour(value)
            }
        }
    }

    fun setAccent(index: Int) {
        viewModelScope.launch { settings.setAccent(index) }
    }

    fun clearRecords(onDone: () -> Unit) {
        viewModelScope.launch {
            reset()
            onDone()
        }
    }
}

@Composable
fun MineScreen(
    onOpenSalary: () -> Unit,
    onToast: (String) -> Unit,
    vm: MineViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val s by vm.appSettings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Text("我的", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth().appear(0), strong = true) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Lucide.Briefcase, c.accent, size = 52.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("工时记录", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("专业 · 全面 · 简洁 · 高效", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }

        SectionHeader("薪资与记录")
        Glass(Modifier.fillMaxWidth()) {
            Column {
                ListRow(
                    title = "薪资设置",
                    subtitle = "计薪方式、时薪、底薪与项目",
                    leading = { IconTile(Lucide.Coins, c.accent, size = 38.dp) },
                    trailing = { Icon(Lucide.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                    onClick = onOpenSalary,
                )
                RowDivider(inset = 68.dp)
                ToggleRow("时长记录", "关闭后改为选择起止时间", s.recordByHour) { vm.set("byHour", it) }
                RowDivider(inset = 68.dp)
                ToggleRow("夜班模式", "默认选中昨天", s.nightShift) { vm.set("night", it) }
                RowDivider(inset = 68.dp)
                ToggleRow("日补扣自动记录", "记录工时后自动带上默认日补扣", s.autoDayPay) { vm.set("autoPay", it) }
                RowDivider(inset = 68.dp)
                ToggleRow("每周从周一开始", "影响日历表头与统计分组", s.weekStartMonday) { vm.set("weekMonday", it) }
            }
        }

        SectionHeader("外观")
        Glass(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("深色模式", if (s.followSystemTheme) "跟随系统" else "", s.darkTheme) { vm.set("dark", it) }
                RowDivider(inset = 68.dp)
                ToggleRow("玻璃效果", "关闭后卡片变成磨砂实色", s.glassEffects) { vm.set("glass", it) }
                RowDivider(inset = 68.dp)
                ToggleRow("纯黑背景", "OLED 省电，仅深色模式生效", s.pureBlack) { vm.set("pureBlack", it) }
                RowDivider(inset = 68.dp)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("强调色", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        accentChoices.forEachIndexed { i, (_, color) ->
                            val selected = s.accentIndex == i
                            Box(
                                Modifier
                                    .size(if (selected) 26.dp else 22.dp)
                                    .background(color, CircleShape)
                                    .then(if (selected) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier)
                                    .pressable({ vm.setAccent(i) }),
                            )
                        }
                    }
                }
            }
        }

        SectionHeader("数据")
        Glass(Modifier.fillMaxWidth()) {
            Column {
                ListRow(
                    title = "清空记录",
                    subtitle = "删除全部打卡与目标，项目和薪资规则保留",
                    leading = { IconTile(Lucide.Trash2, c.negative, size = 38.dp) },
                    onClick = { vm.clearRecords { onToast("已清空记录") } },
                )
                RowDivider(inset = 68.dp)
                ListRow(
                    title = "关于工时记录",
                    subtitle = "本地数据库 · 无需联网",
                    leading = { IconTile(Lucide.Info, c.textSecondary, size = 38.dp) },
                )
            }
        }

        Spacer(Modifier.height(DockClearance))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    ListRow(
        title = title,
        subtitle = subtitle.ifBlank { null },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                ),
            )
        },
    )
}

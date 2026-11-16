package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.AppSettings
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.RecordKind
import com.lhzkml.DayWage.data.SettingsRepository
import com.lhzkml.DayWage.data.WorkRecord
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.data.WorkType
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.Chip
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.GlassScreen
import com.lhzkml.DayWage.ui.components.MonthSwitcher
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.SecondaryButton
import com.lhzkml.DayWage.ui.components.SectionHeader
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.leadingBlanks
import com.lhzkml.DayWage.util.weekLabelsMonday
import com.lhzkml.DayWage.util.weekLabelsSunday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class BatchViewModel(
    private val repo: WorkRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    val snapshot: StateFlow<MonthSnapshot> = _month
        .flatMapLatest { repo.snapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSnapshot())

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var type by mutableStateOf(WorkType.ZB)
    var minutes by mutableStateOf(480)

    fun shiftMonth(delta: Long) = setMonth(_month.value.plusMonths(delta))

    fun setMonth(m: YearMonth) {
        _month.value = m
        selected = emptySet()
    }

    fun toggle(day: Long) {
        selected = if (day in selected) selected - day else selected + day
    }

    fun clear() { selected = emptySet() }

    fun selectWeekdays() {
        val m = _month.value
        selected = (1..m.lengthOfMonth())
            .map { m.atDay(it) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .map { it.toEpochDay() }
            .toSet()
    }

    fun selectWeekend() {
        val m = _month.value
        selected = (1..m.lengthOfMonth())
            .map { m.atDay(it) }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            .map { it.toEpochDay() }
            .toSet()
    }

    /** 把选中的每一天都写成同一种工时；已有记录会合并，不清掉其它类型。 */
    fun apply(defaults: AppSettings, onDone: (String) -> Unit) {
        if (selected.isEmpty()) return
        val snap = snapshot.value
        viewModelScope.launch {
            selected.forEach { day ->
                val existing = snap.records.firstOrNull { it.epochDay == day }
                val base = existing ?: WorkRecord(
                    epochDay = day,
                    projectId = defaults.currentProjectId,
                    shiftId = "1",
                    kind = RecordKind.WORK,
                )
                val updated = base.copy(
                    kind = RecordKind.WORK,
                    zbMinutes = if (type == WorkType.ZB) minutes else base.zbMinutes,
                    jbMinutes = if (type == WorkType.JB) minutes else base.jbMinutes,
                    zmMinutes = if (type == WorkType.ZM) minutes else base.zmMinutes,
                    jjMinutes = if (type == WorkType.JJ) minutes else base.jjMinutes,
                )
                repo.saveRecord(updated)
            }
            onDone("已写入 ${selected.size} 天")
        }
    }

    fun deleteSelected(onDone: (String) -> Unit) {
        if (selected.isEmpty()) return
        val days = selected
        viewModelScope.launch {
            days.forEach { repo.deleteRecord(it) }
            selected = emptySet()
            onDone("已删除 ${days.size} 天的记录")
        }
    }
}

@Composable
fun BatchScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    vm: BatchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val month by vm.month.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.appSettings.collectAsStateWithLifecycle()

    GlassScreen(
        title = "批量记录",
        onBack = onBack,
        subtitle = if (vm.selected.isEmpty()) "先选日期，再选工时" else "已选 ${vm.selected.size} 天",
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("删除选中", { vm.deleteSelected(onToast) }, Modifier.weight(1f))
                PrimaryButton(
                    "写入 ${vm.selected.size} 天",
                    { vm.apply(settings, onToast) },
                    Modifier.weight(1.3f),
                    icon = Lucide.Check,
                    enabled = vm.selected.isNotEmpty(),
                )
            }
        },
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            MonthSwitcher(month, { vm.setMonth(it) })

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("工作日", vm::selectWeekdays, icon = Lucide.Briefcase)
                Chip("周末", vm::selectWeekend, icon = Lucide.Calendar)
                Chip("清空", vm::clear, icon = Lucide.X)
            }

            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val labels = if (settings.weekStartMonday) weekLabelsMonday else weekLabelsSunday
                    Row(Modifier.fillMaxWidth()) {
                        labels.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textTertiary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BatchCalendar(
                        month = month,
                        selected = vm.selected,
                        marked = snap.records.map { it.epochDay }.toSet(),
                        weekStartMonday = settings.weekStartMonday,
                        onToggle = vm::toggle,
                    )
                }
            }

            SectionHeader("写入的工时类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Chip("正班", { vm.type = WorkType.ZB }, selected = vm.type == WorkType.ZB)
                Chip("加班", { vm.type = WorkType.JB }, selected = vm.type == WorkType.JB)
                Chip("周末", { vm.type = WorkType.ZM }, selected = vm.type == WorkType.ZM)
                Chip("节假", { vm.type = WorkType.JJ }, selected = vm.type == WorkType.JJ)
            }

            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每天时长", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text("写入时会覆盖该类型原来的时长", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    StepButton(Lucide.Minus) { vm.minutes = (vm.minutes - 30).coerceAtLeast(0) }
                    Text(
                        com.lhzkml.DayWage.util.formatHoursUnit(vm.minutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp),
                    )
                    StepButton(Lucide.Plus) { vm.minutes = (vm.minutes + 30).coerceAtMost(24 * 60) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "提示：已经在这些日期上打过卡的其它工时类型会保留，只替换当前选中的类型。",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BatchCalendar(
    month: YearMonth,
    selected: Set<Long>,
    marked: Set<Long>,
    weekStartMonday: Boolean,
    onToggle: (Long) -> Unit,
) {
    val c = LocalAppColors.current
    val today = LocalDate.now()
    val blanks = month.leadingBlanks(weekStartMonday)
    val cells: List<Int?> = List(blanks) { null } + (1..month.lengthOfMonth()).toList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day == null) return@Box
                        val date = month.atDay(day)
                        val epoch = date.toEpochDay()
                        val isSelected = epoch in selected
                        val isToday = date == today
                        Box(
                            Modifier
                                .matchParentSize()
                                .pressable({ onToggle(epoch) }, pressedScale = 0.9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) c.accent else Color.Transparent)
                                .then(
                                    if (isToday && !isSelected) Modifier.border(1.5.dp, c.textPrimary.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                    else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) c.onAccent else c.textPrimary.copy(alpha = 0.72f),
                                )
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                epoch !in marked -> Color.Transparent
                                                isSelected -> c.onAccent
                                                else -> c.accent
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .size(36.dp)
            .pressable(onClick, pressedScale = 0.88f)
            .clip(CircleShape)
            .background(c.glassStrong)
            .border(1.dp, c.borderTop.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(16.dp))
    }
}

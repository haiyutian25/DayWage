package com.lhzkml.DayWage.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.DockClearance
import com.lhzkml.DayWage.ui.components.EmptyState
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.RoundButton
import com.lhzkml.DayWage.ui.components.Skeleton
import com.lhzkml.DayWage.ui.components.appear
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.Money
import com.lhzkml.DayWage.util.formatHours
import com.lhzkml.DayWage.util.leadingBlanks
import com.lhzkml.DayWage.util.weekLabelsMonday
import com.lhzkml.DayWage.util.weekLabelsSunday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModel(
    private val repo: WorkRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selected = MutableStateFlow(LocalDate.now().toEpochDay())
    val selected: StateFlow<Long> = _selected

    /** null = 还在读库，界面显示骨架屏而不是 0 */
    val snapshot: StateFlow<MonthSnapshot?> = _month
        .flatMapLatest { repo.snapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun shiftMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
        // 换月时把选中日期挪到该月今天或 1 号，避免停在别的月份
        val m = _month.value
        val today = LocalDate.now()
        _selected.value = if (YearMonth.from(today) == m) today.toEpochDay() else m.atDay(1).toEpochDay()
    }

    fun select(day: Long) { _selected.value = day }
}

@Composable
fun RecordScreen(
    onOpenEntry: (Long) -> Unit,
    onOpenBatch: () -> Unit,
    vm: RecordViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val month by vm.month.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val money = Money("CNY")

    val summary = snap?.summary(month)
    val record = snap?.records?.firstOrNull { it.epochDay == selected }
    val dayMoney = snap?.dayMoney(record)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        // 标题 + 月份切换
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("工时记录", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                Text(
                    if (record != null) "今天也要好好记一笔" else "记录每一天，工资一目了然",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Lucide.ChevronLeft, "上个月", { vm.shiftMonth(-1) }, size = 38.dp)
            AnimatedContent(
                targetState = month,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "month",
            ) { m ->
                Text(
                    "${m.year}年${m.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RoundButton(
                Lucide.ChevronRight, "下个月", { vm.shiftMonth(1) }, size = 38.dp,
                enabled = month < YearMonth.now(),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 还在读库：先铺骨架，别让用户看到一屏 0
        if (snap == null || summary == null || dayMoney == null) {
            RecordSkeleton()
            Spacer(Modifier.height(DockClearance))
            return@Column
        }

        // 三枚指标
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("出勤", "${summary.workDays}", "天", Modifier.weight(1f))
            MiniStat("总工时", formatHours(summary.workMinutes), "H", Modifier.weight(1f))
            MiniStat("收入", money.formatWhole(summary.totalMoneyMinor), "", Modifier.weight(1f), accent = true)
        }

        Spacer(Modifier.height(14.dp))

        // 日历卡
        Glass(Modifier.fillMaxWidth().appear(0)) {
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
                MonthGrid(
                    month = month,
                    marked = snap.records.associateBy { it.epochDay },
                    selected = selected,
                    weekStartMonday = settings.weekStartMonday,
                    onSelect = { day -> vm.select(day) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 选中日卡片
        SelectedDayCard(
            day = selected,
            record = record,
            hours = if (record?.kind == RecordKind.REST) record.leaveMinutes else (record?.totalMinutes ?: 0),
            money = money.format(dayMoney.totalMinor),
            onOpen = { onOpenEntry(selected) },
        )

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本月记录", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "批量",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier.pressable(onOpenBatch),
            )
        }
        Spacer(Modifier.height(8.dp))

        val recent = snap.records.sortedByDescending { it.epochDay }.take(6)
        if (recent.isEmpty()) {
            EmptyState(Lucide.Calendar, "还没有记录", "点下面的打卡按钮，记下今天的工作时长")
        } else {
            recent.forEachIndexed { i, r ->
                RecordRow(record = r, money = money.format(snap.dayMoney(r).totalMinor), onClick = { onOpenEntry(r.epochDay) })
                if (i != recent.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(DockClearance))
    }
}

/**
 * 首帧骨架：三枚指标 + 月历 + 当日卡片。
 * 形状与真实内容一致，数据到了直接替换，不会出现「0 → 540」的跳变。
 */
@Composable
private fun RecordSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            Glass(Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Skeleton(Modifier.width(34.dp).height(10.dp), phase = i * 0.12f)
                    Spacer(Modifier.height(10.dp))
                    Skeleton(Modifier.width(52.dp).height(20.dp), phase = i * 0.12f)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Skeleton(Modifier.width(12.dp).height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            repeat(5) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) {
                        Skeleton(
                            Modifier.weight(1f).aspectRatio(1f),
                            shape = RoundedCornerShape(12.dp),
                            phase = row * 0.06f,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Glass(Modifier.fillMaxWidth(), strong = true) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Skeleton(Modifier.width(132.dp).height(16.dp))
                Spacer(Modifier.height(8.dp))
                Skeleton(Modifier.width(180.dp).height(12.dp), phase = 0.2f)
            }
            Skeleton(Modifier.width(96.dp).height(44.dp), CircleShape)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = LocalAppColors.current
    Glass(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (accent) c.accent else c.textPrimary,
                    maxLines = 1,
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

/** 月历。有记录的日子带一个小圆点，选中的日子用强调色填充。 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    marked: Map<Long, WorkRecord>,
    selected: Long,
    weekStartMonday: Boolean,
    onSelect: (Long) -> Unit,
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
                        val isSelected = epoch == selected
                        val isToday = date == today
                        val rec = marked[epoch]
                        Box(
                            Modifier
                                .matchParentSize()
                                .pressable({ onSelect(epoch) }, pressedScale = 0.9f)
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
                                    color = when {
                                        isSelected -> c.onAccent
                                        isToday -> c.textPrimary
                                        else -> c.textPrimary.copy(alpha = 0.72f)
                                    },
                                )
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                rec == null -> Color.Transparent
                                                isSelected -> c.onAccent
                                                rec.kind == RecordKind.REST -> c.warning
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
private fun SelectedDayCard(day: Long, record: WorkRecord?, hours: Int, money: String, onOpen: () -> Unit) {
    val c = LocalAppColors.current
    val date = LocalDate.ofEpochDay(day)
    Glass(Modifier.fillMaxWidth(), strong = true) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("M月d日")) + " · " + date.format(DateTimeFormatter.ofPattern("EEEE")),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        record == null -> "还没有记录"
                        record.kind == RecordKind.REST -> "休假 ${formatHours(hours)}H"
                        record.kind == RecordKind.DAYPAY -> "日结 ${money}元"
                        else -> "${formatHours(record.zbMinutes)}H 正班 · ${formatHours(record.jbMinutes)}H 加班"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                if (record?.note?.isNotBlank() == true) {
                    Spacer(Modifier.height(2.dp))
                    Text(record.note, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money, style = MaterialTheme.typography.titleLarge, color = c.accent)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = if (record == null) "打卡" else "修改",
                    onClick = onOpen,
                    modifier = Modifier.width(96.dp).height(44.dp),
                    icon = Lucide.Plus,
                )
            }
        }
    }
}

@Composable
private fun RecordRow(record: WorkRecord, money: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val date = LocalDate.ofEpochDay(record.epochDay)
    Glass(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("M月d日")),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                )
                Text(
                    when (record.kind) {
                        RecordKind.REST -> "休假 ${formatHours(record.leaveMinutes)}H"
                        RecordKind.DAYPAY -> "日结"
                        else -> buildString {
                            if (record.zbMinutes > 0) append("正班 ${formatHours(record.zbMinutes)}H ")
                            if (record.jbMinutes > 0) append("加班 ${formatHours(record.jbMinutes)}H ")
                            if (record.zmMinutes > 0) append("周末 ${formatHours(record.zmMinutes)}H ")
                            if (record.jjMinutes > 0) append("节假 ${formatHours(record.jjMinutes)}H")
                        }.ifBlank { "无工时" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Text(money, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

package com.lhzkml.DayWage.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.MonthSummary
import com.lhzkml.DayWage.data.PayKind
import com.lhzkml.DayWage.data.Shift
import com.lhzkml.DayWage.data.ShiftStat
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.ChartSlice
import com.lhzkml.DayWage.ui.components.DockClearance
import com.lhzkml.DayWage.ui.components.DonutChart
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.IconTile
import com.lhzkml.DayWage.ui.components.RoundButton
import com.lhzkml.DayWage.ui.components.RowDivider
import com.lhzkml.DayWage.ui.components.Skeleton
import com.lhzkml.DayWage.ui.components.appear
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.Money
import com.lhzkml.DayWage.util.formatHours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repo: WorkRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    val snapshot: StateFlow<MonthSnapshot?> = _month
        .flatMapLatest { repo.snapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shifts: StateFlow<List<Shift>> = repo.shifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shift(delta: Long) { _month.value = _month.value.plusMonths(delta) }
    fun setMonth(m: YearMonth) { _month.value = m }
}

@Composable
fun StatsScreen(
    onOpenEntry: (Long) -> Unit,
    onOpenReport: () -> Unit,
    onOpenYear: (Int) -> Unit,
    vm: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val month by vm.month.collectAsStateWithLifecycle()
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val money = Money("CNY")
    val summary = snap?.summary(month)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        // 第一行：年份（点击进年度统计） / 报表
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("${month.year}年", Lucide.Calendar, { onOpenYear(month.year) })
            Spacer(Modifier.weight(1f))
            RoundButton(Lucide.ReceiptText, "报表", onOpenReport, size = 38.dp)
        }

        Spacer(Modifier.height(10.dp))

        // 第二行：考勤区间翻页
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Lucide.ChevronLeft, "上个月", { vm.shift(-1) }, size = 32.dp)
            Text(
                intervalLabel(month),
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RoundButton(
                Lucide.ChevronRight, "下个月", { vm.shift(1) }, size = 32.dp,
                enabled = month < YearMonth.now(),
            )
        }

        Spacer(Modifier.height(14.dp))

        if (summary == null || snap == null) {
            StatsSkeleton()
            Spacer(Modifier.height(DockClearance))
            return@Column
        }

        // 金额头
        Text(
            money.format(summary.totalMoneyMinor),
            style = MaterialTheme.typography.displaySmall,
            color = c.accent,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueChip("正班", money.format(summary.regularMoneyMinor), c.accent)
            ValueChip("加班", money.format(summary.overtimeMoneyMinor), c.warning)
        }

        Spacer(Modifier.height(14.dp))

        // 三页卡片
        StatsPager(month, snap, summary, shifts, money)

        Spacer(Modifier.height(14.dp))

        // 补贴 / 扣款
        PayCard(
            title = "补贴项目",
            totalText = "+" + money.format(summary.bonusMinor),
            color = c.positive,
            icon = Lucide.Plus,
            items = payItems(snap, bonus = true),
        )
        Spacer(Modifier.height(10.dp))
        PayCard(
            title = "扣款项目",
            totalText = "−" + money.format(summary.deductMinor),
            color = c.negative,
            icon = Lucide.Minus,
            items = payItems(snap, bonus = false),
        )

        Spacer(Modifier.height(DockClearance))
    }
}

/* ---------------- 三页卡片 ---------------- */

@Composable
private fun StatsPager(
    month: YearMonth,
    snap: MonthSnapshot,
    summary: MonthSummary,
    shifts: List<Shift>,
    money: Money,
) {
    val c = LocalAppColors.current
    var page by remember { mutableStateOf(0) }
    val names = remember(shifts) { shifts.associate { it.id to it.name } }
    val shiftStats = remember(snap, names) { snap.shiftBreakdown(month, names) }
    val drag = Modifier.pointerInput(Unit) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragEnd = {
                if (dx < -60f) page = (page + 1).coerceAtMost(2)
                if (dx > 60f) page = (page - 1).coerceAtLeast(0)
            },
        ) { _, amount -> dx += amount }
    }

    Glass(Modifier.fillMaxWidth().appear(0).then(drag)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it else -it } + fadeIn())
                        .togetherWith(slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "statsPage",
            ) { p ->
                when (p) {
                    0 -> DetailTable(summary, money)
                    1 -> DonutPage(summary, money)
                    else -> ShiftPage(shiftStats, money)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == page) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == page) c.accent else c.textTertiary.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

private data class TypeRow(val name: String, val days: Int, val minutes: Int, val moneyMinor: Long, val color: Color)

/** 第 1 页：正班/加班/周末/节假 + 总计 */
@Composable
private fun DetailTable(summary: MonthSummary, money: Money) {
    val c = LocalAppColors.current
    val rows = listOf(
        TypeRow("正班", summary.zbDays, summary.zbMinutes, summary.zbMoneyMinor, c.accent),
        TypeRow("加班", summary.jbDays, summary.jbMinutes, summary.jbMoneyMinor, c.positive),
        TypeRow("周末", summary.zmDays, summary.zmMinutes, summary.zmMoneyMinor, c.warning),
        TypeRow("节假", summary.jjDays, summary.jjMinutes, summary.jjMoneyMinor, c.negative),
    )
    Column {
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(r.color))
                Spacer(Modifier.width(10.dp))
                Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.width(48.dp))
                Text(
                    "${r.days} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.width(46.dp),
                )
                Text(
                    "${formatHours(r.minutes)} H",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.minutes > 0) r.color else c.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    money.format(r.moneyMinor),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textPrimary,
                )
            }
        }
        RowDivider(inset = 12.dp)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(13.dp))
            Text("总计", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.width(48.dp))
            Text("${summary.recordDays} 天", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.width(46.dp))
            Text(
                "${formatHours(summary.workMinutes)} H",
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(money.format(summary.workMoneyMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
    }
}

/** 第 2 页：上班/休假/总加班/总工时 + 环形图 */
@Composable
private fun DonutPage(summary: MonthSummary, money: Money) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DonutFact("上班", "${summary.workDays} 天", c.textPrimary)
            DonutFact("休假", "${summary.restDays} 天", c.textPrimary)
            DonutFact("总加班", "${formatHours(summary.overtimeMinutes)} H", c.textPrimary)
            DonutFact("总工时", "${formatHours(summary.workMinutes)} H", c.textPrimary)
        }
        Spacer(Modifier.width(10.dp))
        val slices = listOf(
            ChartSlice(summary.zbMinutes.toFloat(), c.accent) to "正班",
            ChartSlice(summary.jbMinutes.toFloat(), c.positive) to "加班",
            ChartSlice(summary.zmMinutes.toFloat(), c.warning) to "周末",
            ChartSlice(summary.jjMinutes.toFloat(), c.negative) to "节假",
        ).filter { it.first.value > 0f }
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            DonutChart(slices.map { it.first }, Modifier.fillMaxSize(), thickness = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatHours(summary.workMinutes), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text("总工时", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

/** 第 3 页：按班次汇总 */
@Composable
private fun ShiftPage(stats: List<ShiftStat>, money: Money) {
    val c = LocalAppColors.current
    if (stats.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无班次数据", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    if (s.isLeave) Lucide.BookOpen else Lucide.Briefcase,
                    if (s.isLeave) c.positive else c.accent,
                    size = 38.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Text("${s.days} 天", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.glassStrong)
                        .border(1.dp, c.borderTop.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${formatHours(s.minutes)} H", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                }
            }
        }
    }
}

/* ---------------- 小部件 ---------------- */

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, c.borderTop.copy(alpha = 0.5f), CircleShape)
            .pressable(onClick, pressedScale = 0.95f)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 1)
    }
}

@Composable
private fun ValueChip(label: String, value: String, color: Color) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun DonutFact(label: String, value: String, color: Color) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun PayCard(
    title: String,
    totalText: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Pair<String, Long>>,
) {
    val c = LocalAppColors.current
    val money = Money("CNY")
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, color, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(totalText, style = MaterialTheme.typography.titleSmall, color = color)
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                items.forEach { (name, amount) ->
                    Row(Modifier.fillMaxWidth().padding(start = 46.dp, top = 4.dp)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(money.format(amount), style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "本月暂无，去打卡页当天添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 46.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.width(160.dp).height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.width(90.dp).height(26.dp), CircleShape)
            Skeleton(Modifier.width(90.dp).height(26.dp), CircleShape)
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                repeat(4) {
                    Skeleton(Modifier.fillMaxWidth().height(18.dp))
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun intervalLabel(month: YearMonth): String =
    "%02d-01 ~ %02d-%02d".format(month.monthValue, month.monthValue, month.lengthOfMonth())

private fun payItems(snap: MonthSnapshot, bonus: Boolean): List<Pair<String, Long>> {
    val kind = if (bonus) PayKind.BONUS else PayKind.DEDUCT
    return snap.pays
        .mapNotNull { p ->
            val item = snap.items.firstOrNull { it.id == p.itemId && it.kind == kind } ?: return@mapNotNull null
            item.name to p.amountMinor
        }
        .groupBy({ it.first }, { it.second })
        .map { (name, amounts) -> name to amounts.sum() }
}

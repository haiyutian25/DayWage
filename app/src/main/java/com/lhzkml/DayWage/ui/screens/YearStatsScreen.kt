package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.MonthSummary
import com.lhzkml.DayWage.data.SalaryMode
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.ChartSlice
import com.lhzkml.DayWage.ui.components.DonutChart
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.GlassScreen
import com.lhzkml.DayWage.ui.components.RoundButton
import com.lhzkml.DayWage.ui.components.SectionHeader
import com.lhzkml.DayWage.ui.components.Skeleton
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.Money
import com.lhzkml.DayWage.util.ReportExport
import com.lhzkml.DayWage.util.formatHours
import com.lhzkml.DayWage.util.leadingBlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class YearStatsViewModel(private val repo: WorkRepository) : ViewModel() {
    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year

    val snapshot: StateFlow<MonthSnapshot?> = _year
        .flatMapLatest { repo.yearSnapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setYear(y: Int) { _year.value = y }
}

@Composable
fun YearStatsScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    vm: YearStatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val year by vm.year.collectAsStateWithLifecycle()
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val money = Money("CNY")
    var exporting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    GlassScreen(
        title = "年度统计",
        onBack = onBack,
        subtitle = "${year}年",
        actions = {
            RoundButton(
                Lucide.Download,
                "导出年度 Excel",
                onClick = {
                    val s = snap ?: return@RoundButton
                    exporting = true
                    scope.launch {
                        runCatching {
                            val f = withContext(Dispatchers.IO) {
                                ReportExport.buildYear(context, year, s, s.projects.firstOrNull()?.mode ?: SalaryMode.XSJS)
                            }
                            ReportExport.share(context, f)
                        }.onFailure { onToast("导出失败：${it.message}") }
                        exporting = false
                    }
                },
                enabled = snap != null && !exporting,
            )
        },
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            YearSwitcher(year) { vm.setYear(it) }
            Spacer(Modifier.height(12.dp))

            if (snap == null) {
                Skeleton(Modifier.width(180.dp).height(34.dp))
                Spacer(Modifier.height(16.dp))
                Skeleton(Modifier.fillMaxWidth().height(140.dp), RoundedCornerShape(28.dp))
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            val months: List<MonthSummary> = (1..12).map { snap.summary(YearMonth.of(year, it)) }
            val recordDays = months.sumOf { it.recordDays }
            val workDays = months.sumOf { it.workDays }
            val restDays = months.sumOf { it.restDays }
            val total = months.sumOf { it.totalMoneyMinor }
            val bonus = months.sumOf { it.bonusMinor }
            val deduct = months.sumOf { it.deductMinor }
            val minutes = months.sumOf { it.workMinutes }
            val overtime = months.sumOf { it.overtimeMinutes }
            val zb = months.sumOf { it.zbMinutes }
            val jb = months.sumOf { it.jbMinutes }
            val zm = months.sumOf { it.zmMinutes }
            val jj = months.sumOf { it.jjMinutes }

            // 三枚指标
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YearFact("记录", "$recordDays", "天", Modifier.weight(1f))
                YearFact("上班", "$workDays", "天", Modifier.weight(1f), c.accent)
                YearFact("休假", "$restDays", "天", Modifier.weight(1f), c.positive)
            }

            Spacer(Modifier.height(12.dp))

            // 环形图 + 图例
            Glass(Modifier.fillMaxWidth(), strong = true) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val slices = listOf(
                        ChartSlice(zb.toFloat(), c.accent) to "正班",
                        ChartSlice(jb.toFloat(), c.positive) to "加班",
                        ChartSlice(zm.toFloat(), c.warning) to "周末",
                        ChartSlice(jj.toFloat(), c.negative) to "节假",
                    ).filter { it.first.value > 0f }
                    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                        DonutChart(slices.map { it.first }, Modifier.fillMaxSize(), thickness = 15.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatHours(minutes), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                                Text("总工时", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            "正班" to (zb to c.accent),
                            "加班" to (jb to c.positive),
                            "周末" to (zm to c.warning),
                            "节假" to (jj to c.negative),
                        ).forEach { (name, pair) ->
                            val (value, color) = pair
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                                Text("${formatHours(value)} H", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 金额
            Glass(Modifier.fillMaxWidth(), strong = true) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(money.format(total), style = MaterialTheme.typography.displaySmall, color = c.accent)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyBox("补贴", "+" + money.format(bonus), c.positive, Modifier.weight(1f))
                        MoneyBox("扣款", "−" + money.format(deduct), c.negative, Modifier.weight(1f))
                    }
                }
            }

            SectionHeader("每月工时")
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    StackedMonthBars(months)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot("正班", c.accent)
                        LegendDot("加班", c.positive)
                        LegendDot("周末", c.warning)
                        LegendDot("节假", c.negative)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "全年 ${formatHours(minutes)}H，其中加班 ${formatHours(overtime)}H（含周末与节假）",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }

            SectionHeader("每日分布")
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val perDay = snap.records.associate { it.epochDay to it.totalMinutes }
                    for (row in 0 until 4) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (col in 0 until 3) {
                                val m = row * 3 + col + 1
                                HeatMonth(
                                    month = YearMonth.of(year, m),
                                    perDay = perDay,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun YearSwitcher(year: Int, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Lucide.ChevronLeft, "上一年", { onChange(year - 1) }, size = 38.dp)
        Text(
            "${year}年",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        RoundButton(
            Lucide.ChevronRight, "下一年", { onChange(year + 1) }, size = 38.dp,
            enabled = year < LocalDate.now().year,
        )
    }
}

@Composable
private fun YearFact(label: String, value: String, unit: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalAppColors.current
    Glass(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = color ?: c.textPrimary)
                Spacer(Modifier.width(2.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}

@Composable
private fun MoneyBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

/** 12 根堆叠柱：每小时一种颜色 */
@Composable
private fun StackedMonthBars(months: List<MonthSummary>) {
    val c = LocalAppColors.current
    val max = months.maxOf { it.workMinutes }.coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.height(120.dp).width(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(formatHours(max), formatHours(max / 2), "0").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
            Canvas(Modifier.weight(1f).height(120.dp)) {
                val slot = size.width / 12f
                val barW = (slot * 0.5f).coerceAtMost(14.dp.toPx())
                months.forEachIndexed { i, m ->
                    val x = slot * i + (slot - barW) / 2
                    var bottom = size.height
                    listOf(
                        m.zbMinutes to c.accent,
                        m.jbMinutes to c.positive,
                        m.zmMinutes to c.warning,
                        m.jjMinutes to c.negative,
                    ).forEach { (minutes, color) ->
                        if (minutes <= 0) return@forEach
                        val h = size.height * (minutes.toFloat() / max)
                        bottom -= h
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, bottom),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 6, barW / 6),
                        )
                    }
                    if (m.workMinutes == 0) {
                        drawRoundRect(
                            color = c.glassStrong,
                            topLeft = Offset(x, size.height - 2.dp.toPx()),
                            size = Size(barW, 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(28.dp))
            Row(Modifier.weight(1f)) {
                (1..12).forEach { m ->
                    Text(
                        "$m",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 小月历热力图：有工时的日子点亮 */
@Composable
private fun HeatMonth(month: YearMonth, perDay: Map<Long, Int>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val blanks = month.leadingBlanks(true)
    val cells: List<Int?> = List(blanks) { null } + (1..month.lengthOfMonth()).toList()
    val monthMinutes = cells.filterNotNull().sumOf { d -> perDay[month.atDay(d).toEpochDay()] ?: 0 }
    Column(modifier) {
        Text(
            "%d-%02d".format(month.year, month.monthValue),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
        )
        Text(
            "${formatHours(monthMinutes)}H",
            style = MaterialTheme.typography.labelSmall,
            color = if (monthMinutes > 0) c.accent else c.textTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        val minutes = if (day == null) 0 else perDay[month.atDay(day).toEpochDay()] ?: 0
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        day == null -> Color.Transparent
                                        minutes >= 480 -> c.accent
                                        minutes >= 240 -> c.accent.copy(alpha = 0.55f)
                                        minutes > 0 -> c.accent.copy(alpha = 0.3f)
                                        else -> c.textTertiary.copy(alpha = 0.12f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

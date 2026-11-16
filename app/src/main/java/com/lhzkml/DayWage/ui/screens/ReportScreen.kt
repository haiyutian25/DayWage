package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.MonthSummary
import com.lhzkml.DayWage.data.RecordKind
import com.lhzkml.DayWage.data.SalaryMode
import com.lhzkml.DayWage.data.Shift
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.GlassScreen
import com.lhzkml.DayWage.ui.components.MonthSwitcher
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.RowDivider
import com.lhzkml.DayWage.ui.components.SecondaryButton
import com.lhzkml.DayWage.ui.components.Skeleton
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.Money
import com.lhzkml.DayWage.util.ReportExport
import com.lhzkml.DayWage.util.formatHours
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
class ReportViewModel(private val repo: WorkRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    val snapshot: StateFlow<MonthSnapshot?> = _month
        .flatMapLatest { repo.snapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shifts: StateFlow<List<Shift>> = repo.shifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(m: YearMonth) { _month.value = m }
}

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    vm: ReportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val month by vm.month.collectAsStateWithLifecycle()
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val money = Money("CNY")
    var exporting by remember { mutableStateOf(false) }

    val summary = snap?.summary(month)
    val mode = snap?.projects?.firstOrNull()?.mode ?: SalaryMode.XSJS
    val shiftNames = remember(shifts) { shifts.associate { it.id to it.name } }

    GlassScreen(
        title = "报表",
        onBack = onBack,
        subtitle = "${month.year}年${month.monthValue}月 · ${mode.label}",
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(
                    "复制",
                    {
                        val s = snap ?: return@SecondaryButton
                        clipboard.setText(AnnotatedString(ReportExport.buildTsv(month, s, shiftNames)))
                        onToast("已复制到剪贴板")
                    },
                    Modifier.weight(1f),
                )
                SecondaryButton(
                    "分享",
                    {
                        val s = snap ?: return@SecondaryButton
                        ReportExport.shareText(
                            context,
                            ReportExport.buildText(month, s, mode, shiftNames),
                            "工时记录 ${month.year}年${month.monthValue}月",
                        )
                    },
                    Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (exporting) "导出中…" else "导出 Excel",
                    onClick = {
                        val s = snap ?: return@PrimaryButton
                        exporting = true
                        scope.launch {
                            runCatching {
                                val file = withContext(Dispatchers.IO) {
                                    ReportExport.build(context, month, s, mode)
                                }
                                ReportExport.share(context, file)
                            }.onFailure { onToast("导出失败：${it.message}") }
                            exporting = false
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    enabled = snap != null && !exporting,
                )
            }
        },
    ) { padding: PaddingValues ->
        if (snap == null || summary == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Skeleton(Modifier.width(120.dp).height(34.dp))
                Spacer(Modifier.height(16.dp))
                Skeleton(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(28.dp))
            }
            return@GlassScreen
        }

        val dayCount = month.lengthOfMonth()
        val byDay = remember(snap) { snap.records.associateBy { it.epochDay } }

        LazyColumn(
            // 上下留白必须加在 modifier 上：stickyHeader 的吸附点是列表自身的顶边，
            // 用 contentPadding 的话表头会吸到屏幕最顶部，把返回栏盖住。
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
        ) {
            item { MonthSwitcher(month, vm::setMonth) }
            item { Spacer(Modifier.height(12.dp)) }
            item { MainCard(summary, mode, month, money) }
            item { Spacer(Modifier.height(12.dp)) }
            item { DetailCard(snap, summary, month, money) }
            item { Spacer(Modifier.height(12.dp)) }

            // 吸顶表头
            stickyHeader {
                TableHeader(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                )
            }

            items(count = dayCount) { index ->
                val day = index + 1
                val date = month.atDay(day)
                val record = byDay[date.toEpochDay()]
                DayRow(
                    day = day,
                    record = record,
                    shiftName = record?.let { r ->
                        when {
                            r.kind == RecordKind.REST -> "休假"
                            else -> shiftNames[r.shiftId].orEmpty().ifBlank { "--" }
                        }
                    } ?: "--",
                    moneyText = if (record == null) "¥0.00" else money.format(snap.dayMoney(record).totalMinor),
                    last = day == dayCount,
                )
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "「复制」得到制表符分隔的表格，粘到 Excel / 飞书表格会自动分列；「导出 Excel」是标准 .xlsx。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textTertiary,
                )
            }
        }
    }
}

/* ---------------- 上方卡片 ---------------- */

@Composable
private fun MainCard(summary: MonthSummary, mode: SalaryMode, month: YearMonth, money: Money) {
    val c = LocalAppColors.current
    Glass(Modifier.fillMaxWidth(), strong = true) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("工时记录", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(rangeLabel(month), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Text(money.format(summary.totalMoneyMinor), style = MaterialTheme.typography.displaySmall, color = c.accent)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Fact("总工时", "${formatHours(summary.workMinutes)} H", Modifier.weight(1f))
                Fact("打卡", "${summary.recordDays} 天", Modifier.weight(1f))
                Fact("计薪方式", mode.label, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun DetailCard(snap: MonthSnapshot, summary: MonthSummary, month: YearMonth, money: Money) {
    val c = LocalAppColors.current
    val projectId = snap.projects.firstOrNull()?.id ?: "default"
    val lastDay = snap.records.maxOfOrNull { it.epochDay } ?: LocalDate.now().toEpochDay()
    val rates = snap.ratesFor(projectId, lastDay)

    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("工时明细", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(money.format(summary.workMoneyMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            DetailRow("正班", summary.zbMinutes, rates.zbMinor, summary.zbMoneyMinor, money, c.accent)
            DetailRow("加班", summary.jbMinutes, rates.jbMinor, summary.jbMoneyMinor, money, c.positive)
            DetailRow("周末", summary.zmMinutes, rates.zmMinor, summary.zmMoneyMinor, money, c.warning)
            DetailRow("节假", summary.jjMinutes, rates.jjMinor, summary.jjMoneyMinor, money, c.negative)
            if (summary.dayPayMinor > 0) {
                DetailRow("日结", 0, -1L, summary.dayPayMinor, money, c.textTertiary)
            }
            if (summary.restDays > 0) {
                Spacer(Modifier.height(6.dp))
                RowDivider(inset = 0.dp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("休假明细", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text("+¥0.00", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                }
                DetailRow("休假", summary.restMinutes, -1L, 0L, money, c.positive, countText = "${summary.restDays}天")
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
    }
}

@Composable
private fun DetailRow(
    name: String,
    minutes: Int,
    rateMinor: Long,
    moneyMinor: Long,
    money: Money,
    color: Color,
    countText: String? = null,
) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.width(44.dp))
        Text(
            countText ?: if (minutes > 0) "${formatHours(minutes)}H" else "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (minutes > 0) color else c.textTertiary,
            modifier = Modifier.width(52.dp),
        )
        Text(
            if (rateMinor > 0) money.format(rateMinor) + "/H" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (moneyMinor > 0) "+" + money.format(moneyMinor) else money.format(0),
            style = MaterialTheme.typography.bodySmall,
            color = if (moneyMinor > 0) c.textPrimary else c.textTertiary,
        )
    }
}

/* ---------------- 表格（吸顶 + 网格） ---------------- */

private val ColDate = 0.85f
private val ColShift = 1.05f
private val ColHour = 0.75f
private val ColMoney = 1.15f

/** 表格底色：不透明，吸顶时才不会透出下面的行 */
private fun tableSurface(c: com.lhzkml.DayWage.ui.theme.AppColors): Color =
    if (c.isDark) Color(0xFF15151C) else Color.White

/** 隔行色 */
private fun zebraTint(c: com.lhzkml.DayWage.ui.theme.AppColors): Color =
    if (c.isDark) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.028f)

/** 网格线 */
private fun gridTint(c: com.lhzkml.DayWage.ui.theme.AppColors): Color =
    if (c.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

@Composable
private fun TableHeader(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(tableSurface(c))
            .drawBehind { gridLine(c, bottom = true, outer = true) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadCell("日期", ColDate, TextAlign.Center)
        HeadCell("班次", ColShift, TextAlign.Center)
        HeadCell("正班", ColHour, TextAlign.Center)
        HeadCell("加班", ColHour, TextAlign.Center)
        HeadCell("周末", ColHour, TextAlign.Center)
        HeadCell("节假", ColHour, TextAlign.Center)
        HeadCell("金额", ColMoney, TextAlign.End, divider = false)
    }
}

@Composable
private fun DayRow(day: Int, record: com.lhzkml.DayWage.data.WorkRecord?, shiftName: String, moneyText: String, last: Boolean) {
    val c = LocalAppColors.current
    val rowShape = if (last) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(rowShape)
            .drawBehind {
                // 底色先铺满，再叠斑马纹
                drawRect(tableSurface(c))
                if (day % 2 == 0) drawRect(zebraTint(c))
                gridLine(c, bottom = true, separator = true, outer = true)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell("$day", ColDate, if (record == null) c.textTertiary else c.textPrimary)
        Cell(shiftName, ColShift, if (record == null) c.textTertiary else c.textSecondary)
        HourCell(record?.zbMinutes ?: 0, c.accent)
        HourCell(record?.jbMinutes ?: 0, c.positive)
        HourCell(record?.zmMinutes ?: 0, c.warning)
        HourCell(record?.jjMinutes ?: 0, c.negative)
        Cell(moneyText, ColMoney, if (record == null) c.textTertiary else c.textPrimary, TextAlign.End, divider = false)
    }
}

@Composable
private fun RowScope.HeadCell(text: String, weight: Float, align: TextAlign, divider: Boolean = true) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .drawBehind { if (divider) verticalLine(c) },
        contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.padding(end = if (align == TextAlign.End) 10.dp else 0.dp),
        )
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color,
    align: TextAlign = TextAlign.Center,
    divider: Boolean = true,
) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .drawBehind { if (divider) verticalLine(c) },
        contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.padding(end = if (align == TextAlign.End) 10.dp else 0.dp),
        )
    }
}

@Composable
private fun RowScope.HourCell(minutes: Int, color: Color) {
    val c = LocalAppColors.current
    Box(
        Modifier.weight(ColHour).fillMaxHeight().drawBehind { verticalLine(c) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (minutes > 0) "${formatHours(minutes)}H" else "0H",
            style = MaterialTheme.typography.labelSmall,
            color = if (minutes > 0) color else c.textTertiary.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** 列之间的竖分隔线（画在单元格右侧） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.verticalLine(c: com.lhzkml.DayWage.ui.theme.AppColors) {
    val w = 1.dp.toPx()
    drawLine(
        color = gridTint(c),
        start = Offset(size.width - w / 2, 0f),
        end = Offset(size.width - w / 2, size.height),
        strokeWidth = w,
    )
}

/** 行底边线；[outer] 为真时补上左右外框 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.gridLine(
    c: com.lhzkml.DayWage.ui.theme.AppColors,
    bottom: Boolean,
    separator: Boolean = true,
    outer: Boolean = false,
) {
    val w = 1.dp.toPx()
    val line = gridTint(c)
    if (bottom) {
        drawLine(line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
    }
    if (separator && outer) {
        drawLine(line, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
        drawLine(line, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
    }
}

private fun rangeLabel(month: YearMonth): String =
    "%02d-01 ~ %02d-%02d".format(month.monthValue, month.monthValue, month.lengthOfMonth())

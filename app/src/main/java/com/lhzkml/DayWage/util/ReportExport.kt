package com.lhzkml.DayWage.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.RecordKind
import com.lhzkml.DayWage.data.SalaryMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/** 把一个月的数据导成 xlsx，并分享出去（微信 / 邮件 / 存到文件）。 */
object ReportExport {

    private val dayFmt = DateTimeFormatter.ofPattern("M月d日")
    private val weekFmt = DateTimeFormatter.ofPattern("EEEE")
    private val stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun hours(minutes: Int): Double = (minutes / 60.0 * 100).roundToLong() / 100.0
    private fun yuan(minor: Long): Double = minor / 100.0

    /** 生成文件，返回它的路径。文件名带月份，方便区分。 */
    fun build(
        context: Context,
        month: YearMonth,
        snap: MonthSnapshot,
        mode: SalaryMode,
    ): File {
        val summary = snap.summary(month)
        val records = snap.records.sortedBy { it.epochDay }

        val rows = mutableListOf<List<Cell>>()

        rows += listOf(Cell.Header("工时记录 · ${month.year}年${month.monthValue}月"))
        rows += listOf(Cell.Text("导出时间"), Cell.Text(LocalDateTime.now().format(stampFmt)))
        rows += listOf(Cell.Text("计薪方式"), Cell.Text(mode.label))
        rows += listOf(
            Cell.Text("出勤天数"), Cell.Num(summary.workDays.toDouble()),
            Cell.Text("总工时"), Cell.Num(hours(summary.workMinutes)),
        )
        rows += listOf(
            Cell.Text("工时金额"), Cell.Num(yuan(summary.workMoneyMinor)),
            Cell.Text("底薪"), Cell.Num(yuan(summary.baseMoneyMinor)),
        )
        rows += listOf(
            Cell.Text("补贴"), Cell.Num(yuan(summary.bonusMinor)),
            Cell.Text("扣款"), Cell.Num(yuan(summary.deductMinor)),
        )
        rows += listOf(Cell.Text("应发合计"), Cell.Num(yuan(summary.totalMoneyMinor)))
        rows.add(emptyList())

        rows += listOf(
            Cell.Header("日期"),
            Cell.Header("星期"),
            Cell.Header("正班(H)"),
            Cell.Header("加班(H)"),
            Cell.Header("周末(H)"),
            Cell.Header("节假(H)"),
            Cell.Header("日结(元)"),
            Cell.Header("补贴(元)"),
            Cell.Header("扣款(元)"),
            Cell.Header("当日合计(元)"),
            Cell.Header("备注"),
        )

        var tZb = 0; var tJb = 0; var tZm = 0; var tJj = 0
        var tBonus = 0L; var tDeduct = 0L; var tTotal = 0L

        records.forEach { r ->
            val date = LocalDate.ofEpochDay(r.epochDay)
            val money = snap.dayMoney(r)
            val dayPays = snap.pays.filter { it.epochDay == r.epochDay }
            val bonus = dayPays.sumOf { p ->
                snap.items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == com.lhzkml.DayWage.data.PayKind.BONUS }
                    ?.let { p.amountMinor } ?: 0L
            }
            val deduct = dayPays.sumOf { p ->
                snap.items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == com.lhzkml.DayWage.data.PayKind.DEDUCT }
                    ?.let { p.amountMinor } ?: 0L
            }
            tZb += r.zbMinutes; tJb += r.jbMinutes; tZm += r.zmMinutes; tJj += r.jjMinutes
            tBonus += bonus; tDeduct += deduct; tTotal += money.totalMinor

            val leaveNote = if (r.kind == RecordKind.REST) {
                "${r.leaveType?.label ?: "休假"} ${hours(r.leaveMinutes)}H"
            } else r.note
            rows += listOf(
                Cell.Text(date.format(dayFmt)),
                Cell.Text(date.format(weekFmt)),
                Cell.Num(hours(r.zbMinutes)),
                Cell.Num(hours(r.jbMinutes)),
                Cell.Num(hours(r.zmMinutes)),
                Cell.Num(hours(r.jjMinutes)),
                Cell.Num(yuan(r.dayPayMinor)),
                Cell.Num(yuan(bonus)),
                Cell.Num(yuan(deduct)),
                Cell.Num(yuan(money.totalMinor)),
                Cell.Text(leaveNote),
            )
        }

        rows += listOf(
            Cell.Header("合计"),
            Cell.Header(""),
            Cell.Num(hours(tZb)),
            Cell.Num(hours(tJb)),
            Cell.Num(hours(tZm)),
            Cell.Num(hours(tJj)),
            Cell.Num(0.0),
            Cell.Num(yuan(tBonus)),
            Cell.Num(yuan(tDeduct)),
            Cell.Num(yuan(tTotal)),
            Cell.Header(""),
        )

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // 清掉上一次的同月文件，避免越积越多
        dir.listFiles { f -> f.name.startsWith("工时记录_${month.year}-${month.monthValue}") }?.forEach { it.delete() }
        val file = File(dir, "工时记录_${month.year}-${month.monthValue}.xlsx")
        XlsxWriter.write(file, "${month.monthValue}月", rows)
        return file
    }

    /**
     * 年度汇总：一个 sheet 里 12 行，每月一行，最后一行合计。
     * 空月份也占一行（全 0），保证表格永远齐整。
     */
    fun buildYear(
        context: Context,
        year: Int,
        snap: MonthSnapshot,
        mode: SalaryMode,
    ): File {
        val rows = mutableListOf<List<Cell>>()

        rows += listOf(Cell.Header("工时记录 · ${year} 年度汇总"))
        rows += listOf(Cell.Text("导出时间"), Cell.Text(LocalDateTime.now().format(stampFmt)))
        rows += listOf(Cell.Text("计薪方式"), Cell.Text(mode.label))
        rows.add(emptyList())

        rows += listOf(
            Cell.Header("月份"),
            Cell.Header("出勤(天)"),
            Cell.Header("总工时(H)"),
            Cell.Header("正班(H)"),
            Cell.Header("加班(H)"),
            Cell.Header("周末(H)"),
            Cell.Header("节假(H)"),
            Cell.Header("工时金额(元)"),
            Cell.Header("底薪(元)"),
            Cell.Header("补贴(元)"),
            Cell.Header("扣款(元)"),
            Cell.Header("应发合计(元)"),
        )

        var tDays = 0
        var tMinutes = 0
        var tZb = 0; var tJb = 0; var tZm = 0; var tJj = 0
        var tWork = 0L; var tBase = 0L; var tBonus = 0L; var tDeduct = 0L; var tTotal = 0L

        (1..12).forEach { m ->
            val s = snap.summary(YearMonth.of(year, m))
            tDays += s.workDays
            tMinutes += s.workMinutes
            tZb += s.zbMinutes; tJb += s.jbMinutes; tZm += s.zmMinutes; tJj += s.jjMinutes
            tWork += s.workMoneyMinor; tBase += s.baseMoneyMinor
            tBonus += s.bonusMinor; tDeduct += s.deductMinor; tTotal += s.totalMoneyMinor
            rows += listOf(
                Cell.Text("${m}月"),
                Cell.Num(s.workDays.toDouble()),
                Cell.Num(hours(s.workMinutes)),
                Cell.Num(hours(s.zbMinutes)),
                Cell.Num(hours(s.jbMinutes)),
                Cell.Num(hours(s.zmMinutes)),
                Cell.Num(hours(s.jjMinutes)),
                Cell.Num(yuan(s.workMoneyMinor)),
                Cell.Num(yuan(s.baseMoneyMinor)),
                Cell.Num(yuan(s.bonusMinor)),
                Cell.Num(yuan(s.deductMinor)),
                Cell.Num(yuan(s.totalMoneyMinor)),
            )
        }

        rows += listOf(
            Cell.Header("合计"),
            Cell.Num(tDays.toDouble()),
            Cell.Num(hours(tMinutes)),
            Cell.Num(hours(tZb)),
            Cell.Num(hours(tJb)),
            Cell.Num(hours(tZm)),
            Cell.Num(hours(tJj)),
            Cell.Num(yuan(tWork)),
            Cell.Num(yuan(tBase)),
            Cell.Num(yuan(tBonus)),
            Cell.Num(yuan(tDeduct)),
            Cell.Num(yuan(tTotal)),
        )

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("工时记录_${year}年度") }?.forEach { it.delete() }
        val file = File(dir, "工时记录_${year}年度.xlsx")
        XlsxWriter.write(file, "${year}年", rows)
        return file
    }

    /** 纯文本报表：给「分享报表」用，也能直接粘到微信。 */
    fun buildText(month: YearMonth, snap: MonthSnapshot, mode: SalaryMode, shiftNames: Map<String, String>): String {
        val s = snap.summary(month)
        val sb = StringBuilder()
        sb.appendLine("工时记录 · ${month.year}年${month.monthValue}月")
        sb.appendLine("${rangeLabel(month)}")
        sb.appendLine("应发合计 ¥${money(s.totalMoneyMinor)}")
        sb.appendLine("总工时 ${hour(s.workMinutes)}H · 打卡 ${s.recordDays}天 · ${mode.label}")
        sb.appendLine()
        sb.appendLine("【工时明细】")
        sb.appendLine("正班  ${hour(s.zbMinutes)}H  ¥${money(s.zbMoneyMinor)}")
        sb.appendLine("加班  ${hour(s.jbMinutes)}H  ¥${money(s.jbMoneyMinor)}")
        sb.appendLine("周末  ${hour(s.zmMinutes)}H  ¥${money(s.zmMoneyMinor)}")
        sb.appendLine("节假  ${hour(s.jjMinutes)}H  ¥${money(s.jjMoneyMinor)}")
        if (s.dayPayMinor > 0) sb.appendLine("日结   —  ¥${money(s.dayPayMinor)}")
        if (s.restDays > 0) {
            sb.appendLine("休假  ${hour(s.restMinutes)}H  ${s.restDays}天")
        }
        if (s.bonusMinor > 0) sb.appendLine("补贴  +¥${money(s.bonusMinor)}")
        if (s.deductMinor > 0) sb.appendLine("扣款  -¥${money(s.deductMinor)}")
        sb.appendLine()
        sb.appendLine("【每日明细】")
        sb.appendLine("日期 班次 正班 加班 周末 节假 金额")
        snap.records.sortedBy { it.epochDay }.forEach { r ->
            val d = LocalDate.ofEpochDay(r.epochDay)
            val name = if (r.kind == com.lhzkml.DayWage.data.RecordKind.REST) "休假"
            else shiftNames[r.shiftId].orEmpty().ifBlank { "--" }
            val m = snap.dayMoney(r)
            sb.appendLine(
                "${d.monthValue}/${d.dayOfMonth} $name ${hourOrDash(r.zbMinutes)} ${hourOrDash(r.jbMinutes)} " +
                    "${hourOrDash(r.zmMinutes)} ${hourOrDash(r.jjMinutes)} ¥${money(m.totalMinor)}",
            )
        }
        return sb.toString().trimEnd()
    }

    /** TSV：制表符分隔，粘到 Excel / 飞书表格里自动分列。 */
    fun buildTsv(month: YearMonth, snap: MonthSnapshot, shiftNames: Map<String, String>): String {
        val sb = StringBuilder()
        sb.appendLine("日期\t班次\t正班\t加班\t周末\t节假\t金额")
        snap.records.sortedBy { it.epochDay }.forEach { r ->
            val d = LocalDate.ofEpochDay(r.epochDay)
            val name = if (r.kind == com.lhzkml.DayWage.data.RecordKind.REST) "休假"
            else shiftNames[r.shiftId].orEmpty().ifBlank { "--" }
            sb.appendLine(
                listOf(
                    d.format(dayFmt),
                    name,
                    hour(r.zbMinutes),
                    hour(r.jbMinutes),
                    hour(r.zmMinutes),
                    hour(r.jjMinutes),
                    money(snap.dayMoney(r).totalMinor),
                ).joinToString("\t"),
            )
        }
        return sb.toString().trimEnd()
    }

    fun shareText(context: Context, text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(intent, "分享报表").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    private fun money(minor: Long): String = String.format(java.util.Locale.US, "%.2f", minor / 100.0)

    private fun hour(minutes: Int): String {
        if (minutes <= 0) return "0"
        val h = minutes / 60.0
        return if (h % 1.0 == 0.0) h.toInt().toString() else String.format(java.util.Locale.US, "%.2f", h).trimEnd('0').trimEnd('.')
    }

    private fun hourOrDash(minutes: Int): String = if (minutes > 0) hour(minutes) + "H" else "--"

    private fun rangeLabel(month: YearMonth): String =
        "%02d-01 ~ %02d-%02d".format(month.monthValue, month.monthValue, month.lengthOfMonth())

    /** 系统分享面板：微信、邮件、云盘、文件管理器都能接。 */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出Excel").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

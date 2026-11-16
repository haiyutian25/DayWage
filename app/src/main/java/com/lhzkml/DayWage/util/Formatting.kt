package com.lhzkml.DayWage.util

import androidx.compose.runtime.staticCompositionLocalOf
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 金额格式化。金额在库里是「分」，这里统一转成「元」显示。
 * 保留原有 API 形状（format / formatWhole / signed / groupInput），调用点不用改写。
 */
class Money(private val code: String = "CNY") {
    private val locale: Locale = Locale.CHINA
    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        currency = java.util.Currency.getInstance(code)
    }

    val symbol: String = "¥"
    val label: String = "¥ CNY"

    fun format(minor: Long): String = synchronized(formatter) {
        formatter.minimumFractionDigits = if (minor % 100 == 0L) 0 else 2
        formatter.maximumFractionDigits = 2
        formatter.format(java.math.BigDecimal.valueOf(minor, 2))
    }

    /** 取整显示，用于大号标题数字。 */
    fun formatWhole(minor: Long): String = format(
        java.math.BigDecimal.valueOf(minor, 2)
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .movePointRight(2).toLong(),
    )

    /** 带正负号：补贴 +，扣款 −。 */
    fun signed(minor: Long, positive: Boolean): String = (if (positive) "+" else "−") + format(abs(minor))

    private val grouping: NumberFormat = NumberFormat.getIntegerInstance(locale)

    /** 键盘输入加千分位。 */
    fun groupInput(text: String): String {
        if (text.isEmpty()) return "0"
        val dot = text.indexOf('.')
        val whole = if (dot >= 0) text.substring(0, dot) else text
        val grouped = synchronized(grouping) { grouping.format(whole.toLongOrNull() ?: 0L) }
        return if (dot >= 0) grouped + text.substring(dot) else grouped
    }
}

val LocalMoney = staticCompositionLocalOf { Money("CNY") }

/** 小时数格式化：480 分钟 → "8"，470 → "7.83"。 */
fun formatHours(minutes: Int): String {
    if (minutes <= 0) return "0"
    val hours = minutes / 60.0
    return if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format(Locale.US, "%.2f", hours).trimEnd('0').trimEnd('.')
}

/** 480 → "8H"；90 → "1.5H"。 */
fun formatHoursUnit(minutes: Int): String = formatHours(minutes) + "H"

/** 分钟转「x 小时 y 分」，用在明细里。 */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}分"
        m == 0 -> "${h}小时"
        else -> "${h}小时${m}分"
    }
}

object AmountInput {
    private val pattern = Regex("""\d{0,9}(\.\d{0,2})?""")

    fun sanitize(raw: String): String? {
        val text = raw.replace(',', '.')
        return if (pattern.matches(text)) text else null
    }

    fun toMinor(text: String): Long? =
        text.toBigDecimalOrNull()?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()

    fun fromMinor(minor: Long): String =
        java.math.BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
}

private val dayThisYear = DateTimeFormatter.ofPattern("M月d日")
private val dayOtherYear = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val monthFormat = DateTimeFormatter.ofPattern("yyyy年M月")

fun LocalDate.friendlyLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    today.plusDays(1) -> "明天"
    else -> format(if (year == today.year) dayThisYear else dayOtherYear)
}

fun LocalDate.shortLabel(): String = format(dayThisYear)

fun YearMonth.label(): String = format(monthFormat)

/** 月份里每一天的序号（1..lengthOfMonth）。 */
fun YearMonth.days(): List<Int> = (1..lengthOfMonth()).toList()

/** 这个月从周几开始（0 = 周一）。 */
fun YearMonth.leadingBlanks(weekStartMonday: Boolean): Int {
    val first = atDay(1).dayOfWeek.value // 1 = 周一 … 7 = 周日
    return if (weekStartMonday) first - 1 else first % 7
}

val weekLabelsMonday = listOf("一", "二", "三", "四", "五", "六", "日")
val weekLabelsSunday = listOf("日", "一", "二", "三", "四", "五", "六")

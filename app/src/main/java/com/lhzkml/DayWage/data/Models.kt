package com.lhzkml.DayWage.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * 计薪方式。与「工时记录」保持一一对应：
 * DXJS 正班+加班 / GDDX 底薪+加班 / CSJB 综合工时制 / XSJS 小时计算
 */
enum class SalaryMode(val label: String, val short: String) {
    DXJS("正班 + 加班", "可同时记正班与加班"),
    GDDX("底薪 + 加班", "打卡只记录加班工时"),
    CSJB("综合工时制", "超过月工作时长算加班"),
    XSJS("小时计算", "正班加班周末统一时薪"),
}

/** 一天的类型：正常打卡 / 休假 / 日结 */
enum class RecordKind { WORK, REST, DAYPAY }

/** 休假类型 */
enum class LeaveType(val label: String) {
    XIU("休假"), BING("病假"), SHI("事假"), TIAO("调休"), XIN("薪假")
}

/** 补贴与扣款 */
enum class PayKind { BONUS, DEDUCT }

/** 补扣项目的记账方式：每日记录 / 金额记录 / 次数记录 */
enum class PayType { DAILY, FIXED, COUNT }

/** 目标类型 */
enum class GoalKind(val label: String) { MONEY("金额目标"), DATE("日期目标"), TODO("待办事项") }

class Converters {
    @TypeConverter fun modeToString(v: SalaryMode): String = v.name
    @TypeConverter fun stringToMode(v: String): SalaryMode = runCatching { SalaryMode.valueOf(v) }.getOrDefault(SalaryMode.XSJS)

    @TypeConverter fun kindToString(v: RecordKind): String = v.name
    @TypeConverter fun stringToKind(v: String): RecordKind = runCatching { RecordKind.valueOf(v) }.getOrDefault(RecordKind.WORK)

    @TypeConverter fun leaveToString(v: LeaveType?): String? = v?.name
    @TypeConverter fun stringToLeave(v: String?): LeaveType? = v?.let { runCatching { LeaveType.valueOf(it) }.getOrNull() }

    @TypeConverter fun payKindToString(v: PayKind): String = v.name
    @TypeConverter fun stringToPayKind(v: String): PayKind = runCatching { PayKind.valueOf(v) }.getOrDefault(PayKind.BONUS)

    @TypeConverter fun payTypeToString(v: PayType): String = v.name
    @TypeConverter fun stringToPayType(v: String): PayType = runCatching { PayType.valueOf(v) }.getOrDefault(PayType.FIXED)

    @TypeConverter fun goalKindToString(v: GoalKind): String = v.name
    @TypeConverter fun stringToGoalKind(v: String): GoalKind = runCatching { GoalKind.valueOf(v) }.getOrDefault(GoalKind.MONEY)
}

/**
 * 项目。每个项目有独立的计薪方式与薪资版本链，切换工作/薪资规则时新建项目而不是改旧项目。
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    val mode: SalaryMode,
    /** 排序，越小越靠前 */
    val position: Int = 0,
    val archived: Boolean = false,
)

/**
 * 薪资版本。按 [fromDay] 生效，历史记录始终按当时的版本计算。
 * 金额单位：分；时薪单位：分/小时。
 */
@Entity(
    tableName = "salary_versions",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class SalaryVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    /** 生效日期（epochDay），越晚优先级越高 */
    val fromDay: Long,
    /** 月底薪（分） */
    val baseMinor: Long = 0,
    /** 正班时薪（分/小时） */
    val zbRateMinor: Long = 0,
    /** 加班时薪（分/小时） */
    val jbRateMinor: Long = 0,
    /** 周末时薪（分/小时） */
    val zmRateMinor: Long = 0,
    /** 节假时薪（分/小时） */
    val jjRateMinor: Long = 0,
    /** 每月标准正班工时（小时），用于底薪折算 */
    val monthHours: Int = 174,
)

/** 班次：白班 / 早班 / 中班 / 晚班 */
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey val id: String,
    val name: String,
    /** CategoryIcons 的 key */
    val iconKey: String = "sun",
    val color: Long = 0xFFFCB819,
    val position: Int = 0,
)

/**
 * 一天的记录。时间是分钟（UI 以 0.5 小时步进），金额是分。
 * 一个日期只有一条记录，主键就是 [epochDay]。
 */
@Entity(
    tableName = "records",
    indices = [Index("projectId"), Index("epochDay")],
)
data class WorkRecord(
    @PrimaryKey val epochDay: Long,
    val projectId: String = "default",
    val shiftId: String = "",
    val kind: RecordKind = RecordKind.WORK,
    val zbMinutes: Int = 0,
    val jbMinutes: Int = 0,
    val zmMinutes: Int = 0,
    val jjMinutes: Int = 0,
    /** 日结金额（分），仅 [RecordKind.DAYPAY] 使用 */
    val dayPayMinor: Long = 0,
    val leaveType: LeaveType? = null,
    val leaveMinutes: Int = 0,
    /** 休假是否计薪（按 8 小时折一天） */
    val leavePaid: Boolean = false,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalMinutes: Int get() = zbMinutes + jbMinutes + zmMinutes + jjMinutes
}

/** 补贴/扣款项目定义 */
@Entity(tableName = "pay_items")
data class PayItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: PayKind = PayKind.BONUS,
    val type: PayType = PayType.FIXED,
    /** 每日记录的默认金额，或金额记录的固定金额（分） */
    val amountMinor: Long = 0,
    /** 次数记录的单价（分） */
    val unitMinor: Long = 0,
    val iconKey: String = "wallet",
    val position: Int = 0,
) {
    val signed: Long get() = if (kind == PayKind.BONUS) amountMinor else -amountMinor
}

/** 某天挂上的补贴/扣款 */
@Entity(
    tableName = "day_pays",
    indices = [Index("epochDay")],
)
data class DayPay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val itemId: Long,
    /** 本次实际金额（分），次数记录为 单价 × 次数 */
    val amountMinor: Long,
    val count: Int = 1,
    val note: String = "",
)

/** 目标 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: GoalKind = GoalKind.MONEY,
    val targetMinor: Long = 0,
    val startDay: Long = 0,
    val endDay: Long = 0,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 一个月的汇总，供记录页/统计页/目标页/报表共用 */
data class MonthSummary(
    val month: java.time.YearMonth,
    /** 有记录的天数（含休假、日结） */
    val recordDays: Int = 0,
    /** 上班天数（非休假） */
    val workDays: Int = 0,
    /** 休假天数 */
    val restDays: Int = 0,
    /** 休假时长（分钟） */
    val restMinutes: Int = 0,
    /** 总工时（分钟，不含休假、不含日结） */
    val workMinutes: Int = 0,
    val zbMinutes: Int = 0,
    val jbMinutes: Int = 0,
    val zmMinutes: Int = 0,
    val jjMinutes: Int = 0,
    /** 各类型出现的天数（当天该类型 > 0 才计一天） */
    val zbDays: Int = 0,
    val jbDays: Int = 0,
    val zmDays: Int = 0,
    val jjDays: Int = 0,
    /** 各类型产生的薪资（分） */
    val zbMoneyMinor: Long = 0,
    val jbMoneyMinor: Long = 0,
    val zmMoneyMinor: Long = 0,
    val jjMoneyMinor: Long = 0,
    /** 日结合计（分） */
    val dayPayMinor: Long = 0,
    /** 工时产生的薪资（分），含日结与计薪休假 */
    val workMoneyMinor: Long = 0,
    /** 底薪（分），仅 底薪+加班 / 综合工时制 */
    val baseMoneyMinor: Long = 0,
    val bonusMinor: Long = 0,
    val deductMinor: Long = 0,
) {
    val totalMoneyMinor: Long get() = workMoneyMinor + baseMoneyMinor + bonusMinor - deductMinor

    /** 总加班：加班 + 周末 + 节假（与原版一致，周末计入加班） */
    val overtimeMinutes: Int get() = jbMinutes + zmMinutes + jjMinutes

    /** 正班金额（统计页顶部 chip） */
    val regularMoneyMinor: Long get() = zbMoneyMinor

    /** 加班金额（含周末、节假，统计页顶部 chip） */
    val overtimeMoneyMinor: Long get() = jbMoneyMinor + zmMoneyMinor + jjMoneyMinor

    val totalHours: Double get() = workMinutes / 60.0
}

/** 按班次汇总（统计页第三页） */
data class ShiftStat(
    val key: String,
    val name: String,
    val days: Int,
    val minutes: Int,
    /** 休假单独一支，图标用咖啡杯 */
    val isLeave: Boolean = false,
)

/** 一天的钱，记录页展示用 */
data class DayMoney(
    val workMinor: Long = 0,
    val baseMinor: Long = 0,
    val bonusMinor: Long = 0,
    val deductMinor: Long = 0,
) {
    val totalMinor: Long get() = workMinor + baseMinor + bonusMinor - deductMinor
}

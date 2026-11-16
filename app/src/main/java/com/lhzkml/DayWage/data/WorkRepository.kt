package com.lhzkml.DayWage.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** 某一天生效的费率，单位：分/小时；[baseMinor] 为月底薪。 */
data class Rates(
    val zbMinor: Long = 0,
    val jbMinor: Long = 0,
    val zmMinor: Long = 0,
    val jjMinor: Long = 0,
    val baseMinor: Long = 0,
    val monthHours: Int = 174,
) {
    fun forType(type: WorkType): Long = when (type) {
        WorkType.ZB -> zbMinor
        WorkType.JB -> jbMinor
        WorkType.ZM -> zmMinor
        WorkType.JJ -> jjMinor
    }
}

enum class WorkType { ZB, JB, ZM, JJ }

/** 一个月份范围内的原始数据，UI 层据此算出汇总与每日金额。 */
data class MonthSnapshot(
    val records: List<WorkRecord> = emptyList(),
    val pays: List<DayPay> = emptyList(),
    val items: List<PayItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val versions: List<SalaryVersion> = emptyList(),
) {
    fun projectOf(record: WorkRecord?): Project? =
        projects.firstOrNull { it.id == (record?.projectId ?: "default") }

    /** 某天生效的费率。小时计算模式下正班/加班/周末同价，只有节假单独。 */
    fun ratesFor(projectId: String, epochDay: Long): Rates {
        val project = projects.firstOrNull { it.id == projectId } ?: return Rates()
        val version = versions
            .filter { it.projectId == projectId && it.fromDay <= epochDay }
            .maxWithOrNull(compareBy({ it.fromDay }, { it.id }))
            ?: versions.filter { it.projectId == projectId }.maxByOrNull { it.fromDay }
            ?: return Rates()
        return when (project.mode) {
            SalaryMode.XSJS -> Rates(
                zbMinor = version.zbRateMinor,
                jbMinor = version.zbRateMinor,
                zmMinor = version.zbRateMinor,
                jjMinor = version.jjRateMinor,
                baseMinor = 0,
                monthHours = version.monthHours,
            )
            else -> Rates(
                zbMinor = version.zbRateMinor,
                jbMinor = version.jbRateMinor,
                zmMinor = version.zmRateMinor,
                jjMinor = version.jjRateMinor,
                baseMinor = version.baseMinor,
                monthHours = version.monthHours,
            )
        }
    }

    fun modeOf(projectId: String): SalaryMode =
        projects.firstOrNull { it.id == projectId }?.mode ?: SalaryMode.XSJS

    /** 工时折算成钱：分钟 × 时薪 ÷ 60，四舍五入到分。 */
    private fun pay(minutes: Int, rateMinor: Long): Long =
        if (minutes <= 0 || rateMinor <= 0) 0L else (minutes.toDouble() * rateMinor / 60.0).roundToLong()

    /** 某天的工时薪资（不含补贴/扣款/底薪）。 */
    fun workMoney(record: WorkRecord): Long {
        val mode = modeOf(record.projectId)
        val rates = ratesFor(record.projectId, record.epochDay)
        return when (mode) {
            // 底薪 + 加班：正班不计时薪，只算加班/周末/节假
            SalaryMode.GDDX -> pay(record.jbMinutes, rates.jbMinor) +
                pay(record.zmMinutes, rates.zmMinor) +
                pay(record.jjMinutes, rates.jjMinor)
            else -> pay(record.zbMinutes, rates.zbMinor) +
                pay(record.jbMinutes, rates.jbMinor) +
                pay(record.zmMinutes, rates.zmMinor) +
                pay(record.jjMinutes, rates.jjMinor)
        }
    }

    fun dayMoney(record: WorkRecord?): DayMoney {
        if (record == null) return DayMoney()
        val mode = modeOf(record.projectId)
        val rates = ratesFor(record.projectId, record.epochDay)
        val work = if (record.kind == RecordKind.DAYPAY) record.dayPayMinor else workMoney(record)
        // 综合工时制按「正班时长上满即底薪」，日维度不摊底薪，只在月度汇总计入
        val base = 0L
        val mine = pays.filter { it.epochDay == record.epochDay }
        val bonus = mine.sumOf { p -> items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == PayKind.BONUS }?.let { p.amountMinor } ?: 0L }
        val deduct = mine.sumOf { p -> items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == PayKind.DEDUCT }?.let { p.amountMinor } ?: 0L }
        val paidLeave = if (record.kind == RecordKind.REST && record.leavePaid) {
            pay(record.leaveMinutes, rates.zbMinor)
        } else 0L
        return DayMoney(workMinor = work + paidLeave, baseMinor = base, bonusMinor = bonus, deductMinor = deduct)
    }

    /** 某个类型在某天的金额；底薪+加班 模式下正班不计薪。 */
    fun typeMoney(record: WorkRecord, type: WorkType): Long {
        val mode = modeOf(record.projectId)
        if (mode == SalaryMode.GDDX && type == WorkType.ZB) return 0L
        val rates = ratesFor(record.projectId, record.epochDay)
        val minutes = when (type) {
            WorkType.ZB -> record.zbMinutes
            WorkType.JB -> record.jbMinutes
            WorkType.ZM -> record.zmMinutes
            WorkType.JJ -> record.jjMinutes
        }
        return pay(minutes, rates.forType(type))
    }

    /** 月度汇总。底薪只在「底薪+加班」「综合工时制」下计入一次。 */
    fun summary(month: YearMonth, projectId: String? = null): MonthSummary {
        val inMonth = records.filter {
            val d = LocalDate.ofEpochDay(it.epochDay)
            YearMonth.of(d.year, d.month) == month && (projectId == null || it.projectId == projectId)
        }
        if (inMonth.isEmpty()) return MonthSummary(month)

        var zb = 0; var jb = 0; var zm = 0; var jj = 0
        var zbDays = 0; var jbDays = 0; var zmDays = 0; var jjDays = 0
        var zbMoney = 0L; var jbMoney = 0L; var zmMoney = 0L; var jjMoney = 0L
        var dayPay = 0L
        var workDays = 0
        var restDays = 0
        var restMinutes = 0
        var money = 0L

        inMonth.forEach { r ->
            zb += r.zbMinutes; jb += r.jbMinutes; zm += r.zmMinutes; jj += r.jjMinutes
            if (r.zbMinutes > 0) zbDays++
            if (r.jbMinutes > 0) jbDays++
            if (r.zmMinutes > 0) zmDays++
            if (r.jjMinutes > 0) jjDays++
            zbMoney += typeMoney(r, WorkType.ZB)
            jbMoney += typeMoney(r, WorkType.JB)
            zmMoney += typeMoney(r, WorkType.ZM)
            jjMoney += typeMoney(r, WorkType.JJ)

            if (r.kind == RecordKind.REST) {
                restDays++
                restMinutes += r.leaveMinutes
            } else if (r.totalMinutes > 0 || r.dayPayMinor > 0) {
                workDays++
            }

            if (r.kind == RecordKind.DAYPAY) {
                dayPay += r.dayPayMinor
                money += r.dayPayMinor
            } else {
                money += workMoney(r)
            }
            if (r.kind == RecordKind.REST && r.leavePaid) {
                money += pay(r.leaveMinutes, ratesFor(r.projectId, r.epochDay).zbMinor)
            }
        }

        val daySet = inMonth.map { it.epochDay }.toSet()
        val mine = pays.filter { it.epochDay in daySet }
        val bonus = mine.sumOf { p -> items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == PayKind.BONUS }?.let { p.amountMinor } ?: 0L }
        val deduct = mine.sumOf { p -> items.firstOrNull { it.id == p.itemId }?.takeIf { it.kind == PayKind.DEDUCT }?.let { p.amountMinor } ?: 0L }

        // 底薪：底薪+加班 一律计入；综合工时制「正班上满即为底薪」，这里同样计入一次
        val modes = inMonth.map { modeOf(it.projectId) }.toSet()
        val base = if (modes.any { it == SalaryMode.GDDX || it == SalaryMode.CSJB }) {
            inMonth.map { ratesFor(it.projectId, it.epochDay).baseMinor }.maxOrNull() ?: 0L
        } else 0L

        return MonthSummary(
            month = month,
            recordDays = inMonth.size,
            workDays = workDays,
            restDays = restDays,
            restMinutes = restMinutes,
            workMinutes = zb + jb + zm + jj,
            zbMinutes = zb,
            jbMinutes = jb,
            zmMinutes = zm,
            jjMinutes = jj,
            zbDays = zbDays,
            jbDays = jbDays,
            zmDays = zmDays,
            jjDays = jjDays,
            zbMoneyMinor = zbMoney,
            jbMoneyMinor = jbMoney,
            zmMoneyMinor = zmMoney,
            jjMoneyMinor = jjMoney,
            dayPayMinor = dayPay,
            workMoneyMinor = money,
            baseMoneyMinor = base,
            bonusMinor = bonus,
            deductMinor = deduct,
        )
    }

    /** 按班次分组：某月每个班次的天数与工时（休假如单独一支）。 */
    fun shiftBreakdown(month: YearMonth, shiftNames: Map<String, String>): List<ShiftStat> {
        val inMonth = records.filter {
            val d = LocalDate.ofEpochDay(it.epochDay)
            YearMonth.of(d.year, d.month) == month
        }
        val work = inMonth.filter { it.kind != RecordKind.REST }
            .groupBy { it.shiftId.ifBlank { "-" } }
            .map { (id, list) ->
                ShiftStat(
                    key = id,
                    name = shiftNames[id] ?: "未分班次",
                    days = list.size,
                    minutes = list.sumOf { it.totalMinutes },
                    isLeave = false,
                )
            }
        val rest = inMonth.filter { it.kind == RecordKind.REST }
        val leave = if (rest.isEmpty()) emptyList() else listOf(
            ShiftStat("leave", "休假", rest.size, rest.sumOf { it.leaveMinutes }, isLeave = true),
        )
        return work.sortedByDescending { it.minutes } + leave
    }
}

class WorkRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    val projects: Flow<List<Project>> = db.projectDao().all()
    val shifts: Flow<List<Shift>> = db.shiftDao().all()
    val payItems: Flow<List<PayItem>> = db.payItemDao().all()
    val goals: Flow<List<Goal>> = db.goalDao().all()
    private val versions: Flow<List<SalaryVersion>> = db.salaryDao().allVersions()

    fun monthStart(month: YearMonth): Long = month.atDay(1).toEpochDay()
    fun monthEnd(month: YearMonth): Long = month.atEndOfMonth().toEpochDay()

    /** 某个月的完整快照：记录、补扣、项目、薪资版本一起给出，UI 自己算汇总。 */
    fun snapshot(month: YearMonth): Flow<MonthSnapshot> {
        val from = monthStart(month)
        val to = monthEnd(month)
        return combine(
            db.recordDao().range(from, to),
            db.dayPayDao().range(from, to),
            db.payItemDao().all(),
            db.projectDao().all(),
            versions,
        ) { records, pays, items, projects, versions ->
            MonthSnapshot(records, pays, items, projects, versions)
        }
    }

    /** 一整年的快照：记录一次读完，UI/导出按 12 个月分别汇总。 */
    fun yearSnapshot(year: Int): Flow<MonthSnapshot> {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return combine(
            db.recordDao().range(from, to),
            db.dayPayDao().range(from, to),
            db.payItemDao().all(),
            db.projectDao().all(),
            versions,
        ) { records, pays, items, projects, versions ->
            MonthSnapshot(records, pays, items, projects, versions)
        }
    }

    fun record(day: Long): Flow<WorkRecord?> = db.recordDao().day(day)

    /** 某个项目的薪资版本链，最新在前。 */
    fun versions(projectId: String): Flow<List<SalaryVersion>> = db.salaryDao().versions(projectId)

    fun dayPays(day: Long): Flow<List<DayPay>> = db.dayPayDao().forDay(day)

    /** 记录页日历上的标记点：哪些天有记录。 */
    fun markedDays(month: YearMonth): Flow<Set<Long>> =
        db.recordDao().range(monthStart(month), monthEnd(month)).map { list -> list.map { it.epochDay }.toSet() }

    suspend fun saveRecord(record: WorkRecord) = db.withTransaction {
        db.recordDao().save(record.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRecord(day: Long) = db.withTransaction {
        db.dayPayDao().clearDay(day)
        db.recordDao().delete(day)
    }

    suspend fun deleteDayPay(day: Long, itemId: Long) = db.dayPayDao().deleteFor(day, itemId)

    suspend fun saveDayPay(pay: DayPay) = db.dayPayDao().save(pay)

    suspend fun projectsOnce(): List<Project> = db.projectDao().list()
    suspend fun shiftsOnce(): List<Shift> = db.shiftDao().list()
    suspend fun payItemsOnce(): List<PayItem> = db.payItemDao().list()
    suspend fun versionsOnce(projectId: String): List<SalaryVersion> = db.salaryDao().versionsOnce(projectId)

    suspend fun saveProject(project: Project) = db.projectDao().upsert(project)
    suspend fun deleteProject(id: String) = db.projectDao().delete(id)
    suspend fun renameProject(id: String, name: String) = db.projectDao().rename(id, name)

    suspend fun saveVersion(version: SalaryVersion) = db.salaryDao().insert(version)
    suspend fun deleteVersion(id: Long) = db.salaryDao().delete(id)

    suspend fun saveShift(shift: Shift) = db.shiftDao().upsert(shift)
    suspend fun savePayItem(item: PayItem) = db.payItemDao().upsert(item)
    suspend fun deletePayItem(id: Long) = db.payItemDao().delete(id)

    suspend fun saveGoal(goal: Goal) = db.goalDao().upsert(goal)
    suspend fun deleteGoal(id: Long) = db.goalDao().delete(id)
    suspend fun setGoalDone(id: Long, done: Boolean) = db.goalDao().setDone(id, done)

    /** 目标进度：区间内累计收入（含补贴、扣减补扣）。 */
    suspend fun goalProgress(goal: Goal): Long {
        val from = if (goal.startDay > 0) goal.startDay else 0L
        val to = if (goal.endDay > 0) goal.endDay else LocalDate.now().toEpochDay()
        val records = db.recordDao().rangeOnce(from, to)
        if (records.isEmpty()) return 0L
        val snap = MonthSnapshot(
            records = records,
            pays = db.dayPayDao().rangeOnce(from, to),
            items = db.payItemDao().list(),
            projects = db.projectDao().list(),
            versions = db.salaryDao().allVersionsOnce(),
        )
        var total = 0L
        records.forEach { r ->
            total += if (r.kind == RecordKind.DAYPAY) r.dayPayMinor else snap.workMoney(r)
            if (r.kind == RecordKind.REST && r.leavePaid) {
                total += (r.leaveMinutes.toDouble() * snap.ratesFor(r.projectId, r.epochDay).zbMinor / 60.0).roundToLong()
            }
        }
        total += snap.pays.sumOf { p ->
            val item = snap.items.firstOrNull { it.id == p.itemId } ?: return@sumOf 0L
            if (item.kind == PayKind.BONUS) p.amountMinor else -p.amountMinor
        }
        return total.coerceAtLeast(0L)
    }

    /** 清空全部记录与目标（项目、班次、薪资规则保留）。 */
    suspend fun resetEverything() {
        db.withTransaction {
            db.dayPayDao().clearAll()
            db.recordDao().clearAll()
            db.goalDao().clearAll()
        }
    }
}

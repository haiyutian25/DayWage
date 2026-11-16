package com.lhzkml.DayWage.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(
    entities = [
        Project::class,
        SalaryVersion::class,
        Shift::class,
        WorkRecord::class,
        PayItem::class,
        DayPay::class,
        Goal::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun salaryDao(): SalaryDao
    abstract fun shiftDao(): ShiftDao
    abstract fun recordDao(): RecordDao
    abstract fun payItemDao(): PayItemDao
    abstract fun dayPayDao(): DayPayDao
    abstract fun goalDao(): GoalDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gongshi.db")
                .addCallback(Seed)
                .build()

        /** 首次创建时写入默认项目、班次和常用补扣项目，让 App 打开就能记。 */
        private object Seed : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val today = LocalDate.now().toEpochDay()

                // 默认项目：小时计算，先在设置里改时薪
                db.execSQL(
                    "INSERT INTO projects (id, name, mode, position, archived) VALUES (?,?,?,?,?)",
                    arrayOf<Any>("default", "默认项目", SalaryMode.XSJS.name, 0, 0),
                )
                db.execSQL(
                    "INSERT INTO salary_versions (projectId, fromDay, baseMinor, zbRateMinor, jbRateMinor, zmRateMinor, jjRateMinor, monthHours) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                    arrayOf<Any>("default", today, 0L, 3000L, 3000L, 3000L, 9000L, 174),
                )

                // 四个默认班次
                val shifts: List<Array<Any>> = listOf(
                    arrayOf<Any>("1", "白班", "briefcase", 0xFFFCB819.toInt(), 0),
                    arrayOf<Any>("2", "早班", "coffee", 0xFF33A0FF.toInt(), 1),
                    arrayOf<Any>("3", "中班", "utensils", 0xFFFF9900.toInt(), 2),
                    arrayOf<Any>("4", "晚班", "house", 0xFF224799.toInt(), 3),
                )
                shifts.forEach { s ->
                    db.execSQL(
                        "INSERT INTO shifts (id, name, iconKey, color, position) VALUES (?,?,?,?,?)",
                        arrayOf(s[0], s[1], s[2], s[3], s[4]),
                    )
                }

                // 常用补贴
                val bonuses = listOf("餐补" to "utensils", "夜补" to "coffee", "住补" to "house", "绩效" to "trending-up",
                    "奖金" to "gift", "全勤奖" to "shield", "工龄补贴" to "briefcase", "交通补贴" to "bus", "其它项目" to "package")
                bonuses.forEachIndexed { i, (name, icon) ->
                    db.execSQL(
                        "INSERT INTO pay_items (name, kind, type, amountMinor, unitMinor, iconKey, position) VALUES (?,?,?,?,?,?,?)",
                        arrayOf<Any>(name, PayKind.BONUS.name, PayType.FIXED.name, 0L, 0L, icon, i),
                    )
                }
                // 常用扣款
                val deducts = listOf("餐费" to "utensils", "罚款" to "receipt", "借款" to "hand-coins", "押金" to "credit-card",
                    "房租" to "house", "个税" to "receipt", "社保" to "shield", "公积金" to "piggy-bank", "水电费" to "zap")
                deducts.forEachIndexed { i, (name, icon) ->
                    db.execSQL(
                        "INSERT INTO pay_items (name, kind, type, amountMinor, unitMinor, iconKey, position) VALUES (?,?,?,?,?,?,?)",
                        arrayOf<Any>(name, PayKind.DEDUCT.name, PayType.FIXED.name, 0L, 0L, icon, i),
                    )
                }
            }
        }
    }
}

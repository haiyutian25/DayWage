package com.lhzkml.DayWage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 全部本地设置。记账相关的字段已随业务替换为工时相关的字段。 */
data class AppSettings(
    /** 跟随系统深色 */
    val followSystemTheme: Boolean = true,
    val darkTheme: Boolean = false,
    val dynamicColor: Boolean = false,
    val glassEffects: Boolean = true,
    val pureBlack: Boolean = false,
    /** 主题强调色下标，-1 表示默认（青柠） */
    val accentIndex: Int = -1,
    /** 日历从周一开始 */
    val weekStartMonday: Boolean = true,
    /** 考勤周期起始日（几号） */
    val periodStartDay: Int = 1,
    /** 打卡方式：true 选时长，false 选起止时间 */
    val recordByHour: Boolean = true,
    /** 默认正班 / 加班 / 周末 / 节假时长（分钟） */
    val defaultZbMinutes: Int = 480,
    val defaultJbMinutes: Int = 120,
    val defaultZmMinutes: Int = 0,
    val defaultJjMinutes: Int = 0,
    /** 当前项目 */
    val currentProjectId: String = "default",
    /** 是否已完成首次引导 */
    val onboarded: Boolean = false,
    /** 记工时后自动带上每日补扣 */
    val autoDayPay: Boolean = false,
    /** 夜班模式：默认选中昨天 */
    val nightShift: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gongshi_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val followSystem = booleanPreferencesKey("follow_system")
        val dark = booleanPreferencesKey("dark")
        val dynamic = booleanPreferencesKey("dynamic")
        val glass = booleanPreferencesKey("glass")
        val pureBlack = booleanPreferencesKey("pure_black")
        val accent = intPreferencesKey("accent")
        val weekMonday = booleanPreferencesKey("week_monday")
        val period = intPreferencesKey("period")
        val byHour = booleanPreferencesKey("by_hour")
        val zb = intPreferencesKey("default_zb")
        val jb = intPreferencesKey("default_jb")
        val zm = intPreferencesKey("default_zm")
        val jj = intPreferencesKey("default_jj")
        val project = stringPreferencesKey("current_project")
        val onboarded = booleanPreferencesKey("onboarded")
        val autoDayPay = booleanPreferencesKey("auto_day_pay")
        val night = booleanPreferencesKey("night_shift")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            followSystemTheme = p[Keys.followSystem] ?: true,
            darkTheme = p[Keys.dark] ?: false,
            dynamicColor = p[Keys.dynamic] ?: false,
            glassEffects = p[Keys.glass] ?: true,
            pureBlack = p[Keys.pureBlack] ?: false,
            accentIndex = p[Keys.accent] ?: -1,
            weekStartMonday = p[Keys.weekMonday] ?: true,
            periodStartDay = p[Keys.period] ?: 1,
            recordByHour = p[Keys.byHour] ?: true,
            defaultZbMinutes = p[Keys.zb] ?: 480,
            defaultJbMinutes = p[Keys.jb] ?: 120,
            defaultZmMinutes = p[Keys.zm] ?: 0,
            defaultJjMinutes = p[Keys.jj] ?: 0,
            currentProjectId = p[Keys.project] ?: "default",
            onboarded = p[Keys.onboarded] ?: false,
            autoDayPay = p[Keys.autoDayPay] ?: false,
            nightShift = p[Keys.night] ?: false,
        )
    }

    suspend fun setFollowSystem(v: Boolean) = context.dataStore.edit { it[Keys.followSystem] = v }
    suspend fun setDark(v: Boolean) = context.dataStore.edit { it[Keys.dark] = v }
    suspend fun setDynamic(v: Boolean) = context.dataStore.edit { it[Keys.dynamic] = v }
    suspend fun setGlass(v: Boolean) = context.dataStore.edit { it[Keys.glass] = v }
    suspend fun setPureBlack(v: Boolean) = context.dataStore.edit { it[Keys.pureBlack] = v }
    suspend fun setAccent(v: Int) = context.dataStore.edit { it[Keys.accent] = v }
    suspend fun setWeekMonday(v: Boolean) = context.dataStore.edit { it[Keys.weekMonday] = v }
    suspend fun setPeriod(v: Int) = context.dataStore.edit { it[Keys.period] = v.coerceIn(1, 28) }
    suspend fun setRecordByHour(v: Boolean) = context.dataStore.edit { it[Keys.byHour] = v }
    suspend fun setDefaults(zb: Int, jb: Int, zm: Int, jj: Int) = context.dataStore.edit {
        it[Keys.zb] = zb; it[Keys.jb] = jb; it[Keys.zm] = zm; it[Keys.jj] = jj
    }
    suspend fun setProject(id: String) = context.dataStore.edit { it[Keys.project] = id }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.onboarded] = v }
    suspend fun setAutoDayPay(v: Boolean) = context.dataStore.edit { it[Keys.autoDayPay] = v }
    suspend fun setNightShift(v: Boolean) = context.dataStore.edit { it[Keys.night] = v }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}

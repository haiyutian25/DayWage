package com.lhzkml.DayWage

import android.app.Application
import android.content.Context
import com.lhzkml.DayWage.data.AppDatabase
import com.lhzkml.DayWage.data.SettingsRepository
import com.lhzkml.DayWage.data.WorkRepository

class DayWageApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 手工依赖容器：数据库、设置、仓库三件套，够用且没有额外框架。 */
class AppContainer(private val context: Context) {
    val appContext: Context get() = context

    private val database = AppDatabase.build(context)

    val settings = SettingsRepository(context)
    val repository = WorkRepository(database, settings)

    /** 清空全部记录，设置与项目保留。 */
    suspend fun resetRecords() {
        repository.resetEverything()
    }
}

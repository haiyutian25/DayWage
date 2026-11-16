package com.lhzkml.DayWage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhzkml.DayWage.data.AppSettings
import com.lhzkml.DayWage.ui.DayWageNavHost
import com.lhzkml.DayWage.ui.components.AuroraBackground
import com.lhzkml.DayWage.ui.components.LocalHazeState
import com.lhzkml.DayWage.ui.theme.DayWageTheme
import com.lhzkml.DayWage.ui.theme.accentChoices
import com.lhzkml.DayWage.util.LocalMoney
import com.lhzkml.DayWage.util.Money
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * 唯一的 Activity：负责主题、极光背景和导航壳。
 * 与原来的记账 App 一致——屏幕本身透明，内容在磨砂标题栏下滚动。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DayWageApp).container
        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkTheme
            val accent = settings.accentIndex
                .takeIf { it in accentChoices.indices }
                ?.let { accentChoices[it].second }
            DayWageTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                glassEffects = settings.glassEffects,
                pureBlack = settings.pureBlack,
                accent = accent,
            ) {
                val haze = remember { HazeState() }
                CompositionLocalProvider(
                    LocalHazeState provides haze,
                    LocalMoney provides remember { Money("CNY") },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AuroraBackground(Modifier.hazeSource(haze))
                        DayWageNavHost()
                    }
                }
            }
        }
    }
}

package com.lhzkml.DayWage.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.LocalNavAnimatedScope
import com.lhzkml.DayWage.ui.components.LocalSharedTransitionScope
import com.lhzkml.DayWage.ui.components.drawAurora
import com.lhzkml.DayWage.ui.components.frosted
import com.lhzkml.DayWage.ui.components.glassBorder
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.screens.BatchScreen
import com.lhzkml.DayWage.ui.screens.EntryScreen
import com.lhzkml.DayWage.ui.screens.GoalsScreen
import com.lhzkml.DayWage.ui.screens.MineScreen
import com.lhzkml.DayWage.ui.screens.RecordScreen
import com.lhzkml.DayWage.ui.screens.ReportScreen
import com.lhzkml.DayWage.ui.screens.SalaryScreen
import com.lhzkml.DayWage.ui.screens.StatsScreen
import com.lhzkml.DayWage.ui.screens.YearStatsScreen
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import java.time.LocalDate

object Routes {
    const val RECORD = "record"
    const val STATS = "stats"
    const val GOALS = "goals"
    const val MINE = "mine"
    const val ENTRY = "entry?day={day}"
    const val SALARY = "salary"
    const val BATCH = "batch"
    const val REPORT = "report"
    const val YEAR = "year"

    fun entry(day: Long = LocalDate.now().toEpochDay()) = "entry?day=$day"

    /** 从左到右的 Tab 顺序；左右滑切换邻位。 */
    val tabOrder = listOf(RECORD, STATS, GOALS, MINE)
    val tabs = tabOrder.toSet()
}

private data class Tab(val route: String, val icon: ImageVector, val label: String)

private val tabs = listOf(
    Tab(Routes.RECORD, Lucide.Calendar, "记录"),
    Tab(Routes.STATS, Lucide.ChartPie, "统计"),
    Tab(Routes.GOALS, Lucide.Target, "目标"),
    Tab(Routes.MINE, Lucide.Settings, "我的"),
)

/** 固定时长缓动，滑动返回时可以跟着手指走。 */
private val navTween = tween<IntOffset>(340, easing = FastOutSlowInEasing)

private fun NavBackStackEntry.route() = destination.route.orEmpty()
private fun NavBackStackEntry.isTab() = route() in Routes.tabs
private fun NavBackStackEntry.isEntry() = route().startsWith("entry")

private fun forward(from: NavBackStackEntry, to: NavBackStackEntry) =
    Routes.tabOrder.indexOf(to.route()) > Routes.tabOrder.indexOf(from.route())

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DayWageNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    var toast by remember { mutableStateOf<String?>(null) }
    val colors = LocalAppColors.current
    val liquid = android.os.Build.VERSION.SDK_INT >= 33 && colors.glass.alpha < 0.9f
    val onDrawBackdrop = remember(colors) {
        { scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope -> scope.drawAurora(colors); scope.drawContent() }
    }
    val backdrop = rememberLayerBackdrop(onDraw = onDrawBackdrop)

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            com.lhzkml.DayWage.ui.components.LocalScreenBackdrop provides if (liquid) backdrop else null,
        ) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.RECORD,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier)
                        .pointerInput(currentRoute) {
                            val index = Routes.tabOrder.indexOf(currentRoute)
                            if (index < 0) return@pointerInput
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = {
                                    val next = when {
                                        dx < -swipeThreshold -> index + 1
                                        dx > swipeThreshold -> index - 1
                                        else -> index
                                    }
                                    if (next != index && next in Routes.tabOrder.indices) nav.switchTab(Routes.tabOrder[next])
                                },
                            ) { _, amount -> dx += amount }
                        },
                    enterTransition = {
                        when {
                            targetState.isEntry() -> slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(220))
                            targetState.isTab() && initialState.isTab() -> if (forward(initialState, targetState)) {
                                slideIntoContainer(SlideDirection.Start, navTween) + fadeIn(tween(220))
                            } else {
                                slideIntoContainer(SlideDirection.End, navTween) { it / 4 } + fadeIn(tween(220))
                            }
                            else -> slideIntoContainer(SlideDirection.Start, navTween) + fadeIn(tween(220))
                        }
                    },
                    exitTransition = {
                        when {
                            targetState.isEntry() -> fadeOut(tween(220))
                            targetState.isTab() && initialState.isTab() -> if (forward(initialState, targetState)) {
                                slideOutOfContainer(SlideDirection.Start, navTween) { it / 4 } + fadeOut(tween(220))
                            } else {
                                slideOutOfContainer(SlideDirection.End, navTween) + fadeOut(tween(220))
                            }
                            else -> slideOutOfContainer(SlideDirection.Start, navTween) { it / 4 } + fadeOut(tween(220))
                        }
                    },
                    popEnterTransition = {
                        when {
                            initialState.isEntry() -> fadeIn(tween(220))
                            targetState.isTab() && initialState.isTab() && forward(initialState, targetState) ->
                                slideIntoContainer(SlideDirection.Start, navTween) + fadeIn(tween(220))
                            else -> slideIntoContainer(SlideDirection.End, navTween) { it / 4 } + fadeIn(tween(220))
                        }
                    },
                    popExitTransition = {
                        when {
                            initialState.isEntry() -> slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(200))
                            targetState.isTab() && initialState.isTab() && forward(initialState, targetState) ->
                                slideOutOfContainer(SlideDirection.Start, navTween) { it / 4 } + fadeOut(tween(220))
                            else -> slideOutOfContainer(SlideDirection.End, navTween) + fadeOut(tween(220))
                        }
                    },
                ) {
                    screen(Routes.RECORD) {
                        RecordScreen(
                            onOpenEntry = { day -> nav.navigate(Routes.entry(day)) },
                            onOpenBatch = { nav.navigate(Routes.BATCH) },
                        )
                    }
                    screen(Routes.STATS) {
                        StatsScreen(
                            onOpenEntry = { day -> nav.navigate(Routes.entry(day)) },
                            onOpenReport = { nav.navigate(Routes.REPORT) },
                            onOpenYear = { nav.navigate(Routes.YEAR) },
                        )
                    }
                    screen(Routes.GOALS) {
                        GoalsScreen()
                    }
                    screen(Routes.MINE) {
                        MineScreen(
                            onOpenSalary = { nav.navigate(Routes.SALARY) },
                            onToast = { toast = it },
                        )
                    }
                    screen(
                        Routes.ENTRY,
                        listOf(navArgument("day") { type = NavType.LongType; defaultValue = LocalDate.now().toEpochDay() }),
                    ) { entry ->
                        EntryScreen(
                            day = entry.arguments?.getLong("day") ?: LocalDate.now().toEpochDay(),
                            onClose = back,
                            onSaved = { message ->
                                toast = message
                                back()
                            },
                            onDeleted = {
                                toast = "已删除当天记录"
                                back()
                            },
                        )
                    }
                    screen(Routes.SALARY) {
                        SalaryScreen(onBack = back, onToast = { toast = it })
                    }
                    screen(Routes.BATCH) {
                        BatchScreen(onBack = back, onToast = { toast = it })
                    }
                    screen(Routes.REPORT) {
                        ReportScreen(onBack = back, onToast = { toast = it })
                    }
                    screen(Routes.YEAR) {
                        YearStatsScreen(onBack = back, onToast = { toast = it })
                    }
                }

                AnimatedVisibility(
                    visible = currentRoute in Routes.tabs,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
                    exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        FloatingDock(
                            backdrop = if (liquid) backdrop else null,
                            current = currentRoute,
                            onTab = { nav.switchTab(it) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = toast != null,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
                    exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    Toast(toast.orEmpty())
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    val detour = currentBackStack.value.any { e -> e.destination.route.let { it != null && it !in Routes.tabs && it != graph.route } }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = !detour }
        launchSingleTop = true
        restoreState = !detour
    }
}

/** 悬浮磨砂 Tab 栏：图标 + 文字，中间是打卡按钮。 */
@Composable
private fun FloatingDock(backdrop: LayerBackdrop?, current: String?, onTab: (String) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(6.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), chromaticAberration = true)
                        },
                        onDrawSurface = {
                            drawRect(if (c.isDark) Color(0xFF0E0E13).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.32f))
                        },
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .frosted(if (c.isDark) c else c.copy(barTint = Color.White.copy(alpha = 0.18f)))
                        .background(Brush.verticalGradient(listOf(c.glassStrong, c.glass.copy(alpha = c.glass.alpha * 0.4f))))
                        .border(1.dp, glassBorder(c), CircleShape)
                },
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEach { DockItem(it, current == it.route) { onTab(it.route) } }
    }
}

/** Dock 每一项固定宽度，图标下面带文字，避免选中态被挤压成椭圆。 */
private val DockItemWidth = 58.dp

@Composable
private fun DockItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(
        if (selected) (if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)) else Color.Transparent,
        label = "dockBg",
    )
    val tint by animateColorAsState(if (selected) c.textPrimary else c.textTertiary, label = "dockTint")
    val labelColor by animateColorAsState(if (selected) c.textPrimary else c.textTertiary, label = "dockLabel")

    Column(
        Modifier
            .width(DockItemWidth)
            .pressable(onClick, pressedScale = 0.92f)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Toast(text: String) {
    val c = LocalAppColors.current
    Glass(shape = CircleShape, strong = true, modifier = Modifier.clip(CircleShape).frosted()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleCheck, contentDescription = null, tint = c.positive, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
        }
    }
}

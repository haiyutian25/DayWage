package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.Goal
import com.lhzkml.DayWage.data.GoalKind
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.DockClearance
import com.lhzkml.DayWage.ui.components.EmptyState
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.ProgressLine
import com.lhzkml.DayWage.ui.components.SegmentedToggle
import com.lhzkml.DayWage.ui.components.appear
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.AmountInput
import com.lhzkml.DayWage.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalsViewModel(private val repo: WorkRepository) : ViewModel() {
    val goals: StateFlow<List<Goal>> = repo.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _progress = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val progress: StateFlow<Map<Long, Long>> = _progress

    init {
        viewModelScope.launch {
            goals.collect { list ->
                _progress.value = list.associate { it.id to repo.goalProgress(it) }
            }
        }
    }

    fun create(title: String, amountText: String, moneyMinor: Long, onDone: () -> Unit) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.saveGoal(
                Goal(
                    title = title,
                    kind = GoalKind.MONEY,
                    targetMinor = moneyMinor,
                    startDay = LocalDate.now().withDayOfMonth(1).toEpochDay(),
                    endDay = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toEpochDay(),
                ),
            )
            onDone()
        }
    }

    fun toggleDone(goal: Goal) {
        viewModelScope.launch { repo.setGoalDone(goal.id, !goal.done) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteGoal(id) }
    }
}

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val c = LocalAppColors.current
    val goals by vm.goals.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val money = Money("CNY")
    var showDone by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("目标", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                Text("给这个月定个收入目标", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            com.lhzkml.DayWage.ui.components.RoundButton(Lucide.Plus, "新建目标", { creating = !creating })
        }

        Spacer(Modifier.height(16.dp))
        SegmentedToggle(
            options = listOf(false, true),
            selected = showDone,
            label = { if (it) "已完成" else "待完成" },
            onSelect = { showDone = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (creating) {
            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth().appear(0), strong = true) {
                Column(Modifier.padding(16.dp)) {
                    Text("新建金额目标", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    GlassField(title, { title = it }, placeholder = "目标标题，例如：这个月存够 8000")
                    Spacer(Modifier.height(8.dp))
                    GlassField(amount, { raw -> AmountInput.sanitize(raw)?.let { amount = it } }, placeholder = "目标金额", keyboardType = KeyboardType.Decimal)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.lhzkml.DayWage.ui.components.SecondaryButton("取消", { creating = false }, Modifier.weight(1f))
                        PrimaryButton(
                            "创建",
                            {
                                vm.create(title, amount, AmountInput.toMinor(amount) ?: 0L) {
                                    creating = false; title = ""; amount = ""
                                }
                            },
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val shown = goals.filter { it.done == showDone }
        if (shown.isEmpty()) {
            EmptyState(
                Lucide.Target,
                if (showDone) "还没有完成的目标" else "有目标才有动力",
                if (showDone) "完成的目标会收在这里" else "定一个金额目标，收入进度会跟着工时自动更新",
            )
        } else {
            shown.forEachIndexed { i, goal ->
                val saved = progress[goal.id] ?: 0L
                val fraction = if (goal.targetMinor > 0) (saved.toFloat() / goal.targetMinor).coerceIn(0f, 1f) else 0f
                Glass(Modifier.fillMaxWidth().appear(i), onClick = { vm.toggleDone(goal) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(goal.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
                                Text(
                                    "已赚 ${money.format(saved)} / 目标 ${money.format(goal.targetMinor)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            Icon(
                                if (goal.done) Lucide.CircleCheck else Lucide.Target,
                                contentDescription = null,
                                tint = if (goal.done) c.positive else c.textTertiary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        ProgressLine(fraction)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (goal.done) "已完成" else "还差 ${money.format((goal.targetMinor - saved).coerceAtLeast(0))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "删除",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.negative,
                                modifier = Modifier.pressable({ vm.delete(goal.id) }),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(DockClearance))
    }
}

package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.Project
import com.lhzkml.DayWage.data.SalaryMode
import com.lhzkml.DayWage.data.SalaryVersion
import com.lhzkml.DayWage.data.SettingsRepository
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.Chip
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.GlassScreen
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.SectionHeader
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.AmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

class SalaryViewModel(
    private val repo: WorkRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val projects: StateFlow<List<Project>> = repo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _projectId = MutableStateFlow("default")
    val projectId: StateFlow<String> = _projectId

    val versions: StateFlow<List<SalaryVersion>> = _projectId
        .flatMapLatest { repo.versions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var mode by mutableStateOf(SalaryMode.XSJS)
    var baseText by mutableStateOf("")
    var zbText by mutableStateOf("")
    var jbText by mutableStateOf("")
    var zmText by mutableStateOf("")
    var jjText by mutableStateOf("")
    var monthHoursText by mutableStateOf("174")
    var newProjectName by mutableStateOf("")

    fun select(id: String) {
        _projectId.value = id
        viewModelScope.launch {
            val project = projects.value.firstOrNull { it.id == id }
            mode = project?.mode ?: SalaryMode.XSJS
            val latest = repo.versionsOnce(id).maxByOrNull { it.fromDay }
            if (latest != null) {
                baseText = AmountInput.fromMinor(latest.baseMinor)
                zbText = AmountInput.fromMinor(latest.zbRateMinor)
                jbText = AmountInput.fromMinor(latest.jbRateMinor)
                zmText = AmountInput.fromMinor(latest.zmRateMinor)
                jjText = AmountInput.fromMinor(latest.jjRateMinor)
                monthHoursText = latest.monthHours.toString()
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val id = _projectId.value
        viewModelScope.launch {
            val project = projects.value.firstOrNull { it.id == id }
            if (project != null && project.mode != mode) repo.saveProject(project.copy(mode = mode))
            repo.saveVersion(
                SalaryVersion(
                    projectId = id,
                    fromDay = LocalDate.now().toEpochDay(),
                    baseMinor = AmountInput.toMinor(baseText) ?: 0L,
                    zbRateMinor = AmountInput.toMinor(zbText) ?: 0L,
                    jbRateMinor = AmountInput.toMinor(jbText) ?: 0L,
                    zmRateMinor = AmountInput.toMinor(zmText) ?: 0L,
                    jjRateMinor = AmountInput.toMinor(jjText) ?: 0L,
                    monthHours = (monthHoursText.toIntOrNull() ?: 174).coerceIn(1, 400),
                ),
            )
            settings.setProject(id)
            // 小时计算模式下三个时薪统一，保存后回填保持一致
            if (mode == SalaryMode.XSJS) {
                jbText = zbText
                zmText = zbText
            }
            onDone()
        }
    }

    fun createProject(onDone: () -> Unit) {
        val name = newProjectName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = "p" + System.currentTimeMillis().toString().takeLast(6)
            repo.saveProject(Project(id = id, name = name, mode = SalaryMode.XSJS, position = projects.value.size))
            repo.saveVersion(
                SalaryVersion(
                    projectId = id,
                    fromDay = LocalDate.now().toEpochDay(),
                    zbRateMinor = 3000, jbRateMinor = 3000, zmRateMinor = 3000, jjRateMinor = 9000,
                    monthHours = 174,
                ),
            )
            newProjectName = ""
            onDone()
            select(id)
        }
    }
}

@Composable
fun SalaryScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    vm: SalaryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val projects by vm.projects.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val currentId by vm.projectId.collectAsStateWithLifecycle()

    LaunchedEffect(projects) {
        if (projects.isNotEmpty() && projects.none { it.id == currentId }) vm.select(projects.first().id)
        else if (versions.isEmpty() && currentId.isNotBlank()) vm.select(currentId)
    }

    val latest = versions.maxByOrNull { it.fromDay }

    GlassScreen(
        title = "薪资设置",
        onBack = onBack,
        subtitle = if (latest != null) "最近生效：${LocalDate.ofEpochDay(latest.fromDay).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}" else "设置计薪方式与时薪",
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("项目")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects.size) { i ->
                    val p = projects[i]
                    Chip(p.name, { vm.select(p.id) }, selected = p.id == currentId)
                }
            }

            Spacer(Modifier.height(8.dp))
            GlassField(vm.newProjectName, { vm.newProjectName = it }, placeholder = "新项目名称，例如：新工厂")
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                "新增项目",
                { vm.createProject { onToast("已新增项目") } },
                Modifier.fillMaxWidth(),
                icon = Lucide.Plus,
            )

            SectionHeader("计薪方式")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SalaryMode.entries.forEach { m ->
                    Glass(
                        Modifier.fillMaxWidth(),
                        strong = vm.mode == m,
                        onClick = { vm.mode = m },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                Text(m.short, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            Text(
                                if (vm.mode == m) "已选" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.accent,
                            )
                        }
                    }
                }
            }

            SectionHeader("时薪（元 / 小时）")
            if (vm.mode == SalaryMode.XSJS) {
                GlassField(vm.zbText, { AmountInput.sanitize(it)?.let { v -> vm.zbText = v } }, placeholder = "日常时薪", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(8.dp))
                GlassField(vm.jjText, { AmountInput.sanitize(it)?.let { v -> vm.jjText = v } }, placeholder = "节假时薪（通常是日常的 3 倍）", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(6.dp))
                Text(
                    "小时计算：正班、加班、周末统一按日常时薪，只有节假单独计价。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        GlassField(vm.zbText, { AmountInput.sanitize(it)?.let { v -> vm.zbText = v } }, placeholder = "正班", keyboardType = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        GlassField(vm.jbText, { AmountInput.sanitize(it)?.let { v -> vm.jbText = v } }, placeholder = "加班", keyboardType = KeyboardType.Decimal)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        GlassField(vm.zmText, { AmountInput.sanitize(it)?.let { v -> vm.zmText = v } }, placeholder = "周末", keyboardType = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        GlassField(vm.jjText, { AmountInput.sanitize(it)?.let { v -> vm.jjText = v } }, placeholder = "节假", keyboardType = KeyboardType.Decimal)
                    }
                }
            }

            if (vm.mode == SalaryMode.GDDX || vm.mode == SalaryMode.CSJB) {
                SectionHeader("底薪")
                GlassField(vm.baseText, { AmountInput.sanitize(it)?.let { v -> vm.baseText = v } }, placeholder = "月底薪（元）", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(8.dp))
                GlassField(
                    vm.monthHoursText,
                    { raw -> if (raw.all { it.isDigit() } && raw.length <= 4) vm.monthHoursText = raw },
                    placeholder = "每月标准正班工时（默认 174）",
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (vm.mode == SalaryMode.GDDX) "底薪 + 加班：正班不用记，月工资 = 底薪 + 加班费。"
                    else "综合工时制：正班上满标准工时即为底薪，超出部分按加班时薪。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "保存薪资规则",
                {
                    vm.save { onToast("已保存，新规则从今天起生效") }
                },
                Modifier.fillMaxWidth(),
                icon = Lucide.Check,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "修改后从今天生效，之前的记录仍按当时的薪资规则计算。",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

package com.lhzkml.DayWage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhzkml.DayWage.data.AppSettings
import com.lhzkml.DayWage.data.DayPay
import com.lhzkml.DayWage.data.LeaveType
import com.lhzkml.DayWage.data.MonthSnapshot
import com.lhzkml.DayWage.data.PayItem
import com.lhzkml.DayWage.data.PayKind
import com.lhzkml.DayWage.data.Project
import com.lhzkml.DayWage.data.RecordKind
import com.lhzkml.DayWage.data.SettingsRepository
import com.lhzkml.DayWage.data.Shift
import com.lhzkml.DayWage.data.WorkRecord
import com.lhzkml.DayWage.data.WorkRepository
import com.lhzkml.DayWage.ui.AppViewModelProvider
import com.lhzkml.DayWage.ui.components.Chip
import com.lhzkml.DayWage.ui.components.Glass
import com.lhzkml.DayWage.ui.components.GlassDialog
import com.lhzkml.DayWage.ui.components.GlassScreen
import com.lhzkml.DayWage.ui.components.PrimaryButton
import com.lhzkml.DayWage.ui.components.SecondaryButton
import com.lhzkml.DayWage.ui.components.SegmentedToggle
import com.lhzkml.DayWage.ui.components.SectionHeader
import com.lhzkml.DayWage.ui.components.Skeleton
import com.lhzkml.DayWage.ui.components.pressable
import com.lhzkml.DayWage.ui.icons.Lucide
import com.lhzkml.DayWage.ui.theme.LocalAppColors
import com.lhzkml.DayWage.util.AmountInput
import com.lhzkml.DayWage.util.formatHours
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class EntryTab(val label: String) { WORK("工时"), LEAVE("休假"), DAYPAY("日结") }

class EntryViewModel(
    private val repo: WorkRepository,
    settings: SettingsRepository,
) : ViewModel() {
    var day by mutableStateOf(LocalDate.now().toEpochDay())
        private set
    var projectId by mutableStateOf("default")
        private set
    var shiftId by mutableStateOf("1")
    var tab by mutableStateOf(EntryTab.WORK)
    var zbMinutes by mutableStateOf(480)
    var jbMinutes by mutableStateOf(120)
    var zmMinutes by mutableStateOf(0)
    var jjMinutes by mutableStateOf(0)
    var leaveType by mutableStateOf(LeaveType.XIU)
    var leaveMinutes by mutableStateOf(480)
    var dayPayText by mutableStateOf("")
    var note by mutableStateOf("")
    var loaded by mutableStateOf(false)
        private set
    /** true = 还在读当天的记录，界面铺骨架，别先画默认的 8H/2H */
    var loading by mutableStateOf(true)
        private set

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val projects: StateFlow<List<Project>> = repo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shifts: StateFlow<List<Shift>> = repo.shifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val payItems: StateFlow<List<PayItem>> = repo.payItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当天的时薪，随项目和薪资版本变化 */
    var rates by mutableStateOf(com.lhzkml.DayWage.data.Rates())
        private set

    var dayPays by mutableStateOf<List<DayPay>>(emptyList())
        private set

    private var snapshot: MonthSnapshot = MonthSnapshot()

    fun load(target: Long) {
        if (loaded && day == target) return
        day = target
        loading = true
        val now = appSettings.value
        projectId = now.currentProjectId
        zbMinutes = now.defaultZbMinutes
        jbMinutes = now.defaultJbMinutes
        zmMinutes = now.defaultZmMinutes
        jjMinutes = now.defaultJjMinutes

        viewModelScope.launch {
            repo.shiftsOnce().firstOrNull()?.let { shiftId = it.id }
            repo.record(target).collect { rec ->
                if (rec != null) {
                    projectId = rec.projectId
                    if (rec.shiftId.isNotBlank()) shiftId = rec.shiftId
                    tab = when (rec.kind) {
                        RecordKind.REST -> EntryTab.LEAVE
                        RecordKind.DAYPAY -> EntryTab.DAYPAY
                        else -> EntryTab.WORK
                    }
                    zbMinutes = rec.zbMinutes
                    jbMinutes = rec.jbMinutes
                    zmMinutes = rec.zmMinutes
                    jjMinutes = rec.jjMinutes
                    rec.leaveType?.let { leaveType = it }
                    leaveMinutes = rec.leaveMinutes
                    dayPayText = if (rec.dayPayMinor > 0) AmountInput.fromMinor(rec.dayPayMinor) else ""
                    note = rec.note
                }
                loaded = true
                loading = false
            }
        }
        viewModelScope.launch {
            repo.dayPays(target).collect { dayPays = it }
        }
        viewModelScope.launch {
            repo.snapshot(YearMonth.from(LocalDate.ofEpochDay(target))).collect { snap ->
                snapshot = snap
                rates = snap.ratesFor(projectId, target)
            }
        }
    }

    /** 切换项目后立刻刷新时薪。 */
    fun selectProject(id: String) {
        projectId = id
        rates = snapshot.ratesFor(id, day)
    }

    fun save(onSaved: (String) -> Unit) {
        val record = WorkRecord(
            epochDay = day,
            projectId = projectId,
            shiftId = shiftId,
            kind = when (tab) {
                EntryTab.WORK -> RecordKind.WORK
                EntryTab.LEAVE -> RecordKind.REST
                EntryTab.DAYPAY -> RecordKind.DAYPAY
            },
            zbMinutes = if (tab == EntryTab.WORK) zbMinutes else 0,
            jbMinutes = if (tab == EntryTab.WORK) jbMinutes else 0,
            zmMinutes = if (tab == EntryTab.WORK) zmMinutes else 0,
            jjMinutes = if (tab == EntryTab.WORK) jjMinutes else 0,
            dayPayMinor = if (tab == EntryTab.DAYPAY) (AmountInput.toMinor(dayPayText) ?: 0L) else 0L,
            leaveType = if (tab == EntryTab.LEAVE) leaveType else null,
            leaveMinutes = if (tab == EntryTab.LEAVE) leaveMinutes else 0,
            note = note,
        )
        viewModelScope.launch {
            repo.saveRecord(record)
            onSaved("已保存")
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteRecord(day)
            onDeleted()
        }
    }

    /** 点一下补贴/扣款：已挂上就取消，没挂上就按默认金额挂上。 */
    fun togglePay(item: PayItem) {
        viewModelScope.launch {
            if (dayPays.any { it.itemId == item.id }) {
                repo.deleteDayPay(day, item.id)
            } else {
                val amount = if (item.amountMinor > 0) item.amountMinor else item.unitMinor
                repo.saveDayPay(DayPay(epochDay = day, itemId = item.id, amountMinor = amount, count = 1))
            }
        }
    }

    /** 手动指定某天某个补扣项的金额。 */
    fun setPayAmount(item: PayItem, amountMinor: Long) {
        viewModelScope.launch {
            if (amountMinor <= 0) repo.deleteDayPay(day, item.id)
            else repo.saveDayPay(DayPay(epochDay = day, itemId = item.id, amountMinor = amountMinor, count = 1))
        }
    }

}

@Composable
fun EntryScreen(
    day: Long,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    vm: EntryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    LaunchedEffect(day) { vm.load(day) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val payItems by vm.payItems.collectAsStateWithLifecycle()
    val date = LocalDate.ofEpochDay(day)
    val money = com.lhzkml.DayWage.util.Money("CNY")
    val rates = vm.rates
    var editing by remember { mutableStateOf<PayItem?>(null) }
    var editAmount by remember { mutableStateOf("") }

    // 输入补贴/扣款金额
    editing?.let { item ->
        GlassDialog(
            title = "${item.name}金额",
            onDismiss = { editing = null },
            confirmEnabled = AmountInput.toMinor(editAmount) != null,
            onConfirm = {
                AmountInput.toMinor(editAmount)?.let { vm.setPayAmount(item, it) }
                editing = null
            },
        ) {
            GlassField(
                editAmount,
                { raw -> AmountInput.sanitize(raw)?.let { editAmount = it } },
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
            )
        }
    }

    GlassScreen(
        title = "打卡",
        onBack = onClose,
        subtitle = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")),
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("取消", onClose, Modifier.weight(1f))
                PrimaryButton(
                    text = "保存记录",
                    onClick = { vm.save(onSaved) },
                    modifier = Modifier.weight(1.4f),
                    icon = Lucide.Check,
                    enabled = !vm.loading,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 读当天的记录：先铺骨架，避免「默认 8H/2H → 真实值」的跳变
            if (vm.loading) {
                EntrySkeleton()
                Spacer(Modifier.height(20.dp))
                return@Column
            }

            // 项目
            SectionHeader("项目")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects.size) { i ->
                    val p = projects[i]
                    Chip(p.name, { vm.selectProject(p.id) }, selected = vm.projectId == p.id)
                }
            }

            // 班次
            SectionHeader("班次")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shifts.size) { i ->
                    val s = shifts[i]
                    Chip(
                        s.name,
                        { vm.shiftId = s.id },
                        selected = vm.shiftId == s.id,
                        icon = Lucide.Briefcase,
                        iconTint = Color(s.color),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SegmentedToggle(
                options = EntryTab.entries,
                selected = vm.tab,
                label = { it.label },
                onSelect = { vm.tab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            when (vm.tab) {
                EntryTab.WORK -> {
                    HourStepper("正班", vm.zbMinutes, rates.zbMinor, money) { vm.zbMinutes = it }
                    Spacer(Modifier.height(10.dp))
                    HourStepper("加班", vm.jbMinutes, rates.jbMinor, money) { vm.jbMinutes = it }
                    Spacer(Modifier.height(10.dp))
                    HourStepper("周末", vm.zmMinutes, rates.zmMinor, money) { vm.zmMinutes = it }
                    Spacer(Modifier.height(10.dp))
                    HourStepper("节假", vm.jjMinutes, rates.jjMinor, money) { vm.jjMinutes = it }
                    Spacer(Modifier.height(12.dp))
                    val total = vm.zbMinutes + vm.jbMinutes + vm.zmMinutes + vm.jjMinutes
                    Text(
                        "合计 ${formatHours(total)} 小时",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                    )
                }
                EntryTab.LEAVE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LeaveType.entries.forEach { t ->
                            Chip(t.label, { vm.leaveType = t }, selected = vm.leaveType == t)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HourStepper("休假时长", vm.leaveMinutes, 0, money) { vm.leaveMinutes = it }
                }
                EntryTab.DAYPAY -> {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("当天工资", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("¥", style = MaterialTheme.typography.headlineSmall, color = c.accent)
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = vm.dayPayText,
                                    onValueChange = { raw ->
                                        AmountInput.sanitize(raw)?.let { vm.dayPayText = it }
                                    },
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = c.textPrimary),
                                    cursorBrush = SolidColor(c.accent),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Box {
                                            if (vm.dayPayText.isEmpty()) {
                                                Text("0.00", style = MaterialTheme.typography.headlineSmall, color = c.textTertiary)
                                            }
                                            inner()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SectionHeader("备注")
            GlassField(
                value = vm.note,
                onChange = { vm.note = it },
                placeholder = "今天做了什么呢",
            )

            if (payItems.isNotEmpty()) {
                SectionHeader("补贴 / 扣款", action = "长按改金额", onAction = {})
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(payItems.size) { i ->
                        val item = payItems[i]
                        PayChip(item, vm.dayPays.any { it.itemId == item.id }) {
                            if (item.amountMinor > 0) vm.togglePay(item)
                            else {
                                editing = item
                                editAmount = ""
                            }
                        }
                    }
                }
                // 已挂上的金额，可单独改或移除
                val applied = vm.dayPays.mapNotNull { pay -> payItems.firstOrNull { it.id == pay.itemId }?.let { it to pay } }
                if (applied.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            applied.forEach { (item, pay) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.textSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        money.signed(pay.amountMinor, item.kind == PayKind.BONUS && pay.amountMinor >= 0),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (item.kind == PayKind.BONUS) c.positive else c.negative,
                                        modifier = Modifier.pressable({
                                            editing = item
                                            editAmount = AmountInput.fromMinor(pay.amountMinor)
                                        }),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Icon(
                                        Lucide.X,
                                        contentDescription = "移除",
                                        tint = c.textTertiary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .pressable({ vm.setPayAmount(item, 0) }),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (vm.loaded) {
                Text(
                    "删除当天的记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.negative,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .pressable({ vm.delete(onDeleted) })
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 打卡页骨架：项目、班次、分段、四行工时、备注，位置与真实控件一致。 */
@Composable
private fun EntrySkeleton() {
    repeat(2) { row ->
        SectionHeader(if (row == 0) "项目" else "班次")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Skeleton(
                    Modifier.width(if (i == 0) 92.dp else 78.dp).height(42.dp),
                    CircleShape,
                    phase = i * 0.1f,
                )
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Skeleton(Modifier.fillMaxWidth().height(44.dp), CircleShape)
    Spacer(Modifier.height(16.dp))
    repeat(4) { i ->
        Glass(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.width(44.dp).height(14.dp), phase = i * 0.08f)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(Modifier.width(64.dp).height(10.dp), phase = i * 0.08f)
                }
                Skeleton(Modifier.size(36.dp), CircleShape, phase = i * 0.08f)
                Spacer(Modifier.width(12.dp))
                Skeleton(Modifier.width(48.dp).height(18.dp), phase = i * 0.08f)
                Spacer(Modifier.width(12.dp))
                Skeleton(Modifier.size(36.dp), CircleShape, phase = i * 0.08f)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    SectionHeader("备注")
    Skeleton(Modifier.fillMaxWidth().height(50.dp), RoundedCornerShape(20.dp))
}

@Composable
private fun HourStepper(label: String, minutes: Int, rateMinor: Long, money: com.lhzkml.DayWage.util.Money, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Glass(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    if (rateMinor > 0) "${money.format(rateMinor)}/H" else "未设置时薪",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            StepperButton(Lucide.Minus) { onChange((minutes - 30).coerceAtLeast(0)) }
            Text(
                "${formatHours(minutes)}H",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.width(72.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepperButton(Lucide.Plus) { onChange((minutes + 30).coerceAtMost(24 * 60)) }
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .size(36.dp)
            .pressable(onClick, pressedScale = 0.88f)
            .clip(CircleShape)
            .background(c.glassStrong)
            .border(1.dp, c.borderTop.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PayChip(item: PayItem, active: Boolean, onClick: () -> Unit) {
    Chip(
        item.name,
        onClick,
        selected = active,
        icon = if (item.kind == PayKind.BONUS) Lucide.Plus else Lucide.Minus,
        iconTint = if (item.kind == PayKind.BONUS) LocalAppColors.current.positive else LocalAppColors.current.negative,
    )
}

/** 玻璃输入框，替代原来 Shared.kt 里的 GlassTextField。 */
@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = LocalAppColors.current
    Glass(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = c.textPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(c.accent),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package cn.xenoblade2.next.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.agent.AgentSettings
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.ThemeMode
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.KeyValue
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.nav.HOME_ENTRY_BY_KEY
import cn.xenoblade2.next.ui.nav.TAB_OPTION_KEYS
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.launch
import java.util.Locale

private data class DbStats(
    val maps: Int = 0,
    val markers: Int = 0,
    val quests: Int = 0,
    val blades: Int = 0,
    val steps: Int = 0,
)

private const val TAB_FULL_HINT = "已达到最多 3 项，先移除一个再添加"

/** 序号圆点：用 Canvas 画，数字按第一行的实际范围居中，不受行高/基线留白影响。 */
@Composable
private fun TabIndexDot(index: Int) {
    val measurer = rememberTextMeasurer()
    // Core 在暗色主题下是浅青（#7DCCC6），白字只有约 1.8:1；用 onPrimary（亮色白字 / 暗色深青字）
    // 才能在两套配色里都读得清，和 MaterialTheme 的 primary 对子保持一致。
    val onCore = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(20.dp)) {
        drawCircle(Palette.Core)
        val layout = measurer.measure(
            "${index + 1}",
            TextStyle(color = onCore, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
        )
        // 布局高度包含行距，直接按 layout.size 居中会偏；用行 top/bottom 对齐圆心。
        val lineTop = layout.getLineTop(0)
        val lineHeight = layout.getLineBottom(0) - lineTop
        drawText(
            layout,
            topLeft = Offset(
                (size.width - layout.size.width) / 2f,
                (size.height - lineHeight) / 2f - lineTop,
            ),
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAgent: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AgentSettings(context) }

    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var temperature by remember { mutableStateOf(settings.temperature) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var planCount by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf(DbStats()) }
    // 满额时点「添加」的那一项，提示就显示在它下面。
    var tabHintKey by remember { mutableStateOf<String?>(null) }
    // Prefs.tabKeys 是 SnapshotState（mutableStateOf），直接读即可随改动重组。
    val tabs = Prefs.tabKeys
    // 从输入框状态推导，而不是读 SharedPreferences：否则清空配置后
    // ScreenHeader / Tag 所在的重启范围没有读到任何 MutableState，Compose 会跳过重组。
    val remoteEnabled = baseUrl.isNotBlank() && apiKey.isNotBlank()

    LaunchedEffect(Unit) {
        stats = WikiRepo.io {
            DbStats(
                maps = Db.count("SELECT COUNT(*) FROM map"),
                markers = Db.count("SELECT COUNT(*) FROM frontiernav_map_marker"),
                quests = Db.count("SELECT COUNT(*) FROM quest"),
                blades = Db.count("SELECT COUNT(*) FROM blade"),
                steps = Db.count("SELECT COUNT(*) FROM task_workflow_step"),
            )
        }
        planCount = WikiRepo.io { PlanStore.plans().size }
    }

    fun save() {
        settings.baseUrl = baseUrl
        settings.apiKey = apiKey
        settings.model = model.trim()
        settings.temperature = temperature
        baseUrl = settings.baseUrl
        apiKey = settings.apiKey
        model = settings.model
        saved = true
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "设置",
            subtitle = if (remoteEnabled) "模型驱动可用" else "离线模式（不发任何网络请求）",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard(title = "外观") {
                Text("暗色模式", color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeChip(mode.label, Prefs.themeMode == mode) { Prefs.applyThemeMode(mode) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "跟随系统时随设备深浅色切换；选亮色 / 暗色会覆盖系统设置。",
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                )
            }

            SectionCard(
                title = "底部 Tab 页设置",
                trailing = {
                    Text("${tabs.size}/${Prefs.MAX_OPTIONAL_TABS}", color = Palette.TextDim, fontSize = 11.sp)
                },
            ) {
                Text(
                    "图鉴和设置固定显示，中间最多 3 个可选入口。改动后底栏立即生效，重启后仍在。",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                // 已添加：带序号、可上下移动、可移除
                Text("底部导航", color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                if (tabs.isEmpty()) {
                    Text("还没添加任何功能", color = Palette.TextDim, fontSize = 12.sp)
                }
                tabs.forEachIndexed { index, key ->
                    val entry = HOME_ENTRY_BY_KEY[key] ?: return@forEachIndexed
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabIndexDot(index)
                        Spacer(Modifier.width(10.dp))
                        Text(entry.title, color = Palette.Text, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        RowAction("上移", enabled = index > 0) {
                            Prefs.moveTab(key, -1)
                            tabHintKey = null
                        }
                        RowAction("下移", enabled = index < tabs.lastIndex) {
                            Prefs.moveTab(key, 1)
                            tabHintKey = null
                        }
                        RowAction("移除", color = Palette.Flame) {
                            Prefs.toggleTab(key)
                            tabHintKey = null
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("可添加功能", color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                TAB_OPTION_KEYS.filterNot { it in tabs }.forEach { key ->
                    val entry = HOME_ENTRY_BY_KEY[key] ?: return@forEach
                    val full = tabs.size >= Prefs.MAX_OPTIONAL_TABS
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(20.dp).clip(CircleShape).border(1.dp, Palette.CardBorder, CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(entry.title, color = Palette.TextDim, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            RowAction(
                                "添加",
                                color = if (full) Palette.TextDim else Palette.Core,
                            ) {
                                if (full) {
                                    tabHintKey = key
                                } else {
                                    Prefs.toggleTab(key)
                                    tabHintKey = null
                                }
                            }
                        }
                        if (tabHintKey == key) {
                            Text(TAB_FULL_HINT, color = Palette.Flame, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            SectionCard(
                title = "Agent 模型",
                trailing = {
                    Tag(if (remoteEnabled) "已连接配置" else "离线", if (remoteEnabled) Palette.Done else Palette.TextDim)
                },
            ) {
                Text(
                    "兼容 OpenAI /chat/completions 协议：OpenAI、DeepSeek、通义、自建 vLLM、Ollama 都能填。留空则只用离线规则规划。",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))

                FieldLabel("接口地址")
                InputField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    placeholder = "https://api.deepseek.com/v1",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))

                FieldLabel("密钥")
                InputField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    placeholder = "sk-...",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "隐藏 Key" else "显示 Key",
                                tint = Palette.TextDim,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))

                FieldLabel("模型名")
                InputField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    placeholder = "多数端点必填，如 grok-4.7 / deepseek-chat / qwen-plus",
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("温度 ${String.format(Locale.US, "%.2f", temperature)}")
                    Spacer(Modifier.weight(1f))
                    Text("越低温越按库说话", color = Palette.TextDim, fontSize = 11.sp)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it; saved = false },
                    valueRange = 0f..1f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.Core,
                        activeTrackColor = Palette.Core,
                        inactiveTrackColor = Palette.SurfaceHigh,
                    ),
                )
                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionRow("保存") { save() }
                    ActionRow("清空配置", color = Palette.TextDim) {
                        baseUrl = ""
                        apiKey = ""
                        model = ""
                        temperature = 0.2f
                        save()
                    }
                }
                if (saved) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (remoteEnabled) "已保存，助手页下一次提问即走模型。" else "已保存，当前为离线规则模式。",
                        color = Palette.Done,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("常用预设", color = Palette.TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionRow("DeepSeek", Palette.Aqua) { baseUrl = "https://api.deepseek.com/v1"; saved = false }
                    ActionRow("OpenAI", Palette.Aqua) { baseUrl = "https://api.openai.com/v1"; saved = false }
                    ActionRow("Ollama", Palette.Aqua) { baseUrl = "http://10.0.2.2:11434/v1"; saved = false }
                }
            }

            SectionCard(title = "本机数据") {
                KeyValue("地图", "${stats.maps} 张")
                KeyValue("点位", "${stats.markers} 个")
                KeyValue("任务", "${stats.quests} 个 · ${stats.steps} 条步骤")
                KeyValue("异刃", "${stats.blades} 把")
                KeyValue("计划", "$planCount 份（可写，存在 plans.db）")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wiki 数据只读：assets/xenoblade2.db。计划与开环进度写在应用私有目录，不会改动原始库。",
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }

            SectionCard(title = "清理") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionRow("删除全部计划", Palette.Flame) { confirmClear = true }
                }
                Spacer(Modifier.height(6.dp))
                Text("只删 plans.db 里的计划与勾选进度，不动 Wiki 数据。", color = Palette.TextDim, fontSize = 11.sp)
            }

            SectionCard(title = "助手（测试）") {
                Text(
                    "大模型规划、路线与开环助手仍在开发中，入口先放在设置里方便试用。",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                ActionRow("打开助手") { onOpenAgent() }
            }

            SectionCard(title = "关于与授权") {
                Text(
                    "本应用是社区复刻：界面与数据结构参考 Gitee「xenoblade2-wiki」1.2.2（MPL-2.0），" +
                        "代码为重新实现，未包含其统计 SDK，也不读取设备广告标识。\n\n" +
                        "游戏数据与地图素材来自 FrontierNav / 异度神剑 Wiki 社区。分发或商用前请自行确认数据与素材授权；" +
                        "《异度神剑 2》相关商标与内容归 MONOLITH SOFT / Nintendo 所有。",
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("cn.xenoblade2.next · 本地优先 · 无账号", color = Palette.TextDim, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.Surface,
            title = { Text("删除全部计划？", color = Palette.Text) },
            text = {
                Text(
                    "将删除 $planCount 份计划及其勾选进度，无法恢复。",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        WikiRepo.io { PlanStore.plans().forEach { PlanStore.deletePlan(it.id) } }
                        planCount = WikiRepo.io { PlanStore.plans().size }
                    }
                }) { Text("删除", color = Palette.Flame) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = Palette.TextDim) }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, color = Palette.TextDim.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(Palette.Core),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun ThemeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Core.copy(alpha = 0.2f) else Palette.SurfaceHigh)
            .border(1.dp, if (active) Palette.Core else Palette.CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (active) Palette.Core else Palette.TextDim,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 行内文字按钮：留给手指的点击区比文字本身大，禁用时只降透明度不隐藏。 */
@Composable
private fun RowAction(
    label: String,
    color: Color = Palette.TextDim,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (enabled) color else color.copy(alpha = 0.3f),
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

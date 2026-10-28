package cn.xenoblade2.next.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.agent.AgentSettings
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.KeyValue
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
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

@Composable
fun SettingsScreen(onBack: () -> Unit) {
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

                FieldLabel("Base URL")
                InputField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    placeholder = "https://api.deepseek.com/v1",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))

                FieldLabel("API Key")
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
            .border(1.dp, Color(0xFF26313F), RoundedCornerShape(9.dp))
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

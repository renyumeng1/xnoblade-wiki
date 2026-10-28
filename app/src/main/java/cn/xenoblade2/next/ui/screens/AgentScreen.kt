package cn.xenoblade2.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.agent.AgentEngine
import cn.xenoblade2.next.agent.AgentEvent
import cn.xenoblade2.next.agent.AgentSettings
import cn.xenoblade2.next.agent.AgentTools
import cn.xenoblade2.next.agent.Conversation
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.Plan
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.components.markdownLite
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Assistant(val text: String) : ChatItem
    data class Tool(val name: String, val args: String) : ChatItem
    data class Notice(val text: String) : ChatItem
}

private val QUICK_PROMPTS = listOf(
    "规划 名流帽",
    "开环 焰",
    "甲狼兽在哪",
    "查看计划",
)

@Composable
fun AgentScreen(
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AgentSettings(context) }
    val conversation = remember { Conversation() }
    val chat = remember { mutableStateListOf<ChatItem>() }
    val listState = rememberLazyListState()

    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var planVersion by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    val events = remember { Channel<AgentEvent>(Channel.UNLIMITED) }
    val engine = remember { AgentEngine(settings, AgentTools { planVersion++ }) }

    LaunchedEffect(planVersion) {
        plans = WikiRepo.io { PlanStore.plans() }
    }

    LaunchedEffect(Unit) {
        for (event in events) {
            when (event) {
                is AgentEvent.ToolUse -> chat.add(ChatItem.Tool(event.name, event.args))
                is AgentEvent.Delta -> Unit
                is AgentEvent.Final -> {
                    chat.add(ChatItem.Assistant(event.text))
                    running = false
                    planVersion++
                }
                is AgentEvent.Failure -> chat.add(ChatItem.Notice(event.message))
            }
        }
    }

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    fun submit(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || running) return
        chat.add(ChatItem.User(text))
        input = ""
        running = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    engine.run(text, conversation) { event -> events.trySend(event) }
                }
            } catch (t: Throwable) {
                events.trySend(AgentEvent.Final("助手执行失败：${t.message ?: t.javaClass.simpleName}"))
            } finally {
                running = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = "行动助手",
            subtitle = if (settings.remoteEnabled) {
                "模型驱动 · ${settings.model.ifBlank { "未填模型名" }}"
            } else {
                "离线规则模式 · 设置里填 Base URL + Key 可切模型"
            },
            onBack = onBack,
        ) {
            if (chat.isNotEmpty()) {
                IconButton(onClick = {
                    chat.clear()
                    conversation.reset()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "清空对话", tint = Palette.TextDim)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.isEmpty()) {
                EmptyChatHint()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(chat) { _, item ->
                        when (item) {
                            is ChatItem.User -> UserBubble(item.text)
                            is ChatItem.Assistant -> {
                                AssistantBubble(item.text)
                                val planId = Regex("(?:#|plan_id\\s*[=:：]?\\s*)(\\d+)").find(item.text)
                                    ?.groupValues?.getOrNull(1)?.toLongOrNull()
                                if (planId != null && plans.any { it.id == planId }) {
                                    Spacer(Modifier.height(4.dp))
                                    OpenPlanRow(planId) { onOpenPlan(planId) }
                                }
                            }
                            is ChatItem.Tool -> ToolLine(item.name, item.args)
                            is ChatItem.Notice -> Text(
                                item.text,
                                color = Palette.Flame,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            )
                        }
                    }
                    if (running) {
                        item { TypingRow() }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_PROMPTS.forEach { prompt ->
                QuickChip(prompt, enabled = !running) { submit(prompt) }
            }
        }

        Spacer(Modifier.height(8.dp))
        InputBar(
            value = input,
            onValueChange = { input = it },
            enabled = !running,
            onSend = { submit(input) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyChatHint() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("把「去哪、打谁、怎么排」交给助手", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "助手只读本机 Wiki 数据库：任务步骤、异刃开环需求、地标与敌人刷新点。\n" +
                "生成计划后会给出编号，点一下就能跳到可打勾的清单。",
            color = Palette.TextDim,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp))
                .background(Palette.Core.copy(alpha = 0.16f))
                .border(1.dp, Palette.Core.copy(alpha = 0.35f), RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text, color = Palette.Text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(Palette.Surface)
                .border(1.dp, Color(0xFF23303F), RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(markdownLite(text), color = Palette.Text, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun ToolLine(name: String, args: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag("工具", Palette.Aqua, filled = true)
        Spacer(Modifier.width(6.dp))
        Text(name, color = Palette.Aqua, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (args.isNotBlank() && args != "{}") {
            Spacer(Modifier.width(6.dp))
            Text(
                args.take(72),
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TypingRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Palette.Core, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("正在查库 / 规划…", color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun OpenPlanRow(planId: Long, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Core.copy(alpha = 0.14f))
            .border(1.dp, Palette.Core.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text("打开计划 #$planId", color = Palette.Core, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Color(0xFF26313F), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (enabled) Palette.TextDim else Palette.TextDim.copy(alpha = 0.4f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, Color(0xFF26313F), RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (value.isEmpty()) {
                Text("问点什么：规划 名流帽 / 开环 焰 / 甲狼兽在哪", color = Palette.TextDim, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.Core),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (value.isNotBlank() && enabled) Palette.Core else Palette.SurfaceHigh)
                .clickable(enabled = value.isNotBlank() && enabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (value.isNotBlank() && enabled) Palette.Bg else Palette.TextDim,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

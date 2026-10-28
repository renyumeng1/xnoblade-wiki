package cn.xenoblade2.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.QuestDetail
import cn.xenoblade2.next.data.QuestSummary
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.ChipRow
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.KeyValue
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun QuestListScreen(onOpen: (Long) -> Unit) {
    var categories by remember { mutableStateOf(listOf("全部")) }
    var category by remember { mutableStateOf("全部") }
    var keyword by remember { mutableStateOf("") }
    var quests by remember { mutableStateOf<List<QuestSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val cats = WikiRepo.io { WikiRepo.questCategories().map { it.first.ifBlank { "未分类" } } }
        categories = listOf("全部") + cats
    }

    LaunchedEffect(category, keyword) {
        loading = true
        delay(if (keyword.isBlank()) 0 else 200)
        quests = WikiRepo.io { WikiRepo.quests(category, keyword.trim(), 400) }
        loading = false
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "任务",
            subtitle = "${quests.size} 条 · 点击查看分步流程并生成地图计划",
        )
        Spacer(Modifier.height(8.dp))
        ChipRow(categories, category) { category = it }
        Spacer(Modifier.height(8.dp))
        SearchField(keyword) { keyword = it }
        Spacer(Modifier.height(4.dp))

        if (loading) {
            LoadingBox(Modifier.fillMaxWidth().height(200.dp))
        } else if (quests.isEmpty()) {
            EmptyHint("没有符合条件的任务")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quests, key = { it.id }) { quest ->
                    QuestRow(quest) { onOpen(quest.id) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("按任务名筛选", color = Color(0xFF5C6B7C), fontSize = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(Palette.Core),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QuestRow(quest: QuestSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (quest.category.contains("主线")) Palette.Gold.copy(alpha = 0.16f) else Palette.Core.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                quest.stepCount.toString(),
                color = if (quest.category.contains("主线")) Palette.Gold else Palette.Core,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                quest.display,
                color = Palette.Text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val meta = listOfNotNull(
                    quest.category.takeIf { it.isNotBlank() },
                    quest.chapter.takeIf { it.isNotBlank() },
                    quest.npc.takeIf { it.isNotBlank() }?.let { "NPC:$it" },
                ).joinToString(" · ")
                Text(meta, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (quest.stepCount == 0) Tag("无分步", Palette.TextDim)
    }
}

@Composable
fun QuestDetailScreen(
    questId: Long,
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<QuestDetail?>(null) }
    var anchored by remember { mutableStateOf<List<Planner.Anchored>>(emptyList()) }
    var planIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createRequested by remember { mutableStateOf(false) }

    LaunchedEffect(questId) {
        val loaded = WikiRepo.io {
            val d = WikiRepo.questDetail(questId)
            val a = d?.let { Planner.anchorQuest(it) } ?: emptyList()
            d to a
        }
        detail = loaded.first
        anchored = loaded.second
        planIds = WikiRepo.io {
            PlanStore.plans().filter { it.sourceType == "quest" && it.sourceId == questId.toString() }.map { it.id }
        }
    }

    LaunchedEffect(createRequested) {
        if (!createRequested) return@LaunchedEffect
        val d = detail ?: return@LaunchedEffect
        val id = WikiRepo.io {
            val steps: List<NewStep> = Planner.buildQuestPlan(d)
            val planId = PlanStore.createPlan("任务 · ${d.title}", "quest", d.id.toString())
            PlanStore.addSteps(planId, steps)
            planId
        }
        createRequested = false
        creating = false
        planIds = listOf(id)
        message = "已生成计划 #$id，共 ${d.steps.size} 步"
        onOpenPlan(id)
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = detail?.title ?: "任务",
            subtitle = detail?.let { "${it.category} · ${it.chapter}" },
            onBack = onBack,
        )

        val d = detail
        if (d == null) {
            LoadingBox()
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "基本信息") {
                    KeyValue("分类", d.category)
                    KeyValue("章节", d.chapter)
                    KeyValue("NPC", d.npc)
                    KeyValue("接取地点", d.locations.joinToString("；"))
                    KeyValue("条件", d.condition.take(200))
                    KeyValue("奖励", listOfNotNull(
                        d.rewardText.takeIf { it.isNotBlank() },
                        if (d.gold > 0) "金币 ${d.gold}" else null,
                        if (d.exp > 0) "EXP ${d.exp}" else null,
                    ).joinToString(" · "))
                    if (d.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(d.description.take(600), color = Palette.TextDim, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionRow(
                        text = when {
                            creating -> "生成中…"
                            planIds.isNotEmpty() -> "已有 ${planIds.size} 份计划 · 查看"
                            else -> "生成地图计划"
                        },
                        onClick = {
                            if (planIds.isNotEmpty()) {
                                onOpenPlan(planIds.first())
                            } else if (!creating) {
                                creating = true
                                createRequested = true
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("共 ${anchored.size} 步，${anchored.count { it.pinned }} 步已定位")
                            val coarse = anchored.count { !it.pinned && it.mapOnly != null }
                            if (coarse > 0) append(" · $coarse 步可到图")
                        },
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                    )
                }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Palette.Core, fontSize = 12.sp)
                }
            }

            items(anchored, key = { it.step.position }) { a ->
                StepCard(a, onOpenMap)
            }
        }
    }
}


@Composable
private fun StepCard(a: Planner.Anchored, onOpenMap: (String) -> Unit) {
    val located = a.pinned
    val mapId = a.pin?.mapId?.takeIf { it.isNotBlank() } ?: a.mapOnly
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(
                1.dp,
                if (located) Palette.Core.copy(alpha = 0.22f) else Color(0xFF23303F),
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (located) Palette.Core.copy(alpha = 0.18f) else Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    a.step.position.toString(),
                    color = if (located) Palette.Core else Palette.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                a.step.kind.replace("官方目标 · ", ""),
                color = Palette.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            when {
                located -> Tag(a.how, Palette.Core)
                mapId != null -> Tag("图级定位", Palette.Core.copy(alpha = 0.7f))
                else -> Tag("未定位", Palette.TextDim)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(a.step.text, color = Palette.Text, fontSize = 14.sp, lineHeight = 20.sp)
        if (a.step.detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(a.step.detail, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (a.step.links.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                a.step.links.joinToString("  ") { "${it.kind}·${it.label}" },
                color = Color(0xFF7FA6D6),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        if (mapId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Core.copy(alpha = if (located) 0.1f else 0.06f))
                    .clickable { onOpenMap(mapId) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("在地图查看", color = Palette.Core, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (located) {
                        "${WikiRepo.map(mapId)?.area ?: mapId} · ${a.pin?.targetName.orEmpty()}"
                    } else {
                        "${WikiRepo.map(mapId)?.area ?: mapId} · 无精确坐标"
                    },
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

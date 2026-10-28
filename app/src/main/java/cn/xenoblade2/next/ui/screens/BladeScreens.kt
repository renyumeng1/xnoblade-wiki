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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.AffinityLevel
import cn.xenoblade2.next.data.AffinityNode
import cn.xenoblade2.next.data.BladeSummary
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun BladeListScreen(onOpen: (Long, String) -> Unit) {
    var blades by remember { mutableStateOf<List<BladeSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        blades = WikiRepo.io { WikiRepo.blades() }
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(title = "异刃", subtitle = "${blades.size} 把 · 开环节点与需求进度")
        if (blades.isEmpty()) {
            LoadingBox(Modifier.height(200.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(blades, key = { it.id }) { blade ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Surface)
                            .clickable { onOpen(blade.id, blade.name) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(blade.elementColor).copy(alpha = 0.18f))
                                .border(1.dp, Color(blade.elementColor).copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                blade.name.take(1),
                                color = Color(blade.elementColor),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (blade.portrait.isNotBlank()) {
                                AssetImage(
                                    path = blade.portrait,
                                    modifier = Modifier.size(38.dp),
                                    contentDescription = blade.name,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(blade.name, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                listOf(blade.element, blade.role, blade.weapon).filter { it.isNotBlank() }.joinToString(" · "),
                                color = Palette.TextDim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Tag("${blade.nodeCount} 环", Color(blade.elementColor))
                    }
                }
            }
        }
    }
}

@Composable
fun BladeDetailScreen(bladeId: Long, onBack: () -> Unit, onOpenPlan: (Long) -> Unit) {
    var blade by remember { mutableStateOf<BladeSummary?>(null) }
    var nodes by remember { mutableStateOf<List<AffinityNode>>(emptyList()) }
    var progress by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bladeId) {
        val loaded = WikiRepo.io {
            val b = WikiRepo.blades().firstOrNull { it.id == bladeId }
            val n = WikiRepo.bladeNodes(bladeId)
            Triple(b, n, PlanStore.allAffinityProgress())
        }
        blade = loaded.first
        nodes = loaded.second
        progress = loaded.third
    }

    Column(Modifier.fillMaxWidth()) {
        val headerBlade = blade
        ScreenHeader(
            title = headerBlade?.name ?: "异刃",
            subtitle = headerBlade?.let { "${it.element} · ${it.role} · ${it.weapon}" },
            onBack = onBack,
            leading = if (headerBlade != null && headerBlade.portrait.isNotBlank()) {
                {
                    AssetImage(
                        path = headerBlade.portrait,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentDescription = headerBlade.name,
                    )
                }
            } else {
                null
            },
        )

        val b = blade
        if (b == null) {
            LoadingBox()
            return@Column
        }

        val total = nodes.sumOf { it.levels.size }
        val done = nodes.sumOf { n -> n.levels.count { it.learned } }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "开环概览") {
                    Text(
                        "${nodes.size} 个节点 · $total 级需求 · 已习得 $done 级",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(b.elementColor),
                        trackColor = Color(0xFF23303F),
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionRow(
                        text = if (creating) "生成中…" else "生成开环计划",
                        onClick = {
                            if (creating) return@ActionRow
                            creating = true
                            scope.launch {
                                val id = WikiRepo.io {
                                    val steps: List<NewStep> = Planner.buildBladePlan(b, nodes)
                                    val planId = PlanStore.createPlan("开环 · ${b.name}", "blade", b.id.toString())
                                    PlanStore.addSteps(planId, steps)
                                    planId
                                }
                                creating = false
                                onOpenPlan(id)
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "计划会跳过「初始习得」；带地名/敌名/NPC 的需求自动钉点，只写地区的落到该地区主图，" +
                            "纯战斗类需求补「怎么刷」提示；同图步骤按最近邻排序。",
                        color = Color(0xFF5C6B7C),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            items(nodes, key = { it.id }) { node ->
                NodeCard(
                    node = node,
                    progress = progress,
                    onProgress = { level, value ->
                        progress = progress.toMutableMap().also { it[level.id] = value }
                        scope.launch { WikiRepo.io { PlanStore.setAffinityProgress(level.id, value) } }
                    },
                )
            }
        }
    }
}

@Composable
private fun NodeCard(node: AffinityNode, progress: Map<Long, Int>, onProgress: (AffinityLevel, Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(node.name, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Tag(categoryLabel(node.category), Palette.Aqua)
        }
        Spacer(Modifier.height(6.dp))
        node.levels.forEach { level ->
            LevelRow(level, progress[level.id] ?: 0) { onProgress(level, it) }
        }
    }
}

private fun categoryLabel(c: String) = when (c) {
    "special" -> "必杀"
    "battle_skill" -> "战斗技能"
    "field_skill" -> "场景技能"
    "blade_skill" -> "异刃技能"
    else -> c
}

@Composable
private fun LevelRow(level: AffinityLevel, current: Int, onProgress: (Int) -> Unit) {
    val target = Regex("""(\d+)\s*/\s*(\d+)""").find(level.requirement)?.groupValues?.get(2)?.toIntOrNull()
        ?: level.achievementCount.takeIf { it > 0 }
    val value = current.coerceAtMost(target ?: Int.MAX_VALUE)
    val learned = level.learned || (target != null && value >= target)

    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Lv${level.level}",
                color = if (learned) Palette.Done else Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(level.effect, color = Palette.Text, fontSize = 12.sp, lineHeight = 17.sp)
                Text(
                    level.requirement.ifBlank { "—" },
                    color = if (level.learned) Palette.Done else Palette.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                if (!level.links.isEmpty()) {
                    Text(
                        level.links.joinToString("  ") { "${it.kind}·${it.label}" },
                        color = Color(0xFF7FA6D6),
                        fontSize = 10.sp,
                    )
                }
            }
            if (!level.learned && target != null) {
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$value/$target",
                        color = if (learned) Palette.Done else Palette.Core,
                        fontSize = 11.sp,
                    )
                    Row {
                        MiniButton("−") { onProgress((value - 1).coerceAtLeast(0)) }
                        Spacer(Modifier.width(4.dp))
                        MiniButton("+") { onProgress(value + 1) }
                    }
                }
            }
        }
        if (!level.learned && target != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (target == 0) 0f else value.toFloat() / target },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (learned) Palette.Done else Palette.Core,
                trackColor = Color(0xFF23303F),
            )
        }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.TextDim, fontSize = 12.sp)
    }
}

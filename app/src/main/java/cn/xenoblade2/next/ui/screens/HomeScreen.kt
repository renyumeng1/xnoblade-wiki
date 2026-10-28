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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.SearchHit
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.Plan
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenQuests: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenBlades: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuest: (Long) -> Unit,
    onOpenBlade: (Long, String) -> Unit,
) {
    var stats by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    LaunchedEffect(Unit) {
        stats = WikiRepo.io {
            listOf(
                "任务" to Db.count("SELECT COUNT(*) FROM quest"),
                "可拆步骤" to Db.count("SELECT COUNT(*) FROM task_workflow_step"),
                "异刃" to Db.count("SELECT COUNT(*) FROM blade"),
                "环等级需求" to Db.count("SELECT COUNT(*) FROM blade_affinity_level"),
                "交互地图" to Db.count("SELECT COUNT(*) FROM map WHERE marker_count > 0"),
                "图上点位" to Db.count("SELECT COUNT(*) FROM frontiernav_map_marker"),
            )
        }
        plans = WikiRepo.io { PlanStore.plans() }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        hits = WikiRepo.io { WikiRepo.search(query.trim(), 30) }
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "异度神剑 2 NEXT",
            subtitle = "离线百科 · 可落图的行动规划",
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Palette.TextDim)
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SearchBox(query) { query = it } }

            if (query.isNotBlank()) {
                if (hits.isEmpty()) {
                    item { Text("没有匹配的条目", color = Palette.TextDim, fontSize = 13.sp) }
                }
                items(hits, key = { "${it.type}-${it.id}" }) { hit ->
                    SearchRow(hit) {
                        when (hit.type) {
                            "quest" -> onOpenQuest(hit.id)
                            "blade" -> onOpenBlade(hit.id, hit.title)
                            else -> Unit
                        }
                    }
                }
            } else {
                item { StatGrid(stats) }

                item {
                    SectionCard(title = "快速开始") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTile("任务地图\n规划", onOpenQuests, Modifier.weight(1f))
                            QuickTile("异刃\n开环", onOpenBlades, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTile("交互\n地图", onOpenMaps, Modifier.weight(1f))
                            QuickTile("智能\n助手", onOpenAgent, Modifier.weight(1f))
                        }
                    }
                }

                item {
                    SectionCard(
                        title = "进行中的计划",
                        trailing = {
                            Text(
                                "全部",
                                color = Palette.Core,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { onOpenPlans() },
                            )
                        },
                    ) {
                        if (plans.isEmpty()) {
                            Text(
                                "还没有计划。在任务详情页点「生成地图计划」，或在助手页发「开环 焰」。",
                                color = Palette.TextDim,
                                fontSize = 13.sp,
                            )
                        }
                        plans.take(3).forEach { plan ->
                            PlanMiniRow(plan) { onOpenPlans() }
                        }
                    }
                }

                item {
                    Text(
                        "数据来自 FrontierNav / Fandom 社区整理，仅供个人游玩参考。",
                        color = Color(0xFF5C6B7C),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Color(0xFF26313F), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("搜任务 / 异刃 / 敌人 / 物品", color = Color(0xFF5C6B7C), fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.Core),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!hit.iconPath.isNullOrBlank()) {
            AssetImage(
                path = hit.iconPath,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = hit.title,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(hit.title, color = Palette.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Tag(typeLabel(hit.type), typeColor(hit.type))
    }
}

private fun typeLabel(type: String) = when (type) {
    "quest" -> "任务"
    "blade" -> "异刃"
    "enemy" -> "敌人"
    "item" -> "物品"
    "location" -> "地点"
    else -> type
}

private fun typeColor(type: String) = when (type) {
    "quest" -> Palette.Gold
    "blade" -> Palette.Core
    "enemy" -> Palette.Flame
    else -> Palette.TextDim
}

@Composable
private fun StatGrid(stats: List<Pair<String, Int>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            stats.take(3).forEach { (label, value) ->
                StatCell(label, value, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            stats.drop(3).take(3).forEach { (label, value) ->
                StatCell(label, value, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text(
            if (value >= 1000) String.format("%,d", value) else value.toString(),
            color = Palette.Core,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, color = Palette.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun QuickTile(text: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Color(0xFF26313F), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Palette.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PlanMiniRow(plan: Plan, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plan.title,
                color = Palette.Text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${plan.doneCount}/${plan.stepCount}", color = Palette.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { plan.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Palette.Core,
            trackColor = Color(0xFF23303F),
        )
    }
}

package cn.xenoblade2.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.EnemyDetailData
import cn.xenoblade2.next.data.EnemyRow
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EnemyListScreen(onOpen: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<EnemyRow>?>(null) }
    var total by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        total = WikiRepo.io { WikiRepo.enemyRows(limit = 10_000).size }
    }
    LaunchedEffect(query) {
        delay(if (query.isBlank()) 0 else 180)
        rows = WikiRepo.io { WikiRepo.enemyRows(query) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "怪物",
            subtitle = if (total > 0) "共 $total 种" else "怪物图鉴",
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
            placeholder = "搜索怪物名",
        )
        Spacer(Modifier.height(8.dp))
        val data = rows
        when {
            data == null -> LoadingBox()
            data.isEmpty() -> EmptyHint("没有匹配的怪物")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data, key = { it.id }) { row -> EnemyRowView(row) { onOpen(row.id) } }
            }
        }
    }
}

private fun levelText(row: EnemyRow): String = when {
    row.levelMax <= 0 -> "等级未知"
    row.levelMin == row.levelMax -> "Lv.${row.levelMax}"
    else -> "Lv.${row.levelMin}–${row.levelMax}"
}

@Composable
private fun EnemyRowView(row: EnemyRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    color = Palette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(levelText(row), color = Palette.Gold, fontSize = 12.sp)
            }
            val sub = listOf(row.region, row.category).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.isTorna) {
            Spacer(Modifier.width(6.dp))
            Tag("黄金国", Palette.Gold)
        }
        Text("›", color = Palette.TextDim, fontSize = 18.sp)
    }
}

@Composable
fun EnemyDetailScreen(enemyId: Long, onBack: () -> Unit) {
    var data by remember { mutableStateOf<EnemyDetailData?>(null) }
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(enemyId) {
        val d = WikiRepo.io { WikiRepo.enemyDetail(enemyId) }
        data = d
        missing = d == null
    }

    Column(Modifier.fillMaxSize()) {
        val d = data
        ScreenHeader(
            title = d?.name ?: "载入中…",
            subtitle = d?.stats?.firstOrNull()?.category?.takeIf { it.isNotBlank() } ?: "怪物",
            onBack = onBack,
        )
        when {
            missing -> EmptyHint("没有找到这只怪物")
            d == null -> LoadingBox()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionCard(title = "基本情报") {
                        if (d.enemyCode.isNotBlank()) KeyValueText("内部编号", d.enemyCode)
                        if (d.isTorna) KeyValueText("版本", "黄金国伊拉")
                        if (d.stats.isNotEmpty()) {
                            val cats = d.stats.map { it.category }.distinct()
                            val regions = d.stats.map { it.region }.filter { it.isNotBlank() }.distinct()
                            KeyValueText("分类", cats.joinToString("、"))
                            KeyValueText("地区", regions.joinToString("、"))
                            val levels = d.stats.map { it.level }.filter { it > 0 }
                            if (levels.isNotEmpty()) {
                                val lo = levels.min()
                                val hi = levels.max()
                                KeyValueText("等级", if (lo == hi) "Lv.$lo" else "Lv.$lo–$hi")
                            }
                        } else {
                            Text("库里没有这只怪的等级 / 地区统计", color = Palette.TextDim, fontSize = 12.sp)
                        }
                    }
                }

                if (d.stats.isNotEmpty()) {
                    item {
                        SectionCard(title = "等级与数值") {
                            d.stats.forEach { s ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.category, color = Palette.Text, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Lv.${s.level}", color = Palette.Gold, fontSize = 12.sp)
                                        Spacer(Modifier.weight(1f))
                                        if (s.region.isNotBlank()) {
                                            Text(s.region, color = Palette.TextDim, fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        "HP ${s.hp} · EXP ${s.exp} · WP ${s.wp} · SP ${s.sp}",
                                        color = Palette.TextDim,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (d.arts.isNotEmpty()) {
                    item {
                        SectionCard(title = "武技（${d.arts.size}）") {
                            d.arts.forEach { a ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(a.name, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (a.damage > 0) Text("${a.damage}", color = Palette.Flame, fontSize = 12.sp)
                                    }
                                    val meta = buildList {
                                        if (a.type.isNotBlank()) add(a.type)
                                        if (a.attribute.isNotBlank()) add(a.attribute)
                                        if (a.target.isNotBlank()) add("目标 ${a.target}")
                                        if (a.cooldown > 0) add(String.format(Locale.US, "CD %.1fs", a.cooldown))
                                    }.joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(meta, color = Palette.TextDim, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (d.drops.isNotEmpty()) {
                    item {
                        SectionCard(title = "掉落（${d.drops.size}）") {
                            d.drops.forEach { drop ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(drop.itemTitle, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (drop.firstNamed) {
                                        Tag("首次", Palette.Cyan)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        String.format(Locale.US, "%.1f%%", drop.probability * 100),
                                        color = Palette.TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (d.spawns.isNotEmpty()) {
                    item {
                        SectionCard(title = "刷新点（${d.spawns.size}）") {
                            d.spawns.groupBy { it.mapName.ifBlank { it.mapId } }.forEach { (map, list) ->
                                Text(map, color = Palette.Core, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                list.take(12).forEach { s ->
                                    Text(
                                        String.format(Locale.US, "· %.0f, %.0f", s.x, s.z),
                                        color = Palette.TextDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                if (d.arts.isEmpty() && d.drops.isEmpty() && d.spawns.isEmpty()) {
                    item {
                        SectionCard(title = "战斗资料") {
                            Text(
                                "这只怪在库里没有武技 / 掉落 / 刷新点数据。多为剧情限定或无掉落配置的敌人。",
                                color = Palette.TextDim,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun KeyValueText(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(value, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.SearchHit
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.nav.HOME_DESC
import cn.xenoblade2.next.ui.nav.HOME_ENTRIES
import cn.xenoblade2.next.ui.nav.HOME_ENTRY_BY_KEY
import cn.xenoblade2.next.ui.nav.HOME_GROUPS
import cn.xenoblade2.next.ui.nav.HomeGroup
import cn.xenoblade2.next.ui.nav.HomeEntry
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenEntry: (String) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = null
            return@LaunchedEffect
        }
        delay(220)
        hits = WikiRepo.io { WikiRepo.search(query.trim(), 60) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeHeader()
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )

            val result = hits
            if (query.isBlank() || result == null) {
                HomeList(onOpenEntry = onOpenEntry)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (result.isEmpty()) {
                        item { Text("没有匹配的条目", color = Palette.TextDim, fontSize = 13.sp) }
                    }
                    items(result, key = { "${it.type}-${it.id}" }) { hit ->
                        SearchRow(hit) { onOpenHit(hit) }
                    }
                }
            }
        }

    }
}

@Composable
private fun HomeHeader() {
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp)) {
        Text(
            "幽界手记",
            style = MaterialTheme.typography.displaySmall,
            color = Palette.Text,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "异度神剑 2 · 图鉴与路线",
            color = Palette.TextDim,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
private fun HomeList(onOpenEntry: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HOME_GROUPS.forEach { group ->
            item(key = "head-${group.title}") { HomeGroupHeader(group) }
            item(key = "group-${group.title}") {
                HomeGroupCard(group, onOpenEntry)
            }
        }

        val recents = Prefs.recents.mapNotNull { HOME_ENTRY_BY_KEY[it] }
        if (recents.isNotEmpty()) {
            item { HomeGroupHeader(HomeGroup("最近访问", "上次看过的条目", emptyList())) }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Surface)
                        .border(1.dp, Palette.CardBorder, RoundedCornerShape(18.dp)),
                ) {
                    recents.forEachIndexed { i, entry ->
                        HomeRow(entry, onOpenEntry)
                        if (i < recents.lastIndex) Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGroupHeader(group: HomeGroup) {
    Row(
        Modifier.padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            group.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Text,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            group.subtitle,
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun HomeGroupCard(group: HomeGroup, onOpenEntry: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(18.dp)),
    ) {
        val entries = group.keys.mapNotNull { HOME_ENTRY_BY_KEY[it] }
        entries.forEachIndexed { i, entry ->
            HomeRow(entry, onOpenEntry)
            if (i < entries.lastIndex) Hairline()
        }
    }
}

@Composable
private fun HomeRow(entry: HomeEntry, onOpenEntry: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenEntry(entry.key) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.Core.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = Palette.Core,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                color = Palette.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            HOME_DESC[entry.key]?.let { desc ->
                Text(
                    desc,
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )
            }
        }
        if (entry.showsCollectionCount) {
            Text(
                "${Prefs.marked.size}",
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text("›", color = Palette.TextDim, fontSize = 20.sp)
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 63.dp)
            .height(1.dp)
            .background(Palette.CardBorder),
    )
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索异刃、任务、物品…",
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Surface.copy(alpha = 0.92f))
            .border(1.dp, Palette.Core.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Palette.TextDim, fontSize = 15.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Palette.Core),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "清空", tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
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
        if (!hit.iconPath.isNullOrBlank()) {
            AssetImage(
                path = hit.iconPath,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
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
    "enemy" -> "怪物"
    "item" -> "物品"
    "location" -> "地点"
    else -> type
}

private fun typeColor(type: String) = when (type) {
    "quest" -> Palette.Gold
    "blade" -> Palette.Core
    "enemy" -> Palette.Flame
    "item" -> Palette.Brown
    else -> Palette.TextDim
}

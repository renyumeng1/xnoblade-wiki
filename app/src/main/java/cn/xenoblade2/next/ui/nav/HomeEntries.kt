package cn.xenoblade2.next.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import cn.xenoblade2.next.ui.theme.Palette

/** 原版 `Accent` 枚举的 8 种强调色。 */
enum class Accent {
    PYRA_RED,
    MYTHRA_GOLD,
    SALVAGE_CYAN,
    TEAL,
    PURPLE,
    BROWN,
    GREEN,
    ORANGE,
    ;

    val color: Color
        get() = when (this) {
            PYRA_RED -> Palette.Core
            MYTHRA_GOLD -> Palette.Gold
            SALVAGE_CYAN -> Palette.Cyan
            TEAL -> Palette.Teal
            PURPLE -> Palette.Purple
            BROWN -> Palette.Brown
            GREEN -> Palette.Green
            ORANGE -> Palette.Orange
        }
}

/**
 * 首页入口。key / icon / accent 与原版 `HomeScreenKt.java:64` 的 homeArchives 一一对应，
 * `entityType` 指向本地库里可列举的实体类型（null 表示走专门页面或暂未收录）。
 */
data class HomeEntry(
    val key: String,
    val title: String,
    val english: String,
    val accent: Accent,
    val icon: ImageVector,
    val entityType: String? = null,
    val showsCollectionCount: Boolean = false,
)

val HOME_ENTRIES: List<HomeEntry> = listOf(
    HomeEntry("journey", "游玩经历", "Journey", Accent.PYRA_RED, Icons.Rounded.CheckCircle, showsCollectionCount = true),
    HomeEntry("planner", "素材规划", "Planner", Accent.MYTHRA_GOLD, Icons.Rounded.AccountTree),
    HomeEntry("mercenary-training", "佣兵团训练", "Mercenary Training", Accent.PYRA_RED, Icons.Rounded.Groups, "merc_mission"),
    HomeEntry("blades", "异刃", "Blades", Accent.PYRA_RED, Icons.Rounded.AutoAwesome, "blade"),
    HomeEntry("fieldSkills", "场景技能", "Field Skills", Accent.MYTHRA_GOLD, Icons.Rounded.Forest),
    HomeEntry("drivers", "御刃者", "Drivers", Accent.TEAL, Icons.Rounded.AccountCircle, "driver"),
    HomeEntry("quests", "任务", "Quests", Accent.MYTHRA_GOLD, Icons.AutoMirrored.Rounded.Assignment, "quest"),
    HomeEntry("items", "物品", "Items", Accent.BROWN, Icons.Rounded.Inventory2, "item"),
    HomeEntry("weapons", "武器", "Weapons", Accent.TEAL, Icons.Rounded.Build, "weapon"),
    HomeEntry("weather", "天气", "Weather", Accent.SALVAGE_CYAN, Icons.Rounded.Cloud, "weather"),
    HomeEntry("monsters", "怪物", "Monsters", Accent.PYRA_RED, Icons.Rounded.Pets, "enemy"),
    HomeEntry("collection", "采集点", "Collection", Accent.GREEN, Icons.Rounded.Explore, "collection_point"),
    HomeEntry("salvage", "打捞点", "Salvage", Accent.SALVAGE_CYAN, Icons.Rounded.Waves, "salvage_point"),
    HomeEntry("chests", "宝箱", "Chests", Accent.MYTHRA_GOLD, Icons.Rounded.Archive, "treasure_chest"),
    HomeEntry("maps", "交互地图", "Maps", Accent.SALVAGE_CYAN, Icons.Rounded.Map),
    HomeEntry("location", "地点", "Locations", Accent.SALVAGE_CYAN, Icons.Rounded.LocationOn, "location"),
    HomeEntry("shops", "商店", "Shops", Accent.ORANGE, Icons.Rounded.Storefront, "shop"),
    HomeEntry("heart", "牵绊对话", "Heart-to-Hearts", Accent.TEAL, Icons.Rounded.Forum, "heart_to_heart"),
    HomeEntry("poppi", "花的零件", "Poppi Parts", Accent.PYRA_RED, Icons.Rounded.Memory, "poppiswap_part"),
    HomeEntry("challenge", "挑战战斗", "Challenge Battles", Accent.PURPLE, Icons.Rounded.Flag, "challenge_battle"),
)

val HOME_ENTRY_BY_KEY: Map<String, HomeEntry> = HOME_ENTRIES.associateBy { it.key }

/** 首页/占位页用的中文说明。界面上不再展示英文副标题。 */
val HOME_DESC: Map<String, String> = mapOf(
    "journey" to "收集进度与游玩统计",
    "planner" to "素材、路线与开环计划",
    "mercenary-training" to "佣兵团任务与派遣条件",
    "blades" to "异刃图鉴与开环节点",
    "fieldSkills" to "场景技能与持有异刃",
    "drivers" to "御刃者与队伍构成",
    "quests" to "任务步骤与地图计划",
    "items" to "物品、用途与获取途径",
    "weapons" to "武器与晶片强化",
    "weather" to "天气、时段与出现条件",
    "monsters" to "敌人、等级与刷新点",
    "collection" to "采集点、掉落与场景技能",
    "salvage" to "打捞点与产出",
    "chests" to "宝箱位置与出现条件",
    "maps" to "地区、分层与点位",
    "location" to "地标、秘境与传送点",
    "shops" to "商店、兑换与库存",
    "heart" to "牵绊对话与触发条件",
    "poppi" to "花的零件与强化",
    "challenge" to "挑战战斗与奖励",
)

/**
 * 图鉴首页的四个分组。20 个功能全部保留，只是从原版的 20 张大卡片收进四段列表。
 */
data class HomeGroup(
    val title: String,
    val subtitle: String,
    val keys: List<String>,
)

val HOME_GROUPS: List<HomeGroup> = listOf(
    HomeGroup("人物", "异刃、御刃者与牵绊", listOf("blades", "drivers", "heart")),
    HomeGroup("旅程", "任务、佣兵团与游玩记录", listOf("quests", "mercenary-training", "journey", "challenge")),
    HomeGroup(
        "世界",
        "地图、地点与可收集物",
        listOf("maps", "location", "shops", "weather", "monsters", "collection", "salvage", "chests"),
    ),
    HomeGroup("装备", "物品、武器与素材规划", listOf("items", "weapons", "poppi", "fieldSkills", "planner")),
)

/** 原版「底部 Tab 页设置」可选的 19 个入口（不含首页、设置、佣兵团训练）。 */
val TAB_OPTION_KEYS: List<String> = listOf(
    "blades", "quests", "monsters", "journey", "planner", "fieldSkills", "drivers",
    "items", "weapons", "weather", "collection", "salvage", "chests", "maps",
    "location", "shops", "heart", "poppi", "challenge",
)

val HOME_ICON: ImageVector = Icons.Rounded.GridView
val SETTINGS_ICON: ImageVector = Icons.Rounded.Settings

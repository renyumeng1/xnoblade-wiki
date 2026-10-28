package cn.xenoblade2.next.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * xenoblade2.cn 社区 wiki 的图标集（线稿武器 / 彩色元素菱形）。
 *
 * 来源：https://xenoblade2.cn/ （CC BY-NC-SA 4.0）
 * 本地落在 `assets/wiki-icons/`，文件名与原站 `File:Icon-<name>.png` 一致。
 * 只在本项目非商业分发；如需商用请向站点作者申请授权。
 */
object WikiIcons {
    val ELEMENTS = listOf("火", "水", "风", "冰", "雷", "地", "光", "暗")

    /** 与 assets/wiki-icons 下的文件名严格对应。 */
    private val WEAPONS = setOf(
        "以太巨炮", "军刀", "刀", "刀2", "双剑", "双环刃", "双镰刀", "圣杯之剑", "圣杯之剑2",
        "大剑", "大剑2", "大炮", "大镰", "太刀", "小刀", "小太刀", "弯刀", "弹击球", "战斧",
        "战锤", "扇", "手甲", "打刀", "旋棍", "枪刀", "结绳", "自制剑", "花之佩剑", "花之盾",
        "花之臂", "莫纳德", "钩爪拳套", "锡杖", "长刀", "长剑", "长枪",
    )

    private const val DIR = "wiki-icons"

    /**
     * 地图点位图标，来自 xenoblade2.cn 的 File:Enemy.png / File:Tbox.png 等。
     * 这些图标本身就是游戏内 22×32 的地图标记，直接画在底图上比自绘水滴更贴近原作。
     */
    private val MAP_ICONS = mapOf(
        "EnemySpawnPoint" to "map-markers/enemy.png",
        "TombstonePoint" to "map-markers/enemy.png",
        "CollectionPoint" to "map-markers/collection-icon.png",
        "TreasurePoint" to "map-markers/tbox.png",
        "SalvagePoint" to "map-markers/salvage.png",
    )

    fun elementPath(element: String): String? =
        element.takeIf { it in ELEMENTS }?.let { "$DIR/$it.png" }

    /** 武器名与图标文件同名；`刀2` / `大剑2` / `圣杯之剑2` 是原版的第二套图标，按需回退。 */
    fun weaponPath(weapon: String): String? {
        val name = weapon.trim()
        return when {
            name in WEAPONS -> "$DIR/$name.png"
            else -> null
        }
    }

    /** 地图点位类型 → wiki 图标；没有对应图标的类型返回 null，由调用方自绘。 */
    fun mapPath(type: String): String? = MAP_ICONS[type]
}

/** 画一个 wiki 图标；资源缺失时返回 false 且不绘制，由调用方兜底。 */
@Composable
fun WikiIcon(
    path: String?,
    size: Dp = 22.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
): Boolean {
    val bmp = rememberAssetBitmap(path) ?: return false
    Image(
        bitmap = bmp,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
    return true
}

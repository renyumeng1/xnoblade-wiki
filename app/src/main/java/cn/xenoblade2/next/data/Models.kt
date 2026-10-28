package cn.xenoblade2.next.data

/** 一张可交互地图（FrontierNav 切片）。坐标字段均为 Web Mercator 空间。 */
data class MapInfo(
    val id: String,
    val gameName: String,
    val imagePath: String,
    val region: String,
    val area: String,
    val width: Double,
    val height: Double,
    val lowerX: Double,
    val lowerZ: Double,
    val upperX: Double,
    val upperZ: Double,
    val markerCount: Int,
    /**
     * 这一层最有代表性的地标名（如「第二层水星天 中央甲板」）。
     * 世界树有两张图都叫「下层」，只靠 menu_group 分不清，用它做副标题。
     */
    val landmark: String = "",
) {
    val aspect: Float get() = (width / height).toFloat().coerceAtLeast(0.05f)

    /** 图是 frontiernav 出的 Web Mercator 瓦片，纵轴要过 mercY 才能换算成像素。 */
    val mercator: Boolean get() = imagePath.contains("maps/frontiernav-")
}

/** 地图上的一个点位。frontier 坐标来自 DB，world 坐标可能为空。 */
data class Marker(
    val featureId: String,
    val mapId: String,
    val mapGameName: String,
    val frontierX: Double,
    val frontierY: Double,
    val worldX: Double?,
    val worldZ: Double?,
    val targetType: String,
    val targetName: String,
    val targetId: String,
)

data class QuestSummary(
    val id: Long,
    val title: String,
    val nameZh: String,
    val category: String,
    val npc: String,
    val chapter: String,
    val stepCount: Int,
    val isTorna: Boolean,
) {
    val display: String get() = nameZh.ifBlank { title }
}

data class StepLink(
    val stepPosition: Int,
    val target: String,
    val label: String,
) {
    /** `地点:landmark-xxx` 形式的内链目标 id；不是地点内链则返回 null。 */
    val placeFeatureId: String?
        get() = if (target.startsWith("地点:")) target.removePrefix("地点:").trim() else null

    val kind: String
        get() = when {
            placeFeatureId != null -> "地点"
            target.startsWith("异刃:") -> "异刃"
            target.startsWith("任务:") -> "任务"
            target.startsWith("打捞点:") -> "打捞点"
            target.startsWith("佣兵团任务:") -> "佣兵团"
            target.startsWith("御刃者:") -> "御刃者"
            target.startsWith("黄金之国任务:") -> "黄金国"
            else -> "其他"
        }
}

data class QuestStep(
    val position: Int,
    val kind: String,
    val text: String,
    val detail: String,
    val links: List<StepLink>,
)

data class QuestDetail(
    val id: Long,
    val title: String,
    val category: String,
    val npc: String,
    val chapter: String,
    val condition: String,
    val description: String,
    val gold: Int,
    val exp: Int,
    val locations: List<String>,
    val steps: List<QuestStep>,
    val rewardText: String,
)

data class BladeSummary(
    val id: Long,
    val name: String,
    val element: String,
    val role: String,
    val weapon: String,
    val nodeCount: Int,
    val portrait: String,
) {
    val elementColor: Long
        get() = when {
            element.contains("火") -> 0xFFE8603C
            element.contains("水") -> 0xFF3C8FE8
            element.contains("地") -> 0xFFC9A227
            element.contains("风") -> 0xFF4FBF7A
            element.contains("雷") -> 0xFF9B6BD6
            element.contains("光") -> 0xFFF2E08A
            element.contains("暗") -> 0xFF7A4FA3
            else -> 0xFF6FE3C4
        }
}

data class AffinityLevel(
    val id: Long,
    val level: Int,
    val effect: String,
    val requirement: String,
    val achievementId: String?,
    val achievementCount: Int,
    val links: List<StepLink>,
) {
    val learned: Boolean get() = requirement == "初始习得" || requirement.isBlank()

    /** 从 requirement 文本里抠出 "0/6" 这类进度数字。 */
    val progressText: String?
        get() {
            val m = Regex("""(\d+)\s*/\s*(\d+)""").find(requirement) ?: return null
            return m.value
        }
}

data class AffinityNode(
    val id: Long,
    val name: String,
    val category: String,
    val position: Int,
    val levels: List<AffinityLevel>,
)

data class SearchHit(
    val id: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val iconPath: String? = null,
)

data class EnemyAt(
    val entityId: Long,
    val name: String,
    val level: Int,
    val mapId: String,
    val featureId: String,
    val worldX: Double?,
    val worldZ: Double?,
)

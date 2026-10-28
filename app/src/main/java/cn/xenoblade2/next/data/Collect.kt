package cn.xenoblade2.next.data

/**
 * 采集候选的一处落点。
 *
 * [fx]/[fy] 是 FrontierNav 平面坐标，能直接画在本地底图上；
 * [px]/[py] 是 xenoblade2.cn 底图的像素坐标，留给后续切换到站点底图时使用。
 * 两者可能只有一边有值。
 */
data class CollectLocation(
    val mapId: String?,
    val mapGameName: String,
    val region: String?,
    val fx: Double? = null,
    val fy: Double? = null,
    val featureId: String? = null,
    val wikiArea: String? = null,
    val px: Double? = null,
    val py: Double? = null,
    val inside: Boolean = true,
) {
    val drawable: Boolean get() = !mapId.isNullOrBlank() && fx != null && fy != null
}

/** 一个采集点（collection_point）及其所有落点。 */
data class CollectCandidate(
    val collectionPointId: Long,
    val collectionCode: String,
    val itemNames: List<String>,
    val probability: Double,
    val region: String?,
    val fieldSkill: String?,
    val locations: List<CollectLocation>,
)

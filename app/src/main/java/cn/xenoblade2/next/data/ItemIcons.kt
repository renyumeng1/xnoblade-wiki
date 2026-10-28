package cn.xenoblade2.next.data

/**
 * 物品 → 分类图标。图标随原 App 的 item-icons 资源一起打进 assets（33 张 webp）。
 * 数据库只有 category / item_type 两个维度，没有单件物品图标，所以按分类取图。
 */
object ItemIcons {

    fun path(category: String?, itemType: String?): String? {
        val c = category.orEmpty()
        val t = itemType.orEmpty()
        return when (c) {
            "收藏道具" -> nested(
                "collectible-categories",
                when (t) {
                    "花草" -> "flower"
                    "矿石" -> "ore"
                    "蔬菜" -> "vegetable"
                    "机械" -> "mechanical"
                    "鱼" -> "fish"
                    "虫" -> "insect"
                    "树木" -> "tree"
                    "敌人掉落" -> "enemy-drop"
                    else -> null
                },
            )
            "收纳包道具" -> nested(
                "pouch-item-categories",
                when (t) {
                    "主食" -> "staple-foods"
                    "肉类" -> "meat"
                    "甜品" -> "desserts"
                    "蔬菜" -> "vegetables"
                    "鱼类" -> "seafood"
                    "饮料" -> "drinks"
                    "纺织品" -> "textiles"
                    "美术品" -> "art"
                    "化妆品" -> "cosmetics"
                    "书籍" -> "literature"
                    "乐器" -> "instruments"
                    "桌游" -> "board-games"
                    else -> null
                },
            )
            // item 表没有装备部位字段，饰品统一给通用图标
            "饰品" -> "item-icons/accessory-types/other-accessory-type.webp"
            "辅助核心" -> "item-icons/aux-core.webp"
            "宝藏" -> "item-icons/treasure-items/treasure.webp"
            else -> null
        }
    }

    private fun nested(dir: String, name: String?): String? =
        name?.let { "item-icons/$dir/$it.webp" }
}

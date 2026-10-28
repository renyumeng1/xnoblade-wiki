# 图标与地图标识补全

## Goal

消除列表里的空白图标位；地图点位用**形状**区分类型，而不是只靠颜色。

## 现状（已核对）

### 异刃图标

- `assets/blades/` 52 张 PNG；数据库 55 个异刃：
  - 53 行有 `portrait` 路径，其中 `blades/dagas.png` 被两行引用 → 52 个唯一文件，文件都存在。
  - `MOMO`、`MOMO (i)` 两行 `portrait` 为空 → 无图。
- `ui/screens/BladeScreens.kt:96/148` 已在列表和详情头部加载 portrait，空值时无兜底占位。

### 物品图标

- 原版 APK 的 `assets/item-icons/` 也只有 **33 个分类图标**，没有逐物品图标；原版是按分类映射。
- 我方 `data/ItemIcons.kt` 已有映射，但只被 `WikiRepo.kt:446`（搜索结果）引用；**没有物品列表页**，所以用户看不到图标。
- 映射缺口：`贵重品`（545 件，含关键物品/信息道具/店铺产权证）与 `核心晶片`（60 件）返回 null。
- 子分类没覆盖到的 `item_type` 也会返回 null。

### 地图标识

- `ui/components/MapCanvas.kt:176` `MarkerGlyph` 已定义 14 种形状（Landmark/Quest/Enemy/Collect/Treasure/Salvage/Heart/Shop/Inn/Gate/Climb/Jump/Npc/Dot），`:206 drawTypeGlyph` 已按类型绘制。
- 点位半径 `5.dp`（`:609`），随放大最多 1.35 倍；地标文字只在 `scale >= 1.6` 显示。
- 用户上轮反馈「标识太不显眼」「不同类型应不同标识」→ 形状已做，**观感尚未确认**。

## Requirements

1. **异刃占位图**：无 portrait 的异刃显示「属性色底 + 名称首字」占位，不留白。
2. **物品列表页**：新增 Items 页面，列表项显示 `ItemIcons.path(category, itemType)`；取不到时按 category 给通用占位。
3. **补全 ItemIcons 映射**：
   - `贵重品` → 通用贵重品/关键物品图标（无现成资源时用矢量占位，不伪造原版资源）
   - `核心晶片` → `core-chip.webp`
   - 逐个核对 `item` 表 `(category, item_type)` 组合，确保 0 个 null（除确实无分类的）
4. **地图标识可见性**：
   - 基础半径从 5dp 提升到 7dp（可配置常量），描边加粗
   - 形状线条加粗到 `r*0.12` 以上，深色底上对比度 ≥ 4.5:1
   - 图标小于 12dp 时退化为「形状轮廓 + 单色填充」，避免糊成一团
5. **图例**：地图页提供「图例」按钮，列出每种形状对应的类型名。
6. **层级**：计划 pin（数字序号）永远压在类型点之上，不被遮挡。

## Acceptance Criteria

- [ ] 55 个异刃在列表/详情中 0 个空白图
- [ ] 物品页每一行都有图标；`贵重品`/`核心晶片` 不再是空白
- [ ] 地图上 14 种类型可用形状区分（灰度截图也能分辨）
- [ ] 图例与实际形状一致
- [ ] 计划 pin 可见且在类型点之上

## Constraints

- 不新增逐物品图片资源（原版也没有），只做分类映射 + 占位。
- 占位图用 Compose 绘制，不复制原版素材。
- 改动集中在 `data/ItemIcons.kt`、`ui/components/MapCanvas.kt`、新增 `ui/screens/ItemScreens.kt`。

## Open Questions

- 是否需要从外部 Wiki 抓取逐物品图标？
  - 回答：需要做

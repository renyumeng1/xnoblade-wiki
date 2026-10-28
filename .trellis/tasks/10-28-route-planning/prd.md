# 地图逐步路线规划

## Goal

让任务/异刃计划在地图上「一步步可走」：每一步有明确落点、有从上一站到下一站的指引，并区分分层地图。

## 现状（已实现，待用户手测）

- `ui/screens/MapScreens.kt:153` 起：计划地图默认「只看计划点 + 最近传送点/地标」，可切回全部点位（`:306`）。
- `ui/components/MapCanvas.kt`：`placeStrict` 用于点位/地标/pin/命中判定；折线用宽松 `place()`。
- `data/MapMath.kt` 墨卡托纵轴；`data/MapSnap.kt` `MAX_FRAC=0.16` + `snapOrNull()`。
- 实测：97.9% 点位落在图上地板；严格模式隐藏 10/6239（0.16%）。

## Requirements

1. 计划详情 →「看地图」：按步骤顺序展示，当前步高亮，已完成步置灰。
2. 地图上只保留：**最近传送点/地标 + 计划点 + 路径折线**；无关 POI 默认隐藏。
3. 每一步给出可执行指引：`从 X 传送到 Y → 向 东北 走 ~120m → 到达 Z`（方位/距离来自 `route_between`）。
4. 步骤跨地图时提示「需切换到地图 A」，不允许把不同层/不同图的点画在同一张图上。
5. 无坐标的步骤明确标注「未定位」，可手动补坐标或跳过，不画假点。
6. 路线 UI 上注明「示意路线（非寻路）」，因为 `route_connection` 为空。

## Acceptance Criteria

- [ ] 任选一个 5 步以上任务计划：地图上点数 = 计划点数 + 最近传送点，无其他杂点
- [ ] 所有图标/文字都在地图可视区域内（用户上轮反馈的「标到地图外」不再出现）
- [ ] 步骤顺序与地图编号一致，点击步骤能定位到对应 pin
- [ ] 跨图步骤有明确切换提示
- [ ] 分层地图切换后，只显示该层点位

## Known Limitations

- 无真实路网：路径是点位图上的几何近似，不能承诺「最短/最优」。
- 部分室内小图坐标拟合失败（76 张中 41 张可拟合）→ 这些图只能显示「区域级」提示，不给精确 pin。
- 采集步骤自动落点率 32%，其余需人工确认。

## Code Pointers

- `app/src/main/java/cn/xenoblade2/next/ui/screens/MapScreens.kt`
- `app/src/main/java/cn/xenoblade2/next/ui/screens/PlanScreens.kt`
- `app/src/main/java/cn/xenoblade2/next/ui/components/MapCanvas.kt`
- `app/src/main/java/cn/xenoblade2/next/data/MapMath.kt`
- `app/src/main/java/cn/xenoblade2/next/data/MapSnap.kt`

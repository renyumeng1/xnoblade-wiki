# 计划顺序优化与路线串联

## Goal

让任务/异刃计划的**拜访顺序**尽量少跑路：按区域/地图分组，组内按距离就近串起来；
地图默认只画「点与点之间的连线」；原有逐段导航保留，但收进「路线规划（Beta）」开关。

> 2026-10-28 范围调整：逐段导航不再作为默认体验（路线规划太难且收益低），
> 改为 Beta 开关；默认体验是「顺序优化 + 连线」。

## 现状（已实现，待用户手测）

- `ui/screens/MapScreens.kt:153` 起：计划地图默认「只看计划点 + 最近传送点/地标」，可切回全部点位（`:306`）。
- `ui/components/MapCanvas.kt`：`placeStrict` 用于点位/地标/pin/命中判定；折线用宽松 `place()`。
- `data/MapMath.kt` 墨卡托纵轴；`data/MapSnap.kt` `MAX_FRAC=0.16` + `snapOrNull()`。
- 实测：97.9% 点位落在图上地板；严格模式隐藏 10/6239（0.16%）。

## Requirements

1. 计划详情 →「看地图」：按步骤顺序展示，当前步高亮，已完成步置灰。
2. 地图上只保留：**最近传送点/地标 + 计划点 + 路径折线**；无关 POI 默认隐藏。
3. **逐段导航改为 Beta 开关**（2026-10-28 第二次调整）：
   - 设置页新增「路线规划（Beta）」开关，默认关闭并标注「实验功能，建议只作方向参考」；
   - 关闭时：地图只画计划点 + 参考点位 + 点与点的连线，不出现「路线估算」「逐段导航」「‹ ›」；
   - 打开时：恢复现有 `RouteGraph` + `RouteGuide` 的路线估算与逐段导航；
   - 关闭状态下不计算 `RouteGraph`，避免白耗性能。
4. 计划生成时计算拜访顺序：
   - 先按地图（=区域/分层）分组，同图步骤连续排列；
   - 组内用最近邻串点，起点取上一组的最后一个点（没有就取本组第一个点）；
   - 无坐标的步骤排在最后，保持原始相对顺序。
5. 采集类需求在同等条件下优先高掉率采集点（`collection_drop.probability`），减少无效跑图。
6. 步骤跨地图时提示「需切换到地图 A」，不允许把不同层/不同图的点画在同一张图上。
7. 无坐标的步骤明确标注「未定位」，可手动补坐标或跳过，不画假点。

## Acceptance Criteria

- [ ] 任选一个 5 步以上任务计划：地图上点数 = 计划点数 + 最近传送点，无其他杂点
- [ ] 步骤顺序与地图编号一致，点击步骤能定位到对应 pin
- [ ] 跨图步骤有明确切换提示
- [ ] 分层地图切换后，只显示该层点位
- [ ] 默认（开关关闭）地图页不出现「路线估算」「逐段导航」「‹ ›」翻段控件
- [ ] 打开开关后，路线估算与逐段导航恢复，且重启后开关状态保留
- [ ] 开环计划生成后，同地图步骤连续，跨图步骤不来回跳
- [ ] 同一需求的多个采集点候选中，高掉率候选排在前面

## Known Limitations

- 无真实路网：顺序是「按图分组 + 最近邻」的启发式，不承诺全局最优。
- 同一张图内的分层（上层/下层/腹部）不做跨层距离换算，按地图 id 直接分组。
- 部分室内小图坐标拟合失败（76 张中 41 张可拟合）→ 这些图只能显示「区域级」提示，不给精确 pin。
- 采集步骤自动落点率 32%，其余需人工确认。

## Code Pointers

- `app/src/main/java/cn/xenoblade2/next/ui/screens/MapScreens.kt`
- `app/src/main/java/cn/xenoblade2/next/ui/screens/PlanScreens.kt`
- `app/src/main/java/cn/xenoblade2/next/ui/components/MapCanvas.kt`
- `app/src/main/java/cn/xenoblade2/next/plan/Planner.kt`
- `app/src/main/java/cn/xenoblade2/next/data/MapMath.kt`
- `app/src/main/java/cn/xenoblade2/next/data/MapSnap.kt`

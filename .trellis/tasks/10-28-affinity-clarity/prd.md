# 异刃开环任务步骤明确化

## Goal

把「开环」从「一句需求原文」变成可执行的待办：每个节点缺什么、去哪拿、能不能跳转。

## 现状（已核对数据）

- 表：`blade_affinity_node` 528 行、`blade_affinity_level` 2372 行、`blade_affinity_level_link` 582 行。
- `blade_affinity_level` 有 `effect` / `requirement` / `requirement_raw_en` / `requirement_source` / `frontier_parse_status`。
- `blade_affinity_level_link` 把节点级需求关联到 `任务:xxx` / `地点:xxx` / 敌人名 / 物品名，共 582 条 → **约 75% 的环等级没有结构化关联**。
- UI `ui/screens/BladeScreens.kt:175` 只有「开环概览」+ 生成计划按钮，节点/等级的需求与途径未展开。

## Requirements

1. 异刃详情页：按「圆环 → 节点 → 等级」三级展示，默认展开未完成节点。
2. 每个等级行显示：
   - 效果（effect）
   - 需求原文（requirement）
   - **获取途径**：有 link 则列「任务 / 地点 / 敌人 / 物品」并可点击跳转；无 link 显示「暂无结构化途径」
   - 解析状态（`frontier_parse_status`）异常时用弱化样式标注，不隐藏
3. 从途径跳转：
   - `任务:xxx` → 任务详情
   - `地点:xxx` → 地图/地点详情并定位
   - 敌人/物品 → 对应列表搜索
4. 「生成开环计划」保留，但生成前展示将写入的步骤数与其中「未定位」数量，让用户知道哪些要手动补。
5. 提供筛选：只看未完成 / 只看有途径 / 只看解析失败。

## Acceptance Criteria

- [ ] 任选 3 把异刃（含光/焰或其他有环的），节点与等级数量与 DB 一致
- [ ] 有 link 的等级可一键跳到任务/地点/敌人/物品
- [ ] 无 link 的等级明确显示「暂无结构化途径」，不臆造
- [ ] 生成的开环计划中，未定位步骤有明确标记
- [ ] 筛选三个模式结果正确

## Constraints

- 不做数据补全式编造：缺映射就显示缺。
- 若要从 `quest_flow_item_acquisition` / `collection_drop` / `enemy_drop` 反推途径，必须标注「推断」并给置信度。
- 改动集中在 `BladeScreens.kt`、`data/` 下的查询层、`plan/` 的开环计划生成。

## Known Limitations

- `requirement_source` 只有 49 行为空，但 `requirement_raw_en` 里存在无法结构化解析的描述（如「在特定天气击杀 xx」）。
- FrontierNav 来源的 2323 行解析质量参差，`frontier_parse_status` 需在 UI 暴露。

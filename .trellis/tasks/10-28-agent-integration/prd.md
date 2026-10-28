# Agent 接入与工具扩展

## Goal

用 App 内真实数据支撑自然语言规划：查任务、查开环、查地图、生成计划，并能解释数据边界。

## 现状

- `agent/AgentEngine.kt`：OpenAI 兼容 chat + tool calling 循环。
- `agent/AgentTools.kt`：已有 16 个工具：`list_quests` / `get_quest` / `create_quest_plan` / `list_blades` / `get_blade` / `create_blade_plan` / `find_place` / `find_enemy` / `list_maps` / `map_markers` / `collect_spots` / `route_between` / `list_plans` / `get_plan` / `add_plan_step` / `set_step_done` / `search_wiki`。
- `agent/OfflineAgent.kt`：无网络时的兜底。
- 配置：endpoint / model / key 存模拟器 SharedPreferences；**不得写入仓库任何文件**。

## Requirements

1. 设置页提供 Agent 配置：endpoint、model、key（密码框，仅本地保存）、连通性测试。
2. 对话中必须把工具结果转成用户能执行的步骤，而不是只复述数据。
3. 新增/完善工具：
   - `plan_route`：给定 plan_id，输出按顺序的逐步指引（含地图切换提示）
   - `blade_affinity_gaps`：某异刃未完成节点的需求与途径
   - `item_sources`：物品 → 采集点/怪物掉落/商店
   - `update_plan_step`：修改步骤坐标/标题
4. 回答约束（写进 system prompt）：
   - 无路网时说明「示意路线」
   - 数据缺失时说明「库中无此映射」，不得编造坐标
   - 区分「库内数据」与「模型推测」 `[INFERENCE]`
5. 失败与超时要有可见错误，不得静默返回空。

## Acceptance Criteria

- [ ] 配置正确时可完成一次多轮工具调用（查任务 → 生成计划 → 查地图点）
- [ ] 生成的计划出现在计划页且可打开地图
- [ ] 询问「某个采集物在哪」时，返回真实 `collection_drop` 命中的点位，并标注命中率
- [ ] 询问不存在的数据时回答「库中无」，不虚构
- [ ] 断网/错误 endpoint 时给出明确提示

## Known Limitations

- 大模型**不能**补出缺失路网或未拟合的坐标；只能做语义匹配与排序。
- 32% 采集落点率不会因为接 LLM 提升到 100%；LLM 可辅助选择候选点，但需人工确认。
- 76 图中 41 张可拟合，其余室内图无法给出精确坐标。

## Security

- 日志里不得打印完整 key（只显示后 4 位）。
- 请求体不写入本地日志文件。

# 分层地图处理

## Goal

同一地区多层地图（世界树、室内多层、洞窟）能正确切换，点位归属正确，不把 A 层的点画到 B 层。

## 现状（已核对）

- `map` 表字段：`id / name / image_path / image_url / display_name / menu_group / width / height / lower_x,y,z / upper_x,y,z / marker_count`，**没有显式 layer 字段**。
- 分层靠 `menu_group` + `area` + `landmark` 推导：
  - `ui/screens/MapScreens.kt:715 layerLabel(m, siblings)`：同 `area` 多张图时显示「区域 · 地标」
  - `Models.kt:21 landmark`：取该层最有代表性的地标名
- 地图页已有同组图 chips（`MapScreens.kt:315-340`），可切换。
- 76 张图中 41 张能拟合「世界坐标 → 平面坐标」，其余多为室内小图、样本不足。

## Requirements

1. **层识别**：给每张图算出稳定的 `layerKey = menu_group + area`，同 key 视为同地区不同层；标签优先用 `landmark`，其次 `display_name`，最后 `gameName`。
2. **切换器**：地图页顶部 chips 显示全部同层图，当前层高亮，并显示该层计划步骤数。
3. **点位归属**：`map_marker` 必须按 `map_id` 过滤；**禁止**按 area/region 跨图取点。
4. **计划跨层**：计划步骤记录 `map_id`；切层后只显示该层步骤，跨层步骤给「→ 切到 X 层」按钮。
5. **坐标容错**：拟合失败的图（室内小图）不画精确 pin，改为「区域示意 + 文字提示」。
6. **验收数据**：列出所有 `menu_group + area` 组合中图数 > 1 的组，逐组人工过一遍。

## Acceptance Criteria

- [ ] 世界树 4 张「下层」图能正确区分并切换，标签不重复
- [ ] 切换层后地图上的点位集合完全变化，无跨层残留
- [ ] 计划步骤在错误层不显示，且有切换提示
- [ ] 室内小图不再出现点位飞出地图
- [ ] 生成一份「多层地图清单」写入 `docs/DATA_NOTES.md`

## Known Limitations

- `map` 表无显式层号，只能靠 `menu_group/area/landmark` 推断 `[INFERENCE]`。
- 41/76 可拟合意味着部分图只能做区域级定位。

## Code Pointers

- `app/src/main/java/cn/xenoblade2/next/ui/screens/MapScreens.kt`（`:153` 计划模式、`:315` chips、`:715 layerLabel`）
- `app/src/main/java/cn/xenoblade2/next/data/Models.kt`（`MapInfo.landmark`）
- `app/src/main/java/cn/xenoblade2/next/data/WikiRepo.kt`（地图与点位查询）

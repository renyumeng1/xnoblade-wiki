# Research · 原版 1.2.2 对照事实

来源：`apk/xenoblade2Wiki-1.2.2.apk`、`src/jadx/sources/cn/xenoblade2/wiki/`、`apk/extracted/assets/xenoblade2.db`、实机截图。

## 配色（XenoColors.java，ARGB）

浅色：`WikiBackground #FFF7F9FC`、`PyraRed #FFD40011`、`MythraGold #FFFFC107`、`Purple #FF7653B5`、`SalvageCyan #FF56EBFF`、`TextMain #FF1A1C1F`、`Card #FFFEFEFF`、`CardBorder #2B5C5C60`、`Secondary #FF73757C`、`Raised #FFFFFFFF`、`TopAppBar #FFFFF0F2`。

深色：`#FF0F1115 / #FFFF4D5A / #FFFFD166 / #FFD38BFF / #FF38BDF8 / #FFF1F3F7 / #FF161920 / #FF2A303C / #FF9CA3AF / #FF1E222B / #FF12141A`。

## 首页 20 卡片（HomeScreenKt.java:64，两列）

journey 游玩经历 · planner 素材规划 · mercenary-training 佣兵团训练 · blades 异刃 · fieldSkills 场景技能 · drivers 御刃者 · quests 任务 · items 物品 · weapons 武器 · weather 天气 · monsters 怪物 · collection 采集点 · salvage 打捞点 · chests 宝箱 · maps 交互地图 · locations 地点 · shops 商店 · heart 牵绊对话 · poppi 花的零件 · challenge 挑战战斗。

## 底部 tab（MainActivityKt.java:135）

home 首页 / blades 异刃 / quests 任务 / enemies 怪物 / settings 设置；另有 20 个可配置 tab 映射（`:245`）。

## 页面结构

- 异刃：多选 + 搜索 + 全部/已收集/未收集 + 属性 chip + 补正 chip + 大立绘网格。
- 任务：普通 / 异刃 / 佣兵团 / 非正式 四张大卡片。
- 怪物：搜索 + 收集筛选 + 地区 chip + 普通/珍稀种 chip + 分组。
- 设置：Tab 页设置 / 外观 / 内容偏好 / 版本更新。
- 游玩经历：9 个统计卡（异刃/物品/任务/佣兵团/非正式/怪物/宝箱/收纳包商店/牵绊对话）。

## 资源事实

- `assets/item-icons/` 只有 33 个分类图标，无逐物品图标。
- APK 内无 `assets/blades/`；`blade.portrait` 写 `blades/xxx.png`，说明原版从别处加载 `[INFERENCE]`。

## 刻意不复刻

更新检查、数据库增量更新、友盟统计、AD_ID、隐私弹窗。

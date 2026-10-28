# UI/功能对齐原版 1.2.2

## Goal

把 App 的视觉与信息架构对齐原版 1.2.2（`cn.xenoblade2.wiki`），同时保留我方已有的计划与 Agent 能力。

## 现状

- `ui/theme/Theme.kt`：硬编码深色 + 青色系，与原版浅色配色不是一套。
- `ui/App.kt:136`：底部 tab 为 首页/任务/异刃/地图/计划（固定 5 个）。
- `ui/screens/HomeScreen.kt`：统计卡 + 4 宫格 + 进行中计划。
- `ui/screens/BladeScreens.kt`：小圆头像列表。
- `ui/screens/QuestScreens.kt`：扁平列表 + chip。

## Requirements

1. **配色**：`Palette` 拆浅/深两套，取值为 `design.md` 第 2.1 节的十六进制；默认浅色，设置页可选「跟随系统 / 浅色 / 深色」。
2. **首页**：顶部搜索框 + 两列大卡片网格，20 个入口按 `design.md` 第 2.2 节的顺序与文案；无数据模块进入统一「未收录」占位页。
3. **底部导航**：固定 5 tab = 首页 / 异刃 / 任务 / 怪物 / 设置；设置页增加「底部 Tab 页设置」，可从 20 个入口里选 5 个并调整顺序，持久化。
4. **异刃页**：大立绘网格 + 多选收集 + 全部/已收集/未收集 + 属性 chip + 补正 chip。
5. **任务页**：四大类卡片（普通 / 异刃 / 佣兵团 / 非正式），进入后各自列表。
6. **怪物页**：搜索 + 收集筛选 + 地区 chip + 普通/珍稀种 chip + 分组列表。
7. **设置页**：Tab 设置 / 外观 / 内容偏好 / 关于（不复制原版的友盟、更新检查、隐私弹窗）。
8. **保留**：Agent 入口、「进行中计划」区块；位置由视觉稿决定，不直接删除。

## Acceptance Criteria

- [ ] 首页截图与原版 `/tmp/ref_home6.png` 并排比对：卡片数量、顺序、文案、两列布局一致
- [ ] 底部 5 tab 与原版一致，且设置中修改后重启仍生效
- [ ] 异刃页能看到大立绘与属性/补正筛选，不再是小圆头像
- [ ] 任务页四大类可分别进入，计数正确
- [ ] 浅色/深色切换后无对比度问题（重点检查地图页与 Agent 页）
- [ ] 未收录模块有明确占位说明，不出现空白页或崩溃

## Constraints

- 文案可对齐，但**不复制**原版代码/资源；布局用现有 Compose 体系重写。
- 不引入新 UI 框架；不接统计 SDK。
- 改动集中 `ui/theme/`、`ui/App.kt`、`ui/screens/*`、`ui/components/`。

## Known Gaps

- 原版 20 个入口中，Mercenary Training / Field Skills / Drivers / Weapons / Weather / Shops / Heart-to-Hearts / Poppi Parts / Challenge Battles 等，我方数据库是否有对应表需逐一核对；缺表的先占位。

## Notes

- 建议分两批：**批 A**（主题 + 首页 + tab + 设置）、**批 B**（异刃/任务/怪物页重做），每批单独编译安装给用户手测。
- 依赖 `10-28-icons-markers` 提供图标，避免做完布局又返工。

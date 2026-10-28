# Android（Compose）开发规范

单模块 Compose 应用，包名 `cn.xenoblade2.next`。这一层只管 `app/src/main/java/cn/xenoblade2/next/`
下的 UI、状态与计划算法约定；SQL / 数据库规则见 `backend/`。

## Pre-Development Checklist

写任何 UI 或计划算法代码之前先过一遍：

- [ ] 这个页面要读的全局设置是不是 `Prefs` 里的 `mutableStateOf`？**直接在组合里读，不要用 `remember` 包。**
- [ ] 新增的可点击元素在暗色主题下前景/背景对比度是否 ≥ 4.5:1？
- [ ] 改 `Planner` 的排序或过滤，会不会改变已存在计划的 `plan_step.ord` 语义？
- [ ] 新增文案是不是中文？有没有把英文内部名（表名、`targetType`）直接显示给用户？

## Guidelines Index

| 文档 | 内容 | 状态 |
|------|------|------|
| [Compose 状态与主题](./compose-state-and-theme.md) | 读 `Prefs`、`rememberSaveable`、暗色对比度 | 已填 |
| [计划算法契约](./planner-contracts.md) | `buildBladePlan` / `orderForWalking` 的签名与边界行为 | 已填 |

## Quality Check

提测前跑：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:assembleDebug
```

项目没有 test 源集，也没有 ktlint/detekt。`./gradlew :app:lintDebug` 目前有 5 个既有
`ProduceStateDoesNotAssignValue` 错误（`AssetImage.kt:23`、`MapCanvas.kt:541/545/553`、
`MapScreens.kt:1251`），与业务改动无关，不能拿来当「本次改动通过」的证据；只看 lint 报告里
**新增**的条目。

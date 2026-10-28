# 实施 · 开环计划可选范围

## 顺序

1. `plan/Planner.kt`：`buildBladePlan` 接受 `levelIds`，只处理选中的等级。
2. `ui/screens/BladeScreens.kt`：
   - 选择态 + 快捷 chip（第 N 层 / 全选未习得 / 清空）；
   - `NodeCard` 的节点级「加入 / 移出」与等级级勾选；
   - 概览卡显示「已选 N 级 · 预计 M 步」，按钮禁用态。
3. 生成计划后把选择清空（避免下次误生成同一批）。

## 验收命令

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

手测路径：

1. 图鉴 → 异刃 → 任选一把有环的 → 牵绊圆环 tab。
2. 只点一个节点的「加入」→ 生成 → 检查计划步骤数 == 该节点未习得等级数。
3. 只点顶部「第 2 层」→ 生成 → 检查每步都是 Lv2。
4. 清空 → 生成按钮不可点且有提示。

## 回滚点

- 只改 `Planner.buildBladePlan` 的签名与 `BladeScreens.kt` 的圆环 tab；出问题可单独回退这两个文件。

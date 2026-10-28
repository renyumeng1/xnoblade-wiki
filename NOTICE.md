# 素材来源与授权

本项目是非商业的同人工具，与 Monolith Soft / 任天堂无关。

## xenoblade2.cn（乐园数据管理室）

- 站点：https://xenoblade2.cn
- 数据与图片授权：CC BY-NC-SA 4.0
- 用到的内容：
  - `app/src/main/assets/wiki-icons/`、`assets/map-markers/`：地图与图鉴用的游戏内标记图标
  - `assets/regions/`：地区缩略图
  - 运行期按需拉取的采集点实景截图（`WikiShots.kt`，不随安装包分发）
  - 图鉴文本、任务步骤、采集点坐标等结构化数据（构建期导入本地库）

界面上出现这些素材的位置都会标注「图源：乐园数据管理室 xenoblade2.cn · CC BY-NC-SA 4.0」。

## 原版 APK

`app/src/main/assets/maps/`、`assets/item-icons/`、`assets/xenoblade2.db` 是从用户自备的原版 APK 中提取的对照素材，
**不纳入版本库**、不随本仓库分发，只在本地通过 `tools/restore_assets.sh` 还原后用于开发比对。

## 代码

本仓库的应用代码为独立实现，未使用原 APK 的源码。

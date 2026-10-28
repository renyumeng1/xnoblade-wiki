# xenoblade2.cn 地图来源

站点是 MediaWiki 1.43 + Semantic MediaWiki。许可为 CC BY-NC-SA 4.0，署名「乐园数据管理室 https://xenoblade2.cn」。

地图不是 FrontierNav 瓦片。`JSON:Mapinfo/<id>` 有宽高和世界坐标范围，共 90 张。底图文件名是 `<Name>_map_0.png`。点位坐标在 `JSON:Gmk/collection|enemy|npc|salvage|tbox`。点位图标来自 Gadget `Xb2map.js`：`Collection-marker-icon.png`、`Enemy.png`、`Salvage.png`、`Tbox.png`、`npc.svg`。

本地 `app/src/main/assets/xenoblade2.db` 已有 `map`、`frontiernav_map_marker`、`collection_marker` 等表，图源是 frontiernav.net。抓取结果先放 `data/wiki-xenoblade2/`，对过差异后再补库，不整表覆盖。

`JSON:` 命名空间有 11486 页，其中大量 `BTL_Arts` 是游戏表的逐行拆分，和现有数据库重复，抓取脚本不下载这些页。

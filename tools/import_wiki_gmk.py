#!/usr/bin/env python3
"""把 xenoblade2.cn 的 Gmk 采集点坐标投影进本地 wiki 库。

来源：
  data/wiki-xenoblade2/json/gmk/JSON__Gmk__collection.json
  data/wiki-xenoblade2/json/mapinfo/*.json

目标表（只新增，不改动原表）：
  wiki_mapinfo(name, display_name, menu_group, width, height, lower_*, upper_*)
  wiki_collection_point(collection_code, area, pos_x, pos_y, pos_z, px, py, inside)

投影与站点 Leaflet 控件一致：
  px = (PosX - LowerX) / (UpperX - LowerX) * Width
  py = (UpperZ - PosZ) / (UpperZ - LowerZ) * Height

数据库文件在 .gitignore 里，不随仓库分发；重建方法：
  python3 tools/import_wiki_gmk.py
"""

from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB = ROOT / "app/src/main/assets/xenoblade2.db"
DEFAULT_DATA = ROOT / "data/wiki-xenoblade2"


def read_mapinfo(folder: Path) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for path in sorted((folder / "json/mapinfo").glob("*.json")):
        raw = json.loads(path.read_text(encoding="utf-8"))
        name = str(raw["Name"])
        out[name.lower()] = raw
    return out


def project(point: dict, info: dict) -> tuple[float, float, bool]:
    lower_x = float(info["LowerX"])
    upper_x = float(info["UpperX"])
    lower_z = float(info["LowerZ"])
    upper_z = float(info["UpperZ"])
    width = float(info["Width"])
    height = float(info["Height"])
    px = (float(point["PosX"]) - lower_x) / (upper_x - lower_x) * width
    py = (upper_z - float(point["PosZ"])) / (upper_z - lower_z) * height
    inside = 0.0 <= px <= width and 0.0 <= py <= height
    return px, py, inside


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, default=DEFAULT_DB)
    ap.add_argument("--data", type=Path, default=DEFAULT_DATA)
    args = ap.parse_args()

    gmk_path = args.data / "json/gmk/JSON__Gmk__collection.json"
    if not gmk_path.exists():
        raise SystemExit(f"缺少 {gmk_path}，先跑 tools/fetch_xenoblade2_wiki.py")
    points = json.loads(gmk_path.read_text(encoding="utf-8"))
    mapinfo = read_mapinfo(args.data)

    rows: list[tuple] = []
    missing_area: set[str] = set()
    outside = 0
    for point in points:
        code = str(point["Name"])
        for area in point.get("areas") or []:
            info = mapinfo.get(str(area).lower())
            if info is None:
                missing_area.add(str(area))
                continue
            px, py, inside = project(point, info)
            if not inside:
                outside += 1
            rows.append(
                (
                    code,
                    str(area),
                    float(point["PosX"]),
                    float(point["PosY"]),
                    float(point["PosZ"]),
                    px,
                    py,
                    1 if inside else 0,
                )
            )

    con = sqlite3.connect(args.db)
    try:
        con.execute("PRAGMA journal_mode=WAL")
        con.executescript(
            """
            CREATE TABLE IF NOT EXISTS wiki_mapinfo(
                name TEXT PRIMARY KEY,
                display_name TEXT NOT NULL DEFAULT '',
                menu_group TEXT NOT NULL DEFAULT '',
                width REAL NOT NULL,
                height REAL NOT NULL,
                lower_x REAL NOT NULL,
                lower_y REAL NOT NULL,
                lower_z REAL NOT NULL,
                upper_x REAL NOT NULL,
                upper_y REAL NOT NULL,
                upper_z REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS wiki_collection_point(
                collection_code TEXT NOT NULL,
                area TEXT NOT NULL,
                pos_x REAL NOT NULL,
                pos_y REAL NOT NULL,
                pos_z REAL NOT NULL,
                px REAL NOT NULL,
                py REAL NOT NULL,
                inside INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(collection_code, area)
            );
            CREATE INDEX IF NOT EXISTS wiki_collection_code
                ON wiki_collection_point(collection_code);
            CREATE INDEX IF NOT EXISTS wiki_collection_area
                ON wiki_collection_point(area);
            """
        )
        con.execute("DELETE FROM wiki_mapinfo")
        con.execute("DELETE FROM wiki_collection_point")
        con.executemany(
            """
            INSERT INTO wiki_mapinfo(name, display_name, menu_group, width, height,
                                     lower_x, lower_y, lower_z, upper_x, upper_y, upper_z)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,
            [
                (
                    str(info["Name"]),
                    str(info.get("mapName", "")),
                    str(info.get("menuGroup", "")),
                    float(info["Width"]),
                    float(info["Height"]),
                    float(info["LowerX"]),
                    float(info["LowerY"]),
                    float(info["LowerZ"]),
                    float(info["UpperX"]),
                    float(info["UpperY"]),
                    float(info["UpperZ"]),
                )
                for info in mapinfo.values()
            ],
        )
        con.executemany(
            """
            INSERT INTO wiki_collection_point(
                collection_code, area, pos_x, pos_y, pos_z, px, py, inside
            ) VALUES(?,?,?,?,?,?,?,?)
            """,
            rows,
        )
        con.commit()

        matched = con.execute(
            """
            SELECT COUNT(DISTINCT cp.entity_id)
            FROM collection_point cp
            JOIN wiki_collection_point w ON w.collection_code = cp.collection_code
            """
        ).fetchone()[0]
        print(f"mapinfo: {len(mapinfo)}")
        print(f"collection point-area rows: {len(rows)} ({outside} outside bounds)")
        print(f"Gmk codes: {len({r[0] for r in rows})}, matched local collection_point: {matched}")
        if missing_area:
            print("areas without Mapinfo:", ", ".join(sorted(missing_area)))
    finally:
        con.close()


if __name__ == "__main__":
    main()

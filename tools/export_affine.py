#!/usr/bin/env python3
"""导出每张地图的「世界坐标 → frontier 平面坐标」线性仿射系数。

算法与 App 内 `MapMath.AffineFit.fit` 完全一致：同一张图上所有同时带
`world_x/world_z` 与 `frontier_x/frontier_y` 的 marker 做一维最小二乘
`f = a * w + b`，x / y 两个方向各拟合一次。

用途：
  1. 校验 `frontiernav_map_marker.transform_rmse` 与现算误差的数量级；
  2. 给只有 world 坐标、没有 frontier 坐标的数据源（`enemy_spawn` 等）换算落图坐标。

用法：
    python3 tools/export_affine.py                  # 打印汇总
    python3 tools/export_affine.py -o affine.json   # 另存 JSON
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sqlite3
import sys

DB = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "xenoblade2.db",
)

# App 侧 Affine1D.usable 的门槛
MIN_SAMPLES = 3
MAX_RMSE = 25.0


def fit(us: list[float], vs: list[float]) -> dict | None:
    n = len(us)
    if n < 2 or n != len(vs):
        return None
    mu = sum(us) / n
    mv = sum(vs) / n
    cov = sum((u - mu) * (v - mv) for u, v in zip(us, vs))
    var = sum((u - mu) ** 2 for u in us)
    if var < 1e-9:
        return None
    a = cov / var
    b = mv - a * mu
    se = sum((a * u + b - v) ** 2 for u, v in zip(us, vs))
    return {"a": a, "b": b, "rmse": math.sqrt(se / n), "samples": n}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("-o", "--out", default="", help="输出 JSON 路径（默认不写文件）")
    ap.add_argument("--db", default=DB)
    args = ap.parse_args()

    if not os.path.exists(args.db):
        print(f"找不到数据库：{args.db}", file=sys.stderr)
        return 2

    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    rows = con.execute(
        """
        SELECT map_id,
               world_x, world_z, frontier_x, frontier_y, transform_rmse
        FROM frontiernav_map_marker
        WHERE world_x IS NOT NULL AND world_z IS NOT NULL
          AND frontier_x IS NOT NULL AND frontier_y IS NOT NULL
        ORDER BY map_id
        """
    ).fetchall()
    maps = con.execute("SELECT id, COALESCE(display_name, id) FROM map ORDER BY id").fetchall()
    con.close()

    by_map: dict[str, list[tuple]] = {}
    for r in rows:
        by_map.setdefault(r[0], []).append(r)

    out: dict[str, dict] = {}
    usable = 0
    for map_id, _display in maps:
        pts = by_map.get(map_id, [])
        fx = fit([p[1] for p in pts], [p[3] for p in pts])
        fy = fit([p[2] for p in pts], [p[4] for p in pts])
        stored = [p[5] for p in pts if p[5] is not None]
        ok = bool(
            fx and fy and fx["samples"] >= MIN_SAMPLES and fy["samples"] >= MIN_SAMPLES
            and fx["rmse"] < MAX_RMSE and fy["rmse"] < MAX_RMSE
        )
        usable += 1 if ok else 0
        out[map_id] = {
            "display_name": _display,
            "samples": len(pts),
            "usable": ok,
            "x": fx,
            "y": fy,
            "stored_rmse": {
                "count": len(stored),
                "median": sorted(stored)[len(stored) // 2] if stored else None,
                "max": max(stored) if stored else None,
            },
        }

    total = len(maps)
    print(f"地图 {total} 张，带 world 坐标的 marker {len(rows)} 个")
    print(f"可拟合（样本≥{MIN_SAMPLES} 且两轴 RMSE<{MAX_RMSE}）：{usable}/{total}")
    print()
    print(f"{'map_id':38} {'pts':>4} {'x_rmse':>8} {'y_rmse':>8} {'ok':>3}")
    for map_id, info in sorted(out.items(), key=lambda kv: -(kv[1]["samples"] or 0))[:12]:
        fx, fy = info["x"], info["y"]
        xr = "%.3f" % fx["rmse"] if fx else "-"
        yr = "%.3f" % fy["rmse"] if fy else "-"
        print(f"{map_id:38} {info['samples']:>4} {xr:>8} {yr:>8} {'Y' if info['usable'] else 'n':>3}")
    worst = [i for i in out.values() if i["y"] and i["y"]["rmse"] >= MAX_RMSE]
    if worst:
        print()
        print(f"y 轴误差超过 {MAX_RMSE} 的地图 {len(worst)} 张（多层地形压平所致，落图需人工校对）")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f"\n已写入 {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

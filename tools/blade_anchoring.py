#!/usr/bin/env python3
"""开环需求落点统计（`Planner.buildBladePlan` 的离线复刻版）。

对比两套逻辑：
  old —— 改版前：地点内链 > 全局地名 > 敌人 > 采集品（不看地区、不认「XX族」、不认 NPC）
  new —— 现在：地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（含族名，只认同地区刷新点） > 采集品
         都定不到但需求里写了地区 → 落到该地区主图（图级定位）

用法：python3 tools/blade_anchoring.py [--samples N] [--blade 焰]
"""
from __future__ import annotations

import argparse
import re
import sqlite3
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "app/src/main/assets/xenoblade2.db"

PLACE_TYPES = (
    "Location", "DoorPoint", "WarpPoint", "ElevatorPoint", "JumpPoint",
    "Quest", "Shop", "Inn", "HeartToHeart", "SalvagePoint",
)


def load(conn):
    maps = {r[0]: (r[1], r[2]) for r in conn.execute(
        "SELECT id, display_name, menu_group FROM map")}
    markers = {}
    for fid, map_id, name, region in conn.execute(
        f"""
        SELECT f.feature_id, f.map_id, TRIM(COALESCE(f.target_name,'')), m.display_name
        FROM frontiernav_map_marker f LEFT JOIN map m ON m.id = f.map_id
        WHERE f.target_name IS NOT NULL AND LENGTH(TRIM(f.target_name)) >= 2
          AND f.target_type IN ({','.join('?' * len(PLACE_TYPES))})
        """, PLACE_TYPES):
        if map_id:
            markers[fid] = (map_id, name, region)
    places = sorted(
        [(n, fid, mid, reg) for fid, (mid, n, reg) in markers.items()],
        key=lambda x: -len(x[0]))

    npcs = []
    for name, mid, fx, fy in conn.execute(
        """
        SELECT TRIM(e.title), m.id, g.x, g.z
        FROM npc_game_appearance g
        JOIN entity e ON e.id = g.npc_id
        JOIN map m ON m.name = g.game_map
        WHERE e.title IS NOT NULL AND LENGTH(TRIM(e.title)) >= 2 AND g.game_map <> ''
        """):
        npcs.append((name, mid, fx, fy))
    npcs.sort(key=lambda x: -len(x[0]))

    enemies = sorted({r[0] for r in conn.execute(
        "SELECT title FROM entity WHERE type='enemy' AND title IS NOT NULL") if len(r[0] or "") >= 2},
        key=len, reverse=True)

    # enemy title -> [(map_id, feature_id)]，与 WikiRepo.enemySpawns 同一条 JOIN
    spawns = {}
    for title, map_id, fid in conn.execute(
        """
        SELECT e.title, f.map_id, f.feature_id
        FROM entity e
        JOIN enemy en ON en.entity_id = e.id
        JOIN enemy_spawn sp ON sp.enemy_id = en.enemy_code
        JOIN frontiernav_map_marker f ON f.target_id = sp.spawn_id
        WHERE f.map_id IS NOT NULL AND f.map_id <> ''
        GROUP BY f.feature_id
        """):
        spawns.setdefault(title, []).append((map_id, fid))

    collect_items = sorted({r[0] for r in conn.execute(
        """
        SELECT DISTINCT d.item_name
        FROM collection_drop d
        JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
        JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
        WHERE LENGTH(d.item_name) >= 2
        """)}, key=len, reverse=True)

    # item -> [(map_id, feature_id, marker_name)]
    item_spots = {}
    for item, map_id, fid, name in conn.execute(
        """
        SELECT d.item_name, f.map_id, f.feature_id,
               COALESCE(f.target_name, f.target_game_name, f.feature_id)
        FROM collection_drop d
        JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
        JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
        """):
        item_spots.setdefault(item, []).append((map_id, fid, name))

    return maps, markers, places, npcs, enemies, spawns, collect_items, item_spots


def norm(s):
    return (s or "").replace("的", "").replace(" ", "").strip()


def region_of(text, region_names):
    for r in region_names:          # 全长优先
        if r in text:
            return r
    for r in region_names:          # 简称：前 3~8 字
        for n in range(min(len(r) - 1, 8), 2, -1):
            if r[:n] in text:
                return r
    return None


def match_place(text, pool):
    for name, fid, mid, reg in pool:
        if name in text:
            return fid, mid, name
    return None


def match_enemy(text, enemies):
    for e in enemies:
        if e in text:
            return e
    return None


def match_family(text, enemies):
    if "族" not in text:
        return None
    head = text[: text.rfind("族")]
    parts = re.split(r"的|在|打倒|讨伐|击败|以外|只| |　|：|:|\u300c|\u300d", head)
    base = next((p for p in reversed(parts) if p.strip()), "")
    if len(base) < 2:
        return None
    return base if any(base in e for e in enemies) else None


def match_npc(text, pool, region):
    hint = norm(region)
    for name, mid, fx, fy in pool:
        if name not in text:
            continue
        if not hint:
            return name, mid, fx, fy
        r = norm(maps_ref[mid][0])
        if r and (hint in r or r in hint):
            return name, mid, fx, fy
    return None


def anchor(req, links, maps, places, region_names, npcs, enemies, spawns, collect_items, item_spots, new):
    for fid in links:
        if fid in maps_ref:
            return "内链", fid
    region = region_of(req, region_names) if new else None
    pool = [p for p in places if region is None or p[3] == region]
    hit = match_place(req, pool) or (new and match_place(req, places))
    if hit:
        return "地名", hit[1]
    if new and not any(k in req for k in ("打倒", "讨伐", "击败", "战斗")):
        npc = match_npc(req, npcs, region)
        if npc:
            return "NPC", npc[1]
    enemy = match_enemy(req, enemies)
    family = None
    if enemy is None and new:
        family = match_family(req, enemies)
    if enemy or family:
        if family:
            # 族名没有单条 entity：把所有名字里含这个片段的敌人刷新点合起来
            spots = [s for title, lst in spawns.items() if family in title for s in lst]
        else:
            spots = spawns.get(enemy, [])
        if new and region:
            spots = [s for s in spots if maps[s[0]][0] == region]
        for map_id, fid in spots[:6 if not new else 40]:
            return ("敌人族" if family else "敌人"), map_id
    item = next((i for i in collect_items if i in req), None)
    if item:
        spots = item_spots.get(item, [])
        if new and region:
            spots = [s for s in spots if maps[s[0]][0] == region] or spots
        if spots:
            return "采集", spots[0][0]
    if new and region:
        main = region_main_map(region)
        if main:
            return "图级", main
    return None, None


def region_main_map(region):
    best = None
    for mid, (display, _) in maps_ref.items():
        if display == region:
            cnt = marker_count.get(mid, 0)
            if best is None or cnt > best[1]:
                best = (mid, cnt)
    return best[0] if best else None


maps_ref = {}
marker_count = {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", type=int, default=0)
    ap.add_argument("--blade", default="")
    ap.add_argument("--include-initial", action="store_true",
                    help="把「初始习得」也算进来（App 生成计划时会跳过，默认不计）")
    args = ap.parse_args()

    conn = sqlite3.connect(DB)
    maps, markers, places, npcs, enemies, spawns, items, item_spots = load(conn)
    maps_ref.update(maps)
    for mid, cnt in conn.execute("SELECT id, marker_count FROM map"):
        marker_count[mid] = cnt or 0
    region_names = sorted({v[0] for v in maps.values() if v[0]}, key=len, reverse=True)

    rows = conn.execute(
        """
        SELECT b.entity_id, e.title, n.name, l.level, l.requirement, l.id
        FROM blade_affinity_level l
        JOIN blade_affinity_node n ON n.id = l.node_id
        JOIN blade b ON b.entity_id = n.blade_id
        JOIN entity e ON e.id = b.entity_id
        WHERE l.requirement IS NOT NULL AND TRIM(l.requirement) <> ''
        """).fetchall()
    links = {}
    for lid, target in conn.execute("SELECT level_id, target FROM blade_affinity_level_link"):
        if target.startswith("地点:"):
            links.setdefault(lid, []).append(target[3:].strip())

    stats = {"old": Counter(), "new": Counter()}
    by_kind_new = Counter()
    missed_new = []
    skipped_initial = 0
    regressions = []
    for blade_id, blade_name, node, level, req, lid in rows:
        if args.blade and args.blade not in blade_name:
            continue
        # App 的 Models.AffinityLevel.learned 把「初始习得」视为已学，buildBladePlan 会跳过
        if req.strip() == "初始习得" and not args.include_initial:
            skipped_initial += 1
            continue
        hows = {}
        for mode in ("old", "new"):
            how, _ = anchor(req, links.get(lid, []), maps, places, region_names, npcs,
                            enemies, spawns, items, item_spots, mode == "new")
            hows[mode] = how
            stats[mode][how or "未定位"] += 1
        by_kind_new[hows["new"] or "未定位"] += 1
        if hows["old"] and not hows["new"]:
            regressions.append((blade_name, node, level, req, hows["old"]))
        if hows["new"] is None and len(missed_new) < args.samples:
            missed_new.append((blade_name, node, level, req))

    for mode in ("old", "new"):
        total = sum(stats[mode].values())
        print(f"\n== {mode} ==  需求总数 {total}")
        for k, v in stats[mode].most_common():
            print(f"  {k:<6} {v:>5}  {v * 100 / max(total, 1):>5.1f}%")
    if skipped_initial:
        print(f"\n(已跳过 {skipped_initial} 条「初始习得」；--include-initial 可计入)")
    print(f"回归检查：old 钉上但 new 没钉上 = {len(regressions)} 条")
    for b, n, lv, req, how in regressions[:10]:
        print("  回归:", b, "|", n, f"Lv{lv}", "|", req, "|", how)
    for b, n, lv, req in missed_new:
        print("  miss:", b, "|", n, f"Lv{lv}", "|", req)


if __name__ == "__main__":
    main()

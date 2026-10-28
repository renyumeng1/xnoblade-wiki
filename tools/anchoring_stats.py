#!/usr/bin/env python3
"""统计任务步骤的落点情况（`Planner.anchor` / `Planner.anchorQuest` 的离线复刻版）。

优先级与 app 内 plan/Planner.kt 一致：
  1) task_workflow_link 里的「地点:feature_id」内链
  2) 文本里出现同地区地标名（Location/Door/Warp/Elevator/Jump/Quest/Shop/Inn/HeartToHeart/Salvage）
  3) NPC 名 → npc_game_appearance（该表 x/z 与 frontier 坐标同尺度）
  4) Battle 步骤里的敌人名 → 任意刷新点
  5) Collect 步骤里的可采集物品名 → collection_drop ⋈ collection_marker ⋈ marker
  6) 图级兜底：同任务多数已定位步骤所在图（只到图，没有坐标）

输出：精确落点 / 图级定位 / 未定位 三档，按 kind 拆分，并单列黄金之国 DLC（库里没有伊拉地图，属数据缺口）。

用法：python3 tools/anchoring_stats.py [--samples N]
"""
from __future__ import annotations

import argparse
import sqlite3
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "app/src/main/assets/xenoblade2.db"

PLACE_TYPES = (
    "Location", "DoorPoint", "WarpPoint", "ElevatorPoint", "JumpPoint",
    "Quest", "Shop", "Inn", "HeartToHeart", "SalvagePoint",
)


def norm_region(region: str | None) -> str:
    return (region or "").replace("的", "").replace(" ", "").strip()


def load(conn: sqlite3.Connection):
    places = []
    for name, fid, map_id, region in conn.execute(
        f"""
        SELECT TRIM(COALESCE(f.target_name,'')), f.feature_id, f.map_id, m.display_name
        FROM frontiernav_map_marker f
        LEFT JOIN map m ON m.id = f.map_id
        WHERE f.target_name IS NOT NULL AND LENGTH(TRIM(f.target_name)) >= 2
          AND f.target_type IN ({','.join('?' * len(PLACE_TYPES))})
        """,
        PLACE_TYPES,
    ):
        if map_id:
            places.append((name, fid, map_id, region))
    places.sort(key=lambda x: -len(x[0]))

    markers_with_map = {
        fid: map_id for fid, map_id in conn.execute(
            "SELECT feature_id, map_id FROM frontiernav_map_marker WHERE map_id IS NOT NULL AND map_id <> ''"
        )
    }

    npcs = []
    for name, map_id, fx, fy, region in conn.execute(
        """
        SELECT TRIM(e.title), m.id, g.x, g.z, m.display_name
        FROM npc_game_appearance g
        JOIN entity e ON e.id = g.npc_id
        JOIN map m ON m.name = g.game_map
        WHERE e.title IS NOT NULL AND LENGTH(TRIM(e.title)) >= 2 AND g.game_map <> ''
        """
    ):
        npcs.append((name, map_id, fx, fy, region))
    npcs.sort(key=lambda x: -len(x[0]))

    enemies = [r[0] for r in conn.execute(
        "SELECT title FROM entity WHERE type='enemy' AND title IS NOT NULL"
    ) if len(r[0] or "") >= 2]
    enemies.sort(key=len, reverse=True)

    collect_items = [r[0] for r in conn.execute(
        """
        SELECT DISTINCT d.item_name
        FROM collection_drop d
        JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
        JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
        WHERE LENGTH(d.item_name) >= 2
        """
    )]
    collect_items.sort(key=len, reverse=True)

    links: dict[tuple[int, int], list[str]] = defaultdict(list)
    for eid, pos, target in conn.execute(
        "SELECT entity_id, step_position, target FROM task_workflow_link WHERE target LIKE '地点:%'"
    ):
        links[(eid, pos)].append(target.split(":", 1)[1])

    quest_regions: dict[int, list[str]] = defaultdict(list)
    for qid, loc in conn.execute("SELECT quest_id, location FROM quest_location ORDER BY position"):
        quest_regions[qid].append(loc)

    return places, markers_with_map, npcs, enemies, collect_items, links, quest_regions


def match_place(text, per_region, region_hint, regions, all_places):
    """先按任务地区过滤（更准），再全局兜底（任务跨区域时 quest_location 只记接取地）。"""
    for region in dict.fromkeys([r for r in [region_hint, *regions] if r]):
        pool = per_region.get(norm_region(region)) or []
        hit = next((p for p in pool if p[0] in text), None)
        if hit:
            return hit
    return next((p for p in all_places if p[0] in text), None)


def match_npc(text, npcs, region_hint):
    hint = norm_region(region_hint)
    any_hit = None
    for n in npcs:
        if n[0] not in text:
            continue
        if any_hit is None:
            any_hit = n
        if not hint:
            return n
        r = norm_region(n[4])
        if r and (r in hint or hint in r):
            return n
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", type=int, default=10)
    args = ap.parse_args()

    if not DB.exists():
        print(f"数据库不存在: {DB}")
        return 2
    conn = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    places, markers_with_map, npcs, enemies, collect_items, links, quest_regions = load(conn)

    per_region: dict[str, list[tuple[str, str, str, str | None]]] = defaultdict(list)
    for p in places:
        per_region[norm_region(p[3])].append(p)
    for v in per_region.values():
        v.sort(key=lambda x: -len(x[0]))

    rows = conn.execute(
        """
        SELECT s.entity_id, s.position, s.kind, s.text, q.is_torna
        FROM task_workflow_step s
        LEFT JOIN quest q ON q.entity_id = s.entity_id
        ORDER BY s.entity_id, s.position
        """
    ).fetchall()

    precise: dict[tuple[int, int], str] = {}
    for eid, pos, kind, text, _torna in rows:
        text = text or ""
        regions = [loc.split("·")[0].strip() for loc in quest_regions.get(eid, [])]
        hint = next((r for r in regions if r), None)
        how = None
        for fid in links.get((eid, pos), []):
            if fid in markers_with_map:
                how = "内链"
                break
        if how is None:
            hit = match_place(text, per_region, hint, regions, places)
            if hit:
                how = "地名"
        if how is None and "Battle" not in (kind or "") and "Collect" not in (kind or ""):
            if match_npc(text, npcs, hint or (regions[0] if regions else None)):
                how = "NPC"
        if how is None and "Battle" in (kind or ""):
            if next((e for e in enemies if e in text), None):
                how = "敌人"
        if how is None and "Collect" in (kind or ""):
            if next((i for i in collect_items if i in text), None):
                how = "采集"
        if how:
            precise[(eid, pos)] = how

    quest_maps: dict[int, Counter] = defaultdict(Counter)
    for (eid, pos), _how in precise.items():
        map_id = None
        for fid in links.get((eid, pos), []):
            map_id = markers_with_map.get(fid)
            if map_id:
                break
        if map_id is None:
            text = next(t for e, p, _k, t, _x in rows if (e, p) == (eid, pos)) or ""
            regions = [loc.split("·")[0].strip() for loc in quest_regions.get(eid, [])]
            hit = match_place(text, per_region, regions[0] if regions else None, regions, places)
            map_id = hit[2] if hit else None
        if map_id:
            quest_maps[eid][map_id] += 1

    total = len(rows)
    kind_total: Counter[str] = Counter()
    kind_precise: Counter[str] = Counter()
    kind_coarse: Counter[str] = Counter()
    by_how: Counter[str] = Counter()
    misses: list[tuple[str, str, int]] = []
    torna_total = 0
    torna_miss = 0
    for eid, pos, kind, text, is_torna in rows:
        kind_total[kind] += 1
        if is_torna:
            torna_total += 1
        if (eid, pos) in precise:
            kind_precise[kind] += 1
            by_how[precise[(eid, pos)]] += 1
        elif quest_maps.get(eid):
            kind_coarse[kind] += 1
        else:
            if is_torna:
                torna_miss += 1
            if len(misses) < 400:
                misses.append((kind, text or "", eid))

    p_total = sum(kind_precise.values())
    c_total = sum(kind_coarse.values())
    print(f"步骤总数 {total}")
    print(f"  精确落点 {p_total} ({p_total / max(total, 1):.1%})")
    print(f"  图级定位 {c_total} ({c_total / max(total, 1):.1%})")
    print(f"  未定位   {total - p_total - c_total} ({(total - p_total - c_total) / max(total, 1):.1%})")
    print(f"  其中黄金之国 DLC {torna_miss} 步无法定位（库里 0 张伊拉地图）")

    print("\n命中方式：")
    for how, n in by_how.most_common():
        print(f"  {how:<6} {n:>4}")

    print("\n按 kind（精确 / 图级 / 总数）：")
    for k, n in kind_total.most_common():
        print(f"  {k:<28} {kind_precise[k]:>4} / {kind_coarse[k]:>4} / {n:<4} {kind_precise[k] / n:.0%}")

    if args.samples > 0:
        print("\n未定位样例：")
        for kind, text, eid in misses[: args.samples]:
            print(f"  [{kind.split('·')[-1].strip()}] {text[:60]}  (quest {eid})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

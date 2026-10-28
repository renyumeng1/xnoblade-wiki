#!/usr/bin/env python3
"""用真实模型跑一遍 App 里同款的 tool-calling 循环，检验三件事：

1. 问「怎么走」时会不会先调 route_between，并主动说明"这是估算、不是真实路网"；
2. 问「某物品在哪采」时会不会调 collect_spots，并给出真库里存在的 map_id；
3. 问一个库里没有的地方时，会不会编造。

工具实现在本文件里，直接查 `app/src/main/assets/xenoblade2.db`，和
`AgentTools.kt` / `RouteGraph.kt` 是同一套 SQL 与同一套构图启发式（route_stats.py 复刻）。

用法：
    export LLM_BASE_URL=https://api.deepseek.com/v1
    export LLM_MODEL=deepseek-chat
    export LLM_API_KEY=sk-...
    python3 tools/llm_probe.py            # 跑全部用例
    python3 tools/llm_probe.py --case 1   # 只跑第 1 个

不带 key 时直接退出并打印缺什么，不会发任何请求。
"""
from __future__ import annotations

import argparse
import json
import math
import re
import os
import sqlite3
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "app/src/main/assets/xenoblade2.db"
sys.path.insert(0, str(ROOT / "tools"))

import route_stats  # noqa: E402

# ---- 逐段导航：和 App 的 RouteGuide.kt 同一套规则（切段阈值、方位、转向、停靠点判定） ----
MIN_LEG = 25.0
MAX_LEGS = 16
COMPASS = ["北", "东北", "东", "东南", "南", "西南", "西", "西北"]
AUTO_POINT = re.compile(
    r"^(Enemy Spawn Point|Treasure Point|Collection Point|Salvage Point|"
    r"Door Point|Warp Point|Elevator Point|Jump Point|Climbing Point)\s*#"
)
NUMBERED = re.compile(r"#\s*\d+$")


def _bearing(x1: float, y1: float, x2: float, y2: float) -> float:
    # App 里 fy 越大越靠南，所以北 = -(y2 - y1)
    return math.degrees(math.atan2(x2 - x1, -(y2 - y1))) % 360.0


def _compass(deg: float) -> str:
    return COMPASS[int((deg % 360.0) / 45.0 + 0.5) % 8]


def _turn(prev: float | None, cur: float) -> str:
    if prev is None:
        return "出发"
    d = (cur - prev + 180.0) % 360.0 - 180.0
    if abs(d) < 22.5:
        return "直行"
    if abs(d) < 135.0:
        return "右转" if d > 0 else "左转"
    return "掉头"


def _notable(name: str, target_type: str) -> bool:
    if target_type in ("Location", "Quest"):
        return True
    return bool(name) and not AUTO_POINT.match(name) and not NUMBERED.search(name)


def route_legs(coords, meta, from_label: str, to_label: str):
    """coords=[(fx,fy)]，meta=[(name, notable)]，返回 App 同款 leg 列表。"""
    n = len(coords)
    if n < 2:
        return []
    keep, last = [0], 0
    for i in range(1, n - 1):
        if not meta[i][1]:
            continue
        if _poly_len(coords, last, i) < MIN_LEG:
            continue
        keep.append(i)
        last = i
    if keep[-1] != n - 1:
        keep.append(n - 1)
    out, prev = [], None
    for a, b in zip(keep, keep[1:]):
        dist = _poly_len(coords, a, b)
        if dist <= 0.0001 and b != n - 1:
            continue
        br = _bearing(coords[a][0], coords[a][1], coords[b][0], coords[b][1])
        is_end = b == n - 1
        frm = from_label if a == 0 else (meta[a][0] or "起点")
        to = to_label if is_end else (meta[b][0] or "途经点")
        out.append(
            {
                "n": len(out) + 1,
                "from": frm[:24],
                "to": to[:24],
                "turn": _turn(prev, br),
                "compass": _compass(br),
                "distance": round(dist),
            }
        )
        prev = br
    return out


def _poly_len(coords, a: int, b: int) -> float:
    return sum(
        math.hypot(coords[j][0] - coords[j - 1][0], coords[j][1] - coords[j - 1][1])
        for j in range(a + 1, b + 1)
    )


SYSTEM_PROMPT = """你是《异度神剑2》Wiki App 内的行动规划助手。你只能通过工具读取本机离线数据库。
规则：
1. 先给结论，再给依据；用简体中文。
2. 凡涉及"在哪、怎么走、去哪打怪"，必须先调用工具拿到 map_id 和坐标，禁止编造地名与坐标。
3. 工具返回空或报错就直说"库里没有"，不要猜。
4. 问"去哪采某个物品"用 collect_spots；问"怎么走"用 route_between，按返回的 legs 分段说（每段转向 + 方位 + 距离，最多复述 5~6 段），并说明这是点位图估算、不是真实路网。
5. 回答不超过 400 字。"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "find_place",
            "description": "按名字模糊查地标，返回 map_id / 地区 / frontier 坐标。name 只放单个地名或区域名，不要把地区拼进去。",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "单个地名或区域名，如 穿岩之门 / 里吉特海域"},
                    "region": {"type": "string", "description": "地区过滤，可空，如 古拉"},
                },
                "required": ["name"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "collect_spots",
            "description": "查某采集物品能在地图哪些点位采到，返回 map_id 与 frontier 坐标。",
            "parameters": {
                "type": "object",
                "properties": {
                    "item": {"type": "string", "description": "物品名"},
                    "region": {"type": "string", "description": "地区过滤，可空"},
                },
                "required": ["item"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "route_between",
            "description": "同图两点间估算路线。返回途经地标、距离、绕行比、access_hops，以及逐段指引 legs（转向/方位/距离/终点）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "map_id": {"type": "string"},
                    "from": {"type": "string"},
                    "to": {"type": "string"},
                },
                "required": ["map_id", "from", "to"],
                "additionalProperties": False,
            },
        },
    },
]


class Toolbox:
    def __init__(self, db: sqlite3.Connection):
        self.db = db
        self._graph_cache: dict[str, tuple] = {}

    # ---- find_place ----
    @staticmethod
    def _norm(s: str | None) -> str:
        return (s or "").replace("的", "").replace(" ", "").strip()

    @staticmethod
    def _split_query(text: str) -> list[str]:
        parts = [text.strip()]
        for sep in ("的", "·", "・", "/", "、", ",", "，", "在", "从", "到", "去"):
            parts = [p for part in parts for p in part.split(sep)]
        out = []
        for p in (p.strip() for p in parts):
            if len(p) >= 2 and p not in out:
                out.append(p)
        return out

    def _markers(self, q: str, region: str | None) -> list[tuple]:
        rows = self.db.execute(
            """
            SELECT f.feature_id, f.map_id, f.frontier_x, f.frontier_y, f.target_name, f.target_type,
                   COALESCE(m.display_name,'') AS region, COALESCE(m.menu_group,'') AS area
            FROM frontiernav_map_marker f
            JOIN map m ON m.id = f.map_id
            WHERE f.target_name IS NOT NULL AND length(trim(f.target_name)) >= 2
              AND f.target_type IN ('Location','DoorPoint','WarpPoint','ElevatorPoint','JumpPoint',
                                    'Quest','Shop','Inn','HeartToHeart','SalvagePoint')
            """
        ).fetchall()
        hint = self._norm(region)
        if hint:
            rows = [r for r in rows if hint in self._norm(r[6]) or self._norm(r[6]) in hint]
        exact = [r for r in rows if r[4] == q]
        # 文本包含地名（整句里带了地名），长名优先
        inside = sorted((r for r in rows if r[4] in q and r[4] != q), key=lambda r: -len(r[4]))
        partial = [r for r in rows if q in r[4] and r[4] != q]
        out, seen = [], set()
        for r in [*exact, *inside, *partial]:
            if r[0] not in seen:
                seen.add(r[0])
                out.append(r)
            if len(out) >= 6:
                break
        return out

    def find_place(self, name: str, region: str | None = None) -> dict:
        raw = name.strip()
        hits = self._markers(raw, region)
        if not hits:
            segs = self._split_query(raw)
            keys = [k for (k,) in self.db.execute(
                "SELECT display_name FROM map UNION SELECT menu_group FROM map"
            ) if k]
            if not region:
                region = next((s for s in segs if any(k == s or s in k for k in keys)), None)
            merged, seen = [], set()
            for s in sorted(segs, key=len, reverse=True):
                if any(k == s or s in k for k in keys):
                    continue  # 纯地区/区域名，交给 maps
                for r in self._markers(s, region):
                    if r[0] not in seen:
                        seen.add(r[0])
                        merged.append(r)
                if len(merged) >= 6:
                    break
            hits = merged[:6]

        places = [
            {
                "name": r[4], "kind": "marker", "type": r[5],
                "map_id": r[1], "region": r[6], "area": r[7],
                "x": round(r[2], 2), "y": round(r[3], 2), "feature_id": r[0],
            }
            for r in hits
        ]
        maps = [] if places else self._match_maps(raw, region)
        return {
            "count": len(places),
            "places": places,
            "map_count": len(maps),
            "maps": maps,
            "matched_region": region or "",
            "note": (
                "库里没有这个地名" if not places and not maps
                else "这是地图/区域，不是单一点位，没有坐标；需要具体位置请再用 map_markers 或给更具体的地名"
                if not places else "x/y 是 frontier 平面坐标，可直接用于 route_between / add_plan_step"
            ),
        }

    def _match_maps(self, text: str, region: str | None) -> list[dict]:
        t = text.strip()
        if len(t) < 2:
            return []
        rows = self.db.execute(
            "SELECT id, name, display_name, menu_group, marker_count FROM map"
        ).fetchall()
        out, seen = [], set()

        def add(kind, cand):
            for r in cand[:4]:
                if r[0] not in seen:
                    seen.add(r[0])
                    out.append({
                        "name": r[3] if kind == "area" else r[2] if kind == "region" else r[1],
                        "kind": kind, "map_id": r[0], "region": r[2],
                        "area": r[3], "markers": r[4],
                    })

        add("area", [r for r in rows if r[3] == t or (len(r[3]) >= 2 and r[3] in t)])
        hint = (region or "").strip()
        add("region", [
            r for r in rows
            if r[2] == t or (len(r[2]) >= 2 and r[2] in t)
            or (hint and (r[2] == hint or hint in r[2]))
        ])
        add("map", [r for r in rows if r[1] == t or r[0] == t])
        return out[:6]

    # ---- collect_spots ----
    def collect_spots(self, item: str, region: str | None = None) -> dict:
        region_sql = "" if not region else " AND f.map_id IN (SELECT id FROM map WHERE display_name = ?)"
        args = [item] + ([region] if region else []) + [8]
        rows = self.db.execute(
            f"""
            SELECT DISTINCT f.feature_id, f.map_id, f.frontier_x, f.frontier_y,
                   COALESCE(m.display_name,'') AS region, COALESCE(m.menu_group,'') AS area
            FROM collection_drop d
            JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
            JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
            JOIN map m ON m.id = f.map_id
            WHERE d.item_name = ?{region_sql}
            LIMIT ?
            """,
            args,
        ).fetchall()
        return {
            "item": item,
            "count": len(rows),
            "spots": [
                {
                    "map_id": r[1], "region": r[4], "area": r[5],
                    "x": round(r[2], 2), "y": round(r[3], 2), "feature_id": r[0],
                }
                for r in rows
            ],
        }

    # ---- route_between ----
    def _graph(self, map_id: str):
        if map_id in self._graph_cache:
            return self._graph_cache[map_id]
        pts = [
            (r[0], r[1], r[2], r[3] or "")
            for r in self.db.execute(
                "SELECT feature_id, frontier_x, frontier_y, COALESCE(target_type,'') "
                "FROM frontiernav_map_marker WHERE map_id = ? AND frontier_x IS NOT NULL",
                (map_id,),
            )
        ]
        access = [
            (r[0], r[1])
            for r in self.db.execute(
                "SELECT a.feature_id, a.point_kind FROM route_access_point a "
                "JOIN frontiernav_map_marker f ON f.feature_id = a.feature_id WHERE f.map_id = ?",
                (map_id,),
            )
        ]
        built = route_stats.build(pts, access)
        self._graph_cache[map_id] = built
        return built

    def route_between(self, map_id: str, frm: str, to: str) -> dict:
        try:
            nodes, adj = self._graph(map_id)
        except Exception as e:  # noqa: BLE001
            return {"error": f"构图失败：{e}"}
        rows = self.db.execute(
            "SELECT feature_id, target_name, target_type FROM frontiernav_map_marker WHERE map_id=?",
            (map_id,),
        ).fetchall()
        names = {r[0]: (r[1], r[2] or "") for r in rows}
        idx = {p[0]: i for i, p in enumerate(nodes)}

        def locate(q: str):
            for fid, (n, _t) in names.items():
                if n == q and fid in idx:
                    return idx[fid]
            for fid, (n, _t) in names.items():
                if n and q in n and fid in idx:
                    return idx[fid]
            return None

        a, b = locate(frm), locate(to)
        if a is None or b is None:
            return {"error": f"这张图上找不到「{frm if a is None else to}」", "map_id": map_id}
        p = route_stats.dijkstra(adj, a, b)
        if p is None:
            return {"error": "不连通", "map_id": map_id}
        straight = math.hypot(nodes[b][1] - nodes[a][1], nodes[b][2] - nodes[a][2])
        dist = sum(
            math.hypot(nodes[y][1] - nodes[x][1], nodes[y][2] - nodes[x][2])
            for x, y in zip(p, p[1:])
        )
        auto = ("Enemy Spawn Point", "Treasure Point", "Collection Point", "Salvage Point")
        waypoints = []
        for i in p:
            fid, fx, fy, t = nodes[i]
            n, nt = names.get(fid, (fid, t))
            if nt in ("Location", "Quest") or not (n or "").startswith(auto):
                waypoints.append({"name": n, "type": nt, "x": round(fx, 2), "y": round(fy, 2)})
        from_label = names.get(nodes[a][0], ("?", ""))[0]
        to_label = names.get(nodes[b][0], ("?", ""))[0]
        coords = [(nodes[i][1], nodes[i][2]) for i in p]
        meta = []
        for i in p:
            n_, nt_ = names.get(nodes[i][0], (nodes[i][0], nodes[i][3]))
            meta.append((n_ or "", _notable(n_, nt_)))
        legs = route_legs(coords, meta, from_label, to_label)
        out = {
            "map_id": map_id,
            "from": from_label,
            "to": to_label,
            "distance": round(dist, 2),
            "straight": round(straight, 2),
            "detour": round(dist / straight, 2) if straight else 1.0,
            "access_hops": 0,
            "estimated": True,
            "note": "点位图几何近似，非真实路网，仅供方向参考",
            "waypoints": waypoints,
            "leg_count": len(legs),
            "legs": legs[:MAX_LEGS],
        }
        if len(legs) > MAX_LEGS:
            out["omitted_legs"] = len(legs) - MAX_LEGS
        return out

    def call(self, name: str, args: dict) -> dict:
        if name == "find_place":
            return self.find_place(str(args.get("name", "")), args.get("region") or None)
        if name == "collect_spots":
            return self.collect_spots(str(args.get("item", "")), args.get("region") or None)
        if name == "route_between":
            return self.route_between(
                str(args.get("map_id", "")), str(args.get("from", "")), str(args.get("to", ""))
            )
        return {"error": f"未知工具 {name}"}


class Client:
    def __init__(self, base_url: str, model: str, key: str):
        self.url = base_url.rstrip("/")
        if not self.url.endswith("/chat/completions"):
            self.url += "/chat/completions"
        self.model = model
        self.key = key

    def chat(self, messages: list[dict]) -> dict:
        payload = {
            "model": self.model,
            "temperature": 0.2,
            "messages": [{"role": "system", "content": SYSTEM_PROMPT}, *messages],
            "tools": TOOLS,
            "tool_choice": "auto",
        }
        req = urllib.request.Request(
            self.url,
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.key}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                body = json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code}: {e.read()[:300].decode(errors='replace')}") from e
        return body["choices"][0]["message"]


def run_case(client: Client, box: Toolbox, prompt: str, max_rounds: int = 6):
    messages = [{"role": "user", "content": prompt}]
    calls: list[tuple[str, dict]] = []
    final = ""
    trace: list[str] = []
    results: list[dict] = []
    for _ in range(max_rounds):
        msg = client.chat(messages)
        messages.append(msg)
        tool_calls = msg.get("tool_calls") or []
        if not tool_calls:
            final = msg.get("content") or ""
            break
        for tc in tool_calls:
            fn = tc.get("function", {})
            name = fn.get("name", "")
            try:
                args = json.loads(fn.get("arguments") or "{}")
            except json.JSONDecodeError:
                args = {}
            calls.append((name, args))
            result = box.call(name, args)
            results.append(result)
            trace.append(f"{name}({json.dumps(args, ensure_ascii=False)}) -> {json.dumps(result, ensure_ascii=False)[:220]}")
            messages.append(
                {
                    "role": "tool",
                    "tool_call_id": tc.get("id"),
                    "content": json.dumps(result, ensure_ascii=False),
                }
            )
    return final, calls, trace, results


CASES = [
    {
        "prompt": "我在古拉上层右边这张图（map_id=gormott-upper-level-right），从穿岩之门去克雷尔水脉怎么走？",
        "expect_tool": "route_between",
        "want_disclaimer": True,
    },
    {
        "prompt": "独刺鱼在哪儿能采到？给我地图和位置。",
        "expect_tool": "collect_spots",
        "must_mention": ["利贝拉里塔斯"],
    },
    {
        "prompt": "亚特兰蒂斯遗迹怎么走？给我坐标。",
        "expect_tool": None,
        "empty_tool_is_ok": True,
        "must_mention_any": ["没有", "找不到", "没有记录", "无此"],
        "no_coordinates": True,
    },
    {
        "prompt": "从古拉的穿岩之门，直接走到利贝拉里塔斯的里吉特海域，给我一条路线。",
        "expect_tool": None,
        "must_mention_any": ["不同", "跨图", "换图", "不在同一", "无法直接"],
        "no_coordinates": True,
    },
    {
        "prompt": "虚无结晶碎片在哪儿采？",
        "expect_tool": "collect_spots",
        "must_mention_any": ["没有", "找不到", "未收录", "采不到"],
        "no_coordinates": True,
    },
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--case", type=int, default=0, help="只跑第 N 个用例（1 起）")
    args = ap.parse_args()

    base = os.environ.get("LLM_BASE_URL", "").strip()
    model = os.environ.get("LLM_MODEL", "").strip()
    key = os.environ.get("LLM_API_KEY", "").strip()
    missing = [n for n, v in (("LLM_BASE_URL", base), ("LLM_MODEL", model), ("LLM_API_KEY", key)) if not v]
    if missing:
        print("缺少：" + " / ".join(missing))
        print("例：export LLM_BASE_URL=https://api.deepseek.com/v1 LLM_MODEL=deepseek-chat LLM_API_KEY=sk-...")
        return 2
    if not DB.exists():
        print(f"找不到数据库：{DB}")
        return 2

    db = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    box = Toolbox(db)
    client = Client(base, model, key)

    idxs = [args.case - 1] if args.case else range(len(CASES))
    passed = 0
    for i in idxs:
        c = CASES[i]
        print(f"\n===== case {i + 1} =====\n> {c['prompt']}")
        final, calls, trace, results = run_case(client, box, c["prompt"])
        for t in trace:
            print(f"  [tool] {t}")
        print(f"  [answer] {final}")
        ok = True
        if c.get("expect_tool") and not any(n == c["expect_tool"] for n, _ in calls):
            print(f"  FAIL: 没有调用 {c['expect_tool']}")
            ok = False
        for s in c.get("must_mention") or []:
            if s not in final:
                print(f"  FAIL: 回答里没有提到 {s}")
                ok = False
        any_of = c.get("must_mention_any") or []
        if any_of and not any(s in final for s in any_of):
            print(f"  FAIL: 回答里没有 {any_of} 里的任何一个")
            ok = False
        all_empty = bool(results) and all(
            r.get("count", 1) == 0 or "error" in r for r in results
        )
        if c.get("no_coordinates") and all_empty and re.search(r"-?\d+\.\d+\s*[,，]\s*-?\d+\.\d+", final):
            print("  FAIL: 回答里给出了具体坐标，但库里没有依据")
            ok = False
        if c.get("empty_tool_is_ok"):
            empty = [r for r in results if r.get("count") == 0]
            if not empty:
                print("  WARN: 没有触发到空结果查询")
        if c.get("want_disclaimer") and not any(
            k in final for k in ("估算", "近似", "不是真实路网", "非真实路网", "仅供参考")
        ):
            print("  WARN: 回答没有说明这是估算路线")
        print("  PASS" if ok else "  FAILED")
        passed += int(ok)
    print(f"\n{passed}/{len(list(idxs))} 通过")
    return 0 if passed == len(list(idxs)) else 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""验证 RouteGraph.kt 的构图参数：同图随机点对的绕行比分布。

这不是 App 里的代码，是同一套启发式（去重 → kNN → 电梯/传送 clique → 分量桥接 → Dijkstra）
的 Python 复刻，用来在改参数前先看数据长什么样。

用法：python3 tools/route_stats.py
"""
from __future__ import annotations

import heapq
import math
import random
import sqlite3
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "app/src/main/assets/xenoblade2.db"

K = 6
CELL = 0.75
WARP, ELEVATOR, JUMP = 3.0, 4.0, 2.0

PRIORITY = {
    "Location": 0, "Quest": 1, "CollectionPoint": 2, "TreasurePoint": 3,
    "EnemySpawnPoint": 5,
}
FACTOR = {
    "Location": 1.0, "Quest": 1.0, "CollectionPoint": 1.05,
    "TreasurePoint": 1.05, "EnemySpawnPoint": 1.35,
}


def factor(t: str) -> float:
    return FACTOR.get(t, 1.12)


def priority(t: str) -> int:
    return PRIORITY.get(t, 4)


def load(db: sqlite3.Connection):
    rows = db.execute(
        "SELECT map_id, feature_id, frontier_x, frontier_y, "
        "COALESCE(target_type,'') FROM frontiernav_map_marker"
    ).fetchall()
    by_map: dict[str, list[tuple]] = {}
    for map_id, fid, fx, fy, t in rows:
        if map_id is None or fx is None or fy is None:
            continue
        by_map.setdefault(map_id, []).append((fid, fx, fy, t))

    access: dict[str, list[tuple[str, str]]] = {}
    for fid, kind, map_id in db.execute(
        "SELECT a.feature_id, a.point_kind, f.map_id FROM route_access_point a "
        "JOIN frontiernav_map_marker f ON f.feature_id=a.feature_id"
    ):
        access.setdefault(map_id, []).append((fid, kind))
    return by_map, access


def build(points, access):
    keep = {}
    for fid, fx, fy, t in sorted(points, key=lambda p: priority(p[3])):
        cell = (int(fx // CELL), int(fy // CELL))
        keep.setdefault(cell, (fid, fx, fy, t))
    nodes = list(keep.values())
    n = len(nodes)
    adj = [[] for _ in range(n)]
    idx = {p[0]: i for i, p in enumerate(nodes)}

    k = min(K, n - 1)
    if k > 0:
        for i in range(n):
            _, ax, ay, _ = nodes[i]
            near = sorted(
                ((math.hypot(nodes[j][1] - ax, nodes[j][2] - ay), j) for j in range(n) if j != i)
            )[:k]
            for d, j in near:
                c = d * factor(nodes[j][3])
                adj[i].append((j, c))
                adj[j].append((i, c))

    acc = [(idx[f], kind) for f, kind in access if f in idx]
    for x in range(len(acc)):
        for y in range(x + 1, len(acc)):
            i, ki = acc[x]
            j, kj = acc[y]
            c = min(
                {"warp": WARP, "jump": JUMP}.get(ki, ELEVATOR),
                {"warp": WARP, "jump": JUMP}.get(kj, ELEVATOR),
            )
            adj[i].append((j, c))
            adj[j].append((i, c))

    # 分量桥接
    while True:
        comp = [-1] * n
        c = 0
        for s in range(n):
            if comp[s] >= 0:
                continue
            stack = [s]
            comp[s] = c
            while stack:
                u = stack.pop()
                for v, _ in adj[u]:
                    if comp[v] < 0:
                        comp[v] = c
                        stack.append(v)
            c += 1
        if c <= 1:
            break
        best, bi, bj = float("inf"), -1, -1
        for i in range(n):
            for j in range(i + 1, n):
                if comp[i] == comp[j]:
                    continue
                d = math.hypot(nodes[j][1] - nodes[i][1], nodes[j][2] - nodes[i][2])
                if d < best:
                    best, bi, bj = d, i, j
        adj[bi].append((bj, best * 1.6))
        adj[bj].append((bi, best * 1.6))

    return nodes, adj


def dijkstra(adj, s, t):
    dist = [float("inf")] * len(adj)
    prev = [-1] * len(adj)
    dist[s] = 0.0
    pq = [(0.0, s)]
    seen = [False] * len(adj)
    while pq:
        d, u = heapq.heappop(pq)
        if seen[u]:
            continue
        seen[u] = True
        if u == t:
            break
        for v, w in adj[u]:
            nd = d + w
            if nd < dist[v]:
                dist[v] = nd
                prev[v] = u
                heapq.heappush(pq, (nd, v))
    if dist[t] == float("inf"):
        return None
    path, cur = [], t
    while cur >= 0:
        path.append(cur)
        if cur == s:
            break
        cur = prev[cur]
    return path[::-1]


def main():
    random.seed(20261026)
    db = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    by_map, access = load(db)
    ratios, all_n, fallbacks, paths = [], 0, 0, []
    for map_id, pts in sorted(by_map.items()):
        if len(pts) < 4:
            continue
        nodes, adj = build(pts, access.get(map_id, []))
        if len(nodes) < 2:
            continue
        for _ in range(20):
            i, j = random.sample(range(len(nodes)), 2)
            straight = math.hypot(nodes[j][1] - nodes[i][1], nodes[j][2] - nodes[i][2])
            if straight < 10:
                continue
            p = dijkstra(adj, i, j)
            all_n += 1
            if p is None:
                fallbacks += 1
                continue
            d = 0.0
            for a, b in zip(p, p[1:]):
                d += math.hypot(nodes[b][1] - nodes[a][1], nodes[b][2] - nodes[a][2])
            ratios.append(d / straight)
            paths.append(len(p))
    if not ratios:
        print("no samples")
        return 1
    ratios.sort()
    print(f"地图数        : {len(by_map)}")
    print(f"采样点对      : {all_n}")
    print(f"不连通回退    : {fallbacks}")
    print(f"绕行比 min    : {ratios[0]:.3f}")
    print(f"绕行比 p50    : {statistics.median(ratios):.3f}")
    print(f"绕行比 p90    : {ratios[int(len(ratios) * 0.9)]:.3f}")
    print(f"绕行比 max    : {ratios[-1]:.3f}")
    print(f"绕行比 > 2.0  : {sum(1 for r in ratios if r > 2.0)} / {len(ratios)}")
    print(f"平均途经点数  : {statistics.mean(paths):.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""静态校验：把 Kotlin 里写死的 SQL 在真实的 SQLite 库上跑一遍。

只做「能否 prepare / 表与列是否存在」的检查：
  · 抽取 WikiRepo.kt / Gazetteer.kt 的只读 SQL，在 assets/xenoblade2.db 上执行
  · 抽取 PlanStore.kt 的 SQL，在临时库（按 PlanStore.init 的 DDL 建表）上执行
  · 跳过含 Kotlin 插值($) 的动态片段
  · 每个 ? 绑定 10，避免 LIMIT NULL 之类的假错误
用法：python3 tools/check_sql.py
"""
from __future__ import annotations

import re
import sqlite3
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
READONLY_FILES = [
    ROOT / "app/src/main/java/cn/xenoblade2/next/data/WikiRepo.kt",
    ROOT / "app/src/main/java/cn/xenoblade2/next/plan/Gazetteer.kt",
]
PLAN_FILES = [ROOT / "app/src/main/java/cn/xenoblade2/next/plan/PlanStore.kt"]

TRIPLE = re.compile(r'"""(.*?)"""', re.S)
SINGLE = re.compile(r'"((?:SELECT|select|WITH)[^"\n]*)"')
LOOKS_SQL = re.compile(r"^\s*(SELECT|WITH)\b", re.I)

# 与 PlanStore.init 保持一致
PLAN_DDL = [
    """CREATE TABLE IF NOT EXISTS plan(
           id INTEGER PRIMARY KEY AUTOINCREMENT,
           title TEXT NOT NULL,
           source_type TEXT NOT NULL DEFAULT '',
           source_id TEXT NOT NULL DEFAULT '',
           created_at INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS plan_step(
           id INTEGER PRIMARY KEY AUTOINCREMENT,
           plan_id INTEGER NOT NULL,
           ord INTEGER NOT NULL,
           title TEXT NOT NULL,
           detail TEXT NOT NULL DEFAULT '',
           map_id TEXT, fx REAL, fy REAL, feature_id TEXT,
           done INTEGER NOT NULL DEFAULT 0)""",
    """CREATE TABLE IF NOT EXISTS affinity_progress(
           level_id INTEGER PRIMARY KEY,
           current INTEGER NOT NULL DEFAULT 0)""",
]


def extract(path: Path):
    text = path.read_text(encoding="utf-8")
    found = []
    for m in TRIPLE.finditer(text):
        if LOOKS_SQL.search(m.group(1)):
            found.append(m.group(1))
    found.extend(m.group(1) for m in SINGLE.finditer(text))
    return found


def check(conn: sqlite3.Connection, files, label: str):
    ok = skipped = failed = 0
    for path in files:
        if not path.exists():
            continue
        for sql in extract(path):
            sql = sql.strip()
            if "$" in sql:
                skipped += 1
                continue
            try:
                conn.execute(sql, [10] * sql.count("?"))
                ok += 1
            except Exception as exc:  # noqa: BLE001
                failed += 1
                head = " ".join(sql.split())[:150]
                print(f"[FAIL] {label}/{path.name}: {exc}\n       {head}\n")
    print(f"{label}: OK={ok} 跳过(含插值)={skipped} 失败={failed}")
    return failed


def main() -> int:
    db_path = ROOT / "app/src/main/assets/xenoblade2.db"
    if not db_path.exists():
        print(f"数据库不存在: {db_path}")
        return 2
    ro = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    failed = check(ro, READONLY_FILES, "wiki(只读库)")

    with tempfile.TemporaryDirectory() as tmp:
        plan_conn = sqlite3.connect(str(Path(tmp) / "plans.db"))
        for ddl in PLAN_DDL:
            plan_conn.execute(ddl)
        failed += check(plan_conn, PLAN_FILES, "plans(计划库)")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

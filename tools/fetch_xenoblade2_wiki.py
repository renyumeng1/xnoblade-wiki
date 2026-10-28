#!/usr/bin/env python3
"""Download map data and article text from https://xenoblade2.cn/.

The site is CC BY-NC-SA 4.0. Output stays in data/wiki-xenoblade2/ and is gitignored.
This does not rewrite app/src/main/assets/xenoblade2.db.
"""

from __future__ import annotations

import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "wiki-xenoblade2"
API = "https://xenoblade2.cn/api.php"
UA = "xnoblade-wiki-research/1.0 (noncommercial local import; CC BY-NC-SA 4.0)"
PAUSE = 0.25

# Human-facing namespaces. The 11k per-row JSON:BTL_* pages duplicate the game DB and are skipped.
NAMESPACES = {
    3000: "item",
    3010: "poppi",
    3100: "enemy",
    3200: "quest",
    3300: "merc",
    3400: "blade",
    3500: "heart",
    3550: "npc",
    3600: "shop",
    3700: "challenge",
    3800: "weapon",
    3900: "collection",
    3910: "chest",
    3950: "salvage",
    5000: "torna",
    5100: "torna-item",
    5200: "torna-enemy",
    5300: "torna-quest",
    5400: "torna-collection",
    5410: "torna-chest",
}

ICONS = [
    "Collection-marker-icon.png",
    "Collection-marker-current.png",
    "Enemy.png",
    "Salvage.png",
    "Tbox.png",
    "Player.png",
    "Npc.svg",
]


def get(params: dict) -> dict:
    params = dict(params)
    params.setdefault("format", "json")
    params.setdefault("maxlag", "5")
    url = API + "?" + urllib.parse.urlencode(params)
    last = None
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as response:
                return json.load(response)
        except Exception as exc:  # noqa: BLE001 - retry transient HTTP errors
            last = exc
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"request failed: {url[:160]} ({last})")


def download(url: str, dest: Path) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"empty body: {url}")
    tmp.write_bytes(data)
    tmp.replace(dest)
    return True


def safe_name(title: str) -> str:
    cleaned = title.replace(":", "__").replace("/", "__").replace(" ", "_")
    return "".join(ch if ch.isalnum() or ch in "._-" or ord(ch) > 127 else "_" for ch in cleaned)[:180]


def list_pages(namespace: int, prefix: str = "") -> list[str]:
    titles = []
    cont = None
    while True:
        params = {
            "action": "query",
            "list": "allpages",
            "apnamespace": str(namespace),
            "aplimit": "500",
        }
        if prefix:
            params["apprefix"] = prefix
        if cont:
            params["apcontinue"] = cont
        data = get(params)
        titles.extend(page["title"] for page in data["query"]["allpages"])
        cont = (data.get("continue") or {}).get("apcontinue")
        time.sleep(PAUSE)
        if not cont:
            return titles


def save_json_pages(prefix: str, folder: str) -> int:
    titles = list_pages(8888, prefix)
    saved = 0
    for title in titles:
        dest = OUT / "json" / folder / f"{safe_name(title)}.json"
        if dest.exists() and dest.stat().st_size > 0:
            continue
        data = get({
            "action": "query",
            "titles": title,
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
        })
        page = next(iter(data["query"]["pages"].values()))
        content = page["revisions"][0]["slots"]["main"]["*"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")
        saved += 1
        time.sleep(PAUSE)
    return len(titles)


def save_pages(namespace: int, folder: str) -> int:
    titles = list_pages(namespace)
    pending = []
    for title in titles:
        dest = OUT / "pages" / folder / f"{safe_name(title)}.wikitext"
        if not dest.exists() or dest.stat().st_size == 0:
            pending.append(title)
    for start in range(0, len(pending), 20):
        chunk = pending[start:start + 20]
        data = get({
            "action": "query",
            "titles": "|".join(chunk),
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
        })
        for page in data["query"]["pages"].values():
            revs = page.get("revisions") or []
            if not revs:
                continue
            content = revs[0]["slots"]["main"].get("*", "")
            dest = OUT / "pages" / folder / f"{safe_name(page['title'])}.wikitext"
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")
        time.sleep(PAUSE)
    return len(titles)


def save_maps() -> int:
    count = 0
    folder = OUT / "json" / "mapinfo"
    for path in sorted(folder.glob("*.json")):
        meta = json.loads(path.read_text(encoding="utf-8"))
        name = meta.get("Name")
        if not name:
            continue
        dest = OUT / "maps" / f"{name}_map_0.png"
        url = "https://xenoblade2.cn/index.php?" + urllib.parse.urlencode({
            "title": "Special:Redirect/file/" + name + "_map_0.png",
        })
        try:
            if download(url, dest):
                count += 1
        except Exception as exc:  # noqa: BLE001 - one missing file should not stop the run
            (OUT / "maps-errors.txt").open("a", encoding="utf-8").write(f"{name}\t{exc}\n")
        time.sleep(PAUSE)
    return count


def save_icons() -> None:
    for name in ICONS:
        dest = OUT / "icons" / name
        url = "https://xenoblade2.cn/index.php?" + urllib.parse.urlencode({
            "title": "Special:Redirect/file/" + name,
        })
        try:
            download(url, dest)
        except Exception as exc:  # noqa: BLE001
            (OUT / "icon-errors.txt").open("a", encoding="utf-8").write(f"{name}\t{exc}\n")
        time.sleep(PAUSE)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    summary = {
        "source": "https://xenoblade2.cn/",
        "license": "CC BY-NC-SA 4.0",
        "attribution": "乐园数据管理室 https://xenoblade2.cn",
        "json": {},
        "pages": {},
    }
    for prefix, folder in (("Mapinfo/", "mapinfo"), ("FLD maplist/", "maplist"), ("Gmk/", "gmk")):
        summary["json"][folder] = save_json_pages(prefix, folder)
        print(f"json {folder}: {summary['json'][folder]}", flush=True)
    summary["maps_downloaded"] = save_maps()
    print(f"maps touched: {summary['maps_downloaded']}", flush=True)
    save_icons()
    print("icons done", flush=True)
    for namespace, folder in NAMESPACES.items():
        summary["pages"][folder] = save_pages(namespace, folder)
        print(f"pages {folder}: {summary['pages'][folder]}", flush=True)
    (OUT / "manifest.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print("manifest written", flush=True)


if __name__ == "__main__":
    main()

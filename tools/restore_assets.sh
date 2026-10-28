#!/usr/bin/env bash
# 还原构建所需、但不纳入 git 的资源。
#
# 这些文件是从原版 APK 提取的（数据库 / 地图 / 物品分类图标），
# 版权归原作者与 Monolith Soft 所有，因此只保留在本地，不提交。
#
# 用法：
#   tools/restore_assets.sh            # 优先用 apk/extracted，没有就从 apk/*.apk 解压
#   tools/restore_assets.sh --verify   # 只校验，不写入
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets"
DB_SHA256="71f72425a21a48030b48f746fe5da8c3a6187d0cad5baa73dd49bcc38486db68"
VERIFY_ONLY=0
[ "${1:-}" = "--verify" ] && VERIFY_ONLY=1

fail() { echo "错误：$*" >&2; exit 1; }

check_db() {
  [ -f "$1" ] || fail "缺少 $1"
  local got
  got="$(shasum -a 256 "$1" | awk '{print $1}')"
  [ "$got" = "$DB_SHA256" ] || fail "数据库校验失败：$got != $DB_SHA256"
  echo "数据库 sha256 校验通过"
}

if [ "$VERIFY_ONLY" = 1 ]; then
  check_db "$DEST/xenoblade2.db"
  echo "地图：$(ls "$DEST/maps"/*.png 2>/dev/null | wc -l | tr -d ' ') 张 PNG"
  echo "物品图标：$(find "$DEST/item-icons" -type f | wc -l | tr -d ' ') 个"
  exit 0
fi

SRC=""
if [ -f "$ROOT/apk/extracted/assets/xenoblade2.db" ]; then
  SRC="$ROOT/apk/extracted/assets"
else
  APK="$(ls "$ROOT"/apk/*.apk 2>/dev/null | head -1 || true)"
  [ -n "$APK" ] || fail "找不到 apk/extracted/assets 或 apk/*.apk；请先把原版 APK 放到 apk/"
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  unzip -q "$APK" 'assets/xenoblade2.db' 'assets/maps/*' 'assets/item-icons/*' -d "$TMP"
  SRC="$TMP/assets"
fi

check_db "$SRC/xenoblade2.db"
[ -d "$SRC/maps" ] || fail "$SRC/maps 不存在"
[ -d "$SRC/item-icons" ] || fail "$SRC/item-icons 不存在"

mkdir -p "$DEST"
cp "$SRC/xenoblade2.db" "$DEST/xenoblade2.db"
rm -rf "$DEST/maps" "$DEST/item-icons"
cp -R "$SRC/maps" "$DEST/maps"
cp -R "$SRC/item-icons" "$DEST/item-icons"

echo "已还原到 $DEST"
echo "  地图 PNG：$(ls "$DEST/maps"/*.png | wc -l | tr -d ' ')"
echo "  物品图标：$(find "$DEST/item-icons" -type f | wc -l | tr -d ' ')"

#!/usr/bin/env bash
# Download the offline OSM database pinned in data/osm-db.lock, verify its SHA-256
# and integrity, and write it to the given path. Run from the repository root.
#   bash scripts/restore_osm_db.sh app/src/main/assets/df_roads_v3.sqlite
set -euo pipefail
DEST="${1:?destination .sqlite path}"
LOCK="$(dirname "$0")/../data/osm-db.lock"
URL="$(sed -n 's/^URL=//p' "$LOCK")"
SHA="$(sed -n 's/^SHA256=//p' "$LOCK")"
[[ "$URL" == https://github.com/alexpmr/speed-alert/releases/download/* ]] || { echo "Unexpected URL in $LOCK" >&2; exit 2; }
[[ "$SHA" =~ ^[0-9a-f]{64}$ ]] || { echo "Bad SHA256 in $LOCK" >&2; exit 2; }
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
curl --fail --location --retry 3 --connect-timeout 15 --max-time 300 -o "$TEMP/source" "$URL"
if command -v sha256sum >/dev/null; then echo "$SHA  $TEMP/source" | sha256sum -c -
else echo "$SHA  $TEMP/source" | shasum -a 256 -c -; fi
mkdir -p "$(dirname "$DEST")"
case "$URL" in
  *.apk) unzip -p "$TEMP/source" assets/df_roads_v3.sqlite > "$DEST" ;;
  *.sqlite) cp "$TEMP/source" "$DEST" ;;
  *) echo "Unsupported source type: $URL" >&2; exit 2 ;;
esac
python3 -I - "$DEST" <<'PY'
import sqlite3,sys
db=sqlite3.connect(sys.argv[1])
assert db.execute("PRAGMA quick_check").fetchone()[0]=="ok"
assert db.execute("PRAGMA user_version").fetchone()[0]==3
roads=db.execute("SELECT COUNT(*) FROM roads").fetchone()[0]
named=db.execute("SELECT COUNT(*) FROM roads WHERE name<>'' OR ref<>''").fetchone()[0]
cams=db.execute("SELECT COUNT(*) FROM cameras").fetchone()[0]
assert roads>=200000 and named>=150000 and cams>=100,(roads,named,cams)
try: built=dict(db.execute("SELECT k,v FROM metadata")).get("updated_utc","?")
except sqlite3.Error: built="?"
print(f"Offline OSM v3 restored: {roads} segments, {named} named, {cams} cameras, built {built}")
PY

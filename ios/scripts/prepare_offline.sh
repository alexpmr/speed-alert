#!/usr/bin/env bash
# Run from the repository root.
set -euo pipefail
ROOT="$(pwd)"
NAME="PT2VHF-Speed-Alert-v1.0.3-beta.1.apk"
SHA="9fab2b29e90f2d5b95682d257206e4f49a78d33c7e5562a1117c1588fa989b00"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
curl --fail --location --retry 3 --connect-timeout 15 --max-time 240 \
 -o "$TEMP/$NAME" \
 "https://github.com/alexpmr/speed-alert/releases/download/v1.0.3-beta.1/$NAME"
printf '%s  %s\n' "$SHA" "$TEMP/$NAME" | shasum -a 256 -c -
mkdir -p "$ROOT/ios/SpeedAlertIOS/Offline"
unzip -p "$TEMP/$NAME" assets/df_roads_v3.sqlite \
 > "$ROOT/ios/SpeedAlertIOS/Offline/df_roads_v3.sqlite"
python3 - "$ROOT/ios/SpeedAlertIOS/Offline/df_roads_v3.sqlite" <<'PY'
import sqlite3,sys
db=sqlite3.connect(sys.argv[1])
assert db.execute("PRAGMA user_version").fetchone()[0]==3
assert db.execute("PRAGMA quick_check").fetchone()[0]=="ok"
n=db.execute("SELECT COUNT(*) FROM roads").fetchone()[0]
c=db.execute("SELECT COUNT(*) FROM cameras").fetchone()[0]
assert n>=200000 and c>=100,(n,c)
print("iOS offline data verified:",n,"segments",c,"cameras")
PY
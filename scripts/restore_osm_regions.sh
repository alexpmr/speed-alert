#!/usr/bin/env bash
# Restore the regional databases pinned in data/osm-regions.lock into the Play asset pack
# modules (osm_<region>/src/main/assets/osm/<region>.sqlite). Run from the repository root.
#   bash scripts/restore_osm_regions.sh               # every region is required (release)
#   bash scripts/restore_osm_regions.sh --allow-missing  # CI smoke tests before the first rebuild
set -euo pipefail
ALLOW_MISSING=${1:-}
LOCK="$(dirname "$0")/../data/osm-regions.lock"
for region in centro-oeste sul sudeste; do
  line="$(grep -E "^$region " "$LOCK" || true)"
  if [ -z "$line" ]; then
    if [ "$ALLOW_MISSING" = "--allow-missing" ]; then echo "::warning::No database pinned for $region"; continue; fi
    echo "::error::No database pinned for $region in data/osm-regions.lock. Run 'Rebuild offline OSM database' first."; exit 2
  fi
  read -r _ url sha <<<"$line"
  [[ "$url" == https://github.com/alexpmr/speed-alert/releases/download/*.sqlite ]] || { echo "Bad URL for $region" >&2; exit 2; }
  [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || { echo "Bad SHA256 for $region" >&2; exit 2; }
  dest="osm_${region//-/_}/src/main/assets/osm/$region.sqlite"
  mkdir -p "$(dirname "$dest")"
  curl --fail --location --retry 3 --connect-timeout 15 --max-time 1200 -o "$dest" "$url"
  echo "$sha  $dest" | sha256sum -c -
  python3 -I - "$dest" "$region" <<'PY'
import sqlite3,sys
d=sqlite3.connect(sys.argv[1])
assert d.execute("PRAGMA quick_check").fetchone()[0]=="ok"
assert d.execute("PRAGMA user_version").fetchone()[0]==3
meta=dict(d.execute("SELECT k,v FROM metadata"))
assert meta.get("region")==sys.argv[2],meta.get("region")
assert all("bbox_"+k in meta for k in ("south","north","west","east"))
roads=d.execute("SELECT COUNT(*) FROM roads").fetchone()[0]
assert roads>=100000,roads
print(f"{sys.argv[2]}: {roads} segments, built {meta.get('updated_utc','?')[:10]}, profile {meta.get('profile')}")
PY
done

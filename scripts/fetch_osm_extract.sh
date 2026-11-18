#!/usr/bin/env bash
# Download a Brazilian regional OSM extract with integrity check.
#   bash scripts/fetch_osm_extract.sh centro-oeste /tmp/r.osm.pbf
# Geofabrik first (MD5-checked), retried with long pauses because it refuses
# connections from busy CI address ranges at times; then the openstreetmap.fr mirror.
set -uo pipefail
REGION="${1:?region: centro-oeste|sul|sudeste}"; OUT="${2:?output .osm.pbf}"
case "$REGION" in
  centro-oeste) FR=centre-ouest ;;  sul) FR=sud ;;  sudeste) FR=sudeste ;;
  *) echo "unknown region $REGION" >&2; exit 2 ;;
esac
geofabrik() {
  local base="https://download.geofabrik.de/south-america/brazil/$REGION-latest.osm.pbf"
  curl --fail --location --connect-timeout 20 --max-time 2400 -o "$OUT" "$base" &&
  curl --fail --location --connect-timeout 20 -o "$OUT.md5" "$base.md5" &&
  echo "$(cut -d' ' -f1 "$OUT.md5")  $OUT" | md5sum -c -
}
for attempt in 1 2 3 4 5 6 7 8; do
  if geofabrik; then echo "Geofabrik OK ($REGION)"; exit 0; fi
  echo "::warning::Geofabrik attempt $attempt failed for $REGION; waiting 60 s"; sleep 60
done
mirror="https://download.openstreetmap.fr/extracts/south-america/brazil"
for name in "$REGION" "$FR"; do
  if curl --fail --location --connect-timeout 20 --max-time 2400 -o "$OUT" "$mirror/$name-latest.osm.pbf"; then
    if curl --fail --location -o "$OUT.md5" "$mirror/$name-latest.osm.pbf.md5" 2>/dev/null; then
      echo "$(cut -d' ' -f1 "$OUT.md5")  $OUT" | md5sum -c - || continue
    fi
    python3 -I -c "import osmium,sys;osmium.io.Reader(sys.argv[1]).header()" "$OUT" || continue
    echo "openstreetmap.fr OK ($REGION as $name)"; exit 0
  fi
done
echo "::error::Could not download the $REGION extract"; exit 1

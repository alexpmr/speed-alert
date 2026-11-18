#!/usr/bin/env bash
# Run from the repository root. Uses the same pinned database as Android (data/osm-db.lock).
set -euo pipefail
bash scripts/restore_osm_db.sh ios/SpeedAlertIOS/Offline/df_roads_v3.sqlite

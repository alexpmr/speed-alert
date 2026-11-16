#!/usr/bin/env python3
"""Convert OSM road SQLite v1 into portable, indexed SQLite v2.

Data: © OpenStreetMap contributors, ODbL 1.0.
Usage: python tools/convert_offline_db.py old.sqlite new.sqlite
"""
import math
import pathlib
import sqlite3
import sys

src, dst = sys.argv[1:]
pathlib.Path(dst).parent.mkdir(parents=True, exist_ok=True)
pathlib.Path(dst).unlink(missing_ok=True)
source = sqlite3.connect("file:" + src + "?mode=ro", uri=True)
target = sqlite3.connect(dst)
target.executescript("""
CREATE TABLE roads(
  id INTEGER PRIMARY KEY,
  lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL
);
CREATE TABLE road_tiles(
  tile_lat INTEGER NOT NULL,
  tile_lon INTEGER NOT NULL,
  road_id INTEGER NOT NULL,
  PRIMARY KEY (tile_lat,tile_lon,road_id)
) WITHOUT ROWID;
PRAGMA user_version=2;
""")
rows = source.execute("SELECT id,lat1,lon1,lat2,lon2,fwd,bwd FROM roads")
count = 0
while True:
    batch = rows.fetchmany(1500)
    if not batch:
        break
    target.executemany("INSERT INTO roads VALUES(?,?,?,?,?,?,?)", batch)
    coords = []
    for ident,a,b,c,d,fw,bw in batch:
        lat0,lat1 = sorted((math.floor(a*1000),math.floor(c*1000)))
        lon0,lon1 = sorted((math.floor(b*1000),math.floor(d*1000)))
        if (lat1-lat0+1)*(lon1-lon0+1) > 1000:
            continue
        for lat in range(lat0,lat1+1):
            for lon in range(lon0,lon1+1):
                coords.append((lat,lon,ident))
    target.executemany("INSERT OR IGNORE INTO road_tiles VALUES(?,?,?)", coords)
    count += len(batch)
target.commit()
n=target.execute("SELECT COUNT(*) FROM road_tiles").fetchone()[0]
check=target.execute("PRAGMA quick_check").fetchone()[0]
version=target.execute("PRAGMA user_version").fetchone()[0]
assert check=="ok" and version==2 and count>70000 and n>=count, (count,n,check,version)
print("SQLite v2 ready:",count,"roads,",n,"tile entries",flush=True)
target.execute("VACUUM")
target.close()
source.close()

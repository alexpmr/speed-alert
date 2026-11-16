#!/usr/bin/env python3
"""Fallback: upgrade validated DF SQLite v2 to v3 without fabricating street names or cameras.
Names and cameras remain unknown until the full tagged OSM extract is reachable.
"""
import sqlite3,sys,pathlib,datetime
sourcePath,outputPath=sys.argv[1:3]
pathlib.Path(outputPath).parent.mkdir(parents=True,exist_ok=True)
pathlib.Path(outputPath).unlink(missing_ok=True)
src=sqlite3.connect("file:"+sourcePath+"?mode=ro",uri=True)
db=sqlite3.connect(outputPath)
db.executescript("""
CREATE TABLE roads(id INTEGER PRIMARY KEY,wayid INTEGER,lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL,name TEXT,ref TEXT);
CREATE TABLE road_tiles(tile_lat INTEGER,tile_lon INTEGER,road_id INTEGER,PRIMARY KEY(tile_lat,tile_lon,road_id)) WITHOUT ROWID;
CREATE TABLE cameras(id INTEGER PRIMARY KEY,wayid INTEGER,lat REAL,lon REAL,speed REAL,tile_lat INTEGER,tile_lon INTEGER);
CREATE INDEX cameras_tiles ON cameras(tile_lat,tile_lon);
CREATE TABLE metadata(k TEXT PRIMARY KEY,v TEXT);
PRAGMA user_version=3;
""")
db.executemany("INSERT INTO roads VALUES(?,?,?,?,?,?,?,?,?,?)",
  ((id,id,a,b,c,d,fw,bw,'','') for id,a,b,c,d,fw,bw in
    src.execute("SELECT id,lat1,lon1,lat2,lon2,fwd,bwd FROM roads")))
db.executemany("INSERT INTO road_tiles VALUES(?,?,?)",
   src.execute("SELECT tile_lat,tile_lon,road_id FROM road_tiles"))
db.executemany("INSERT INTO metadata VALUES(?,?)",[
 ("data_origin","© OpenStreetMap contributors, ODbL 1.0"),
 ("dataset","fallback-v2-compatible"),
 ("named_roads","unavailable"),
 ("speed_cameras","unavailable"),
 ("updated_utc",datetime.datetime.now(datetime.timezone.utc).isoformat())
])
db.commit()
assert db.execute("PRAGMA quick_check").fetchone()[0]=="ok"
assert db.execute("PRAGMA user_version").fetchone()[0]==3
roads=db.execute("SELECT count(*) FROM roads").fetchone()[0]
tiles=db.execute("SELECT count(*) FROM road_tiles").fetchone()[0]
assert roads>=70000 and tiles>=roads
print("FALLBACK V3 GENERATED:",roads,"speed segments, names/radars unavailable")
db.execute("VACUUM")
db.close();src.close()

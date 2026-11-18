#!/usr/bin/env python3
"""Split regional offline OSM databases (v3 schema) into 1x1 degree tiles for on-demand download.

    python3 -I tools/build_tiles.py OUTDIR BASE_URL centro-oeste.sqlite sul.sqlite sudeste.sqlite

Writes OUTDIR/<tile>.sqlite.gz (one v3 database per tile, gzip) and OUTDIR/tiles.json.
Tile names use the south-west corner: s16w048 covers lat [-16,-15), lon [-48,-47).
Road segments touching the tile are kept whole; cameras are kept with a 2 km margin so a
camera just across the edge is still found from the tile the driver is in.
Overlapping regional extracts are de-duplicated. © OpenStreetMap contributors, ODbL 1.0.
"""
import sys,sqlite3,gzip,hashlib,json,os,datetime,shutil

SCHEMA="""
CREATE TABLE roads(id INTEGER PRIMARY KEY,wayid INTEGER,lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL,name TEXT,ref TEXT);
CREATE TABLE road_tiles(tile_lat INTEGER,tile_lon INTEGER,road_id INTEGER,PRIMARY KEY(tile_lat,tile_lon,road_id)) WITHOUT ROWID;
CREATE TABLE cameras(id INTEGER PRIMARY KEY,wayid INTEGER,lat REAL,lon REAL,speed REAL,tile_lat INTEGER,tile_lon INTEGER);
CREATE INDEX cameras_tiles ON cameras(tile_lat,tile_lon);
CREATE TABLE metadata(k TEXT PRIMARY KEY,v TEXT);
PRAGMA user_version=3;
"""
CAMERA_MARGIN=20   # in 0.001-degree index tiles (~2 km)

def name(lat0,lon0):
    return f"{'s' if lat0<0 else 'n'}{abs(lat0):02d}{'w' if lon0<0 else 'e'}{abs(lon0):03d}"

def main():
    out,base_url,*sources=sys.argv[1:]
    os.makedirs(out,exist_ok=True)
    srcs=[]
    for p in sources:
        d=sqlite3.connect(f"file:{p}?mode=ro",uri=True)
        meta=dict(d.execute("SELECT k,v FROM metadata"))
        srcs.append((d,meta.get("region",os.path.basename(p)),meta))
    tiles={}   # (lat0,lon0) -> [regions]
    for d,region,_ in srcs:
        for lat0,lon0 in d.execute("SELECT DISTINCT tile_lat/1000 - (tile_lat%1000<0), tile_lon/1000 - (tile_lon%1000<0) FROM road_tiles"):
            # SQLite integer division truncates toward zero; the subtraction makes it a floor.
            tiles.setdefault((lat0,lon0),[]).append(region)
    built=max(m.get("updated_utc","") for _,_,m in srcs)
    manifest={"schema":1,"generated":datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
              "data_built":built,"base_url":base_url.rstrip("/")+"/","tile_degrees":1,"tiles":{}}
    tmp=os.path.join(out,"_tmp.sqlite")
    for (lat0,lon0),regions in sorted(tiles.items()):
        tid=name(lat0,lon0)
        if os.path.exists(tmp):os.remove(tmp)
        t=sqlite3.connect(tmp);t.executescript(SCHEMA)
        t.execute("PRAGMA journal_mode=OFF");t.execute("PRAGMA synchronous=OFF")
        la,lo=lat0*1000,lon0*1000
        seen={};cams=set();next_id=1
        for d,region,_ in srcs:
            if region not in regions:continue
            rows=d.execute("""SELECT r.id,r.wayid,r.lat1,r.lon1,r.lat2,r.lon2,r.fwd,r.bwd,r.name,r.ref
                FROM roads r WHERE r.id IN (SELECT road_id FROM road_tiles
                WHERE tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?)""",(la,la+999,lo,lo+999)).fetchall()
            idmap={}
            for rid,*rest in rows:
                key=(rest[0],rest[1],rest[2],rest[3],rest[4])
                if key not in seen:
                    seen[key]=next_id
                    t.execute("INSERT INTO roads VALUES(?,?,?,?,?,?,?,?,?,?)",(next_id,*rest))
                    next_id+=1
                idmap[rid]=seen[key]
            t.executemany("INSERT OR IGNORE INTO road_tiles VALUES(?,?,?)",
                ((tl,tn,idmap[rid]) for tl,tn,rid in d.execute(
                    "SELECT tile_lat,tile_lon,road_id FROM road_tiles WHERE tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?",
                    (la,la+999,lo,lo+999)) if rid in idmap))
            for wayid,lat,lon,speed,tl,tn in d.execute(
                    "SELECT wayid,lat,lon,speed,tile_lat,tile_lon FROM cameras WHERE tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?",
                    (la-CAMERA_MARGIN,la+999+CAMERA_MARGIN,lo-CAMERA_MARGIN,lo+999+CAMERA_MARGIN)):
                if (wayid,lat,lon) in cams:continue
                cams.add((wayid,lat,lon))
                t.execute("INSERT INTO cameras(wayid,lat,lon,speed,tile_lat,tile_lon) VALUES(?,?,?,?,?,?)",(wayid,lat,lon,speed,tl,tn))
        nroads=next_id-1
        t.executemany("INSERT INTO metadata VALUES(?,?)",[
            ("data_origin","© OpenStreetMap contributors; ODbL 1.0"),("region",tid),("tile",tid),
            ("regions",",".join(sorted(set(regions)))),("profile",srcs[0][2].get("profile","")),
            ("updated_utc",built),("roads",str(nroads)),("speed_cameras",str(len(cams))),
            ("bbox_south",repr(float(lat0))),("bbox_north",repr(float(lat0+1))),
            ("bbox_west",repr(float(lon0))),("bbox_east",repr(float(lon0+1)))])
        t.commit()
        assert t.execute("PRAGMA quick_check").fetchone()[0]=="ok"
        t.execute("VACUUM");t.close()
        raw=open(tmp,"rb").read()
        sha=hashlib.sha256(raw).hexdigest()
        gz=os.path.join(out,tid+".sqlite.gz")
        with gzip.GzipFile(gz,"wb",compresslevel=9,mtime=0) as f:f.write(raw)
        manifest["tiles"][tid]={"regions":sorted(set(regions)),"sha256":sha,"bytes":len(raw),
            "gz_bytes":os.path.getsize(gz),"roads":nroads,"cameras":len(cams)}
    if os.path.exists(tmp):os.remove(tmp)
    with open(os.path.join(out,"tiles.json"),"w") as f:json.dump(manifest,f,separators=(",",":"),sort_keys=True)
    total=sum(v["gz_bytes"] for v in manifest["tiles"].values())
    biggest=max(manifest["tiles"].items(),key=lambda kv:kv[1]["gz_bytes"])
    print(f"{len(manifest['tiles'])} tiles, {total/1048576:.0f} MB gz total, "
          f"largest {biggest[0]} {biggest[1]['gz_bytes']/1048576:.1f} MB gz",flush=True)

if __name__=="__main__":main()

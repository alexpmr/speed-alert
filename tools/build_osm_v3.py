#!/usr/bin/env python3
"""Build offline OSM speed/name/radar database for Brasília DF + surroundings.
Source: Geofabrik Centro-Oeste PBF; © OpenStreetMap contributors, ODbL.
No inferred speed limits; only explicit numeric maxspeed tags.
"""
import sys,sqlite3,re,math,pathlib,datetime
import osmium

pbf,output=sys.argv[1:3]
target=pathlib.Path(output);target.parent.mkdir(parents=True,exist_ok=True)
target.unlink(missing_ok=True)
db=sqlite3.connect(str(target))
db.execute("PRAGMA journal_mode=OFF")
db.execute("PRAGMA synchronous=OFF")
db.executescript("""
CREATE TABLE roads(id INTEGER PRIMARY KEY,wayid INTEGER,lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL,name TEXT,ref TEXT);
CREATE TABLE road_tiles(tile_lat INTEGER,tile_lon INTEGER,road_id INTEGER,PRIMARY KEY(tile_lat,tile_lon,road_id)) WITHOUT ROWID;
CREATE TABLE cameras(id INTEGER PRIMARY KEY,wayid INTEGER,lat REAL,lon REAL,speed REAL,tile_lat INTEGER,tile_lon INTEGER);
CREATE INDEX cameras_tiles ON cameras(tile_lat,tile_lon);
CREATE TABLE metadata(k TEXT PRIMARY KEY,v TEXT);
PRAGMA user_version=3;
""")
bounds=(-16.30,-15.35,-48.43,-47.18)
def nearby(lat,lon):
    return bounds[0]<=lat<=bounds[1] and bounds[2]<=lon<=bounds[3]
def kmh(text):
    if not text:return None
    m=re.fullmatch(r"(\d{1,3})\s*(km/h|kmh|kph|mph)?",str(text).lower().strip())
    if not m:return None
    value=int(m.group(1))
    if value<10 or value>160:return None
    return round(value*1.609344,2) if m.group(2)=="mph" else float(value)
class Builder(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.cameras={}
        self.cameraWays={}
        self.roads=[]
        self.tiles=[]
        self.count=0
    def node(self,n):
        if n.tags.get('highway')!='speed_camera':return
        try:
            lat=n.location.lat;lon=n.location.lon
        except (ValueError,osmium.InvalidLocationError):return
        if nearby(lat,lon):
            self.cameras[int(n.id)]=(lat,lon,kmh(n.tags.get('maxspeed')))
    def way(self,w):
        highway=w.tags.get('highway')
        if highway not in ('motorway','trunk','primary','secondary','tertiary','residential',
            'unclassified','living_street','service','motorway_link','trunk_link',
            'primary_link','secondary_link','tertiary_link'):return
        name=(w.tags.get('name') or '').strip()[:120]
        ref=(w.tags.get('ref') or '').strip()[:50]
        if w.tags.get('maxspeed:conditional'):
            fwd=bwd=None
        else:
            shared=kmh(w.tags.get('maxspeed'))
            fwd=kmh(w.tags.get('maxspeed:forward')) or shared
            bwd=kmh(w.tags.get('maxspeed:backward')) or shared
        if not (name or ref or fwd or bwd):return
        try:
            points=[(n.location.lat,n.location.lon) for n in w.nodes]
        except (ValueError,osmium.InvalidLocationError):return
        if len(points)<2:return
        wayid=int(w.id)
        for n in w.nodes:
            cameraId=int(n.ref)
            if cameraId in self.cameras:
                if cameraId not in self.cameraWays:self.cameraWays[cameraId]=wayid
                elif self.cameraWays[cameraId]!=wayid:self.cameraWays[cameraId]=None
        for (a,b),(c,d) in zip(points,points[1:]):
            if not (nearby(a,b) or nearby(c,d)) or (a==c and b==d):continue
            self.count+=1
            self.roads.append((self.count,wayid,a,b,c,d,fwd,bwd,name,ref))
            la0,la1=sorted((math.floor(a*1000),math.floor(c*1000)))
            lo0,lo1=sorted((math.floor(b*1000),math.floor(d*1000)))
            if (la1-la0+1)*(lo1-lo0+1)>1000:continue
            for lat in range(la0,la1+1):
                for lon in range(lo0,lo1+1):
                    self.tiles.append((lat,lon,self.count))
            if len(self.roads)>=10000:self.flush()
    def flush(self):
        db.executemany("INSERT INTO roads VALUES(?,?,?,?,?,?,?,?,?,?)",self.roads)
        db.executemany("INSERT OR IGNORE INTO road_tiles VALUES(?,?,?)",self.tiles)
        self.roads.clear();self.tiles.clear()
b=Builder()
b.apply_file(pbf,locations=True)
b.flush()
cams=[(cid,wayid,*b.cameras[cid],math.floor(b.cameras[cid][0]*1000),
       math.floor(b.cameras[cid][1]*1000))
      for cid,wayid in b.cameraWays.items() if wayid is not None and cid in b.cameras]
onWay=len(cams)
# Many Brazilian cameras are mapped as a node beside the carriageway (or on a
# junction node shared by two ways). Attach those to the nearest road segment
# within SNAP_METERS so the app, which only matches cameras on the current way,
# can still warn about them.
SNAP_METERS=25.0
def nearestWay(lat,lon):
    tl=math.floor(lat*1000);tn=math.floor(lon*1000)
    fx=111320.0*math.cos(math.radians(lat))
    best=None
    for la1,lo1,la2,lo2,wayid in db.execute(
        "SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.wayid FROM road_tiles t JOIN roads r ON t.road_id=r.id "
        "WHERE t.tile_lat BETWEEN ? AND ? AND t.tile_lon BETWEEN ? AND ?",(tl-1,tl+1,tn-1,tn+1)):
        x1=(lo1-lon)*fx;y1=(la1-lat)*111320.0;x2=(lo2-lon)*fx;y2=(la2-lat)*111320.0
        dx=x2-x1;dy=y2-y1;n=dx*dx+dy*dy
        if n<1.0:continue
        t=max(0.0,min(1.0,-(x1*dx+y1*dy)/n))
        dist=math.hypot(x1+t*dx,y1+t*dy)
        if dist<=SNAP_METERS and (best is None or dist<best[0]):best=(dist,wayid)
    return best[1] if best else None
matched={c[0] for c in cams}
for cid,(lat,lon,speed) in b.cameras.items():
    if cid in matched:continue
    wayid=nearestWay(lat,lon)
    if wayid is not None:
        cams.append((cid,wayid,lat,lon,speed,math.floor(lat*1000),math.floor(lon*1000)))
snapped=len(cams)-onWay
db.executemany("INSERT INTO cameras VALUES(?,?,?,?,?,?,?)",cams)
db.executemany("INSERT INTO metadata VALUES(?,?)",[
 ('data_origin','© OpenStreetMap contributors; ODbL 1.0'),
 ('coverage','Brasília DF bounding region; explicit speed data only'),
 ('updated_utc',datetime.datetime.now(datetime.timezone.utc).isoformat()),
 ('roads',str(b.count)),('speed_cameras',str(len(cams))),
 ('speed_cameras_snapped',str(snapped)),('speed_cameras_in_osm',str(len(b.cameras)))
])
db.commit()
roads=db.execute("SELECT COUNT(*) FROM roads").fetchone()[0]
speed=db.execute("SELECT COUNT(*) FROM roads WHERE fwd IS NOT NULL OR bwd IS NOT NULL").fetchone()[0]
named=db.execute("SELECT COUNT(*) FROM roads WHERE name<>'' OR ref<>''").fetchone()[0]
tiles=db.execute("SELECT COUNT(*) FROM road_tiles").fetchone()[0]
assert roads>=50000 and tiles>=50000 and speed>=10000,(roads,tiles,speed)
assert db.execute("PRAGMA quick_check").fetchone()[0]=="ok"
assert db.execute("PRAGMA user_version").fetchone()[0]==3
print(f"OSM V3: {roads} roads, {speed} with maxspeed, {named} named, {len(cams)} cameras ({snapped} snapped from {len(b.cameras)-onWay} off-way), {tiles} tiles",flush=True)
db.execute("VACUUM")
db.close()

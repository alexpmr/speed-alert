package com.pt2vhf.speedalert

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.floor

/**
 * Play version, outside the prebuilt tiles: road limits for small 0.1° cells (~11 km) are
 * fetched from the public OpenStreetMap Overpass API and stored as v3 databases, so Roads
 * reads them like any other tile. Only the cell's bounding box is sent (no exact position).
 */
object OnlineTiles {
  private const val TAG="PT2VHF.Online"
  private val SERVERS=listOf("https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter")
  private const val REFRESH_MS=30L*24*60*60*1000
  @Volatile var lastError:String?=null; private set

  private fun dir(c:Context)=File(c.applicationContext.filesDir,"online").apply { mkdirs() }
  fun files(c:Context):List<Pair<String,File>> =
    dir(c).listFiles { f -> f.name.endsWith(".sqlite") }?.map { it.name.removeSuffix(".sqlite") to it } ?: emptyList()
  fun clear(c:Context) { dir(c).listFiles()?.forEach { it.delete() } }
  fun usage(c:Context):Pair<Int,Long> = files(c).let { l -> l.size to l.sumOf { it.second.length() } }

  private fun name(la:Int,lo:Int)="o${la}_${lo}"
  /** The driver's cell first, then its 8 neighbours. */
  private fun around(lat:Double,lon:Double):List<Pair<Int,Int>> {
    val la=floor(lat*10).toInt(); val lo=floor(lon*10).toInt()
    return listOf(la to lo)+(-1..1).flatMap { a -> (-1..1).map { b -> la+a to lo+b } }.filter { it!=la to lo }
  }

  /** Blocking (worker thread). Returns true when a cell file changed and Roads must reload. */
  @Synchronized fun ensure(c:Context,lat:Double,lon:Double):Boolean {
    val cells=around(lat,lon)
    var changed=false
    var failed:String?=null
    for((la,lo) in cells) {
      val f=File(dir(c),name(la,lo)+".sqlite")
      if(f.exists() && System.currentTimeMillis()-f.lastModified()<REFRESH_MS)continue
      try { build(c,la,lo,f); changed=true }
      catch(e:Exception) { Log.w(TAG,"cell ${name(la,lo)}",e); failed=e.message?:e.javaClass.simpleName; break }
    }
    lastError=failed
    evict(c,cells.map { name(it.first,it.second) }.toSet())
    return changed
  }
  /** Least recently fetched cells go first once the user's space limit is exceeded. */
  private fun evict(c:Context,keep:Set<String>) {
    val limit=AppConfig.tileLimitMb(AppConfig.prefs(c))*1048576L
    var total=files(c).sumOf { it.second.length() }
    files(c).filter { it.first !in keep }.sortedBy { it.second.lastModified() }.forEach { (_,f) ->
      if(total>limit) { total-=f.length(); f.delete() }
    }
  }

  private fun query(la:Int,lo:Int):String {
    val bbox=String.format(Locale.US,"%.1f,%.1f,%.1f,%.1f",la/10.0,lo/10.0,(la+1)/10.0,(lo+1)/10.0)
    val way="way[\"highway\"~\"^(${OsmTags.HIGHWAYS.joinToString("|")})$\"]"
    val keys=listOf("maxspeed","maxspeed:forward","maxspeed:backward","name","ref")
    return "[out:json][timeout:90];("+keys.joinToString("") { "$way[\"$it\"]($bbox);" }+");out tags geom;"
  }

  private fun build(c:Context,la:Int,lo:Int,target:File) {
    val body=("data="+URLEncoder.encode(query(la,lo),"UTF-8")).toByteArray()
    var last:Exception?=null
    for(server in SERVERS) {
      val conn=(URL(server).openConnection() as HttpURLConnection).apply {
        connectTimeout=10000; readTimeout=120000; requestMethod="POST"; doOutput=true
        setRequestProperty("User-Agent","PT2VHF-Speed-Alert (Android)")
        setRequestProperty("Content-Type","application/x-www-form-urlencoded")
      }
      try {
        conn.outputStream.use { it.write(body) }
        if(conn.responseCode!=200)throw IOException("OpenStreetMap HTTP ${conn.responseCode}")
        val tmp=File(target.parentFile,target.name+".part")
        try {
          write(conn.inputStream,tmp,la,lo)
          if(target.exists() && !target.delete())throw IOException("não foi possível substituir ${target.name}")
          if(!tmp.renameTo(target))throw IOException("não foi possível instalar ${target.name}")
        } finally { tmp.delete(); File(tmp.path+"-journal").delete() }
        return
      } catch(e:Exception) { last=e }
      finally { conn.disconnect() }
    }
    throw last?:IOException("OpenStreetMap indisponível")
  }

  /** Streams the Overpass JSON into a v3 database (same schema as tools/build_tiles.py). */
  private fun write(input:java.io.InputStream,file:File,la:Int,lo:Int) {
    file.delete()
    val db=SQLiteDatabase.openDatabase(file.path,null,
      SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    try {
      db.execSQL("CREATE TABLE roads(id INTEGER PRIMARY KEY,wayid INTEGER,lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL,name TEXT,ref TEXT)")
      db.execSQL("CREATE TABLE road_tiles(tile_lat INTEGER,tile_lon INTEGER,road_id INTEGER,PRIMARY KEY(tile_lat,tile_lon,road_id)) WITHOUT ROWID")
      db.execSQL("CREATE TABLE metadata(k TEXT PRIMARY KEY,v TEXT)")
      val road=db.compileStatement("INSERT INTO roads VALUES(?,?,?,?,?,?,?,?,?,?)")
      val tile=db.compileStatement("INSERT OR IGNORE INTO road_tiles VALUES(?,?,?)")
      var id=0L
      db.beginTransaction()
      try {
        JsonReader(input.reader(Charsets.UTF_8)).use { r ->
          r.beginObject()
          while(r.hasNext()) when(r.nextName()) {
            "elements" -> {
              r.beginArray()
              while(r.hasNext()) {
                val (wayid,tags,points)=readWay(r)
                val (fwd,bwd)=OsmTags.limits(tags)?:continue
                for(i in 0 until points.size-1) {
                  val (a,b)=points[i]; val (cc,d)=points[i+1]
                  if(a==cc && b==d)continue
                  id++
                  road.clearBindings()
                  road.bindLong(1,id); road.bindLong(2,wayid)
                  road.bindDouble(3,a); road.bindDouble(4,b); road.bindDouble(5,cc); road.bindDouble(6,d)
                  if(fwd!=null)road.bindDouble(7,fwd) else road.bindNull(7)
                  if(bwd!=null)road.bindDouble(8,bwd) else road.bindNull(8)
                  road.bindString(9,tags["name"].orEmpty().trim().take(120))
                  road.bindString(10,tags["ref"].orEmpty().trim().take(50))
                  road.executeInsert()
                  val la0=minOf(floor(a*1000),floor(cc*1000)).toInt(); val la1=maxOf(floor(a*1000),floor(cc*1000)).toInt()
                  val lo0=minOf(floor(b*1000),floor(d*1000)).toInt(); val lo1=maxOf(floor(b*1000),floor(d*1000)).toInt()
                  if((la1-la0+1)*(lo1-lo0+1)>1000)continue
                  for(x in la0..la1) for(y in lo0..lo1) {
                    tile.bindLong(1,x.toLong()); tile.bindLong(2,y.toLong()); tile.bindLong(3,id); tile.executeInsert()
                  }
                }
              }
              r.endArray()
            }
            "remark" -> { val m=r.nextString(); if(m.contains("error",true))throw IOException("OpenStreetMap: "+m.take(80)) }
            else -> r.skipValue()
          }
          r.endObject()
        }
        listOf("data_origin" to "© OpenStreetMap contributors; ODbL 1.0","region" to "online",
          "bbox_south" to "${la/10.0}","bbox_north" to "${(la+1)/10.0}",
          "bbox_west" to "${lo/10.0}","bbox_east" to "${(lo+1)/10.0}",
          "updated_utc" to java.time.Instant.now().toString(),"roads" to "$id").forEach { (k,v) ->
          db.execSQL("INSERT INTO metadata VALUES(?,?)",arrayOf<Any>(k,v)) }
        db.setTransactionSuccessful()
      } finally { db.endTransaction() }
      db.version=3
    } finally { db.close() }
  }

  private fun readWay(r:JsonReader):Triple<Long,Map<String,String>,List<Pair<Double,Double>>> {
    var id=0L; val tags=HashMap<String,String>(); val points=ArrayList<Pair<Double,Double>>()
    r.beginObject()
    while(r.hasNext()) when(r.nextName()) {
      "id" -> id=r.nextLong()
      "tags" -> { r.beginObject(); while(r.hasNext()) { val k=r.nextName(); tags[k]=r.nextString() }; r.endObject() }
      "geometry" -> {
        r.beginArray()
        while(r.hasNext()) {
          if(r.peek()==JsonToken.NULL) { r.nextNull(); continue }
          var lat=Double.NaN; var lon=Double.NaN
          r.beginObject()
          while(r.hasNext()) when(r.nextName()) { "lat"->lat=r.nextDouble(); "lon"->lon=r.nextDouble(); else->r.skipValue() }
          r.endObject()
          if(!lat.isNaN() && !lon.isNaN())points.add(lat to lon)
        }
        r.endArray()
      }
      else -> r.skipValue()
    }
    r.endObject()
    return Triple(id,tags,points)
  }
}

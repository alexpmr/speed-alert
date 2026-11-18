package com.pt2vhf.speedalert

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import java.io.File
import kotlin.math.*

/** Offline map matching for road speeds, names and road-mounted speed cameras. */
class Roads(context:Context) {
  data class Result(
    val limit:Double?, val source:String,
    val roadName:String="Via não identificada",
    val radarDistance:Double?=null, val radarLimit:Double?=null,
    val radarKey:String?=null,
    val wayId:Long=0L
  )
  /** One opened regional database and the area it covers (null = unknown, always queried). */
  private class Db(val id:String,val label:String,val db:SQLiteDatabase,
    val south:Double?,val north:Double?,val west:Double?,val east:Double?,val built:String) {
    fun covers(lat:Double,lon:Double):Boolean {
      if(south==null||north==null||west==null||east==null)return true
      val m=0.05
      return lat>=south-m && lat<=north+m && lon>=west-m && lon<=east+m
    }
  }
  companion object {
    private const val TAG="PT2VHF.Roads"
    @Volatile private var summary:String="?"
    fun regionLabel(id:String)=when(id) {
      "centro-oeste"->"Centro-Oeste"; "sul"->"Sul"; "sudeste"->"Sudeste"
      "df","df_roads_v3"->"DF e entorno"; else->"bloco "+id }
    /** Build date(s) of the loaded offline databases, for bug reports. */
    fun dataVersion(@Suppress("UNUSED_PARAMETER") context:Context):String=summary

    /**
     * Databases bundled as APK assets (github: df_roads_v3.sqlite) are copied once per app version
     * into filesDir/osm, so an app update always replaces older data. Downloaded tiles
     * (TileStore) are opened in place.
     */
    fun sources(context:Context):List<Pair<String,File>> {
      val app=context.applicationContext
      val out=ArrayList<Pair<String,File>>()
      val dir=File(app.filesDir,"osm").apply { mkdirs() }
      val version=try {
        val info=app.packageManager.getPackageInfo(app.packageName,0)
        if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode.toString()
        else @Suppress("DEPRECATION") info.versionCode.toString()
      } catch(_:Exception) {"0"}
      val bundled=ArrayList<String>()
      try { if(app.assets.list("")?.contains("df_roads_v3.sqlite")==true)bundled.add("df_roads_v3.sqlite") } catch(_:Exception) {}
      try { app.assets.list("osm")?.filter { it.endsWith(".sqlite") }?.forEach { bundled.add("osm/"+it) } } catch(_:Exception) {}
      for(asset in bundled) {
        val name=asset.substringAfterLast('/')
        val target=File(dir,name)
        val marker=File(dir,"$name.version")
        try {
          if(!target.exists() || marker.takeIf{it.exists()}?.readText()!=version) {
            val temp=File(dir,"$name.tmp")
            app.assets.open(asset).use { inp -> temp.outputStream().use { out -> inp.copyTo(out);out.fd.sync() } }
            if(target.exists() && !target.delete())throw java.io.IOException("cannot replace $name")
            if(!temp.renameTo(target))throw java.io.IOException("cannot install $name")
            marker.writeText(version)
          }
          out.add(name.removeSuffix(".sqlite") to target)
        } catch(e:Exception) { Log.e(TAG,"Could not install bundled $asset",e) }
      }
      // Remove the pre-1.0.7 single-file copy.
      File(app.filesDir,"df_roads_v3.sqlite").delete()
      out.addAll(TileStore.files(app))
      return out
    }
  }
  private val dbs=ArrayList<Db>()
  private val initError:String?
  init {
    var failed:String?=null
    for((id,file) in try { sources(context) } catch(e:Exception) { Log.e(TAG,"sources",e);emptyList() }) {
      try {
        val d=SQLiteDatabase.openDatabase(file.absolutePath,null,
          SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        if(d.version!=3){d.close();failed="versão inválida: $id";continue}
        val meta=HashMap<String,String>()
        try { d.rawQuery("SELECT k,v FROM metadata",null).use { while(it.moveToNext())meta[it.getString(0)]=it.getString(1) } }
        catch(_:Exception) {}
        dbs.add(Db(id,regionLabel(meta["region"]?:id),d,meta["bbox_south"]?.toDoubleOrNull(),meta["bbox_north"]?.toDoubleOrNull(),
          meta["bbox_west"]?.toDoubleOrNull(),meta["bbox_east"]?.toDoubleOrNull(),meta["updated_utc"]?.take(10)?:"?"))
      } catch(e:Exception) {
        failed=e.javaClass.simpleName
        Log.e(TAG,"Cannot open offline database $id",e)
      }
    }
    initError=if(dbs.isEmpty())(failed?:"nenhuma base instalada") else null
    summary=if(dbs.isEmpty())"nenhuma" else dbs.joinToString(", ") { it.id+" "+it.built }
  }
  fun close() { dbs.forEach { try { it.db.close() } catch(_:Exception) {} } }
  fun resolve(loc:Location, radarRangeMeters:Double=300.0):Result {
    if(dbs.isEmpty())return Result(null,"BASE OFFLINE INDISPONÍVEL: "+(initError ?: "erro"))
    val lat=loc.latitude;val lon=loc.longitude
    val here=dbs.filter { it.covers(lat,lon) }
    if(here.isEmpty())return Result(null,"REGIÃO SEM BASE OFFLINE — veja Configurações")
    if(!loc.hasBearing())return Result(null,"AGUARDANDO DIREÇÃO GPS")
    val tlat=floor(lat*1000.0).toInt()
    val tlon=floor(lon*1000.0).toInt()
    data class Match(val score:Double,val speed:Double?,val name:String,val wayid:Long,val db:Db)
    val candidates=ArrayList<Match>()
    try { for(src in here) {
      src.db.rawQuery(
        "SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.fwd,r.bwd,r.name,r.ref,r.wayid "+
        "FROM road_tiles t JOIN roads r ON t.road_id=r.id "+
        "WHERE t.tile_lat BETWEEN ? AND ? AND t.tile_lon BETWEEN ? AND ? "+
        "GROUP BY r.id LIMIT 1200",
        arrayOf((tlat-1).toString(),(tlat+1).toString(),
          (tlon-1).toString(),(tlon+1).toString())
      ).use { c ->
        val factor=111320.0*cos(Math.toRadians(lat))
        while(c.moveToNext()) {
          val x1=(c.getDouble(1)-lon)*factor
          val y1=(c.getDouble(0)-lat)*111320.0
          val x2=(c.getDouble(3)-lon)*factor
          val y2=(c.getDouble(2)-lat)*111320.0
          val dx=x2-x1;val dy=y2-y1
          val n=dx*dx+dy*dy
          if(n<1.0)continue
          val t=(-(x1*dx+y1*dy)/n).coerceIn(0.0,1.0)
          val distance=hypot(x1+t*dx,y1+t*dy)
          if(distance>27.0)continue
          val segmentBearing=(Math.toDegrees(atan2(dx,dy))+360.0)%360.0
          val f=angle(loc.bearing.toDouble(),segmentBearing)
          val b=angle(loc.bearing.toDouble(),(segmentBearing+180)%360)
          val heading=min(f,b)
          if(heading>45.0)continue
          val column=if(f<=b)4 else 5
          val max=if(c.isNull(column))null else c.getDouble(column)
          val named=c.getString(6).orEmpty().ifBlank{c.getString(7).orEmpty()}
          candidates.add(Match(distance+heading*0.3,max,named,c.getLong(8),src))
        }
      }
    } } catch(e:Exception) {
      Log.e("PT2VHF.Roads","Road query failed",e)
      return Result(null,"ERRO CONSULTA OFFLINE: "+e.javaClass.simpleName)
    }
    candidates.sortBy {it.score}
    val best=candidates.firstOrNull()?:return Result(null,"VIA OU LIMITE NÃO CADASTRADO")
    val competing=candidates.drop(1).any {
      it.score-best.score<7.0 && it.wayid!=best.wayid &&
      (it.speed!=best.speed || (it.name.isNotBlank() && best.name.isNotBlank() && it.name!=best.name))
    }
    if(competing)return Result(null,"VIA AMBÍGUA")
    val roadName=best.name.ifBlank{"Via não identificada"}
    var radarDistance:Double?=null
    var radarLimit:Double?=null
    var radarKey:String?=null
    // Show an upcoming camera farther away than the audible warning threshold, on this or
    // the next OSM ways. The service activates the radar alarm only inside the user-selected range.
    val radius=1500.0
    val offset=(radius+40.0)/111320.0
    val latTiles=(offset*1000).toInt()+2
    val lonTiles=(offset*1000/max(0.4,cos(Math.toRadians(lat)))).toInt()+2
    try {
      val cams=ArrayList<CameraAhead.Camera>()
      best.db.db.rawQuery(
        "SELECT lat,lon,speed,wayid FROM cameras WHERE "+
        "tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?",
        arrayOf((tlat-latTiles).toString(),(tlat+latTiles).toString(),
          (tlon-lonTiles).toString(),(tlon+lonTiles).toString())
      ).use { c ->
        while(c.moveToNext()) {
          val posted=if(c.isNull(2))best.speed else c.getDouble(2)
          if(posted!=null)cams.add(CameraAhead.Camera(c.getDouble(0),c.getDouble(1),posted,c.getLong(3)==best.wayid))
        }
      }
      CameraAhead.nearest(lat,lon,loc.bearing.toDouble(),cams,radius)?.let { (cam,dist) ->
        radarDistance=dist
        radarLimit=cam.speed
        radarKey=String.format(java.util.Locale.US,"%.6f,%.6f",cam.lat,cam.lon)
      }
    } catch(e:Exception) {Log.w("PT2VHF.Roads","Radar lookup failed",e)}
    return Result(best.speed,"OSM OFFLINE • "+best.db.label,roadName,radarDistance,radarLimit,radarKey,best.wayid)
  }
  private fun angle(a:Double,b:Double)=abs((a-b+540.0)%360.0-180.0)
}

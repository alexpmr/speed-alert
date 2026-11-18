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
    val radarKey:String?=null
  )
  companion object {
    /** Build date of the installed offline database (metadata.updated_utc), for bug reports. */
    fun dataVersion(context:Context):String=try {
      val f=File(context.applicationContext.filesDir,"df_roads_v3.sqlite")
      SQLiteDatabase.openDatabase(f.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use { d ->
        d.rawQuery("SELECT v FROM metadata WHERE k='updated_utc'",null).use {
          if(it.moveToFirst())it.getString(0).take(10) else "?"
        }
      }
    } catch(_:Exception) {"?"}
  }
  private val app=context.applicationContext
  private val file=File(app.filesDir,"df_roads_v3.sqlite")
  private val db:SQLiteDatabase?
  private val initError:String?
  init {
    var opened:SQLiteDatabase?=null
    var failed:String?=null
    try { opened=openChecked() }
    catch(e:Exception) {
      failed=e.javaClass.simpleName
      Log.e("PT2VHF.Roads","Offline v3 initialization failed",e)
    }
    db=opened
    initError=failed
  }
  private fun replace() {
    val temp=File(file.parentFile,file.name+".tmp")
    try {
      app.assets.open(file.name).use { inp ->
        temp.outputStream().use { out -> inp.copyTo(out);out.fd.sync() }
      }
      if(file.exists() && !file.delete())throw java.io.IOException("db replacement failed")
      if(!temp.renameTo(file))throw java.io.IOException("db installation failed")
    } finally { if(temp.exists())temp.delete() }
  }
  private fun openChecked():SQLiteDatabase {
    if(!file.exists())replace()
    fun open()=SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READONLY)
    try {
      val d=open()
      val valid=d.version==3 && d.rawQuery("SELECT COUNT(*) FROM road_tiles",null).use {
        it.moveToFirst() && it.getLong(0)>=50000L
      }
      if(valid)return d
      d.close()
    } catch(e:Exception) {Log.w("PT2VHF.Roads","Bad local db, recovering",e)}
    replace()
    val d=open()
    if(d.version!=3){d.close();throw java.io.IOException("Invalid v3 database")}
    return d
  }
  fun resolve(loc:Location, radarRangeMeters:Double=300.0):Result {
    val d=db?:return Result(null,"BASE OFFLINE INDISPONÍVEL: "+(initError ?: "erro"))
    val lat=loc.latitude;val lon=loc.longitude
    if(!loc.hasBearing())return Result(null,"AGUARDANDO DIREÇÃO GPS")
    val tlat=floor(lat*1000.0).toInt()
    val tlon=floor(lon*1000.0).toInt()
    data class Match(val score:Double,val speed:Double?,val name:String,val wayid:Long)
    val candidates=ArrayList<Match>()
    try {
      d.rawQuery(
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
          candidates.add(Match(distance+heading*0.3,max,named,c.getLong(8)))
        }
      }
    } catch(e:Exception) {
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
    // Show an upcoming camera farther away than the audible warning threshold.
    // The service activates the radar alarm only inside the user-selected range.
    val radius=1500.0
    val offset=(radius+40.0)/111320.0
    val latTiles=(offset*1000).toInt()+2
    val lonTiles=(offset*1000/max(0.4,cos(Math.toRadians(lat)))).toInt()+2
    try {
      d.rawQuery(
        "SELECT lat,lon,speed FROM cameras WHERE wayid=? AND "+
        "tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?",
        arrayOf(best.wayid.toString(),(tlat-latTiles).toString(),(tlat+latTiles).toString(),
          (tlon-lonTiles).toString(),(tlon+lonTiles).toString())
      ).use { c ->
        val factor=111320.0*cos(Math.toRadians(lat))
        while(c.moveToNext()) {
          val east=(c.getDouble(1)-lon)*factor
          val north=(c.getDouble(0)-lat)*111320.0
          val dist=hypot(east,north)
          if(dist>radius || dist<8.0)continue
          val heading=(Math.toDegrees(atan2(east,north))+360)%360
          if(angle(loc.bearing.toDouble(),heading)>45.0)continue
          val posted=if(c.isNull(2))best.speed else c.getDouble(2)
          if(posted==null)continue
          if(dist<(radarDistance ?: Double.POSITIVE_INFINITY)) {
            radarDistance=dist
            radarLimit=posted
            radarKey=String.format(java.util.Locale.US,"%.6f,%.6f",c.getDouble(0),c.getDouble(1))
          }
        }
      }
    } catch(e:Exception) {Log.w("PT2VHF.Roads","Radar lookup failed",e)}
    return Result(best.speed,"OSM OFFLINE v3",roadName,radarDistance,radarLimit,radarKey)
  }
  private fun angle(a:Double,b:Double)=abs((a-b+540.0)%360.0-180.0)
}

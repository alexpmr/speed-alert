package com.pt2vhf.speedalert

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import java.io.File
import kotlin.math.*

/** Portable offline SQLite speed limits, using a regular indexed tile table. */
class Roads(context: Context) {
  data class Result(val limit: Double?, val source: String)
  private val app = context.applicationContext
  private val file = File(context.filesDir, "df_roads_v2.sqlite")
  private var db: SQLiteDatabase? = null
  private var initError: String? = null
  init {
    try { db = openChecked() }
    catch (e: Exception) {
      initError = e.javaClass.simpleName
      Log.e("PT2VHF.Roads", "Offline initialization failed", e)
    }
  }
  private fun install() {
    val temp = File(file.parentFile, file.name + ".tmp")
    try {
      app.assets.open(file.name).use { src ->
        temp.outputStream().use { out ->
          src.copyTo(out)
          out.fd.sync()
        }
      }
      if (file.exists() && !file.delete()) throw java.io.IOException("old db not removable")
      if (!temp.renameTo(file)) throw java.io.IOException("db rename failed")
    } finally {
      if (temp.exists()) temp.delete()
    }
  }
  private fun openChecked(): SQLiteDatabase {
    if (!file.exists()) install()
    fun open() = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    try {
      val candidate = open()
      val valid = candidate.version == 2 &&
        candidate.rawQuery("SELECT COUNT(*) FROM road_tiles", null).use {
          it.moveToFirst() && it.getInt(0) >= 1000
        }
      if (valid) return candidate
      candidate.close()
    } catch (e: Exception) {
      Log.w("PT2VHF.Roads", "Cached database invalid", e)
    }
    install()
    val fresh = open()
    if (fresh.version != 2) {
      fresh.close()
      throw java.io.IOException("Unexpected database schema version")
    }
    return fresh
  }

  fun resolve(loc: Location): Result {
    val d = db ?: return Result(null, "BASE OFFLINE INDISPONÍVEL (" + (initError ?: "ERRO") + ")")
    if (!loc.hasBearing()) return Result(null, "AGUARDANDO DIREÇÃO")
    val lat = loc.latitude; val lon = loc.longitude
    val tileLat = floor(lat * 1000.0).toInt()
    val tileLon = floor(lon * 1000.0).toInt()
    val found = mutableListOf<Pair<Double, Double>>()
    try {
      d.rawQuery(
        "SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.fwd,r.bwd " +
        "FROM road_tiles t JOIN roads r ON r.id=t.road_id " +
        "WHERE t.tile_lat BETWEEN ? AND ? AND t.tile_lon BETWEEN ? AND ? " +
        "GROUP BY r.id LIMIT 600",
        arrayOf((tileLat-1).toString(),(tileLat+1).toString(),
          (tileLon-1).toString(),(tileLon+1).toString())
      ).use { c ->
        while (c.moveToNext()) {
          val x1 = (c.getDouble(1)-lon)*111320.0*cos(Math.toRadians(lat))
          val y1 = (c.getDouble(0)-lat)*111320.0
          val x2 = (c.getDouble(3)-lon)*111320.0*cos(Math.toRadians(lat))
          val y2 = (c.getDouble(2)-lat)*111320.0
          val dx = x2-x1; val dy = y2-y1
          val length2 = dx*dx + dy*dy
          if (length2 < 1.0) continue
          val fraction = (-(x1*dx+y1*dy)/length2).coerceIn(0.0,1.0)
          val distance = hypot(x1+fraction*dx, y1+fraction*dy)
          if (distance > 27.0) continue
          val direction = (Math.toDegrees(atan2(dx,dy))+360.0)%360.0
          val forward = angle(loc.bearing.toDouble(),direction)
          val backward = angle(loc.bearing.toDouble(),(direction+180.0)%360.0)
          val heading = min(forward,backward)
          if (heading > 45.0) continue
          val selected = if (forward <= backward) 4 else 5
          if (c.isNull(selected)) continue
          found.add((distance+heading*0.3) to c.getDouble(selected))
        }
      }
    } catch (e: Exception) {
      Log.e("PT2VHF.Roads", "Offline query failed", e)
      return Result(null, "ERRO CONSULTA OFFLINE (" + e.javaClass.simpleName + ")")
    }
    found.sortBy { it.first }
    val winner = found.firstOrNull() ?: return Result(null, "LIMITE NÃO CADASTRADO")
    if (found.drop(1).any { it.first-winner.first < 7 &&
      abs(it.second-winner.second) > 0.1 }) return Result(null, "VIA AMBÍGUA")
    return Result(winner.second, "OSM OFFLINE V2")
  }
  private fun angle(a: Double,b: Double) = abs((a-b+540.0)%360.0-180.0)
}

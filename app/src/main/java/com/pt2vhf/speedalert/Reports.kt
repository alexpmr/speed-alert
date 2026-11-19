package com.pt2vhf.speedalert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Driver error reports ("Marcar erro"). A mark is saved instantly and offline with what the
 * app was showing; the driver completes it later, while stopped, and may publish it as a
 * public OpenStreetMap note so mappers can fix the road. Nothing is sent without
 * an explicit confirmation.
 */
object Reports {
  data class Report(
    val id:String,val created:Long,
    val lat:Double,val lon:Double,val accuracy:Float,val bearing:Float,
    val speed:Double,val shownLimit:Double,val road:String,val wayId:Long,
    val appVersion:String,val dataVersion:String,
    val type:String="",val correct:String="",val comment:String="",
    val noteId:Long=0L
  )
  val types=linkedMapOf(
    "LIMITE" to "Limite de velocidade errado",
    "SEM_LIMITE" to "Via sem limite cadastrado",
    "NOME" to "Nome da via errado",
    "OUTRO" to "Outro problema")

  private const val NOTES_URL="https://api.openstreetmap.org/api/0.6/notes.json"
  private fun file(c:Context)=File(c.applicationContext.filesDir,"reports.json")

  @Synchronized fun all(c:Context):List<Report> = try {
    val arr=JSONArray(file(c).takeIf{it.exists()}?.readText()?:"[]")
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o ->
      Report(o.getString("id"),o.getLong("created"),o.getDouble("lat"),o.getDouble("lon"),
        o.optDouble("accuracy",-1.0).toFloat(),o.optDouble("bearing",-1.0).toFloat(),
        o.optDouble("speed",-1.0),o.optDouble("shownLimit",-1.0),o.optString("road"),
        o.optLong("wayId",0L),o.optString("appVersion"),
        o.optString("dataVersion"),o.optString("type"),o.optString("correct"),
        o.optString("comment"),o.optLong("noteId",0L)) } }
  } catch(_:Exception) { emptyList() }

  @Synchronized private fun save(c:Context,list:List<Report>) {
    val arr=JSONArray()
    list.forEach { r -> arr.put(JSONObject().put("id",r.id).put("created",r.created)
      .put("lat",r.lat).put("lon",r.lon).put("accuracy",r.accuracy.toDouble())
      .put("bearing",r.bearing.toDouble()).put("speed",r.speed).put("shownLimit",r.shownLimit)
      .put("road",r.road).put("wayId",r.wayId)
      .put("appVersion",r.appVersion).put("dataVersion",r.dataVersion).put("type",r.type)
      .put("correct",r.correct).put("comment",r.comment).put("noteId",r.noteId)) }
    val f=file(c); val tmp=File(f.parentFile,f.name+".tmp")
    tmp.writeText(arr.toString()); if(!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
  }

  /** Snapshot of what the app is showing now; null without a usable GPS position. */
  fun mark(c:Context):Report? {
    val s=MonitorService.latest?:return null
    if(s.lat.isNaN() || s.lon.isNaN())return null
    val version=try{c.packageManager.getPackageInfo(c.packageName,0).versionName?:"?"}catch(_:Exception){"?"}
    val r=Report(UUID.randomUUID().toString(),System.currentTimeMillis(),s.lat,s.lon,s.gpsAccuracy,
      s.bearing,s.speed,s.limit,s.roadName,s.wayId,version,Roads.dataVersion(c))
    save(c,all(c)+r)
    return r
  }
  fun update(c:Context,r:Report) = save(c,all(c).map { if(it.id==r.id) r else it })
  fun delete(c:Context,id:String) = save(c,all(c).filter { it.id!=id })
  fun pending(c:Context)=all(c).count { it.noteId==0L }

  private fun date(ms:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US)
    .apply{timeZone=TimeZone.getTimeZone("America/Sao_Paulo")}.format(Date(ms))
  private fun n(v:Double,unit:String)=if(v>=0)"${Math.round(v)} $unit" else "—"

  /** Public note text: facts first, so a mapper can act without contacting the reporter. */
  fun noteText(r:Report):String = buildString {
    appendLine("${types[r.type]?:"Problema"} — relato de motorista (PT2VHF Speed Alert)")
    appendLine()
    appendLine("Via: ${r.road.ifBlank{"não identificada"}}"+
      (if(r.wayId>0)" — https://www.openstreetmap.org/way/${r.wayId}" else ""))
    appendLine("O app mostrava: limite ${n(r.shownLimit,"km/h")}")
    if(r.correct.isNotBlank())appendLine("Segundo o motorista, no local: ${r.correct}")
    if(r.comment.isNotBlank())appendLine("Comentário: ${r.comment.take(500)}")
    appendLine("Sentido de deslocamento: "+(if(r.bearing>=0)"${Math.round(r.bearing.toDouble())}°" else "—")+
      "; precisão do GPS: ${n(r.accuracy.toDouble(),"m")}; data: ${date(r.created)}")
    appendLine()
    appendLine("Fonte do app: dados OSM (base ${r.dataVersion}), app v${r.appVersion}. "+
      "Confirme no local ou em imagens com licença compatível antes de editar.")
    append("#PT2VHFSpeedAlert")
  }

  /** Publishes [r] as an anonymous OpenStreetMap note. Blocking; call off the main thread. */
  fun publish(c:Context,r:Report):Long {
    val body="lat="+"%.6f".format(Locale.US,r.lat)+"&lon="+"%.6f".format(Locale.US,r.lon)+
      "&text="+URLEncoder.encode(noteText(r),"UTF-8")
    val conn=(URL(NOTES_URL).openConnection() as HttpURLConnection).apply {
      requestMethod="POST"; doOutput=true; connectTimeout=10000; readTimeout=20000
      setRequestProperty("User-Agent","PT2VHF-Speed-Alert/${r.appVersion} (Android; +https://www.pt2vhf.com)")
      setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8")
    }
    try {
      conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      val code=conn.responseCode
      if(code!=200)throw IOException("OpenStreetMap respondeu HTTP $code")
      val json=JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
      val id=json.getJSONObject("properties").getLong("id")
      update(c,r.copy(noteId=id))
      return id
    } finally { conn.disconnect() }
  }
}

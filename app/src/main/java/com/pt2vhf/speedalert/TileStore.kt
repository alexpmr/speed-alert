package com.pt2vhf.speedalert

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.math.floor

/**
 * Offline map data in 1x1 degree tiles, downloaded on demand around the driver and evicted
 * when unused. The manifest (data/tiles.json in the repository) lists every tile with its
 * SHA-256; tiles are GitHub release assets. Only tile names (≈110 km squares) are requested:
 * no exact position, identifier or route is sent.
 */
object TileStore {
  const val MANIFEST_URL="https://raw.githubusercontent.com/alexpmr/speed-alert/main/data/tiles.json"
  private const val TAG="PT2VHF.Tiles"
  private const val MANIFEST_MAX_AGE_MS=12L*60*60*1000
  private const val UNUSED_EVICT_MS=30L*24*60*60*1000
  @Volatile var busy=false; private set
  @Volatile var lastError:String?=null; private set

  private fun dir(c:Context)=File(c.applicationContext.filesDir,"tiles").apply { mkdirs() }
  private fun indexFile(c:Context)=File(dir(c),"index.json")
  private fun manifestFile(c:Context)=File(dir(c),"tiles.json")

  fun tileId(lat:Double,lon:Double):String {
    val la=floor(lat).toInt(); val lo=floor(lon).toInt()
    return (if(la<0)"s" else "n")+"%02d".format(Math.abs(la))+(if(lo<0)"w" else "e")+"%03d".format(Math.abs(lo))
  }
  fun around(lat:Double,lon:Double):Set<String> =
    (-1..1).flatMap { a -> (-1..1).map { b -> tileId(lat+a,lon+b) } }.toSet()

  /** True when the prebuilt tile set covers this 1° tile (from the cached manifest). */
  fun listed(c:Context,id:String):Boolean =
    manifest(c,refresh=false)?.optJSONObject("tiles")?.has(id)==true

  /** Tile databases present on the device, for Roads. */
  fun files(c:Context):List<Pair<String,File>> =
    dir(c).listFiles { f -> f.name.endsWith(".sqlite") }?.map { it.name.removeSuffix(".sqlite") to it } ?: emptyList()

  // --- local index: id -> {sha, used, pinned} -------------------------------------------------
  private fun readIndex(c:Context):JSONObject = try { JSONObject(indexFile(c).readText()) } catch(_:Exception) { JSONObject() }
  private fun writeIndex(c:Context,o:JSONObject) {
    val f=indexFile(c); val tmp=File(f.parentFile,"index.tmp"); tmp.writeText(o.toString())
    if(!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
  }

  // --- manifest -------------------------------------------------------------------------------
  private fun connect(url:String)=(URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout=10000; readTimeout=30000; instanceFollowRedirects=true
    setRequestProperty("User-Agent","PT2VHF-Speed-Alert (Android)")
  }
  @Synchronized fun manifest(c:Context,refresh:Boolean):JSONObject? {
    val f=manifestFile(c)
    val fresh=f.exists() && System.currentTimeMillis()-f.lastModified()<MANIFEST_MAX_AGE_MS
    if(refresh || !fresh) try {
      val conn=connect(MANIFEST_URL)
      try {
        if(conn.responseCode!=200)throw IOException("manifesto HTTP ${conn.responseCode}")
        val text=conn.inputStream.bufferedReader().use { it.readText() }
        val o=JSONObject(text)
        require(o.optInt("schema")==1 && o.has("tiles"))
        val tmp=File(f.parentFile,"tiles.tmp"); tmp.writeText(text)
        if(!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        return o
      } finally { conn.disconnect() }
    } catch(e:Exception) { Log.w(TAG,"manifest",e); lastError="sem Internet" }
    return try { JSONObject(f.readText()) } catch(_:Exception) { null }
  }

  fun networkAllowed(c:Context):Boolean {
    val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps=cm.getNetworkCapabilities(cm.activeNetwork)?:return false
    if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return false
    return !AppConfig.tilesWifiOnly(AppConfig.prefs(c)) ||
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
  }

  private fun download(c:Context,base:String,id:String,sha:String) {
    val target=File(dir(c),"$id.sqlite"); val tmp=File(dir(c),"$id.part")
    val conn=connect(base+id+".sqlite.gz")
    try {
      if(conn.responseCode!=200)throw IOException("bloco $id HTTP ${conn.responseCode}")
      val digest=MessageDigest.getInstance("SHA-256")
      GZIPInputStream(conn.inputStream).use { inp -> tmp.outputStream().use { out ->
        val buf=ByteArray(65536)
        while(true) { val n=inp.read(buf); if(n<0)break; digest.update(buf,0,n); out.write(buf,0,n) }
        out.fd.sync()
      } }
      val actual=digest.digest().joinToString("") { "%02x".format(it) }
      if(actual!=sha)throw IOException("SHA-256 do bloco $id não confere")
      if(target.exists() && !target.delete())throw IOException("não foi possível substituir $id")
      if(!tmp.renameTo(target))throw IOException("não foi possível instalar $id")
    } finally { conn.disconnect(); tmp.delete() }
  }

  /**
   * Make sure [ids] are present and current. Blocking: call from a worker thread.
   * Returns true when any tile file changed (Roads must be reloaded).
   */
  @Synchronized fun ensure(c:Context,ids:Set<String>,pin:Boolean=false,progress:((Int,Int)->Unit)?=null):Boolean {
    busy=true
    try {
      val index=readIndex(c)
      val now=System.currentTimeMillis()
      ids.forEach { id -> index.optJSONObject(id)?.put("used",now) }
      var changed=false
      if(networkAllowed(c)) {
        val m=manifest(c,refresh=false)
        val tiles=m?.optJSONObject("tiles")
        val base=m?.optString("base_url").orEmpty()
        if(tiles!=null && base.startsWith("https://")) {
          val todo=ids.filter { id -> tiles.has(id) &&
            (index.optJSONObject(id)?.optString("sha")!=tiles.getJSONObject(id).getString("sha256") ||
             !File(dir(c),"$id.sqlite").exists()) }
          todo.forEachIndexed { i,id ->
            progress?.invoke(i,todo.size)
            val sha=tiles.getJSONObject(id).getString("sha256")
            try {
              download(c,base,id,sha)
              index.put(id,JSONObject().put("sha",sha).put("used",now)
                .put("pinned",pin || index.optJSONObject(id)?.optBoolean("pinned")==true))
              changed=true
            } catch(e:Exception) { Log.w(TAG,"download $id",e); lastError=e.message }
          }
          progress?.invoke(todo.size,todo.size)
          if(todo.isEmpty() || changed)lastError=null
        }
      }
      if(pin) ids.forEach { id -> index.optJSONObject(id)?.put("pinned",true) }
      writeIndex(c,index)
      return changed
    } finally { busy=false }
  }

  /** Tiles of a whole region (for trips), from the cached or fresh manifest. */
  fun regionTiles(c:Context,region:String):Set<String> {
    val tiles=manifest(c,refresh=false)?.optJSONObject("tiles")?:return emptySet()
    return tiles.keys().asSequence().filter { id ->
      val r=tiles.getJSONObject(id).optJSONArray("regions")
      r!=null && (0 until r.length()).any { r.getString(it)==region }
    }.toSet()
  }
  fun regionSizeMb(c:Context,region:String):Int {
    val tiles=try { JSONObject(manifestFile(c).readText()).getJSONObject("tiles") } catch(_:Exception) { return -1 }
    var total=0L
    tiles.keys().forEach { id -> val t=tiles.getJSONObject(id); val r=t.optJSONArray("regions")
      if(r!=null && (0 until r.length()).any { r.getString(it)==region }) total+=t.optLong("gz_bytes") }
    return ((total+524287)/1048576).toInt()
  }

  /** Remove tiles unused for 30 days, then least-recently-used ones above the space limit. */
  @Synchronized fun evict(c:Context,keep:Set<String>):Boolean {
    val index=readIndex(c)
    val limit=AppConfig.tileLimitMb(AppConfig.prefs(c))*1048576L
    val now=System.currentTimeMillis()
    val present=files(c).map { (id,f) -> Triple(id,f,index.optJSONObject(id)) }
    var removed=false
    fun drop(id:String,f:File) { f.delete(); index.remove(id); removed=true }
    present.filter { (id,_,e) -> id !in keep && e?.optBoolean("pinned")!=true &&
      now-(e?.optLong("used")?:0L)>UNUSED_EVICT_MS }.forEach { (id,f,_) -> drop(id,f) }
    var total=files(c).sumOf { it.second.length() }
    files(c).map { (id,f) -> Triple(id,f,index.optJSONObject(id)) }
      .filter { (id,_,e) -> id !in keep && e?.optBoolean("pinned")!=true }
      .sortedBy { it.third?.optLong("used")?:0L }
      .forEach { (id,f,_) -> if(total>limit) { total-=f.length(); drop(id,f) } }
    writeIndex(c,index)
    return removed
  }

  @Synchronized fun clear(c:Context) {
    files(c).forEach { it.second.delete() }; indexFile(c).delete()
  }
  fun usage(c:Context):Pair<Int,Long> = files(c).let { l -> l.size to l.sumOf { it.second.length() } }
  fun dataDate(c:Context):String = try {
    JSONObject(manifestFile(c).readText()).optString("data_built").take(10).ifBlank { "?" }
  } catch(_:Exception) { "?" }
}

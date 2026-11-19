package com.pt2vhf.speedalert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import java.util.Locale

class MonitorService:Service() {
  data class State(
    val speed:Double,val limit:Double,val status:String,val source:String,val alarm:Boolean,
    val roadName:String="Via não identificada",val muted:Boolean=false,
    val gpsAccuracy:Float=-1f,
    val lat:Double=Double.NaN,val lon:Double=Double.NaN,val bearing:Float=-1f,
    val wayId:Long=0L
  )
  companion object {
    const val START="com.pt2vhf.speedalert.START"
    const val STOP="com.pt2vhf.speedalert.STOP"
    const val MUTE="com.pt2vhf.speedalert.MUTE"
    const val UPDATE="com.pt2vhf.speedalert.UPDATE"
    /** Reopen offline databases after a region was downloaded or removed. */
    const val RELOAD="com.pt2vhf.speedalert.RELOAD"
    @Volatile var latest:State?=null
  }
  private val handler=Handler(Looper.getMainLooper())
  private val prefs by lazy { AppConfig.prefs(this) }
  private lateinit var locationManager:LocationManager
  /** Loaded off the main thread: copying a bundled regional database can take seconds. */
  @Volatile private var roads:Roads?=null
  private var lastReload=0L
  private var tilesFor:String?=null
  private var tilesAt=0L
  @Volatile private var tilesRunning=false
  /** Download/refresh the 3x3 tiles around the driver off the main thread; reload when changed. */
  private fun maintainTiles(lat:Double,lon:Double) {
    val here=TileStore.tileId(lat,lon)
    val now=SystemClock.elapsedRealtime()
    if(tilesRunning || (here==tilesFor && now-tilesAt<10*60_000L))return
    tilesFor=here; tilesAt=now; tilesRunning=true
    Thread {
      try {
        val keep=TileStore.around(lat,lon)
        val changed=TileStore.ensure(this,keep)
        val evicted=TileStore.evict(this,keep)
        if(changed || evicted)handler.post { loadRoads() }
      } catch(e:Exception) { android.util.Log.w("PT2VHF.Tiles","maintain",e) }
      finally { tilesRunning=false }
    }.apply { name="pt2vhf-tiles";start() }
  }
  private fun loadRoads() {
    lastReload=SystemClock.elapsedRealtime()
    Thread {
      val fresh=Roads(this)
      handler.post {   // resolve() runs on the main looper: swap and close there
        val old=roads
        roads=fresh
        old?.close()
      }
    }.apply { name="pt2vhf-roads";start() }
  }
  private lateinit var audio:AlarmAudio
  private var running=false
  private var lastFix=0L
  // GPS may arrive at 10 Hz; the road lookup (SQLite) runs at most once a second.
  private var road:Roads.Result?=null
  private var roadAt=0L
  private var notifiedAt=0L
  private val checkStale=object:Runnable {
    override fun run() {
      if(running && SystemClock.elapsedRealtime()-lastFix>5000L)
        publish(State(-1.0,-1.0,"GPS INDISPONÍVEL","--",false))
      if(running) handler.postDelayed(this,1500L)
    }
  }
  private val listener=object:LocationListener {
    override fun onLocationChanged(loc:Location) { onFix(loc) }
    override fun onProviderDisabled(provider:String) {
      publish(State(-1.0,-1.0,"GPS DESLIGADO","--",false))
    }
  }
  override fun onCreate() {
    super.onCreate()
    locationManager=getSystemService(LOCATION_SERVICE) as LocationManager
    loadRoads()
    audio=AlarmAudio(this)
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
      NotificationChannel("speed","PT2VHF velocidade",NotificationManager.IMPORTANCE_LOW))
  }
  override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
    if(intent?.action==STOP) { shutdown(); return START_NOT_STICKY }
    if(intent?.action==RELOAD) {
      loadRoads()
      return START_NOT_STICKY
    }
    if(intent?.action==MUTE) {
      latest?.let { publish(it.copy(muted=AppConfig.muted(prefs))) }
      return START_NOT_STICKY
    }
    if(intent?.action!=START || running) return START_NOT_STICKY
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
      stopSelf();return START_NOT_STICKY
    }
    if(Build.VERSION.SDK_INT>=29)
      startForeground(101,notification("Aguardando GPS"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    else startForeground(101,notification("Aguardando GPS"))
    running=true;lastFix=SystemClock.elapsedRealtime()
    // Silencing lasts until the app is closed, so nobody drives on unknowingly muted.
    prefs.edit().putBoolean("muted",false).apply()
    publish(State(-1.0,-1.0,"AGUARDANDO GPS","--",false))
    // 100 ms = up to 10 Hz; phones whose GNSS chip only does 1 Hz keep delivering 1 Hz.
    try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,100L,0f,listener,Looper.getMainLooper()) }
    catch(_:Exception) { publish(State(-1.0,-1.0,"FALHA GPS","--",false)) }
    handler.postDelayed(checkStale,1500L)
    return START_NOT_STICKY
  }
  private fun onFix(loc:Location) {
    val age=(SystemClock.elapsedRealtimeNanos()-loc.elapsedRealtimeNanos)/1000000L
    if(age<0 || age>4500 || !loc.hasSpeed() || !loc.hasAccuracy() || loc.accuracy>50f ||
      (Build.VERSION.SDK_INT>=26 && loc.hasSpeedAccuracy() && loc.speedAccuracyMetersPerSecond>4f)) {
      publish(State(-1.0,-1.0,"GPS IMPRECISO","--",false,gpsAccuracy=if(loc.hasAccuracy())loc.accuracy else -1f,
        lat=loc.latitude,lon=loc.longitude,bearing=if(loc.hasBearing())loc.bearing else -1f));return
    }
    lastFix=SystemClock.elapsedRealtime()
    val speed=loc.speed.toDouble()*3.6
    val now=SystemClock.elapsedRealtime()
    val selected=road?.takeIf { now-roadAt<1000L } ?: run {
      roadAt=now
      (if(!AppConfig.offline(prefs)) Roads.Result(null,"BASE OFFLINE DESATIVADA")
        else roads?.resolve(loc) ?: Roads.Result(null,"CARREGANDO BASE OFFLINE"))
        .let { r -> if(r.source.startsWith("REGIÃO SEM BASE") || r.source.startsWith("BASE OFFLINE INDISPONÍVEL"))
          r.copy(source=if(TileStore.busy || tilesRunning)"BAIXANDO BASE DESTA REGIÃO…"
            else if(!TileStore.networkAllowed(this))"SEM BASE AQUI: conecte à Internet para baixar"
            else "REGIÃO SEM COBERTURA (Centro-Oeste, Sudeste e Sul)") else r }
        .also { road=it; if(AppConfig.offline(prefs))maintainTiles(loc.latitude,loc.longitude) }
    }
    val max=if(AppConfig.manual(prefs)) AppConfig.manualLimit(prefs).toDouble() else selected.limit
    val excess=max!=null && speed>max+AppConfig.tolerance(prefs)
    val message=when {
      max==null -> if(AppConfig.warnUnknown(prefs)) "LIMITE DESCONHECIDO" else "SEM LIMITE IDENTIFICADO"
      excess -> "VELOCIDADE EXCEDIDA"
      else -> "DENTRO DO LIMITE"
    }
    publish(State(speed,max?:-1.0,message,
      if(AppConfig.manual(prefs)) "MANUAL • "+selected.source else selected.source,
      excess,selected.roadName,AppConfig.muted(prefs),loc.accuracy,
      loc.latitude,loc.longitude,if(loc.hasBearing())loc.bearing else -1f,selected.wayId))
  }
  private fun publish(s:State) {
    latest=s
    audio.setAlert(s.alarm && !s.muted,AppConfig.mode(prefs),
      AppConfig.volume(prefs),AppConfig.beepMs(prefs),AppConfig.pauseMs(prefs))
    sendBroadcast(Intent(UPDATE).setPackage(packageName).apply {
      putExtra("speed",s.speed);putExtra("limit",s.limit)
      putExtra("status",s.status);putExtra("source",s.source);putExtra("alarm",s.alarm)
      putExtra("road",s.roadName);putExtra("muted",s.muted)
      putExtra("accuracy",s.gpsAccuracy)
      putExtra("lat",s.lat);putExtra("lon",s.lon);putExtra("bearing",s.bearing)
    })
    // Android drops notification updates faster than ~5/s; once a second is enough there.
    val now=SystemClock.elapsedRealtime()
    if(now-notifiedAt<1000L)return
    notifiedAt=now
    val speed=if(s.speed>=0) String.format(Locale.US,"%.1f km/h",s.speed) else "GPS indisponível"
    try {
      (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
        101,notification(speed+" — "+s.status))
    } catch(_:SecurityException) { /* permission denied: location service still runs */ }
  }
  /** Exit: stop GPS, alarm and the foreground notification. */
  private fun shutdown() {
    running=false;handler.removeCallbacks(checkStale);audio.stop()
    try { locationManager.removeUpdates(listener) } catch(_:Exception){}
    prefs.edit().putBoolean("muted",false).apply()
    stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    latest=null
  }
  /** Swiping the app away from Recents closes it for real. */
  override fun onTaskRemoved(rootIntent:Intent?) { shutdown(); super.onTaskRemoved(rootIntent) }
  private fun notification(s:String):Notification {
    val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val stop=PendingIntent.getService(this,2,Intent(this,MonitorService::class.java).setAction(STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this,"speed").setSmallIcon(R.drawable.ic_speed_alert)
      .setContentTitle("PT2VHF Speed Alert").setContentText(s).setContentIntent(open)
      .setOngoing(true).addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
  }
  override fun onDestroy() {
    running=false;handler.removeCallbacks(checkStale);audio.stop()
    try { locationManager.removeUpdates(listener) } catch(_:Exception){}
    audio.close();latest=null
    roads?.close();roads=null
    super.onDestroy()
  }
  override fun onBind(intent:Intent?):IBinder?=null
}

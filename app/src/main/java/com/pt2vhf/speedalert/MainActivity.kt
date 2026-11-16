package com.pt2vhf.speedalert

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.roundToInt

/** Driver-first, high-contrast, responsive UI: no map and no start button. */
class MainActivity:Activity() {
  private val navy=Color.rgb(8,18,30)
  private val panel=Color.rgb(19,35,52)
  private val pale=Color.rgb(232,242,250)
  private val green=Color.rgb(0,119,65)
  private val yellow=Color.rgb(255,204,28)
  private val red=Color.rgb(220,29,43)
  private val darkRed=Color.rgb(105,10,22)
  private val gray=Color.rgb(49,67,84)
  private val ui=Handler(Looper.getMainLooper())
  private val prefs by lazy { AppConfig.prefs(this) }
  private lateinit var speedCard:LinearLayout
  private lateinit var speedView:TextView
  private lateinit var unitView:TextView
  private lateinit var limitPlate:TextView
  private lateinit var road:TextView
  private lateinit var status:TextView
  private lateinit var radar:TextView
  private lateinit var source:TextView
  private lateinit var mute:Button
  private var currentSpeed=-1.0
  private var currentLimit=-1.0
  private var overLimit=false
  private var blink=false
  private val blinkTask=object:Runnable {
    override fun run() {
      blink=!blink
      setSpeedBackground()
      if(overLimit)ui.postDelayed(this,450L)
    }
  }
  private val receiver=object:BroadcastReceiver() {
    override fun onReceive(context:Context?,intent:Intent?) {
      if(intent?.action!=MonitorService.UPDATE)return
      showState(
        intent.getDoubleExtra("speed",-1.0),
        intent.getDoubleExtra("limit",-1.0),
        intent.getStringExtra("status")?:"GPS INDISPONÍVEL",
        intent.getStringExtra("source")?:"--",
        intent.getStringExtra("road")?:"Via não identificada",
        intent.getDoubleExtra("radar",-1.0),
        intent.getBooleanExtra("radarActive",false),
        intent.getBooleanExtra("muted",false)
      )
    }
  }
  private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
  private fun bg(color:Int,radius:Int=18,stroke:Int?=null,border:Int=0):GradientDrawable {
    val d=GradientDrawable()
    d.setColor(color)
    d.cornerRadius=dp(radius).toFloat()
    if(stroke!=null)d.setStroke(dp(border),stroke)
    return d
  }
  private fun label(value:String,size:Float,color:Int=pale,bold:Boolean=false):TextView =
    TextView(this).apply {
      text=value;textSize=size;setTextColor(color)
      gravity=Gravity.CENTER
      if(bold)typeface=Typeface.DEFAULT_BOLD
      includeFontPadding=true
    }
  private fun add(root:LinearLayout,view:View,margin:Int=8) {
    val p=LinearLayout.LayoutParams(-1,-2)
    p.setMargins(0,dp(margin),0,dp(margin))
    root.addView(view,p)
  }
  private fun section():LinearLayout = LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL
    gravity=Gravity.CENTER
  }
  private fun standardButton(s:String):Button = Button(this).apply {
    text=s
    textSize=15f
    setTextColor(Color.WHITE)
    isAllCaps=false
    background=bg(Color.rgb(39,64,84),14)
    minHeight=dp(52)
  }
  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
    window.statusBarColor=navy
    window.navigationBarColor=navy
    val scroll=ScrollView(this).apply { setBackgroundColor(navy);fillViewport=true }
    val root=section().apply {setPadding(dp(18),dp(14),dp(18),dp(24))}
    scroll.addView(root)
    val header=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    val appName=label("PT2VHF  SPEED ALERT",20f,Color.WHITE,true).apply {
      gravity=Gravity.START or Gravity.CENTER_VERTICAL
    }
    header.addView(appName,LinearLayout.LayoutParams(0,dp(46),1f))
    val version=try {packageManager.getPackageInfo(packageName,0).versionName ?: "-"}catch(_:Exception){"-"}
    header.addView(label("v"+version,12f,Color.rgb(151,184,207)),LinearLayout.LayoutParams(-2,dp(46)))
    add(root,header,0)
    add(root,label("●  MONITORAMENTO GPS ATIVO",13f,Color.rgb(77,228,154),true),0)

    val dashboard=LinearLayout(this).apply {
      orientation=if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)
        LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
      gravity=Gravity.CENTER
    }
    speedCard=section().apply {
      background=bg(gray,22)
      setPadding(dp(22),dp(14),dp(22),dp(18))
    }
    speedCard.addView(label("VELOCIDADE ATUAL",14f,Color.WHITE,true))
    speedView=label("—",90f,Color.WHITE,true)
    speedCard.addView(speedView)
    unitView=label("km/h",18f,Color.WHITE,true)
    speedCard.addView(unitView)

    val limitBox=section().apply {
      setPadding(dp(14),dp(12),dp(14),dp(12))
    }
    limitBox.addView(label("LIMITE DA VIA",13f,Color.rgb(188,208,222),true))
    limitPlate=label("—",60f,Color.BLACK,true).apply {
      background=GradientDrawable().apply {
        shape=GradientDrawable.OVAL
        setColor(Color.WHITE)
        setStroke(dp(12),Color.rgb(235,39,53))
      }
      gravity=Gravity.CENTER
    }
    val plateParams=LinearLayout.LayoutParams(dp(152),dp(152))
    plateParams.setMargins(0,dp(8),0,dp(10))
    limitBox.addView(limitPlate,plateParams)
    road=label("Via não identificada",16f,Color.WHITE,true).apply {
      maxLines=2
      setPadding(dp(4),dp(8),dp(4),0)
    }
    limitBox.addView(road)

    if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) {
      dashboard.addView(speedCard,LinearLayout.LayoutParams(0,dp(237),1.2f).apply {
        setMargins(0,0,dp(8),0)
      })
      dashboard.addView(limitBox,LinearLayout.LayoutParams(0,dp(237),1f))
    } else {
      dashboard.addView(speedCard,LinearLayout.LayoutParams(-1,-2))
      dashboard.addView(limitBox,LinearLayout.LayoutParams(-1,-2))
    }
    add(root,dashboard,12)
    val feedback=section().apply {
      background=bg(panel,16)
      setPadding(dp(12),dp(12),dp(12),dp(12))
    }
    status=label("AGUARDANDO GPS",17f,Color.WHITE,true)
    radar=label("Radar: sem informação confiável",13f,Color.rgb(186,207,222))
    source=label("Base offline • dados OSM",12f,Color.rgb(146,172,194))
    feedback.addView(status)
    feedback.addView(radar)
    feedback.addView(source)
    add(root,feedback,8)

    mute=standardButton("SILENCIAR ALARMES")
    mute.setOnClickListener {
      val next=!AppConfig.muted(prefs)
      prefs.edit().putBoolean("muted",next).apply()
      setMuteLabel(next)
      startService(Intent(this,MonitorService::class.java).setAction(MonitorService.MUTE))
    }
    add(root,mute,8)
    val actions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
    val settings=standardButton("CONFIGURAÇÕES").apply {
      setOnClickListener {startActivity(Intent(this@MainActivity,SettingsActivity::class.java))}
    }
    val update=standardButton("VERIFICAR ATUALIZAÇÃO").apply {
      setOnClickListener { UpdateManager.check(this@MainActivity,false) }
    }
    actions.addView(settings,LinearLayout.LayoutParams(0,dp(52),1f).apply {
      setMargins(0,0,dp(5),0)
    })
    actions.addView(update,LinearLayout.LayoutParams(0,dp(52),1f).apply {
      setMargins(dp(5),0,0,0)
    })
    add(root,actions,2)
    add(root,label("© OpenStreetMap contributors • Sem Internet durante o trajeto • Respeite a sinalização.",11f,Color.rgb(145,166,184)),16)
    setContentView(scroll)
    setMuteLabel(AppConfig.muted(prefs))
    // The launch disclaimer must be accepted before GPS and OTA checks.
    StartupDisclaimer.show(this) {
      startMonitor()
      if(AppConfig.autoUpdates(prefs))UpdateManager.check(this,true)
    }
  }
  private fun setMuteLabel(muted:Boolean) {
    if(!::mute.isInitialized)return
    mute.text=if(muted)"REATIVAR SOM DOS ALARMES" else "SILENCIAR ALARMES"
    mute.background=bg(if(muted)Color.rgb(110,67,26) else Color.rgb(39,64,84),14)
  }
  private fun setSpeedBackground() {
    if(!::speedCard.isInitialized)return
    val bgColor=when {
      currentSpeed<0 || currentLimit<=0 -> gray
      overLimit -> if(blink)red else darkRed
      currentSpeed>=currentLimit*0.95 -> yellow
      else -> green
    }
    speedCard.background=bg(bgColor,22)
    val fg=if(bgColor==yellow)Color.BLACK else Color.WHITE
    speedView.setTextColor(fg)
    unitView.setTextColor(fg)
  }
  private fun showState(v:Double,max:Double,message:String,origin:String,name:String,
                        radarMeters:Double,radarAlert:Boolean,muted:Boolean) {
    currentSpeed=v;currentLimit=max
    speedView.text=if(v>=0)v.roundToInt().toString() else "—"
    limitPlate.text=if(max>0)max.roundToInt().toString() else "—"
    road.text=name.ifBlank{"Via não identificada"}
    status.text=message
    source.text="Fonte: "+origin
    radar.text=when {
      radarMeters<0 -> "Radar: nenhum confirmado à frente"
      radarAlert -> "ATENÇÃO: RADAR A "+radarMeters.roundToInt()+" m"
      else -> "Radar cadastrado a "+radarMeters.roundToInt()+" m"
    }
    radar.setTextColor(if(radarAlert)Color.rgb(255,108,108) else Color.rgb(186,207,222))
    overLimit=max>0 && v>max
    ui.removeCallbacks(blinkTask)
    blink=false
    setSpeedBackground()
    if(overLimit)ui.postDelayed(blinkTask,450L)
    setMuteLabel(muted)
  }
  private fun startMonitor() {
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
      val perms=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
      if(Build.VERSION.SDK_INT>=33)perms.add(Manifest.permission.POST_NOTIFICATIONS)
      requestPermissions(perms.toTypedArray(),440)
      return
    }
    try {
      startForegroundService(Intent(this,MonitorService::class.java).setAction(MonitorService.START))
      status.text="AGUARDANDO GPS"
    } catch(e:Exception) {
      status.text="NÃO FOI POSSÍVEL INICIAR GPS"
      Toast.makeText(this,e.localizedMessage ?: "Falha no serviço",Toast.LENGTH_LONG).show()
    }
  }
  override fun onRequestPermissionsResult(code:Int,perms:Array<out String>,results:IntArray) {
    super.onRequestPermissionsResult(code,perms,results)
    if(code!=440)return
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
      startMonitor()
    else status.text="AUTORIZE LOCALIZAÇÃO PRECISA"
  }
  override fun onStart() {
    super.onStart()
    if(Build.VERSION.SDK_INT>=33)
      registerReceiver(receiver,IntentFilter(MonitorService.UPDATE),Context.RECEIVER_NOT_EXPORTED)
    else {
      @Suppress("DEPRECATION")
      registerReceiver(receiver,IntentFilter(MonitorService.UPDATE))
    }
    MonitorService.latest?.let {
      showState(it.speed,it.limit,it.status,it.source,it.roadName,it.radarMeters?:-1.0,
        it.radarActive,it.muted)
    }
  }
  override fun onResume() {
    super.onResume()
    if(AppConfig.screen(prefs))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setMuteLabel(AppConfig.muted(prefs))
  }
  override fun onStop() {
    ui.removeCallbacks(blinkTask)
    unregisterReceiver(receiver)
    super.onStop()
  }
}
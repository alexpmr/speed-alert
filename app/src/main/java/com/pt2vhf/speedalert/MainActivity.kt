package com.pt2vhf.speedalert

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

/** Driver-facing dashboard matching the user-approved three-state visual reference. */
class MainActivity:Activity() {
    private val bg=Color.rgb(4,13,20)
    private val white=Color.rgb(247,249,251)
    private val secondary=Color.rgb(163,186,202)
    private val green=Color.rgb(0,122,32)
    private val yellow=Color.rgb(255,199,22)
    private val red=Color.rgb(244,12,36)
    private val redDark=Color.rgb(142,2,23)
    private val neutral=Color.rgb(48,67,82)
    private val ui=Handler(Looper.getMainLooper())
    private val p by lazy {AppConfig.prefs(this)}
    private lateinit var plate:SpeedSignView
    private lateinit var speedBox:LinearLayout
    private lateinit var speedText:TextView
    private lateinit var unitText:TextView
    private lateinit var roadText:TextView
    private lateinit var roadSource:TextView
    private lateinit var monitorText:TextView
    private lateinit var monitorDot:TextView
    private lateinit var gpsBars:GpsBarsView
    private lateinit var muteButton:LinearLayout
    private lateinit var muteIcon:ImageView
    private lateinit var muteLabel:TextView
    private lateinit var coordRow:LinearLayout
    private lateinit var coordText:TextView
    private var fixLat=Double.NaN
    private var fixLon=Double.NaN
    private var fixBearing=-1f
    private var fixAccuracy=-1f
    private var lastStatus=""
    private var lastSource=""
    private var lastRoad=""
    private var kmh=-1.0
    private var limit=-1.0
    private var excessive=false
    private var blink=true

    private val flash=object:Runnable {
        override fun run() {
            if(!excessive)return
            blink=!blink
            updateColors()
            ui.postDelayed(this,450L)
        }
    }
    private val receiver=object:android.content.BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action!=MonitorService.UPDATE)return
            show(
                intent.getDoubleExtra("speed",-1.0),
                intent.getDoubleExtra("limit",-1.0),
                intent.getStringExtra("status")?:"AGUARDANDO GPS",
                intent.getStringExtra("source")?:"--",
                intent.getStringExtra("road")?:"Via não identificada",
                intent.getBooleanExtra("muted",false),
                intent.getFloatExtra("accuracy",-1f),
                intent.getDoubleExtra("lat",Double.NaN),
                intent.getDoubleExtra("lon",Double.NaN),
                intent.getFloatExtra("bearing",-1f))
        }
    }

    private fun dp(n:Int)=(n*resources.displayMetrics.density+0.5f).toInt()
    private fun round(color:Int,radius:Int=18,border:Int?=null,width:Int=1):GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius=dp(radius).toFloat()
            if(border!=null)setStroke(dp(width),border)
        }
    private fun gradient(a:Int,b:Int,radius:Int=18,border:Int?=null):GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(a,b)).apply {
            cornerRadius=dp(radius).toFloat()
            if(border!=null)setStroke(dp(2),border)
        }
    private fun label(s:String,size:Float,color:Int=white,bold:Boolean=false):TextView =
        TextView(this).apply {
            text=s
            textSize=size
            setTextColor(color)
            gravity=Gravity.CENTER
            typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding=true
        }
    private fun column()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
    }
    private fun add(parent:LinearLayout,child:View,height:Int=-2,top:Int=0,bottom:Int=0) {
        parent.addView(child,LinearLayout.LayoutParams(-1,height).apply {
            setMargins(0,dp(top),0,dp(bottom))
        })
    }
    private fun header():View {
        val head=column()
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        row.addView(Pt2vhfLogoView(this),LinearLayout.LayoutParams(dp(53),dp(53)))
        val names=column().apply {gravity=Gravity.CENTER_VERTICAL}
        names.addView(label("PT2VHF",22f,white,true).apply {
            gravity=Gravity.START
            typeface=Typeface.create("sans-serif",Typeface.BOLD_ITALIC)
        })
        names.addView(label("Speed Alert",15f).apply{gravity=Gravity.START})
        row.addView(names,LinearLayout.LayoutParams(0,-2,1f).apply {marginStart=dp(8)})
        val ver=try{packageManager.getPackageInfo(packageName,0).versionName?:"—"}
            catch(_:Exception){"—"}
        row.addView(label(ver,10f,secondary),LinearLayout.LayoutParams(-2,dp(44)))
        // Discreet GPS signal, top right next to the gear.
        gpsBars=GpsBarsView(this).apply { contentDescription="Sinal GPS: aguardando" }
        row.addView(gpsBars,LinearLayout.LayoutParams(dp(22),dp(18)).apply {marginStart=dp(8)})
        val gear=ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings_24)
            contentDescription="Configurações"
            scaleType=ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11),dp(11),dp(11),dp(11))
            background=round(bg,11)
            setOnClickListener {
                startActivity(Intent(this@MainActivity,SettingsActivity::class.java))
            }
        }
        row.addView(gear,LinearLayout.LayoutParams(dp(48),dp(48)).apply {marginStart=dp(6)})
        add(head,row,dp(58))
        val status=LinearLayout(this).apply {gravity=Gravity.CENTER}
        monitorDot=label("●",24f,Color.rgb(0,226,63),true)
        monitorText=label("Aguardando monitoramento",15f,white)
        status.addView(monitorDot,LinearLayout.LayoutParams(dp(29),dp(31)))
        status.addView(monitorText)
        add(head,status,-2,2)
        return head
    }
    private fun plateSize(landscape:Boolean)=
        dp(if(landscape)140 else if(resources.configuration.screenWidthDp<360)150 else 170)
    private fun topPlate(landscape:Boolean):View {
        val container=column()
        val dim=plateSize(landscape)
        plate=SpeedSignView(this).apply { contentDescription="Limite da via" }
        container.addView(plate,LinearLayout.LayoutParams(dim,dim))
        add(container,label("Velocidade atual",17f,white,true),-2,6,6)
        return container
    }
    /** Current speed in a square tile: grey unknown, green within, yellow near, red over the limit. */
    private fun speedDisplay(landscape:Boolean):View {
        val dim=plateSize(landscape)
        speedBox=LinearLayout(this).apply {
            gravity=Gravity.CENTER
            orientation=LinearLayout.VERTICAL
            background=gradient(neutral,Color.rgb(32,52,65),20,Color.rgb(104,128,147))
            elevation=dp(5).toFloat()
            contentDescription="Velocidade atual"
        }
        speedText=label("—",if(landscape)62f else 72f,white,true).apply {
            typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            includeFontPadding=false
        }
        unitText=label("km/h",20f,white,true)
        speedBox.addView(speedText)
        speedBox.addView(unitText)
        return LinearLayout(this).apply {
            gravity=Gravity.CENTER
            addView(speedBox,LinearLayout.LayoutParams(dim,dim))
        }
    }
    private fun streetLine():View {
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_road_view)
            contentDescription="Via atual"
        },LinearLayout.LayoutParams(dp(33),dp(33)).apply {marginEnd=dp(13)})
        val labels=column()
        roadText=label("Via não identificada",18f,white,true).apply {
            gravity=Gravity.START
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        roadSource=label("Base de vias offline",13f,secondary).apply {gravity=Gravity.START}
        add(labels,roadText)
        add(labels,roadSource)
        coordRow=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        coordText=label("GPS: —",11f,secondary).apply {
            gravity=Gravity.START
            typeface=Typeface.MONOSPACE
            maxLines=1
            contentDescription="Coordenadas GPS; toque para copiar"
            setOnClickListener {copyCoordinates()}
        }
        coordRow.addView(coordText,LinearLayout.LayoutParams(0,-2,1f))
        coordRow.addView(label("Marcar erro",11f,Color.rgb(94,214,156),true).apply {
            setPadding(dp(8),dp(6),0,dp(6))
            contentDescription="Marcar erro de limite neste local"
            setOnClickListener {markError()}
        })
        add(labels,coordRow,-2,2)
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        return row
    }
    private fun coordinates():String? =
        if(fixLat.isNaN() || fixLon.isNaN())null
        else String.format(java.util.Locale.US,"%.5f,%.5f",fixLat,fixLon)
    private fun copyCoordinates() {
        val c=coordinates()?:return
        val clip=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("Coordenadas",c))
        if(Build.VERSION.SDK_INT<33)Toast.makeText(this,"Coordenadas copiadas: $c",Toast.LENGTH_SHORT).show()
    }
    /**
     * One tap, safe while driving: saves position, heading, road and what the app showed.
     * The driver completes and publishes it later, stopped (Configurações → Relatos de erro).
     */
    private fun markError() {
        val r=Reports.mark(this)
        if(r==null) {
            Toast.makeText(this,"Sem posição GPS válida para marcar.",Toast.LENGTH_SHORT).show()
            return
        }
        if(kmh in 0.0..5.0) startActivity(Intent(this,ReportsActivity::class.java))
        else Toast.makeText(this,"Erro marcado. Complete com o veículo parado em Configurações → Relatos de erro.",
            Toast.LENGTH_LONG).show()
    }
    private fun audioButton():View {
        muteButton=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            isClickable=true
            isFocusable=true
            setOnClickListener {
                val toggled=!AppConfig.muted(p)
                p.edit().putBoolean("muted",toggled).apply()
                setMuted(toggled)
                startService(Intent(this@MainActivity,MonitorService::class.java)
                    .setAction(MonitorService.MUTE))
            }
        }
        muteIcon=ImageView(this)
        muteButton.addView(muteIcon,LinearLayout.LayoutParams(dp(26),dp(26)).apply {marginEnd=dp(10)})
        muteLabel=label("",18f,white,true)
        muteButton.addView(muteLabel)
        return muteButton
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(bg)
        }
        val outer=column().apply {setPadding(dp(19),dp(13),dp(19),dp(22))}
        scroll.addView(outer)
        val content=column()
        add(outer,content)
        add(content,header())
        val sign=topPlate(landscape)
        val speed=speedDisplay(landscape)
        val road=streetLine()
        val mute=audioButton()
        if(!landscape) {
            add(content,sign,-2,10,0)
            add(content,speed,-2,0,4)
            add(content,road,-2,18,15)
            val centered=LinearLayout(this).apply {
                gravity=Gravity.CENTER
                addView(mute,LinearLayout.LayoutParams(dp(280),dp(55)))
            }
            add(content,centered,-2,3)
        }else {
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            val left=LinearLayout(this).apply {
                gravity=Gravity.CENTER
                setPadding(0,0,dp(12),0)
                addView(sign); addView(speed,LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(12)})
            }
            val right=column().apply {setPadding(dp(12),0,0,0)}
            add(right,road,-2,0,14)
            add(right,mute,dp(56))
            row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(right,LinearLayout.LayoutParams(0,-2,1.05f))
            add(content,row,-2,10)
        }
        setContentView(scroll)
        EdgeToEdge.apply(this,scroll)
        setMuted(AppConfig.muted(p))
        applyCoordVisibility()
        if(Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backCallback as android.window.OnBackInvokedCallback)
        StartupDisclaimer.show(this) {
            p.edit().putBoolean("statsNoticeAccepted",true).apply()
            startMonitor()
            UpdateScheduler.sync(this)
        }
    }
    /** The button shows the CURRENT state (icon, text and colour); tapping switches it. */
    private fun setMuted(isMuted:Boolean) {
        if(!::muteButton.isInitialized)return
        muteIcon.setImageResource(if(isMuted)R.drawable.ic_mute_alert else R.drawable.ic_volume_on)
        muteLabel.text=if(isMuted)"Som DESLIGADO" else "Som ligado"
        muteButton.background=if(isMuted)gradient(red,redDark,26,Color.rgb(255,174,181))
            else gradient(Color.rgb(43,71,92),Color.rgb(19,38,52),26,Color.rgb(104,146,174))
        muteButton.contentDescription=if(isMuted)"Som dos alarmes desligado. Toque para ligar."
            else "Som dos alarmes ligado. Toque para silenciar."
    }
    private fun updateColors() {
        if(!::speedBox.isInitialized)return
        val colors=when {
            kmh<0 || limit<=0 -> intArrayOf(neutral,Color.rgb(30,49,64))
            excessive -> if(blink)intArrayOf(Color.rgb(255,27,51),red)
                else intArrayOf(red,redDark)
            kmh>=limit*0.95 -> intArrayOf(Color.rgb(255,222,37),yellow)
            else -> intArrayOf(Color.rgb(0,166,22),green)
        }
        val border=when {
            kmh<0 || limit<=0 -> Color.rgb(93,114,133)
            excessive -> Color.rgb(255,174,181)
            kmh>=limit*0.95 -> Color.rgb(255,234,95)
            else -> Color.rgb(33,237,86)
        }
        speedBox.background=gradient(colors[0],colors[1],20,border)
        val textColor=if(!excessive && kmh>=0 && limit>0 && kmh>=limit*0.95)
            Color.BLACK else white
        speedText.setTextColor(textColor)
        unitText.setTextColor(textColor)
        speedBox.elevation=dp(if(excessive&&blink)12 else 5).toFloat()
    }
    private fun show(v:Double,max:Double,status:String,source:String,street:String,
                     isMuted:Boolean,accuracy:Float,
                     lat:Double=Double.NaN,lon:Double=Double.NaN,bearing:Float=-1f) {
        fixLat=lat;fixLon=lon;fixBearing=bearing;fixAccuracy=accuracy
        lastStatus=status;lastSource=source;lastRoad=street
        coordText.text=coordinates()?.let { c ->
            "GPS "+c.replace(",",", ")+
                (if(accuracy>=0)" ±${accuracy.roundToInt()}m" else "")+
                (if(bearing>=0)" ${bearing.roundToInt()}°" else "")
        } ?: "GPS: —"
        kmh=v
        limit=max
        speedText.text=if(v>=0)v.roundToInt().toString() else "—"
        plate.limit=if(max>0)max.roundToInt().toString() else "—"
        roadText.text=street.ifBlank{"Via não identificada"}
        roadSource.text=source.ifBlank{"Fonte indisponível"}
        gpsBars.level=when {
            accuracy<0f -> 0
            accuracy<=10f -> 4
            accuracy<=20f -> 3
            accuracy<=35f -> 2
            else -> 1
        }
        gpsBars.contentDescription="Sinal GPS: "+when(gpsBars.level) {
            4->"forte"
            3->"bom"
            2->"regular"
            1->"fraco"
            else->"aguardando"
        }
        monitorText.text=if(status=="MONITORAMENTO PARADO")"Monitoramento parado"
            else "Monitoramento ativo"
        monitorDot.setTextColor(if(status=="GPS DESLIGADO" || status=="FALHA GPS")
            Color.rgb(255,181,48) else Color.rgb(0,228,67))
        val was=excessive
        excessive=v>=0 && max>0 && v>max
        // Fixes arrive up to 10x/s: restart the blink only when the state changes.
        if(excessive!=was) {
            ui.removeCallbacks(flash)
            blink=true
            if(excessive)ui.postDelayed(flash,450L)
        }
        updateColors()
        setMuted(isMuted)
    }
    private fun startMonitor() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED) {
            val perms=mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            if(Build.VERSION.SDK_INT>=33)perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(),440)
            return
        }
        try {
            startForegroundService(Intent(this,MonitorService::class.java)
                .setAction(MonitorService.START))
            monitorText.text="Monitoramento ativo"
        }catch(e:Exception) {
            monitorText.text="Falha ao iniciar GPS"
            monitorDot.setTextColor(Color.rgb(255,181,48))
            Toast.makeText(this,e.localizedMessage?:"Falha no GPS",Toast.LENGTH_LONG).show()
        }
    }
    override fun onRequestPermissionsResult(code:Int,perms:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,perms,results)
        if(code==440) {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    ==PackageManager.PERMISSION_GRANTED)startMonitor()
            else monitorText.text="Localização precisa necessária"
        }
    }
    override fun onStart() {
        super.onStart()
        val f=IntentFilter(MonitorService.UPDATE)
        androidx.core.content.ContextCompat.registerReceiver(
            this,receiver,f,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        MonitorService.latest?.let {
            show(it.speed,it.limit,it.status,it.source,it.roadName,
                it.muted,it.gpsAccuracy,it.lat,it.lon,it.bearing)
        }
    }
    /** Back = ask: really exit (stops GPS and alarms) or keep alerting in the background. */
    private fun confirmExit() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Sair do Speed Alert?")
            .setMessage("Ao sair, o GPS e os alarmes são desligados.\n\n"+
                "Para usar com Waze/Maps, escolha \"Segundo plano\": os alertas continuam.")
            .setPositiveButton("Sair") { _,_ ->
                try { startService(Intent(this,MonitorService::class.java).setAction(MonitorService.STOP)) }
                catch(_:Exception) {}
                finishAndRemoveTask()
            }
            .setNeutralButton("Segundo plano") { _,_ -> moveTaskToBack(true) }
            .setNegativeButton("Cancelar",null)
            .show()
    }
    private val backCallback:Any? by lazy {
        if(Build.VERSION.SDK_INT>=33) android.window.OnBackInvokedCallback { confirmExit() } else null
    }
    // Still called on Android 12 and on 13–15 without predictive-back opt-in; Android 16
    // (targetSdk 36) uses the OnBackInvokedCallback registered in onCreate instead.
    @Deprecated("Android 15 and older; 16+ uses OnBackInvokedCallback")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() = confirmExit()
    override fun onResume() {
        super.onResume()
        if(AppConfig.screen(p))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setMuted(AppConfig.muted(p))
        applyCoordVisibility()
        StoreUpdates.onResume(this)
    }
    private fun applyCoordVisibility() {
        if(::coordRow.isInitialized)
            coordRow.visibility=if(AppConfig.showCoords(p))View.VISIBLE else View.GONE
    }
    override fun onStop() {
        ui.removeCallbacks(flash)
        unregisterReceiver(receiver)
        super.onStop()
    }
}

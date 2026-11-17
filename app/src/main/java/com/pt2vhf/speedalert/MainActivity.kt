package com.pt2vhf.speedalert

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
    private lateinit var miniPlate:SpeedSignView
    private lateinit var speedBox:LinearLayout
    private lateinit var speedText:TextView
    private lateinit var unitText:TextView
    private lateinit var roadText:TextView
    private lateinit var roadSource:TextView
    private lateinit var monitorText:TextView
    private lateinit var monitorDot:TextView
    private lateinit var radarValue:TextView
    private lateinit var limitValue:TextView
    private lateinit var gpsValue:TextView
    private lateinit var gpsBars:GpsBarsView
    private lateinit var muteButton:LinearLayout
    private lateinit var muteLabel:TextView
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
                intent.getDoubleExtra("radar",-1.0),
                intent.getBooleanExtra("radarActive",false),
                intent.getBooleanExtra("muted",false),
                intent.getFloatExtra("accuracy",-1f))
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
    private fun topPlate(landscape:Boolean):View {
        val container=column()
        val dim=dp(if(landscape)152 else if(resources.configuration.screenWidthDp<360)170 else 191)
        plate=SpeedSignView(this)
        container.addView(plate,LinearLayout.LayoutParams(dim,dim))
        add(container,label("Velocidade atual",19f,white,true),-2,7,7)
        return container
    }
    private fun speedDisplay():View {
        speedBox=LinearLayout(this).apply {
            gravity=Gravity.CENTER
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(12),0,dp(12),0)
            background=gradient(neutral,Color.rgb(32,52,65),23,Color.rgb(104,128,147))
            elevation=dp(5).toFloat()
        }
        speedText=label("—",83f,white,true).apply {
            typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        }
        unitText=label("km/h",23f,white,true).apply {
            setPadding(dp(9),dp(22),0,0)
        }
        speedBox.addView(speedText)
        speedBox.addView(unitText)
        return speedBox
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
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        return row
    }
    private fun dashboardStats():View {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(5),dp(9),dp(5),dp(9))
            background=gradient(Color.rgb(22,44,60),Color.rgb(10,27,39),18,Color.rgb(32,58,73))
        }
        fun cell()=column().apply{gravity=Gravity.CENTER}
        fun sep():View=View(this).apply{setBackgroundColor(0x77647B8A)}
        val camera=cell()
        camera.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_radar_camera)
        },LinearLayout.LayoutParams(dp(27),dp(27)))
        add(camera,label("Próximo radar",11.5f),-2,3)
        radarValue=label("—",17f,white,true)
        add(camera,radarValue)
        box.addView(camera,LinearLayout.LayoutParams(0,dp(91),1f))
        box.addView(sep(),LinearLayout.LayoutParams(dp(1),dp(67)))
        val limitCell=cell()
        miniPlate=SpeedSignView(this)
        limitCell.addView(miniPlate,LinearLayout.LayoutParams(dp(36),dp(36)))
        add(limitCell,label("Limite",11.5f))
        limitValue=label("—",15f,white,true)
        add(limitCell,limitValue)
        box.addView(limitCell,LinearLayout.LayoutParams(0,dp(91),1f))
        box.addView(sep(),LinearLayout.LayoutParams(dp(1),dp(67)))
        val gps=cell()
        gpsBars=GpsBarsView(this)
        gps.addView(gpsBars,LinearLayout.LayoutParams(dp(31),dp(29)))
        add(gps,label("GPS",11.5f),-2,3)
        gpsValue=label("Aguarde",15f,white,true)
        add(gps,gpsValue)
        box.addView(gps,LinearLayout.LayoutParams(0,dp(91),1f))
        return box
    }
    private fun audioButton():View {
        muteButton=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            background=gradient(Color.rgb(43,71,92),Color.rgb(19,38,52),26,
                Color.rgb(104,146,174))
            contentDescription="Silenciar ou reativar os alertas sonoros"
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
        muteButton.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_mute_alert)
        },LinearLayout.LayoutParams(dp(26),dp(26)).apply {marginEnd=dp(10)})
        muteLabel=label("Silenciar",18f,white,true)
        muteButton.addView(muteLabel)
        return muteButton
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
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
        val speed=speedDisplay()
        val road=streetLine()
        val metrics=dashboardStats()
        val mute=audioButton()
        if(!landscape) {
            add(content,sign,-2,10,0)
            add(content,speed,dp(110),5,4)
            add(content,road,-2,15,15)
            add(content,metrics,-2,0,9)
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
            val left=column().apply {setPadding(0,0,dp(12),0)}
            val right=column().apply {setPadding(dp(12),0,0,0)}
            add(left,sign)
            add(left,speed,dp(95),3)
            add(right,road,-2,0,14)
            add(right,metrics,-2,0,13)
            add(right,mute,dp(56))
            row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(right,LinearLayout.LayoutParams(0,-2,1.05f))
            add(content,row,-2,10)
        }
        setContentView(scroll)
        setMuted(AppConfig.muted(p))
        StartupDisclaimer.show(this) {
            p.edit().putBoolean("statsNoticeAccepted",true).apply()
            startMonitor()
            UpdateScheduler.sync(this)
        }
    }
    private fun setMuted(isMuted:Boolean) {
        if(::muteLabel.isInitialized)muteLabel.text=if(isMuted)"Ativar som" else "Silenciar"
        if(::muteButton.isInitialized)muteButton.contentDescription=
            if(isMuted)"Reativar o som dos alarmes" else "Silenciar os alarmes"
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
        speedBox.background=gradient(colors[0],colors[1],23,border)
        val textColor=if(!excessive && kmh>=0 && limit>0 && kmh>=limit*0.95)
            Color.BLACK else white
        speedText.setTextColor(textColor)
        unitText.setTextColor(textColor)
        speedBox.elevation=dp(if(excessive&&blink)12 else 5).toFloat()
    }
    private fun show(v:Double,max:Double,status:String,source:String,street:String,
                     radar:Double,radarWarning:Boolean,isMuted:Boolean,accuracy:Float) {
        kmh=v
        limit=max
        speedText.text=if(v>=0)v.roundToInt().toString() else "—"
        plate.limit=if(max>0)max.roundToInt().toString() else "—"
        miniPlate.limit=plate.limit
        limitValue.text=if(max>0)max.roundToInt().toString()+" km/h" else "—"
        roadText.text=street.ifBlank{"Via não identificada"}
        roadSource.text=source.ifBlank{"Fonte indisponível"}
        radarValue.text=if(radar>=0)radar.roundToInt().toString()+" m" else "—"
        radarValue.setTextColor(if(radarWarning)Color.rgb(255,93,93) else white)
        gpsBars.level=when {
            accuracy<0f -> 0
            accuracy<=10f -> 4
            accuracy<=20f -> 3
            accuracy<=35f -> 2
            else -> 1
        }
        gpsValue.text=when(gpsBars.level) {
            4->"Forte"
            3->"Bom"
            2->"Regular"
            1->"Fraco"
            else->"Aguarde"
        }
        monitorText.text=if(status=="MONITORAMENTO PARADO")"Monitoramento parado"
            else "Monitoramento ativo"
        monitorDot.setTextColor(if(status=="GPS DESLIGADO" || status=="FALHA GPS")
            Color.rgb(255,181,48) else Color.rgb(0,228,67))
        excessive=v>=0 && max>0 && v>max
        ui.removeCallbacks(flash)
        blink=true
        updateColors()
        if(excessive)ui.postDelayed(flash,450L)
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
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver,f)
        }
        MonitorService.latest?.let {
            show(it.speed,it.limit,it.status,it.source,it.roadName,
                it.radarMeters?:-1.0,it.radarActive,it.muted,it.gpsAccuracy)
        }
    }
    override fun onResume() {
        super.onResume()
        if(AppConfig.screen(p))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setMuted(AppConfig.muted(p))
    }
    override fun onStop() {
        ui.removeCallbacks(flash)
        unregisterReceiver(receiver)
        super.onStop()
    }
}

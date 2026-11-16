package com.pt2vhf.speedalert

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
  private val p by lazy { AppConfig.prefs(this) }
  private lateinit var manualLimit: EditText
  private lateinit var beepMs: EditText
  private lateinit var pauseMs: EditText
  private val navy = Color.rgb(12,20,29)
  private val white = Color.WHITE
  private val green = Color.rgb(94,214,156)

  private fun add(root: LinearLayout, view: View) {
    val lp = LinearLayout.LayoutParams(-1,-2)
    lp.setMargins(0,8,0,8)
    root.addView(view,lp)
  }
  private fun label(t:String, size:Float=15f, color:Int=white) =
    TextView(this).apply { text=t; textSize=size; setTextColor(color);setPadding(8,12,8,8) }
  private fun toggle(root:LinearLayout,title:String,key:String,default:Boolean,after:((Boolean)->Unit)?=null):Switch {
    val item=Switch(this).apply {
      text=title;textSize=15f;setTextColor(white);setPadding(8,14,8,14)
      isChecked=p.getBoolean(key,default)
      setOnCheckedChangeListener { _,value ->
        p.edit().putBoolean(key,value).apply()
        after?.invoke(value)
      }
    }
    add(root,item)
    return item
  }
  private fun edit(root:LinearLayout,title:String,initial:Int):EditText {
    add(root,label(title,14f))
    val field=EditText(this).apply {
      inputType=InputType.TYPE_CLASS_NUMBER
      gravity=Gravity.CENTER
      textSize=20f;setTextColor(white);setText(initial.toString())
    }
    add(root,field)
    return field
  }
  private fun seek(root:LinearLayout,title:String,key:String,default:Int,maximum:Int,suffix:String) {
    val caption=label("",15f)
    add(root,caption)
    val bar=SeekBar(this).apply { max=maximum;progress=p.getInt(key,default).coerceIn(0,maximum) }
    caption.text=title+": "+bar.progress+suffix
    bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(s:SeekBar?,value:Int,user:Boolean) {
        caption.text=title+": "+value+suffix
        if(user) p.edit().putInt(key,value).apply()
      }
      override fun onStartTrackingTouch(s:SeekBar?){}
      override fun onStopTrackingTouch(s:SeekBar?){}
    })
    add(root,bar)
  }
  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
    window.statusBarColor=navy;window.navigationBarColor=navy
    val scroll=ScrollView(this).apply { setBackgroundColor(navy) }
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,24) }
    scroll.addView(root)
    add(root,label("CONFIGURAÇÕES",24f,green))
    add(root,label("MONITORAMENTO",18f,green))
    add(root,label("O monitoramento inicia automaticamente após Continuar no aviso.",13f,Color.LTGRAY))
    toggle(root,"Manter a tela ligada","keepScreenOn",false)
    toggle(root,"Consultar base de limites offline","offline",true)
    toggle(root,"Mostrar aviso de limite desconhecido","warnUnknown",true)
    val useManual=toggle(root,"Usar limite manual","manual",false) { enabled ->
      manualLimit.isEnabled=enabled
    }
    manualLimit=edit(root,"Limite manual (10–160 km/h)",AppConfig.manualLimit(p))
    manualLimit.isEnabled=useManual.isChecked
    seek(root,"Tolerância de velocidade","tolerance",0,20," km/h")
    seek(root,"Distância para aviso de radar","radarRange",300,1000," m")
    add(root,label("ALARME SONORO",18f,green))
    seek(root,"Volume relativo","volume",80,100,"%")
    add(root,label("Tipo de alarme",14f))
    val options=arrayOf("Bipes repetitivos","Tom ininterrupto")
    val spinner=Spinner(this)
    spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
    spinner.setSelection(if(AppConfig.mode(p)=="CONTINUO") 1 else 0)
    spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
        p.edit().putString("soundMode",if(position==1) "CONTINUO" else "BIPES").apply()
      }
      override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
    }
    add(root,spinner)
    beepMs=edit(root,"Duração dos bipes (100–1000 ms)",AppConfig.beepMs(p))
    pauseMs=edit(root,"Intervalo entre bipes (100–2000 ms)",AppConfig.pauseMs(p))
    add(root,label("ATUALIZAÇÕES E PRIVACIDADE",18f,green))
    toggle(root,"Verificar novas versões pelo GitHub quando houver Internet","autoUpdates",true)
    toggle(root,"Participar das estatísticas anônimas de instalações no site","shareStats",false)
    add(root,label("Ao participar, cada consulta de versão envia apenas um UUID aleatório e a versão; nenhum dado de GPS, trajeto ou velocidade. O país é estimado pelo servidor quando disponível.",12f,Color.LTGRAY))
    add(root,label("Alterações aplicadas automaticamente às próximas leituras de GPS. Sem Internet, o monitoramento e os alarmes continuam funcionando.",12f,Color.LTGRAY))
    add(root,Button(this).apply { text="SALVAR E VOLTAR";setOnClickListener { save();finish() } })
    setContentView(scroll)
  }
  private fun save() {
    fun value(field:EditText,original:Int,min:Int,max:Int):Int =
      (field.text.toString().toIntOrNull()?:original).coerceIn(min,max)
    p.edit().putInt("manualLimit",value(manualLimit,AppConfig.manualLimit(p),10,160))
      .putInt("beepMs",value(beepMs,AppConfig.beepMs(p),100,1000))
      .putInt("pauseMs",value(pauseMs,AppConfig.pauseMs(p),100,2000)).apply()
  }
  override fun onPause() {save();super.onPause()}
}

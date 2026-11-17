package com.pt2vhf.speedalert

import android.app.Activity
import android.content.Intent
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
  private val exportRequest=7031
  private val importRequest=7032
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
    toggle(root,"Manter a tela ligada","keepScreenOn",true)
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
    val options=arrayOf("Bipes simples","Bipes duplos","Bipes triplos",
      "Sirene alternada","Alerta suave","Tom ininterrupto")
    val modeKeys=arrayOf("BIPES","DUPLO","TRIPLO","SIRENE","SUAVE","CONTINUO")
    val spinner=Spinner(this)
    spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
    spinner.setSelection(modeKeys.indexOf(AppConfig.mode(p)).coerceAtLeast(0))
    spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
        p.edit().putString("soundMode",modeKeys[position]).apply()
      }
      override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
    }
    add(root,spinner)
    beepMs=edit(root,"Duração dos bipes (100–1000 ms)",AppConfig.beepMs(p))
    pauseMs=edit(root,"Intervalo entre bipes (100–2000 ms)",AppConfig.pauseMs(p))
    add(root,label("ALARMES EM SEGUNDO PLANO",18f,green))
    add(root,label("O serviço de localização mantém os alertas ativos com a tela bloqueada ou quando Waze/Google Maps estiverem visíveis. A reprodução pode depender da política de áudio da central multimídia, do Android e das permissões.",12f,Color.LTGRAY))
    add(root,label("ATUALIZAÇÕES E PRIVACIDADE",18f,green))
    toggle(root,"Verificar novas atualizações periodicamente","autoUpdates",true) {
      UpdateScheduler.sync(this)
    }
    add(root,label("Verificação diária quando houver Internet. O Android pode adiar a execução para poupar bateria.",12f,Color.LTGRAY))
    add(root,Button(this).apply {
      text="VERIFICAR ATUALIZAÇÃO AGORA"
      setOnClickListener { UpdateManager.check(this@SettingsActivity,false) }
    })
    toggle(root,"Participar das estatísticas anônimas de instalações no site","shareStats",true)
    add(root,label("Quando habilitado, a consulta envia um identificador aleatório persistente e a versão. A atividade é pseudônima, não plenamente anônima. Não são enviados GPS, trajeto nem velocidade. O país pode ser estimado pelo servidor. Você pode desativar a opção.",12f,Color.LTGRAY))
    add(root,label("MIGRAÇÃO E CÓPIA DE SEGURANÇA",18f,green))
    add(root,label("Antes de desinstalar uma beta antiga, exporte as configurações. Na nova versão assinada, importe esse arquivo. Por segurança, o identificador das estatísticas e permissões não são exportados.",12f,Color.LTGRAY))
    add(root,Button(this).apply {
      text="EXPORTAR CONFIGURAÇÕES"
      setOnClickListener {
        save()
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type="application/json"
          putExtra(Intent.EXTRA_TITLE,"PT2VHF-SpeedAlert-settings.json")
        }
        startActivityForResult(intent,exportRequest)
      }
    })
    add(root,Button(this).apply {
      text="IMPORTAR CONFIGURAÇÕES"
      setOnClickListener {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type="application/json"
        }
        startActivityForResult(intent,importRequest)
      }
    })
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
  @Deprecated("Activity result API used for compatibility with Android 8+")
  override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
    super.onActivityResult(requestCode,resultCode,data)
    if(resultCode!=RESULT_OK || data?.data==null)return
    try {
      val uri=data.data!!
      if(requestCode==exportRequest) {
        contentResolver.openOutputStream(uri,"wt")?.use { output ->
          output.write(SettingsBackup.export(p).toByteArray(Charsets.UTF_8))
        } ?: error("Não foi possível abrir arquivo de saída")
        Toast.makeText(this,"Configurações exportadas",Toast.LENGTH_LONG).show()
      } else if(requestCode==importRequest) {
        val text=contentResolver.openInputStream(uri)?.use {
          val bytes=it.readBytes()
          require(bytes.size<=16384){"Arquivo muito grande"}
          String(bytes,Charsets.UTF_8)
        } ?: error("Não foi possível ler o arquivo")
        val count=SettingsBackup.import(p,text)
        manualLimit.setText(AppConfig.manualLimit(p).toString())
        beepMs.setText(AppConfig.beepMs(p).toString())
        pauseMs.setText(AppConfig.pauseMs(p).toString())
        UpdateScheduler.sync(this)
        Toast.makeText(this,"$count ajustes importados; reabra Configurações.",Toast.LENGTH_LONG).show()
        finish()
      }
    } catch(e:Exception) {
      Toast.makeText(this,e.localizedMessage?:"Falha no arquivo",Toast.LENGTH_LONG).show()
    }
  }
  override fun onPause() {save();super.onPause()}
}

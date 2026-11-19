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
  private lateinit var manualLimit: SeekBar
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
  private fun seek(root:LinearLayout,title:String,key:String,default:Int,maximum:Int,suffix:String,minimum:Int=0):SeekBar {
    val caption=label("",15f)
    add(root,caption)
    val bar=SeekBar(this).apply { min=minimum;max=maximum;progress=p.getInt(key,default).coerceIn(minimum,maximum) }
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
    return bar
  }
  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
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
    manualLimit=seek(root,"Limite manual","manualLimit",60,160," km/h",minimum=10)
    manualLimit.isEnabled=useManual.isChecked
    seek(root,"Tolerância de velocidade","tolerance",0,20," km/h")
    toggle(root,"Mostrar coordenadas GPS e \"Marcar erro\" na tela","showCoords",true)
    add(root,label("Linha discreta abaixo da via. Toque nas coordenadas para copiar; \"Marcar erro\" guarda o local para relatar depois.",12f,Color.LTGRAY))
    add(root,label("ALARME SONORO",18f,green))
    seek(root,"Volume relativo","volume",80,100,"%")
    add(root,label("O alarme segue o volume de MÍDIA do celular (ou do carro, no Bluetooth).",12f,Color.LTGRAY))
    add(root,Button(this).apply {
      text="TESTAR SOM (2 s)"
      setOnClickListener { button ->
        save()
        val test=AlarmAudio(this@SettingsActivity)
        test.setAlert(true,AppConfig.mode(p),AppConfig.volume(p).coerceAtLeast(1),AppConfig.beepMs(p),AppConfig.pauseMs(p))
        test.lastError?.let { Toast.makeText(this@SettingsActivity,"Som: $it",Toast.LENGTH_LONG).show() }
        button.isEnabled=false
        button.postDelayed({ test.close(); button.isEnabled=true },2000L)
      }
    })
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
    if(BuildConfig.OTA) {
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
    } else {
      add(root,label("As atualizações são entregues pelo Google Play. GPS, limites e alarmes funcionam totalmente no aparelho; só os relatos de erro que você confirmar são enviados (ao OpenStreetMap).",12f,Color.LTGRAY))
    }
    add(root,Button(this).apply {
      text="POLÍTICA DE PRIVACIDADE"
      setOnClickListener {
        try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AppConfig.PRIVACY_URL))) }
        catch(_:Exception) { Toast.makeText(this@SettingsActivity,AppConfig.PRIVACY_URL,Toast.LENGTH_LONG).show() }
      }
    })
    add(root,label("RELATOS DE ERRO",18f,green))
    add(root,label("Erros marcados com \"Marcar erro\" na tela principal. Complete e envie ao OpenStreetMap com o veículo parado.",12f,Color.LTGRAY))
    add(root,Button(this).apply {
      text="RELATOS DE ERRO (${Reports.pending(this@SettingsActivity)} pendentes)"
      setOnClickListener { startActivity(Intent(this@SettingsActivity,ReportsActivity::class.java)) }
    })
    tilesSection(root)
    add(root,label("CÓPIA DE SEGURANÇA",18f,green))
    add(root,label("Exporte as configurações para um arquivo e importe-o em outro aparelho ou após reinstalar. Por segurança, o identificador das estatísticas e permissões não são exportados.",12f,Color.LTGRAY))
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
    EdgeToEdge.apply(this,scroll)
  }
  private fun save() {
    fun value(field:EditText,original:Int,min:Int,max:Int):Int =
      (field.text.toString().toIntOrNull()?:original).coerceIn(min,max)
    p.edit().putInt("beepMs",value(beepMs,AppConfig.beepMs(p),100,1000))
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
  private fun reloadMonitor() {
    if(MonitorService.latest!=null)
      try { startService(Intent(this,MonitorService::class.java).setAction(MonitorService.RELOAD)) } catch(_:Exception) {}
  }
  /** Map data in ~110 km tiles: downloaded around the driver, kept for trips, evicted when unused. */
  private fun tilesSection(root:LinearLayout) {
    add(root,label("BASE OFFLINE POR BLOCOS",18f,green))
    add(root,label("Limites de velocidade são baixados em blocos de cerca de 110 km ao redor de onde você está, "+
      "e os não usados há 30 dias são apagados. Cobertura: Centro-Oeste, Sudeste e Sul. "+
      "Antes de viajar para um lugar sem sinal, baixe a região inteira."+
      (if(BuildConfig.ONLINE_TILES)"\n\nNo resto do Brasil e do mundo, o app baixa áreas de cerca de 11 km direto do "+
        "OpenStreetMap (servidor público Overpass) enquanto você dirige. Só os limites da área são enviados, "+
        "não a sua posição exata." else ""),12f,Color.LTGRAY))
    val status=label("",13f)
    add(root,status)
    fun refresh() {
      val (n,bytes)=TileStore.usage(this)
      val (on,onBytes)=OnlineTiles.usage(this)
      status.text="$n blocos no aparelho • ${(bytes+524287)/1048576} MB • dados OSM de ${TileStore.dataDate(this)}"+
        (if(BuildConfig.ONLINE_TILES)" • $on áreas online (${(onBytes+524287)/1048576} MB)" else "")+
        (if(TileStore.busy)" • baixando…" else "")+
        ((TileStore.lastError?:OnlineTiles.lastError)?.let { " • último erro: $it" }?:"")
    }
    refresh()
    toggle(root,"Baixar blocos só no Wi-Fi","tilesWifiOnly",false)
    seek(root,"Espaço máximo para blocos automáticos","tileLimitMb",500,4000," MB")
    for((id,title) in listOf("centro-oeste" to "CENTRO-OESTE","sudeste" to "SUDESTE","sul" to "SUL")) {
      add(root,Button(this).apply {
        val mb=TileStore.regionSizeMb(this@SettingsActivity,id)
        text="BAIXAR $title PARA VIAGEM"+(if(mb>0)" (~$mb MB)" else "")
        setOnClickListener {
          isEnabled=false
          Toast.makeText(this@SettingsActivity,"Baixando $title…",Toast.LENGTH_SHORT).show()
          Thread {
            val ids=TileStore.regionTiles(this@SettingsActivity,id)
            val changed=if(ids.isEmpty())false else TileStore.ensure(this@SettingsActivity,ids,pin=true) { done,total ->
              runOnUiThread { status.text="Baixando $title: $done de $total blocos…" } }
            runOnUiThread {
              if(isFinishing)return@runOnUiThread
              isEnabled=true; refresh()
              if(changed)reloadMonitor()
              Toast.makeText(this@SettingsActivity,
                if(ids.isEmpty())"Sem Internet ou lista de blocos indisponível." else
                TileStore.lastError?.let { "Concluído com falhas: $it" } ?: "$title disponível offline.",
                Toast.LENGTH_LONG).show()
            }
          }.start()
        }
      })
    }
    add(root,Button(this).apply {
      text="APAGAR BLOCOS BAIXADOS"
      setOnClickListener {
        android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Apagar blocos?")
          .setMessage("Os blocos ao redor serão baixados de novo automaticamente quando houver Internet.")
          .setNegativeButton("Cancelar",null)
          .setPositiveButton("Apagar") { _,_ -> TileStore.clear(this@SettingsActivity);OnlineTiles.clear(this@SettingsActivity);reloadMonitor();refresh() }
          .show()
      }
    })
  }
}

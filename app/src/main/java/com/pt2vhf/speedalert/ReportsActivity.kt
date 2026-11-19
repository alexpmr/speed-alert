package com.pt2vhf.speedalert

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Marked errors: complete (stopped), publish as an OpenStreetMap note, share or delete. */
class ReportsActivity:Activity() {
  private val navy=Color.rgb(12,20,29)
  private val green=Color.rgb(94,214,156)
  private lateinit var list:LinearLayout

  private fun label(t:String,size:Float=14f,color:Int=Color.WHITE)=
    TextView(this).apply { text=t;textSize=size;setTextColor(color);setPadding(8,8,8,8) }
  private fun moving()=(MonitorService.latest?.speed?:-1.0)>10.0

  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
    val scroll=ScrollView(this).apply { setBackgroundColor(navy) }
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,24) }
    scroll.addView(root)
    root.addView(label("RELATOS DE ERRO",24f,green))
    root.addView(label("Marque erros na tela principal (\"Marcar erro\") e complete aqui com o veículo parado. "+
      "Ao enviar, o relato vira uma nota pública no OpenStreetMap, no local marcado, para que mapeadores "+
      "voluntários corrijam o mapa. A correção chega ao app na atualização seguinte das bases.",12f,Color.LTGRAY))
    list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    root.addView(list)
    setContentView(scroll)
    EdgeToEdge.apply(this,scroll)
  }
  override fun onResume() { super.onResume();render() }

  private fun render() {
    list.removeAllViews()
    val items=Reports.all(this).sortedByDescending { it.created }
    if(items.isEmpty()) { list.addView(label("Nenhum erro marcado.",15f,Color.LTGRAY));return }
    val fmt=SimpleDateFormat("dd/MM HH:mm",Locale.US)
    for(r in items) {
      val card=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12)
        setBackgroundColor(Color.rgb(22,40,54))
      }
      val title=if(r.noteId>0)"Enviado ✓ — "+(Reports.types[r.type]?:"") else
        if(r.type.isBlank())"Pendente — complete o relato" else "Pronto para enviar — "+Reports.types[r.type]
      card.addView(label(title,15f,if(r.noteId>0)green else Color.WHITE))
      card.addView(label("${fmt.format(Date(r.created))} • ${r.road.ifBlank{"via não identificada"}}\n"+
        "Mostrava: limite ${if(r.shownLimit>0)Math.round(r.shownLimit).toString()+" km/h" else "—"}"+
        (if(r.correct.isNotBlank())" • no local: ${r.correct}" else "")+
        "\n%.5f, %.5f".format(Locale.US,r.lat,r.lon),12f,Color.LTGRAY))
      val buttons=LinearLayout(this)
      fun button(t:String,action:()->Unit)=Button(this).apply { text=t;textSize=12f;setOnClickListener{action()} }
        .also { buttons.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }
      if(r.noteId>0) {
        button("VER NOTA") { startActivity(Intent(Intent.ACTION_VIEW,
          Uri.parse("https://www.openstreetmap.org/note/${r.noteId}"))) }
      } else {
        button("COMPLETAR") { complete(r) }
        if(r.type.isNotBlank())button("ENVIAR AO OSM") { publish(r) }
      }
      button("COMPARTILHAR") { share(r) }
      button("EXCLUIR") { Reports.delete(this,r.id);render() }
      card.addView(buttons)
      list.addView(card,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,8,0,8) })
    }
  }

  private fun complete(r:Reports.Report) {
    if(moving()) { Toast.makeText(this,"Complete o relato com o veículo parado.",Toast.LENGTH_LONG).show();return }
    val keys=Reports.types.keys.toList()
    val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,8,32,0) }
    val type=Spinner(this).apply {
      adapter=ArrayAdapter(this@ReportsActivity,android.R.layout.simple_spinner_dropdown_item,Reports.types.values.toList())
      setSelection(keys.indexOf(r.type).coerceAtLeast(0))
    }
    val correct=EditText(this).apply {
      hint="Valor correto no local (ex.: 70 km/h)";setText(r.correct)
      inputType=InputType.TYPE_CLASS_TEXT
    }
    val comment=EditText(this).apply {
      hint="Comentário (opcional)";setText(r.comment);minLines=2
      inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }
    form.addView(TextView(this).apply { text="Tipo de problema" })
    form.addView(type);form.addView(correct);form.addView(comment)
    AlertDialog.Builder(this).setTitle("Completar relato").setView(form)
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Salvar") { _,_ ->
        Reports.update(this,r.copy(type=keys[type.selectedItemPosition],
          correct=correct.text.toString().trim().take(80),comment=comment.text.toString().trim().take(500)))
        render()
      }.show()
  }

  private fun publish(r:Reports.Report) {
    if(moving()) { Toast.makeText(this,"Envie com o veículo parado.",Toast.LENGTH_LONG).show();return }
    AlertDialog.Builder(this).setTitle("Publicar no OpenStreetMap?")
      .setMessage("Será criada uma nota PÚBLICA e anônima no OpenStreetMap, visível para qualquer pessoa, "+
        "com este texto e a posição marcada:\n\n"+Reports.noteText(r))
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Publicar") { _,_ ->
        Toast.makeText(this,"Enviando…",Toast.LENGTH_SHORT).show()
        Thread {
          val result=try { "Nota publicada (nº ${Reports.publish(this,r)}). Obrigado!" }
            catch(e:Exception) { "Falha ao enviar: ${e.localizedMessage?:"sem conexão"}. Tente de novo mais tarde." }
          runOnUiThread { if(!isFinishing) { Toast.makeText(this,result,Toast.LENGTH_LONG).show();render() } }
        }.start()
      }.show()
  }

  private fun share(r:Reports.Report) {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
      type="text/plain"
      putExtra(Intent.EXTRA_SUBJECT,"PT2VHF Speed Alert — erro de limite")
      putExtra(Intent.EXTRA_TEXT,Reports.noteText(r)+"\nhttps://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=18/%.5f/%.5f"
        .format(Locale.US,r.lat,r.lon,r.lat,r.lon))
    },"Compartilhar relato"))
  }
}

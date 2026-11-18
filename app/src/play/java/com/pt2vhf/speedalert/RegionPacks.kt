package com.pt2vhf.speedalert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import java.io.File

/**
 * Regional offline databases delivered by Google Play (Play Asset Delivery).
 * Centro-Oeste is a fast-follow pack (Play downloads it right after install); Sul and Sudeste
 * are on-demand packs the driver downloads in Settings. All are opened in place, never copied.
 */
object RegionPacks {
  private data class Region(val pack:String,val id:String,val label:String)
  private val onDemand=listOf(
    Region("osm_centro_oeste","centro-oeste","Centro-Oeste (DF, GO, MT, MS)"),
    Region("osm_sudeste","sudeste","Sudeste (SP, RJ, MG, ES)"),
    Region("osm_sul","sul","Sul (PR, SC, RS)"))

  private fun manager(context:Context):AssetPackManager=
    AssetPackManagerFactory.getInstance(context.applicationContext)

  fun installedFiles(context:Context):List<Pair<String,File>> {
    val m=try { manager(context) } catch(e:Exception) { return emptyList() }
    return onDemand.mapNotNull { r ->
      val path=try { m.getPackLocation(r.pack)?.assetsPath() } catch(_:Exception) { null }
      path?.let { File(it,"osm/${r.id}.sqlite") }?.takeIf { it.isFile }?.let { r.id to it }
    }
  }

  private fun reloadMonitor(activity:Activity) {
    if(MonitorService.latest==null)return   // not running: it loads fresh data when started
    try { activity.startService(Intent(activity,MonitorService::class.java).setAction(MonitorService.RELOAD)) }
    catch(e:Exception) { Log.w("PT2VHF.Regions","reload",e) }
  }

  fun addSettingsSection(activity:Activity,root:LinearLayout,
      title:(String)->Unit,note:(String)->Unit):()->Unit {
    title("REGIÕES OFFLINE")
    note("O Centro-Oeste é baixado automaticamente após a instalação. Baixe as outras regiões pelo Google Play antes de viajar (de preferência no Wi-Fi); depois funcionam sem Internet.")
    val m=manager(activity)
    val rows=HashMap<String,Pair<TextView,Button>>()
    fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    fun mb(bytes:Long)=if(bytes>0)" (${(bytes+524287)/1048576} MB)" else ""
    fun render(r:Region,state:AssetPackState?) {
      val (status,button)=rows[r.pack]?:return
      val code=state?.status()?:AssetPackStatus.UNKNOWN
      val total=state?.totalBytesToDownload()?:0L
      when(code) {
        AssetPackStatus.COMPLETED -> { status.text="Instalada";button.text="REMOVER";button.isEnabled=true }
        AssetPackStatus.PENDING,AssetPackStatus.DOWNLOADING,AssetPackStatus.TRANSFERRING -> {
          val pct=if(total>0)(100*state!!.bytesDownloaded()/total).toInt() else 0
          status.text="Baixando… $pct%";button.text="AGUARDE";button.isEnabled=false }
        AssetPackStatus.WAITING_FOR_WIFI,AssetPackStatus.REQUIRES_USER_CONFIRMATION -> {
          status.text="Aguardando Wi-Fi ou confirmação";button.text="CONFIRMAR";button.isEnabled=true }
        AssetPackStatus.FAILED -> { status.text="Falha (código ${state?.errorCode()})";button.text="TENTAR DE NOVO";button.isEnabled=true }
        else -> { status.text="Não baixada"+mb(total);button.text="BAIXAR";button.isEnabled=true }
      }
    }
    fun refresh() {
      m.getPackStates(onDemand.map{it.pack}).addOnSuccessListener { states ->
        onDemand.forEach { render(it,states.packStates()[it.pack]) }
      }.addOnFailureListener { e ->
        Log.w("PT2VHF.Regions","states",e)
        rows.values.forEach { (s,b)-> s.text="Google Play indisponível";b.isEnabled=false }
      }
    }
    for(r in onDemand) {
      val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(8,6,8,6) }
      val texts=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
      texts.addView(TextView(activity).apply { text=r.label;textSize=15f;setTextColor(Color.WHITE) })
      val status=TextView(activity).apply { text="Consultando…";textSize=12f;setTextColor(Color.LTGRAY) }
      texts.addView(status)
      row.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
      val button=Button(activity).apply {
        text="…"
        setOnClickListener {
          when(text.toString()) {
            "REMOVER" -> m.removePack(r.pack).addOnCompleteListener { reloadMonitor(activity);refresh() }
            "CONFIRMAR" -> @Suppress("DEPRECATION") m.showConfirmationDialog(activity)
            else -> m.fetch(listOf(r.pack)).addOnFailureListener { e ->
              Log.w("PT2VHF.Regions","fetch",e);status.text="Falha ao iniciar download" }
          }
        }
      }
      row.addView(button,LinearLayout.LayoutParams(dp(150),-2))
      root.addView(row,LinearLayout.LayoutParams(-1,-2))
      rows[r.pack]=status to button
    }
    val listener=AssetPackStateUpdateListener { state ->
      onDemand.firstOrNull { it.pack==state.name() }?.let { r ->
        render(r,state)
        if(state.status()==AssetPackStatus.COMPLETED)reloadMonitor(activity)
        if(state.status()==AssetPackStatus.REQUIRES_USER_CONFIRMATION)
          @Suppress("DEPRECATION") m.showConfirmationDialog(activity)
      }
    }
    m.registerListener(listener)
    refresh()
    return { m.unregisterListener(listener) }
  }
}

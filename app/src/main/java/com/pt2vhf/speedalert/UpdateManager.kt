package com.pt2vhf.speedalert

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * GitHub Releases OTA: check on a worker thread, verify SHA-256 and ask the
 * Android package installer for confirmation. Core speed monitoring never waits for it.
 */
object UpdateManager {
  private const val releases="https://api.github.com/repos/alexpmr/speed-alert/releases?per_page=10"
  private const val activityUrl="https://www.pt2vhf.com/speed_activity.php"

  private fun currentVersion(activity:Activity):String=try {
    activity.packageManager.getPackageInfo(activity.packageName,0).versionName ?: "0.0.0"
  }catch(_:Exception){"0.0.0"}

  private fun versionParts(s:String):List<Int> {
    val matched=Regex("^(?:v)?([0-9]+)\\.([0-9]+)\\.([0-9]+)").find(s)?:return listOf(0,0,0)
    return (1..3).map {matched.groupValues[it].toIntOrNull()?:0}
  }
  private fun newer(a:String,b:String):Boolean {
    val x=versionParts(a);val y=versionParts(b)
    for(i in 0..2)if(x[i]!=y[i])return x[i]>y[i]
    return a.substringAfter("-", "").isEmpty() && b.substringAfter("-","").isNotEmpty()
  }
  private fun connection(url:String):HttpURLConnection=(URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout=9000;readTimeout=12000;instanceFollowRedirects=true
    setRequestProperty("User-Agent","PT2VHF-Speed-Alert-Android")
    setRequestProperty("Accept","application/vnd.github+json")
  }
  private fun readText(url:String):String {
    val c=connection(url)
    try {
      if(c.responseCode!=200)throw java.io.IOException("HTTP "+c.responseCode)
      return c.inputStream.bufferedReader(Charsets.UTF_8).use {it.readText()}
    } finally { c.disconnect() }
  }
  private fun reportOptIn(activity:Activity,version:String) {
    val p=AppConfig.prefs(activity)
    if(!AppConfig.shareStats(p))return
    val id=p.getString("installUuid",null) ?: UUID.randomUUID().toString().also {
      p.edit().putString("installUuid",it).apply()
    }
    val payload=JSONObject()
      .put("product","speed-alert")
      .put("install_id",id)
      .put("version",version)
      .put("platform","android")
      .put("event","update_check").toString()
    try {
      val c=connection(activityUrl)
      c.requestMethod="POST";c.doOutput=true
      c.setRequestProperty("Content-Type","application/json")
      c.outputStream.use {it.write(payload.toByteArray(Charsets.UTF_8))}
      c.responseCode
      c.disconnect()
    } catch(_:Exception) { /* non-blocking opt-in report; never interrupts GPS */ }
  }

  fun check(activity:Activity,silent:Boolean) {
    val current=currentVersion(activity)
    Thread {
      // No device location, speed, radio ID or road names are ever transmitted.
      reportOptIn(activity,current)
      try {
        val list=JSONArray(readText(releases))
        var winner:JSONObject?=null
        for(i in 0 until list.length()) {
          val item=list.optJSONObject(i)?:continue
          if(item.optBoolean("draft"))continue
          val ver=item.optString("tag_name").removePrefix("v")
          if(!newer(ver,current))continue
          if(winner==null || newer(ver,winner.optString("tag_name").removePrefix("v")))winner=item
        }
        val chosen=winner
        activity.runOnUiThread {
          if(activity.isFinishing)return@runOnUiThread
          if(chosen==null) {
            if(!silent)AlertDialog.Builder(activity).setTitle("Atualizações")
              .setMessage("Versão "+current+" instalada. Não há versão mais recente no GitHub Releases.")
              .setPositiveButton("OK",null).show()
          } else {
            val assets=chosen.optJSONArray("assets")
            var apk:JSONObject?=null
            if(assets!=null)for(j in 0 until assets.length()) {
              val a=assets.optJSONObject(j)?:continue
              if(a.optString("name").lowercase().endsWith(".apk")){apk=a;break}
            }
            if(apk==null){
              if(!silent)Toast.makeText(activity,"Release ainda sem APK instalável",Toast.LENGTH_LONG).show()
            } else {
              val appAsset=apk
              AlertDialog.Builder(activity).setTitle("Nova versão disponível")
                .setMessage("Versão "+chosen.optString("tag_name")+
                  "\nDeseja baixar o APK verificado pelo SHA-256 e abrir o instalador do Android?"+
                  "\n\nO Android exige confirmação e assinatura compatível com a versão instalada.")
                .setNegativeButton("Agora não",null)
                .setPositiveButton("Baixar") {_,_-> download(activity,appAsset!!) }
                .show()
            }
          }
        }
      } catch(_:Exception) {
        if(!silent)activity.runOnUiThread {
          if(!activity.isFinishing)Toast.makeText(activity,
            "Não foi possível consultar atualizações. O GPS continua offline.",
            Toast.LENGTH_LONG).show()
        }
      }
    }.start()
  }

  private fun download(activity:Activity,asset:JSONObject) {
    val url=asset.optString("browser_download_url")
    val sha=asset.optString("digest").removePrefix("sha256:").lowercase()
    if(!url.startsWith("https://github.com/alexpmr/speed-alert/releases/download/") ||
        !Regex("^[a-f0-9]{64}$").matches(sha)){
      Toast.makeText(activity,"Pacote sem checksum válido; download recusado",Toast.LENGTH_LONG).show()
      return
    }
    Toast.makeText(activity,"Baixando atualização em segundo plano...",Toast.LENGTH_LONG).show()
    Thread {
      val file=File(File(activity.cacheDir,"updates"),"PT2VHF-Speed-Alert-update.apk")
      try {
        file.parentFile?.mkdirs()
        val c=connection(url)
        val md=MessageDigest.getInstance("SHA-256")
        try {
          if(c.responseCode!=200)throw java.io.IOException("APK HTTP "+c.responseCode)
          c.inputStream.use {input->
            file.outputStream().use {out->
              val buffer=ByteArray(8192)
              while(true) {
                val n=input.read(buffer);if(n<0)break
                out.write(buffer,0,n)
                md.update(buffer,0,n)
              }
            }
          }
        } finally {c.disconnect()}
        val actual=md.digest().joinToString("") { "%02x".format(it) }
        if(actual!=sha)throw java.io.IOException("SHA-256 do APK não confere")
        activity.runOnUiThread {
          if(activity.isFinishing)return@runOnUiThread
          if(Build.VERSION.SDK_INT>=26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("Permitir instalação")
              .setMessage("Autorize instalação de aplicativos desta fonte nas configurações do Android e depois verifique novamente a atualização.")
              .setPositiveButton("Abrir configurações") {_,_->
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                  Uri.parse("package:"+activity.packageName)))
              }.setNegativeButton("Cancelar",null).show()
          } else {
            val uri=FileProvider.getUriForFile(activity,
              activity.packageName+".fileprovider",file)
            val intent=Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(uri,"application/vnd.android.package-archive")
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
          }
        }
      } catch(e:Exception) {
        file.delete()
        activity.runOnUiThread {
          if(!activity.isFinishing)Toast.makeText(activity,
            "Falha na atualização: "+(e.localizedMessage?:"erro"),Toast.LENGTH_LONG).show()
        }
      }
    }.start()
  }
}

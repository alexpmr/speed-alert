package com.pt2vhf.speedalert

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Official OTA provider: PT2VHF website, NOT GitHub's release API.
 * The website may redirect a counted APK download to its binary storage.
 * Driving, GPS, map matching and alarms never depend on this API.
 */
object UpdateManager {
  private const val catalogUrl =
    "https://www.pt2vhf.com/update.php?product=speed&platform=android&kind=apk"
  private const val activityUrl = "https://www.pt2vhf.com/speed_activity.php"

  private data class Package(
    val version:String,val notes:String,val url:String,val sha256:String,
    val size:Long,val filename:String
  )
  private fun currentVersion(context:Context):String=try {
    context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "0.0.0"
  } catch(_:Exception) {"0.0.0"}

  private data class V(val major:Int,val minor:Int,val patch:Int,val pre:String,val preNum:Int)
  private fun parseVersion(raw:String):V? {
    val m=Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(beta|rc)\\.(\\d+))?$",RegexOption.IGNORE_CASE)
      .matchEntire(raw)?:return null
    return V(m.groupValues[1].toIntOrNull()?:return null,
      m.groupValues[2].toIntOrNull()?:return null,
      m.groupValues[3].toIntOrNull()?:return null,
      m.groupValues[4].lowercase(),m.groupValues[5].toIntOrNull()?:0)
  }
  private fun newer(candidate:String,installed:String):Boolean {
    val a=parseVersion(candidate)?:return false
    val b=parseVersion(installed)?:return false
    val ax=listOf(a.major,a.minor,a.patch);val bx=listOf(b.major,b.minor,b.patch)
    for(i in 0..2)if(ax[i]!=bx[i])return ax[i]>bx[i]
    fun precedence(s:String)=when(s){"beta"->0;"rc"->1;else->2}
    val p=precedence(a.pre);val q=precedence(b.pre)
    return if(p==q)a.preNum>b.preNum else p>q
  }

  private fun connect(url:String):HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout=8000
      readTimeout=15000
      instanceFollowRedirects=true
      setRequestProperty("User-Agent","PT2VHF-Speed-Alert-Android")
      setRequestProperty("Accept","application/json")
      useCaches=false
    }
  private fun fetchText(url:String):String {
    val conn=connect(url)
    try {
      if(conn.responseCode!=200)throw IOException("HTTP "+conn.responseCode)
      return conn.inputStream.bufferedReader(Charsets.UTF_8).use {it.readText()}
    } finally {conn.disconnect()}
  }

  private fun reportOptIn(context:Context,version:String) {
    val p=AppConfig.prefs(context)
    if(!AppConfig.shareStats(p) || !p.getBoolean("statsNoticeAccepted",false))return
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
      val conn=connect(activityUrl)
      try {
        conn.requestMethod="POST"
        conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
        conn.outputStream.use {it.write(payload.toByteArray(Charsets.UTF_8))}
        conn.responseCode
      } finally {conn.disconnect()}
    } catch(_:Exception) {
      // Never postpone or interrupt speed monitoring due to site availability.
    }
  }

  /** Parse the site JSON, including SHA-256 and official counted APK link. */
  private fun latestAvailable():Package? {
    val doc=JSONObject(fetchText(catalogUrl))
    if(doc.optInt("schema")!=1 ||
      doc.optString("product")!="speed" ||
      doc.optString("source")!="https://www.pt2vhf.com" ||
      doc.optString("status")!="available")return null
    val version=doc.optString("version")
    if(parseVersion(version)==null)return null
    val files=doc.optJSONArray("files")?:return null
    for(i in 0 until files.length()) {
      val file=files.optJSONObject(i)?:continue
      if(file.optString("kind")!="apk" ||
         file.optString("platform")!="android")continue
      val filename=file.optString("name")
      val expected="PT2VHF-Speed-Alert-v"+version+".apk"
      if(filename!=expected)continue
      val url=file.optString("url")
      val uri=Uri.parse(url)
      if(uri.scheme!="https" || uri.host!="www.pt2vhf.com" ||
        uri.path!="/download.php" ||
        uri.getQueryParameter("product")!="speed" ||
        uri.getQueryParameter("file")!=filename)continue
      val sha=file.optString("sha256").lowercase()
      if(!Regex("^[a-f0-9]{64}$").matches(sha))continue
      val size=file.optLong("size",0)
      if(size<100000 || size>100000000)continue
      return Package(version,doc.optString("notes"),url,sha,size,filename)
    }
    return null
  }

  /** Runs only on WorkManager's network-constrained background worker thread. */
  fun checkPeriodic(context:Context):String? {
    val prefs=AppConfig.prefs(context)
    if(!AppConfig.autoUpdates(prefs) || !prefs.getBoolean("statsNoticeAccepted",false))
      return null
    val installed=currentVersion(context)
    reportOptIn(context,installed)
    val item=latestAvailable()
    return if(item!=null && newer(item.version,installed))item.version else null
  }

  fun check(activity:Activity,silent:Boolean) {
    val installed=currentVersion(activity)
    Thread {
      reportOptIn(activity.applicationContext,installed)
      try {
        val pkg=latestAvailable()
        activity.runOnUiThread {
          if(activity.isFinishing)return@runOnUiThread
          if(pkg==null || !newer(pkg.version,installed)) {
            if(!silent)AlertDialog.Builder(activity)
              .setTitle("Atualizações")
              .setMessage(if(pkg==null)
                "Nenhuma versão publicável foi encontrada no site PT2VHF."
                else "Você está usando a versão mais recente: "+installed)
              .setPositiveButton("OK",null).show()
          } else {
            AlertDialog.Builder(activity)
              .setTitle("Nova versão disponível no PT2VHF")
              .setMessage("Versão: "+pkg.version+"\n\n"+pkg.notes+
                "\n\nO arquivo será baixado pelo site e verificado por SHA-256. "+
                "O Android exigirá confirmação e uma assinatura compatível.")
              .setNegativeButton("Agora não",null)
              .setPositiveButton("Baixar") {_,_-> download(activity,pkg)}
              .show()
          }
        }
      } catch(_:Exception) {
        if(!silent)activity.runOnUiThread {
          if(!activity.isFinishing)Toast.makeText(activity,
            "Site de atualização indisponível. Os alarmes offline continuam ativos.",
            Toast.LENGTH_LONG).show()
        }
      }
    }.start()
  }

  /** Distinct SHA-256 APK signature check before invoking package installer. */
  @Suppress("DEPRECATION")
  private fun certificateDigest(activity:Activity,apk:File?):String? {
    val pm=activity.packageManager
    val flags=if(Build.VERSION.SDK_INT>=28)
      PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val info=try {
      if(apk==null)pm.getPackageInfo(activity.packageName,flags)
      else pm.getPackageArchiveInfo(apk.absolutePath,flags)
    } catch(_:Exception){null} ?: return null
    val signatures=if(Build.VERSION.SDK_INT>=28)
      info.signingInfo?.apkContentsSigners else info.signatures
    val bytes=signatures?.firstOrNull()?.toByteArray()?:return null
    return MessageDigest.getInstance("SHA-256").digest(bytes)
      .joinToString(""){"%02x".format(it)}
  }
  private fun compatible(activity:Activity,file:File):Boolean {
    val old=certificateDigest(activity,null)
    val target=certificateDigest(activity,file)
    return old!=null && target!=null && old==target
  }

  private fun download(activity:Activity,pkg:Package) {
    Toast.makeText(activity,"Baixando APK do site PT2VHF…",Toast.LENGTH_LONG).show()
    Thread {
      val target=File(File(activity.cacheDir,"updates"),pkg.filename)
      try {
        target.parentFile?.mkdirs()
        val conn=connect(pkg.url)
        val digest=MessageDigest.getInstance("SHA-256")
        var received=0L
        try {
          if(conn.responseCode!=200)throw IOException("Download HTTP "+conn.responseCode)
          conn.inputStream.use {input ->
            target.outputStream().use {output ->
              val buffer=ByteArray(16384)
              while(true) {
                val n=input.read(buffer)
                if(n<0)break
                received+=n
                if(received>pkg.size)throw IOException("APK maior que o esperado")
                output.write(buffer,0,n)
                digest.update(buffer,0,n)
              }
            }
          }
        } finally {conn.disconnect()}
        if(received!=pkg.size)throw IOException("Tamanho do APK não confere")
        val actual=digest.digest().joinToString(""){"%02x".format(it)}
        if(actual!=pkg.sha256)throw IOException("SHA-256 diferente do manifesto")
        val compatibleSignature=compatible(activity,target)
        activity.runOnUiThread {
          if(activity.isFinishing)return@runOnUiThread
          if(!compatibleSignature) {
            AlertDialog.Builder(activity)
              .setTitle("Assinatura incompatível")
              .setMessage("Este APK tem certificado diferente do aplicativo instalado. "+
                "O Android impede a atualização sem desinstalar a versão antiga. "+
                "Anote suas configurações antes de desinstalar: os dados locais serão apagados. "+
                "As versões futuras exigem chave permanente de assinatura.")
              .setPositiveButton("Entendi",null).show()
          } else if(Build.VERSION.SDK_INT>=26 &&
              !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
              .setTitle("Autorização para instalar")
              .setMessage("Permita a instalação de aplicativos desta fonte e depois "+
                "execute novamente a atualização.")
              .setNegativeButton("Cancelar",null)
              .setPositiveButton("Configurações") {_,_->
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                  Uri.parse("package:"+activity.packageName)))
              }.show()
          } else {
            val content=FileProvider.getUriForFile(activity,
              activity.packageName+".fileprovider",target)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(content,"application/vnd.android.package-archive")
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
          }
        }
      } catch(e:Exception) {
        target.delete()
        activity.runOnUiThread {
          if(!activity.isFinishing)Toast.makeText(activity,
            "Falha ao baixar atualização: "+(e.localizedMessage?:"desconhecida"),
            Toast.LENGTH_LONG).show()
        }
      }
    }.start()
  }
}

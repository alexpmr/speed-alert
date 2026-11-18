package com.pt2vhf.speedalert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Unique 24-hour background schedule; never needed for GNSS or the offline speed alarm. */
object UpdateScheduler {
  private const val NAME="speed-alert-periodic-github-check"
  fun sync(context:Context) {
    val app=context.applicationContext
    val prefs=AppConfig.prefs(app)
    val manager=WorkManager.getInstance(app)
    if(!AppConfig.autoUpdates(prefs) || !prefs.getBoolean("statsNoticeAccepted",false)) {
      manager.cancelUniqueWork(NAME)
      return
    }
    val request=PeriodicWorkRequestBuilder<PeriodicUpdatesWorker>(24,TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    manager.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.KEEP,request)
  }
}

class PeriodicUpdatesWorker(context:Context, params:WorkerParameters):Worker(context,params) {
  override fun doWork():Result {
    val prefs=AppConfig.prefs(applicationContext)
    if(!AppConfig.autoUpdates(prefs) || !prefs.getBoolean("statsNoticeAccepted",false))
      return Result.success()
    return try {
      val newer=UpdateManager.checkPeriodic(applicationContext)
      if(newer!=null && prefs.getString("lastUpdateNotification",null)!=newer) {
        showNotification(newer)
        prefs.edit().putString("lastUpdateNotification",newer).apply()
      }
      Result.success()
    } catch (_:java.io.IOException) {
      Result.retry()
    } catch (_:Exception) {
      Result.retry()
    }
  }

  private fun showNotification(version:String) {
    if(Build.VERSION.SDK_INT>=33 &&
      applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        !=PackageManager.PERMISSION_GRANTED)return
    val mgr=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.createNotificationChannel(NotificationChannel(
      "speed_updates","Atualizações PT2VHF Speed Alert",NotificationManager.IMPORTANCE_DEFAULT))
    val intent=Intent(applicationContext,SettingsActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val pending=PendingIntent.getActivity(
      applicationContext,304,intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=Notification.Builder(applicationContext,"speed_updates")
      .setSmallIcon(R.drawable.ic_speed_alert)
      .setContentTitle("PT2VHF Speed Alert: nova versão")
      .setContentText("v$version disponível. Abra Configurações para atualizar.")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .build()
    mgr.notify(304,notification)
  }
}

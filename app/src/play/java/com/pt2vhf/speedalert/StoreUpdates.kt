package com.pt2vhf.speedalert

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import java.lang.ref.WeakReference
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play in-app updates (flexible): Play downloads the new version in the background
 * while monitoring continues; the app only restarts after the user confirms, and the
 * restart prompt is shown only while the vehicle is stopped.
 */
object StoreUpdates {
  private const val REQUEST=7301
  private const val PROMPT_INTERVAL_MS=24L*60*60*1000
  private var manager:AppUpdateManager?=null
  private var listener:InstallStateUpdatedListener?=null
  private var restartDialog:AlertDialog?=null
  private var current=WeakReference<Activity>(null)

  private fun stopped():Boolean {
    val speed=MonitorService.latest?.speed ?: -1.0
    return speed<5.0   // unknown speed (-1) also counts as stopped
  }

  fun onResume(activity:Activity) {
    val app=activity.applicationContext
    current=WeakReference(activity)
    val m=manager ?: AppUpdateManagerFactory.create(app).also { manager=it }
    if(listener==null) {
      listener=InstallStateUpdatedListener { state ->
        if(state.installStatus()==InstallStatus.DOWNLOADED)
          current.get()?.let { offerRestart(it,m) }
      }.also { m.registerListener(it) }
    }
    m.appUpdateInfo.addOnSuccessListener { info ->
      if(activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
      if(info.installStatus()==InstallStatus.DOWNLOADED) {
        offerRestart(activity,m)
        return@addOnSuccessListener
      }
      if(info.updateAvailability()!=UpdateAvailability.UPDATE_AVAILABLE ||
         !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return@addOnSuccessListener
      val prefs=AppConfig.prefs(app)
      val key="storeUpdatePrompt_"+info.availableVersionCode()
      val last=prefs.getLong(key,0L)
      if(System.currentTimeMillis()-last<PROMPT_INTERVAL_MS) return@addOnSuccessListener
      prefs.edit().putLong(key,System.currentTimeMillis()).apply()
      try {
        m.startUpdateFlowForResult(info,activity,
          AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),REQUEST)
      } catch(e:Exception) { Log.w("PT2VHF.Update","Play update flow failed",e) }
    }.addOnFailureListener { Log.d("PT2VHF.Update","Play update check unavailable",it) }
  }

  private fun offerRestart(activity:Activity,m:AppUpdateManager) {
    if(activity.isFinishing || activity.isDestroyed || restartDialog?.isShowing==true) return
    if(!stopped()) return   // asked again on the next resume, never while driving
    restartDialog=AlertDialog.Builder(activity)
      .setTitle("Atualização pronta")
      .setMessage("A nova versão foi baixada pelo Google Play. Reiniciar o app agora para concluir? "+
        "O monitoramento será retomado após o aviso inicial.")
      .setNegativeButton("Depois",null)
      .setPositiveButton("Reiniciar") { _,_ -> m.completeUpdate() }
      .show()
  }
}

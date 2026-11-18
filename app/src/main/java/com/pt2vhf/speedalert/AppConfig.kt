package com.pt2vhf.speedalert
import android.content.Context
import android.content.SharedPreferences

object AppConfig {
  const val PRIVACY_URL = "https://alexpmr.github.io/speed-alert/privacidade.html"
  fun prefs(c: Context) = c.getSharedPreferences("config", Context.MODE_PRIVATE)
  fun auto(p: SharedPreferences) = p.getBoolean("autoStart",true)
  fun screen(p: SharedPreferences) = p.getBoolean("keepScreenOn",true)
  fun offline(p: SharedPreferences) = p.getBoolean("offline",true)
  fun manual(p: SharedPreferences) = p.getBoolean("manual",false)
  fun manualLimit(p: SharedPreferences) = p.getInt("manualLimit",60).coerceIn(10,160)
  fun tolerance(p: SharedPreferences) = p.getInt("tolerance",0).coerceIn(0,20)
  fun volume(p: SharedPreferences) = p.getInt("volume",80).coerceIn(0,100)
  fun beepMs(p: SharedPreferences) = p.getInt("beepMs",250).coerceIn(100,1000)
  fun pauseMs(p: SharedPreferences) = p.getInt("pauseMs",350).coerceIn(100,2000)
  fun mode(p: SharedPreferences) = p.getString("soundMode","BIPES") ?: "BIPES"
  fun warnUnknown(p: SharedPreferences) = p.getBoolean("warnUnknown",true)
  fun muted(p: SharedPreferences) = p.getBoolean("muted",false)
  fun shareStats(p: SharedPreferences) = p.getBoolean("shareStats",true)
  fun radarRange(p: SharedPreferences) = p.getInt("radarRange",300).coerceIn(100,1000)
  fun autoUpdates(p: SharedPreferences) = p.getBoolean("autoUpdates",true)
  fun radarChime(p: SharedPreferences) = p.getBoolean("radarChime",true)
  fun showCoords(p: SharedPreferences) = p.getBoolean("showCoords",true)
  fun tilesWifiOnly(p: SharedPreferences) = p.getBoolean("tilesWifiOnly",false)
  fun tileLimitMb(p: SharedPreferences) = p.getInt("tileLimitMb",500).coerceIn(100,4000)
}

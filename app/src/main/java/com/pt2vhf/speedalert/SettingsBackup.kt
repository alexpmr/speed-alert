package com.pt2vhf.speedalert

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Human-readable Settings backup for one-time migration away from historical debug keys.
 * Does not export pseudonymous installation identifiers, GPS, history or consent state.
 */
object SettingsBackup {
    private val booleans = listOf("keepScreenOn","offline","warnUnknown","manual",
        "autoUpdates","shareStats","muted","radarChime","showCoords","tilesWifiOnly")
    private val ranges = mapOf(
        "tolerance" to (0..20), "radarRange" to (100..1000),
        "volume" to (0..100), "manualLimit" to (10..160),
        "beepMs" to (100..1000), "pauseMs" to (100..2000), "tileLimitMb" to (100..4000)
    )
    private val soundModes = setOf("BIPES","DUPLO","TRIPLO","SIRENE","SUAVE","CONTINUO")

    fun export(prefs:SharedPreferences):String {
        val data=JSONObject().put("schema",1).put("product","speed-alert")
        val values=JSONObject()
        val snapshot=prefs.all
        booleans.forEach { key ->
            (snapshot[key] as? Boolean)?.let { values.put(key,it) }
        }
        ranges.keys.forEach { key ->
            (snapshot[key] as? Int)?.let { values.put(key,it) }
        }
        (snapshot["soundMode"] as? String)?.takeIf {it in soundModes}
            ?.let {values.put("soundMode",it)}
        data.put("settings",values)
        return data.toString(2)
    }

    fun import(prefs:SharedPreferences,json:String):Int {
        require(json.length<=16384){"Arquivo de configurações muito grande"}
        val root=JSONObject(json)
        require(root.optInt("schema")==1 && root.optString("product")=="speed-alert") {
            "Formato de arquivo incompatível"
        }
        val values=root.optJSONObject("settings") ?: error("Configurações ausentes")
        val edit=prefs.edit()
        var imported=0
        for(key in booleans) {
            if(!values.has(key))continue
            val value=values.opt(key)
            require(value is Boolean){"Valor inválido para $key"}
            edit.putBoolean(key,value)
            imported++
        }
        for((key,range) in ranges) {
            if(!values.has(key))continue
            val value=values.opt(key)
            require(value is Int && value in range){"Valor inválido para $key"}
            edit.putInt(key,value)
            imported++
        }
        if(values.has("soundMode")) {
            val sound=values.optString("soundMode")
            require(sound in soundModes){"Tipo de alarme desconhecido"}
            edit.putString("soundMode",sound)
            imported++
        }
        require(imported>0){"Nenhuma configuração válida"}
        check(edit.commit()){"Não foi possível salvar as configurações"}
        return imported
    }
}

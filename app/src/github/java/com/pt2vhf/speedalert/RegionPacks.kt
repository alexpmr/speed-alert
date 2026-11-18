package com.pt2vhf.speedalert

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import java.io.File

/** Site/GitHub APK: ships the DF database as an APK asset; no downloadable regions yet. */
object RegionPacks {
  fun installedFiles(context:Context):List<Pair<String,File>> = emptyList()
  /** Returns a cleanup callback for Activity.onDestroy. */
  fun addSettingsSection(activity:Activity,root:LinearLayout,
      title:(String)->Unit,note:(String)->Unit):()->Unit {
    note("Cobertura desta versão: DF e entorno. As regiões Centro-Oeste, Sul e Sudeste estão disponíveis na versão do Google Play.")
    return {}
  }
}

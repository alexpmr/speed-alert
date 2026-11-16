package com.pt2vhf.speedalert

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color

/** Required explicit opt-in every time the app is opened. */
object StartupDisclaimer {
  fun show(activity: Activity, onContinue: () -> Unit) {
    val message = (
      "O PT2VHF Speed Alert é uma ferramenta auxiliar e experimental.\n\n"+
      "A velocidade indicada pelo GPS e os limites de velocidade da base offline "+
      "podem estar incorretos, desatualizados, incompletos ou indisponíveis.\n\n"+
      "O aplicativo e seus desenvolvedores não assumem responsabilidade por multas, "+
      "acidentes, perdas ou danos decorrentes de seu uso ou de informações imprecisas, "+
      "observados os limites da legislação aplicável.\n\n"+
      "A sinalização oficial e as normas de trânsito prevalecem sempre. "+
      "Mantenha a atenção na direção; não manuseie o celular enquanto dirige.\n\n"+
      "Deseja continuar?"
    )
    val dlg = AlertDialog.Builder(activity)
      .setTitle("AVISO IMPORTANTE")
      .setMessage(message)
      .setCancelable(false)
      .setPositiveButton("CONTINUAR") { _,_ -> onContinue() }
      .setNegativeButton("FECHAR") { _,_ ->
        if (MonitorService.latest != null) {
          activity.startService(Intent(activity,MonitorService::class.java)
            .setAction(MonitorService.STOP))
        }
        activity.finish()
      }
      .create()
    dlg.setOnShowListener {
      dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        .setTextColor(Color.rgb(24,125,70))
      dlg.getButton(AlertDialog.BUTTON_NEGATIVE)
        .setTextColor(Color.rgb(170,45,45))
    }
    dlg.show()
  }
}

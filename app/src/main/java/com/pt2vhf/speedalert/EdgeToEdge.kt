package com.pt2vhf.speedalert

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15+ (targetSdk 35+) always draws apps edge-to-edge and ignores bar colors.
 * Do the same on every version and pad [root] by the system bars, cutout and keyboard,
 * so content never sits under the status/navigation bars.
 */
object EdgeToEdge {
  fun apply(activity:Activity, root:View) {
    WindowCompat.setDecorFitsSystemWindows(activity.window,false)
    if(Build.VERSION.SDK_INT<35) {
      @Suppress("DEPRECATION")
      activity.window.statusBarColor=Color.TRANSPARENT
      @Suppress("DEPRECATION")
      activity.window.navigationBarColor=Color.TRANSPARENT
    }
    WindowCompat.getInsetsController(activity.window,root).apply {
      isAppearanceLightStatusBars=false
      isAppearanceLightNavigationBars=false
    }
    val l=root.paddingLeft; val t=root.paddingTop; val r=root.paddingRight; val b=root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
      val bars=insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
      v.setPadding(l+bars.left,t+bars.top,r+bars.right,b+maxOf(bars.bottom,ime.bottom))
      WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(root)
  }
}

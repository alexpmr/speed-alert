package com.pt2vhf.speedalert

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** PT2VHF branded splash; defers navigation to disclaimer screen. */
class SplashActivity : Activity() {
  private val handler=Handler(Looper.getMainLooper())
  private val open=Runnable {
    if (!isFinishing && !isDestroyed) {
      startActivity(Intent(this,MainActivity::class.java))
      finish()
    }
  }
  private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  override fun onCreate(state:Bundle?) {
    super.onCreate(state)
    val navy=Color.rgb(12,20,29)
    window.statusBarColor=navy
    window.navigationBarColor=navy
    val root=LinearLayout(this).apply {
      orientation=LinearLayout.VERTICAL
      gravity=Gravity.CENTER
      setBackgroundColor(navy)
      setPadding(dp(24),dp(24),dp(24),dp(24))
    }
    root.addView(ImageView(this).apply {
      setImageResource(R.drawable.ic_speed_alert)
      contentDescription="PT2VHF Speed Alert"
    },LinearLayout.LayoutParams(dp(212),dp(212)))
    root.addView(TextView(this).apply {
      text="PT2VHF"
      textSize=32f
      gravity=Gravity.CENTER
      setTextColor(Color.WHITE)
      setPadding(0,dp(10),0,0)
    })
    root.addView(TextView(this).apply {
      text="SPEED ALERT"
      textSize=21f
      gravity=Gravity.CENTER
      setTextColor(Color.rgb(94,214,156))
    })
    root.addView(TextView(this).apply {
      text="Powered by www.PT2VHF.com"
      textSize=12f
      gravity=Gravity.CENTER
      setTextColor(Color.LTGRAY)
      setPadding(0,dp(35),0,0)
    })
    setContentView(root)
    handler.postDelayed(open,1000)
  }
  override fun onDestroy() { handler.removeCallbacks(open);super.onDestroy() }
}

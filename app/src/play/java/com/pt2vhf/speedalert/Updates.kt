package com.pt2vhf.speedalert

import android.app.Activity
import android.content.Context

/** Google Play build: updates come only from Play, and nothing is sent over the network. */
object UpdateScheduler {
  fun sync(context:Context) {}
}

object UpdateManager {
  fun check(activity:Activity,silent:Boolean) {}
}

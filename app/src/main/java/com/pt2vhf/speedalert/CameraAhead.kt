package com.pt2vhf.speedalert

import kotlin.math.*

/**
 * Picks the speed camera the driver is heading to. OSM splits roads into short ways (median ~80 m),
 * so cameras on the next way must count too; those get a narrow corridor to skip parallel streets.
 */
object CameraAhead {
  data class Camera(val lat:Double,val lon:Double,val speed:Double?,val sameWay:Boolean)

  /** Nearest camera ahead and its distance in metres, or null. */
  fun nearest(lat:Double,lon:Double,bearing:Double,cams:List<Camera>,radius:Double=1500.0):Pair<Camera,Double>? {
    val factor=111320.0*cos(Math.toRadians(lat))
    val b=Math.toRadians(bearing)
    var best:Pair<Camera,Double>?=null
    for(c in cams) {
      val east=(c.lon-lon)*factor
      val north=(c.lat-lat)*111320.0
      val dist=hypot(east,north)
      if(dist>radius || dist<8.0)continue
      val off=abs(((Math.toDegrees(atan2(east,north))-bearing)+540.0)%360.0-180.0)
      val ok=if(c.sameWay) off<=45.0
        else off<=20.0 && abs(east*cos(b)-north*sin(b))<=40.0  // sideways distance from the travel line
      if(ok && dist<(best?.second ?: Double.POSITIVE_INFINITY))best=c to dist
    }
    return best
  }
}

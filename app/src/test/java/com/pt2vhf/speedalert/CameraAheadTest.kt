package com.pt2vhf.speedalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraAheadTest {
  // Driver in Brasília heading due north (bearing 0).
  private val lat=-15.8
  private val lon=-47.8
  private val mPerDegLat=111320.0
  private val mPerDegLon=111320.0*Math.cos(Math.toRadians(lat))
  private fun cam(northM:Double,eastM:Double,sameWay:Boolean,speed:Double?=60.0)=
    CameraAhead.Camera(lat+northM/mPerDegLat,lon+eastM/mPerDegLon,speed,sameWay)

  @Test fun cameraOnNextWayStraightAheadIsFound() {
    val hit=CameraAhead.nearest(lat,lon,0.0,listOf(cam(250.0,5.0,sameWay=false)))!!
    assertEquals(250.0,hit.second,2.0)
  }

  @Test fun parallelStreetCameraIsIgnored() {
    // 300 m ahead but 80 m to the side: another road, not ours.
    assertNull(CameraAhead.nearest(lat,lon,0.0,listOf(cam(300.0,80.0,sameWay=false))))
  }

  @Test fun cameraBehindIsIgnored() {
    assertNull(CameraAhead.nearest(lat,lon,0.0,listOf(cam(-200.0,0.0,sameWay=false))))
  }

  @Test fun sameWayKeepsTheWider45DegreeCone() {
    // Curve: 35° off the heading. Accepted on our own way, rejected on another way.
    val n=200.0*Math.cos(Math.toRadians(35.0)); val e=200.0*Math.sin(Math.toRadians(35.0))
    assertEquals(200.0,CameraAhead.nearest(lat,lon,0.0,listOf(cam(n,e,sameWay=true)))!!.second,2.0)
    assertNull(CameraAhead.nearest(lat,lon,0.0,listOf(cam(n,e,sameWay=false))))
  }

  @Test fun nearestOfSeveralWins() {
    val hit=CameraAhead.nearest(lat,lon,0.0,listOf(cam(900.0,0.0,false,80.0),cam(400.0,10.0,false,60.0)))!!
    assertEquals(60.0,hit.first.speed!!,0.0)
  }

  @Test fun beyondRadiusOrRightOnTopIsIgnored() {
    assertNull(CameraAhead.nearest(lat,lon,0.0,listOf(cam(1600.0,0.0,true),cam(5.0,0.0,true))))
  }
}

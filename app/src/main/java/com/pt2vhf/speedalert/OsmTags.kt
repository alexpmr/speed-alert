package com.pt2vhf.speedalert

import kotlin.math.roundToLong

/** OSM tag rules shared with tools/build_osm_v3.py: only explicit numeric limits, no inference. */
object OsmTags {
  val HIGHWAYS=setOf("motorway","trunk","primary","secondary","tertiary","residential",
    "unclassified","living_street","service","motorway_link","trunk_link",
    "primary_link","secondary_link","tertiary_link")
  private val SPEED=Regex("(\\d{1,3})\\s*(km/h|kmh|kph|mph)?")

  /** "60", "60 km/h", "40 mph" -> km/h; null for "none", "signals", "DE:urban" or outside 10–160. */
  fun kmh(text:String?):Double? {
    val m=SPEED.matchEntire(text?.lowercase()?.trim()?:return null)?:return null
    val v=m.groupValues[1].toInt()
    if(v<10 || v>160)return null
    return if(m.groupValues[2]=="mph") (v*1.609344*100).roundToLong()/100.0 else v.toDouble()
  }
  /** Minor roads are kept only with an explicit limit ("slim" profile, as the regional tiles). */
  val MINOR=setOf("residential","unclassified","living_street","service")

  /** Forward and backward limit of a way, or null when the way should be skipped. */
  fun limits(tags:Map<String,String>):Pair<Double?,Double?>? {
    val highway=tags["highway"]
    if(highway !in HIGHWAYS)return null
    val (fwd,bwd)=if(tags["maxspeed:conditional"]!=null) null to null else {
      val shared=kmh(tags["maxspeed"])
      (kmh(tags["maxspeed:forward"])?:shared) to (kmh(tags["maxspeed:backward"])?:shared)
    }
    val limited=fwd!=null || bwd!=null
    val named=!tags["name"].isNullOrBlank() || !tags["ref"].isNullOrBlank()
    if(!limited && (!named || highway in MINOR))return null
    return fwd to bwd
  }
}

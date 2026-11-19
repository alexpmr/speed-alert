package com.pt2vhf.speedalert

import org.junit.Assert.*
import org.junit.Test

class OsmTagsTest {
  @Test fun parsesExplicitLimits() {
    assertEquals(60.0,OsmTags.kmh("60")!!,0.0)
    assertEquals(80.0,OsmTags.kmh(" 80 km/h ")!!,0.0)
    assertEquals(64.37,OsmTags.kmh("40 mph")!!,0.001)
  }
  @Test fun ignoresImplicitOrOutOfRange() {
    listOf(null,"none","signals","DE:urban","5","200","60;80").forEach { assertNull(it,OsmTags.kmh(it)) }
  }
  @Test fun directionalLimitsOverrideShared() {
    val t=mapOf("highway" to "primary","maxspeed" to "60","maxspeed:backward" to "40")
    assertEquals(60.0 to 40.0,OsmTags.limits(t))
  }
  @Test fun conditionalLimitKeepsNamedMajorRoadWithoutSpeed() {
    val t=mapOf("highway" to "primary","maxspeed" to "60","maxspeed:conditional" to "30 @ (Mo-Fr)","name" to "Av. A")
    assertEquals(null to null,OsmTags.limits(t))
  }
  @Test fun slimProfileSkipsMinorRoadsWithoutSpeed() {
    assertNull(OsmTags.limits(mapOf("highway" to "footway","maxspeed" to "10")))
    assertNull(OsmTags.limits(mapOf("highway" to "primary")))
    assertNull(OsmTags.limits(mapOf("highway" to "residential","name" to "Rua B")))
    assertEquals(30.0 to 30.0,OsmTags.limits(mapOf("highway" to "residential","maxspeed" to "30")))
    assertNotNull(OsmTags.limits(mapOf("highway" to "secondary","ref" to "DF-001")))
  }
}

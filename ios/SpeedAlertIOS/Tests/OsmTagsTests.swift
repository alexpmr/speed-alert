import XCTest
@testable import PT2VHFSpeedAlert

/// Same cases as Android OsmTagsTest.kt, so both platforms read OSM tags identically.
final class OsmTagsTests: XCTestCase {
    func testParsesExplicitLimits() {
        XCTAssertEqual(OsmTags.kmh("60"), 60)
        XCTAssertEqual(OsmTags.kmh(" 80 km/h "), 80)
        XCTAssertEqual(OsmTags.kmh("40 mph")!, 64.37, accuracy: 0.001)
    }
    func testIgnoresImplicitOrOutOfRange() {
        for t in [nil, "none", "signals", "DE:urban", "5", "200", "60;80"] {
            XCTAssertNil(OsmTags.kmh(t), String(describing: t))
        }
    }
    func testDirectionalLimitsOverrideShared() {
        let l = OsmTags.limits(["highway": "primary", "maxspeed": "60", "maxspeed:backward": "40"])
        XCTAssertEqual(l?.fwd, 60)
        XCTAssertEqual(l?.bwd, 40)
    }
    func testConditionalLimitKeepsNamedMajorRoadWithoutSpeed() {
        let l = OsmTags.limits(["highway": "primary", "maxspeed": "60",
                                "maxspeed:conditional": "30 @ (Mo-Fr)", "name": "Av. A"])
        XCTAssertNotNil(l)
        XCTAssertNil(l?.fwd)
        XCTAssertNil(l?.bwd)
    }
    func testSlimProfileSkipsMinorRoadsWithoutSpeed() {
        XCTAssertNil(OsmTags.limits(["highway": "footway", "maxspeed": "10"]))
        XCTAssertNil(OsmTags.limits(["highway": "primary"]))
        XCTAssertNil(OsmTags.limits(["highway": "residential", "name": "Rua B"]))
        let r = OsmTags.limits(["highway": "residential", "maxspeed": "30"])
        XCTAssertEqual(r?.fwd, 30)
        XCTAssertEqual(r?.bwd, 30)
        XCTAssertNotNil(OsmTags.limits(["highway": "secondary", "ref": "DF-001"]))
    }
}

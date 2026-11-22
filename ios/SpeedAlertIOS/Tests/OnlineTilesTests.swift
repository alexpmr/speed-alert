import XCTest
import CoreLocation
@testable import PT2VHFSpeedAlert

/// Overpass answer -> v3 database -> road lookup, without network.
final class OnlineTilesTests: XCTestCase {
    private var file: URL!
    override func setUp() {
        file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".sqlite")
    }
    override func tearDown() { try? FileManager.default.removeItem(at: file) }

    // A north-south avenue at lon -47.85 (60 km/h northbound, 40 southbound) and an unnamed
    // residential street without a limit, which the slim profile must drop.
    private let json = """
    {"remark":null,"elements":[
     {"type":"way","id":11,"tags":{"highway":"primary","name":"Eixo Teste","maxspeed":"60","maxspeed:backward":"40"},
      "geometry":[{"lat":-15.80,"lon":-47.85},null,{"lat":-15.79,"lon":-47.85}]},
     {"type":"way","id":12,"tags":{"highway":"residential"},
      "geometry":[{"lat":-15.80,"lon":-47.849},{"lat":-15.79,"lon":-47.849}]}
    ]}
    """

    private func fix(course: Double) -> CLLocation {
        CLLocation(coordinate: CLLocationCoordinate2D(latitude: -15.795, longitude: -47.85), altitude: 0,
                   horizontalAccuracy: 5, verticalAccuracy: 5, course: course, courseAccuracy: 5,
                   speed: 15, speedAccuracy: 1, timestamp: Date())
    }

    func testWritesDatabaseThatRoadsCanRead() throws {
        try OnlineTiles.write(Data(json.utf8), to: file, la: -158, lo: -479)
        let roads = OfflineRoads(files: [(file!, false)])
        let north = try XCTUnwrap(roads.lookup(fix(course: 0)))
        XCTAssertEqual(north.speed, 60)
        XCTAssertEqual(north.name, "Eixo Teste")
        XCTAssertEqual(north.source, "OSM • OpenStreetMap")
        XCTAssertEqual(roads.lookup(fix(course: 180))?.speed, 40)
        XCTAssertFalse(roads.bundledCovers(-15.795, -47.85))
    }
    func testOutsideTheCellThereIsNoSource() throws {
        try OnlineTiles.write(Data(json.utf8), to: file, la: -158, lo: -479)
        let roads = OfflineRoads(files: [(file!, false)])
        let far = CLLocation(latitude: 38.72, longitude: -9.14)
        XCTAssertNil(roads.lookup(far))
    }
    func testOverpassErrorRemarkIsReported() {
        let bad = #"{"remark":"runtime error: Query timed out","elements":[]}"#
        XCTAssertThrowsError(try OnlineTiles.write(Data(bad.utf8), to: file, la: 0, lo: 0))
    }
    func testOnlyNearbyNeighbourCellsAreFetched() {
        XCTAssertEqual(OnlineTiles.around(-15.85, -47.85).count, 1)          // centre of a cell
        XCTAssertEqual(OnlineTiles.key(-15.85, -47.85), "o-159_-479")
        XCTAssertEqual(OnlineTiles.around(-15.801, -47.85).count, 2)         // ~100 m from the edge
        XCTAssertTrue(OnlineTiles.query(-159, -479).contains("(-15.9,-47.9,-15.8,-47.8)"))
    }
}

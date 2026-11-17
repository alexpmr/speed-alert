import Foundation
import CoreLocation
import SQLite3

struct RoadMatch {
    var speed: Double?
    var name: String = "Via não identificada"
    var source: String = "LIMITE NÃO CADASTRADO"
    var cameraMeters: Double?
    var cameraLimit: Double?
}

final class OfflineRoads {
    private var db: OpaquePointer?
    private(set) var ready = false

    init() {
        guard let path = Bundle.main.path(forResource: "df_roads_v3", ofType: "sqlite") else {
            return // Simulator/local builds without the offline bundle remain honest.
        }
        guard sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY, nil) == SQLITE_OK else {
            sqlite3_close(db); db = nil
            return
        }
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, "PRAGMA user_version", -1, &statement, nil) == SQLITE_OK {
            ready = sqlite3_step(statement) == SQLITE_ROW &&
                sqlite3_column_int(statement, 0) == 3
        }
        sqlite3_finalize(statement)
    }
    deinit { sqlite3_close(db) }

    private struct Candidate {
        let score: Double
        let speed: Double?
        let name: String
        let wayID: Int64
    }

    private func diff(_ a: Double, _ b: Double) -> Double {
        abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180)
    }
    private func text(_ statement: OpaquePointer?, _ col: Int32) -> String {
        guard let ptr = sqlite3_column_text(statement, col) else { return "" }
        return String(cString: ptr)
    }
    private func double(_ s: OpaquePointer?, _ col: Int32) -> Double? {
        sqlite3_column_type(s, col) == SQLITE_NULL ? nil : sqlite3_column_double(s, col)
    }

    func lookup(_ position: CLLocation, radarRange: Double = 300) -> RoadMatch {
        guard ready, let db else {
            return RoadMatch(source: "BASE OFFLINE INDISPONÍVEL")
        }
        guard position.course >= 0 else {
            return RoadMatch(source: "AGUARDANDO DIREÇÃO GPS")
        }
        let lat = position.coordinate.latitude
        let lon = position.coordinate.longitude
        let tlat = Int32(floor(lat * 1000))
        let tlon = Int32(floor(lon * 1000))
        let factor = 111320 * cos(lat * .pi / 180)
        var stmt: OpaquePointer?
        let sql = """
          SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.fwd,r.bwd,r.name,r.ref,r.wayid
          FROM road_tiles t JOIN roads r ON t.road_id=r.id
          WHERE t.tile_lat BETWEEN ? AND ? AND t.tile_lon BETWEEN ? AND ?
          GROUP BY r.id LIMIT 1200
          """
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            sqlite3_finalize(stmt)
            return RoadMatch(source: "ERRO NA CONSULTA OFFLINE")
        }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int(stmt, 1, tlat - 1)
        sqlite3_bind_int(stmt, 2, tlat + 1)
        sqlite3_bind_int(stmt, 3, tlon - 1)
        sqlite3_bind_int(stmt, 4, tlon + 1)
        var matches: [Candidate] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            let x1 = (sqlite3_column_double(stmt, 1)-lon)*factor
            let y1 = (sqlite3_column_double(stmt, 0)-lat)*111320
            let x2 = (sqlite3_column_double(stmt, 3)-lon)*factor
            let y2 = (sqlite3_column_double(stmt, 2)-lat)*111320
            let dx = x2 - x1, dy = y2 - y1
            let sq = dx*dx + dy*dy
            if sq < 1 { continue }
            let t = max(0, min(1, -(x1*dx + y1*dy)/sq))
            let distance = hypot(x1 + t*dx, y1 + t*dy)
            if distance > 27 { continue }
            let bearing = atan2(dx, dy) * 180 / .pi + 360
            let forward = diff(position.course, bearing.truncatingRemainder(dividingBy:360))
            let backward = diff(position.course, (bearing+180).truncatingRemainder(dividingBy:360))
            let heading = min(forward, backward)
            if heading > 45 { continue }
            let speed = double(stmt, forward <= backward ? 4 : 5)
            let name = text(stmt, 6).isEmpty ? text(stmt, 7) : text(stmt, 6)
            matches.append(Candidate(score: distance + heading*0.3,
                speed: speed, name: name, wayID: sqlite3_column_int64(stmt, 8)))
        }
        matches.sort { $0.score < $1.score }
        guard let best = matches.first else { return RoadMatch(source:"VIA NÃO IDENTIFICADA") }
        if matches.dropFirst().contains(where: {
            $0.score - best.score < 7 && $0.wayID != best.wayID &&
            ($0.speed != best.speed || (!$0.name.isEmpty && !best.name.isEmpty && $0.name != best.name))
        }) { return RoadMatch(source: "VIA AMBÍGUA") }
        var result = RoadMatch(speed:best.speed,
            name:best.name.isEmpty ? "Via não identificada" : best.name,
            source:"OSM OFFLINE v3")
        let radius = 1500.0
        let latTiles = Int32((radius/111320*1000).rounded(.up))+2
        let lonTiles = Int32((radius/(111320*max(0.4,cos(lat * .pi/180)))*1000).rounded(.up))+2
        var cam: OpaquePointer?
        let cameraSQL = """
          SELECT lat,lon,speed FROM cameras
          WHERE wayid=? AND tile_lat BETWEEN ? AND ? AND tile_lon BETWEEN ? AND ?
          """
        guard sqlite3_prepare_v2(db, cameraSQL, -1, &cam, nil) == SQLITE_OK else {
            sqlite3_finalize(cam); return result
        }
        defer { sqlite3_finalize(cam) }
        sqlite3_bind_int64(cam, 1, best.wayID)
        sqlite3_bind_int(cam, 2, tlat-latTiles)
        sqlite3_bind_int(cam, 3, tlat+latTiles)
        sqlite3_bind_int(cam, 4, tlon-lonTiles)
        sqlite3_bind_int(cam, 5, tlon+lonTiles)
        while sqlite3_step(cam) == SQLITE_ROW {
            let east = (sqlite3_column_double(cam,1)-lon)*factor
            let north = (sqlite3_column_double(cam,0)-lat)*111320
            let distance = hypot(east,north)
            if distance < 8 || distance > radius { continue }
            let bearing = (atan2(east,north)*180 / .pi+360).truncatingRemainder(dividingBy:360)
            if diff(position.course,bearing)>45 { continue }
            guard let limit = double(cam,2) ?? best.speed else { continue }
            if distance < (result.cameraMeters ?? .infinity) {
                result.cameraMeters = distance
                result.cameraLimit = limit
            }
        }
        return result
    }
}

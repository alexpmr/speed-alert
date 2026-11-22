import Foundation
import CoreLocation
import SQLite3

struct RoadMatch {
    var speed: Double?
    var name: String = "Via não identificada"
    var source: String = "LIMITE NÃO CADASTRADO"
}

/// Offline map matching (port of Android Roads.kt) over every v3 database present:
/// the bundled DF database and the OpenStreetMap cells downloaded by OnlineTiles.
final class OfflineRoads {
    /// One opened database and the area it covers (nil bbox = unknown, always queried).
    private struct Source {
        let db: OpaquePointer
        let label: String
        let bundled: Bool
        let south, north, west, east: Double?
        func covers(_ lat: Double, _ lon: Double) -> Bool {
            guard let south, let north, let west, let east else { return true }
            let m = 0.05
            return lat >= south - m && lat <= north + m && lon >= west - m && lon <= east + m
        }
    }
    private var sources: [Source] = []

    /// Bundled DF database plus downloaded cells; tests pass their own files.
    convenience init() {
        var files: [(URL, Bool)] = []
        if let df = Bundle.main.url(forResource: "df_roads_v3", withExtension: "sqlite") { files.append((df, true)) }
        files += OnlineTiles.files().map { ($0, false) }
        self.init(files: files)
    }
    init(files: [(URL, Bool)]) {
        for (url, bundled) in files {
            var db: OpaquePointer?
            guard sqlite3_open_v2(url.path, &db, SQLITE_OPEN_READONLY, nil) == SQLITE_OK, let db else {
                sqlite3_close(db); continue
            }
            guard intValue(db, "PRAGMA user_version") == 3 else { sqlite3_close(db); continue }
            var meta: [String: String] = [:]
            var s: OpaquePointer?
            if sqlite3_prepare_v2(db, "SELECT k,v FROM metadata", -1, &s, nil) == SQLITE_OK {
                while sqlite3_step(s) == SQLITE_ROW { meta[text(s, 0)] = text(s, 1) }
            }
            sqlite3_finalize(s)
            // A bundled database without bbox metadata would "cover" the whole world and stop
            // downloads everywhere: fall back to the DF area of tools/build_osm_v3.py.
            let fallback: [String: Double] = bundled ? ["south": -16.30, "north": -15.35, "west": -48.43, "east": -47.18] : [:]
            func edge(_ k: String) -> Double? { Double(meta["bbox_" + k] ?? "") ?? fallback[k] }
            sources.append(Source(db: db, label: meta["region"] == "online" ? "OpenStreetMap" : "DF e entorno",
                bundled: bundled, south: edge("south"), north: edge("north"), west: edge("west"), east: edge("east")))
        }
    }
    deinit { sources.forEach { sqlite3_close($0.db) } }

    /// True when the database shipped with the app covers this point (no download needed).
    func bundledCovers(_ lat: Double, _ lon: Double) -> Bool {
        sources.contains { $0.bundled && $0.covers(lat, lon) }
    }

    private func intValue(_ db: OpaquePointer, _ sql: String) -> Int32? {
        var s: OpaquePointer?
        defer { sqlite3_finalize(s) }
        guard sqlite3_prepare_v2(db, sql, -1, &s, nil) == SQLITE_OK, sqlite3_step(s) == SQLITE_ROW else { return nil }
        return sqlite3_column_int(s, 0)
    }
    private func diff(_ a: Double, _ b: Double) -> Double {
        abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180)
    }
    private func text(_ s: OpaquePointer?, _ col: Int32) -> String {
        guard let ptr = sqlite3_column_text(s, col) else { return "" }
        return String(cString: ptr)
    }

    private struct Candidate { let score: Double; let speed: Double?; let name: String; let wayID: Int64; let label: String }

    /// nil source => no database covers this point (the caller explains why).
    func lookup(_ position: CLLocation) -> RoadMatch? {
        let lat = position.coordinate.latitude, lon = position.coordinate.longitude
        let here = sources.filter { $0.covers(lat, lon) }
        if here.isEmpty { return nil }
        guard position.course >= 0 else { return RoadMatch(source: "AGUARDANDO DIREÇÃO GPS") }
        let tlat = Int32(floor(lat * 1000)), tlon = Int32(floor(lon * 1000))
        let factor = 111320 * cos(lat * .pi / 180)
        let sql = """
          SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.fwd,r.bwd,r.name,r.ref,r.wayid
          FROM road_tiles t JOIN roads r ON t.road_id=r.id
          WHERE t.tile_lat BETWEEN ? AND ? AND t.tile_lon BETWEEN ? AND ?
          GROUP BY r.id LIMIT 1200
          """
        var matches: [Candidate] = []
        for src in here {
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(src.db, sql, -1, &stmt, nil) == SQLITE_OK else {
                sqlite3_finalize(stmt); return RoadMatch(source: "ERRO NA CONSULTA OFFLINE")
            }
            sqlite3_bind_int(stmt, 1, tlat - 1); sqlite3_bind_int(stmt, 2, tlat + 1)
            sqlite3_bind_int(stmt, 3, tlon - 1); sqlite3_bind_int(stmt, 4, tlon + 1)
            while sqlite3_step(stmt) == SQLITE_ROW {
                let x1 = (sqlite3_column_double(stmt, 1) - lon) * factor
                let y1 = (sqlite3_column_double(stmt, 0) - lat) * 111320
                let x2 = (sqlite3_column_double(stmt, 3) - lon) * factor
                let y2 = (sqlite3_column_double(stmt, 2) - lat) * 111320
                let dx = x2 - x1, dy = y2 - y1
                let sq = dx * dx + dy * dy
                if sq < 1 { continue }
                let t = max(0, min(1, -(x1 * dx + y1 * dy) / sq))
                let distance = hypot(x1 + t * dx, y1 + t * dy)
                if distance > 27 { continue }
                let bearing = (atan2(dx, dy) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
                let forward = diff(position.course, bearing)
                let backward = diff(position.course, (bearing + 180).truncatingRemainder(dividingBy: 360))
                let heading = min(forward, backward)
                if heading > 45 { continue }
                let col: Int32 = forward <= backward ? 4 : 5
                let speed = sqlite3_column_type(stmt, col) == SQLITE_NULL ? nil : sqlite3_column_double(stmt, col)
                let name = text(stmt, 6).isEmpty ? text(stmt, 7) : text(stmt, 6)
                matches.append(Candidate(score: distance + heading * 0.3, speed: speed, name: name,
                                         wayID: sqlite3_column_int64(stmt, 8), label: src.label))
            }
            sqlite3_finalize(stmt)
        }
        matches.sort { $0.score < $1.score }
        guard let best = matches.first else { return RoadMatch(source: "VIA OU LIMITE NÃO CADASTRADO") }
        if matches.dropFirst().contains(where: {
            $0.score - best.score < 7 && $0.wayID != best.wayID &&
            ($0.speed != best.speed || (!$0.name.isEmpty && !best.name.isEmpty && $0.name != best.name))
        }) { return RoadMatch(source: "VIA AMBÍGUA") }
        return RoadMatch(speed: best.speed, name: best.name.isEmpty ? "Via não identificada" : best.name,
                         source: "OSM • " + best.label)
    }
}

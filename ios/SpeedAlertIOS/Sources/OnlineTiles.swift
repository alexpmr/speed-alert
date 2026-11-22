import Foundation
import SQLite3

/// Port of Android OnlineTiles.kt (Play version). Road limits for small 0.1° cells (~11 km) are
/// fetched from the public OpenStreetMap Overpass API and stored as v3 databases, so OfflineRoads
/// reads them like the bundled one. Only the cell's bounding box is sent, never the exact position.
enum OnlineTiles {
    private static let servers = ["https://overpass-api.de/api/interpreter",
                                  "https://overpass.kumi.systems/api/interpreter"]
    private static let refresh: TimeInterval = 30 * 24 * 3600
    private static let limitBytes: Int64 = 500 * 1_048_576

    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var dir = base.appendingPathComponent("online", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true      // re-downloadable map data stays out of iCloud
            try? dir.setResourceValues(values)
        }
        return dir
    }
    static func files() -> [URL] {
        let list = (try? FileManager.default.contentsOfDirectory(at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey])) ?? []
        return list.filter { $0.pathExtension == "sqlite" }
    }
    static func usage() -> (cells: Int, bytes: Int64) {
        let f = files()
        return (f.count, f.reduce(0) { $0 + size($1) })
    }
    static func clear() {
        for f in (try? FileManager.default.contentsOfDirectory(at: directory,
                    includingPropertiesForKeys: nil)) ?? [] { try? FileManager.default.removeItem(at: f) }
    }
    private static func size(_ u: URL) -> Int64 {
        Int64((try? u.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
    }
    private static func modified(_ u: URL) -> Date {
        (try? u.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
    }

    private static func name(_ la: Int, _ lo: Int) -> String { "o\(la)_\(lo)" }
    /// The driver's cell first, then only the neighbours whose edge is within ~3 km
    /// (Overpass allows few requests per IP; a dense city cell is ~1 MB gzip).
    static func around(_ lat: Double, _ lon: Double) -> [(Int, Int)] {
        let la = Int(floor(lat * 10)), lo = Int(floor(lon * 10))
        let m = 0.3
        var dLa = [0], dLo = [0]
        if lat * 10 - Double(la) < m { dLa.append(-1) }
        if Double(la + 1) - lat * 10 < m { dLa.append(1) }
        if lon * 10 - Double(lo) < m { dLo.append(-1) }
        if Double(lo + 1) - lon * 10 < m { dLo.append(1) }
        return dLa.flatMap { a in dLo.map { b in (la + a, lo + b) } }
    }
    /// Changes when the set of cells needed here changes (the model re-checks then).
    static func key(_ lat: Double, _ lon: Double) -> String {
        around(lat, lon).map { name($0.0, $0.1) }.joined(separator: ",")
    }

    /// Downloads missing or stale cells around the driver. Returns whether a file changed and
    /// the error that stopped the run (Overpass busy, no Internet...).
    static func ensure(_ lat: Double, _ lon: Double) async -> (changed: Bool, error: String?) {
        let cells = around(lat, lon)
        var changed = false
        var failed: String?
        for (la, lo) in cells {
            let target = directory.appendingPathComponent(name(la, lo) + ".sqlite")
            if FileManager.default.fileExists(atPath: target.path),
               Date().timeIntervalSince(modified(target)) < refresh { continue }
            do { try await build(la, lo, target); changed = true }
            catch { failed = error.localizedDescription; break }
        }
        evict(keep: Set(cells.map { name($0.0, $0.1) + ".sqlite" }))
        return (changed, failed)
    }
    /// Least recently fetched cells go first once the space limit is exceeded.
    private static func evict(keep: Set<String>) {
        var total = files().reduce(Int64(0)) { $0 + size($1) }
        for f in files().filter({ !keep.contains($0.lastPathComponent) })
                        .sorted(by: { modified($0) < modified($1) }) where total > limitBytes {
            total -= size(f)
            try? FileManager.default.removeItem(at: f)
        }
    }

    static func query(_ la: Int, _ lo: Int) -> String {
        let bbox = String(format: "%.1f,%.1f,%.1f,%.1f", locale: Locale(identifier: "en_US_POSIX"),
                          Double(la) / 10, Double(lo) / 10, Double(la + 1) / 10, Double(lo + 1) / 10)
        // Any road with a limit; named/numbered roads only if major (OsmTags slim profile).
        let all = "way[\"highway\"~\"^(\(OsmTags.highways.sorted().joined(separator: "|")))$\"]"
        let major = "way[\"highway\"~\"^(\(OsmTags.highways.subtracting(OsmTags.minor).sorted().joined(separator: "|")))$\"]"
        let parts = ["maxspeed", "maxspeed:forward", "maxspeed:backward"].map { "\(all)[\"\($0)\"](\(bbox));" } +
                    ["name", "ref"].map { "\(major)[\"\($0)\"](\(bbox));" }
        return "[out:json][timeout:90];(" + parts.joined() + ");out tags geom;"
    }

    private static func build(_ la: Int, _ lo: Int, _ target: URL) async throws {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        let body = "data=" + (query(la, lo).addingPercentEncoding(withAllowedCharacters: allowed) ?? "")
        var last: Error = Failure("OpenStreetMap indisponível")
        for server in servers {
            var req = URLRequest(url: URL(string: server)!, timeoutInterval: 120)
            req.httpMethod = "POST"
            req.httpBody = Data(body.utf8)
            req.setValue("PT2VHF-Speed-Alert (iOS)", forHTTPHeaderField: "User-Agent")
            req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            do {
                let (data, response) = try await URLSession.shared.data(for: req)
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                guard code == 200 else { throw Failure("OpenStreetMap HTTP \(code)") }
                let part = target.appendingPathExtension("part")
                defer { try? FileManager.default.removeItem(at: part) }
                try write(data, to: part, la: la, lo: lo)
                if FileManager.default.fileExists(atPath: target.path) {
                    try FileManager.default.removeItem(at: target)
                }
                try FileManager.default.moveItem(at: part, to: target)
                return
            } catch { last = error }
        }
        throw last
    }

    struct Failure: LocalizedError {
        let message: String
        init(_ m: String) { message = m }
        var errorDescription: String? { message }
    }
    private struct Response: Decodable { let elements: [Way]?; let remark: String? }
    private struct Way: Decodable { let id: Int64; let tags: [String: String]?; let geometry: [Point?]? }
    private struct Point: Decodable { let lat: Double; let lon: Double }

    /// Writes an Overpass JSON answer as a v3 database (same schema as tools/build_tiles.py).
    static func write(_ json: Data, to file: URL, la: Int, lo: Int) throws {
        let answer = try JSONDecoder().decode(Response.self, from: json)
        if let remark = answer.remark, remark.lowercased().contains("error") {
            throw Failure("OpenStreetMap: " + String(remark.prefix(80)))
        }
        try? FileManager.default.removeItem(at: file)
        var db: OpaquePointer?
        guard sqlite3_open(file.path, &db) == SQLITE_OK else {
            sqlite3_close(db); throw Failure("não foi possível criar \(file.lastPathComponent)")
        }
        defer { sqlite3_close(db) }
        func exec(_ sql: String) throws {
            guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else {
                throw Failure("SQLite: " + String(cString: sqlite3_errmsg(db)))
            }
        }
        try exec("""
            CREATE TABLE roads(id INTEGER PRIMARY KEY,wayid INTEGER,lat1 REAL,lon1 REAL,lat2 REAL,lon2 REAL,fwd REAL,bwd REAL,name TEXT,ref TEXT);
            CREATE TABLE road_tiles(tile_lat INTEGER,tile_lon INTEGER,road_id INTEGER,PRIMARY KEY(tile_lat,tile_lon,road_id)) WITHOUT ROWID;
            CREATE TABLE metadata(k TEXT PRIMARY KEY,v TEXT);
            BEGIN;
            """)
        var road: OpaquePointer?, tile: OpaquePointer?, meta: OpaquePointer?
        sqlite3_prepare_v2(db, "INSERT INTO roads VALUES(?,?,?,?,?,?,?,?,?,?)", -1, &road, nil)
        sqlite3_prepare_v2(db, "INSERT OR IGNORE INTO road_tiles VALUES(?,?,?)", -1, &tile, nil)
        sqlite3_prepare_v2(db, "INSERT INTO metadata VALUES(?,?)", -1, &meta, nil)
        defer { sqlite3_finalize(road); sqlite3_finalize(tile); sqlite3_finalize(meta) }
        let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
        var id: Int64 = 0
        for way in answer.elements ?? [] {
            let tags = way.tags ?? [:]
            guard let limits = OsmTags.limits(tags) else { continue }
            let fwd = limits.fwd, bwd = limits.bwd
            let points = (way.geometry ?? []).compactMap { $0 }
            let name = String((tags["name"] ?? "").trimmingCharacters(in: .whitespaces).prefix(120))
            let ref = String((tags["ref"] ?? "").trimmingCharacters(in: .whitespaces).prefix(50))
            for (a, b) in zip(points, points.dropFirst()) where a.lat != b.lat || a.lon != b.lon {
                id += 1
                sqlite3_reset(road)
                sqlite3_bind_int64(road, 1, id); sqlite3_bind_int64(road, 2, way.id)
                sqlite3_bind_double(road, 3, a.lat); sqlite3_bind_double(road, 4, a.lon)
                sqlite3_bind_double(road, 5, b.lat); sqlite3_bind_double(road, 6, b.lon)
                if let fwd { sqlite3_bind_double(road, 7, fwd) } else { sqlite3_bind_null(road, 7) }
                if let bwd { sqlite3_bind_double(road, 8, bwd) } else { sqlite3_bind_null(road, 8) }
                sqlite3_bind_text(road, 9, name, -1, transient)
                sqlite3_bind_text(road, 10, ref, -1, transient)
                guard sqlite3_step(road) == SQLITE_DONE else {
                    throw Failure("SQLite: " + String(cString: sqlite3_errmsg(db)))
                }
                let la0 = Int(floor(min(a.lat, b.lat) * 1000)), la1 = Int(floor(max(a.lat, b.lat) * 1000))
                let lo0 = Int(floor(min(a.lon, b.lon) * 1000)), lo1 = Int(floor(max(a.lon, b.lon) * 1000))
                if (la1 - la0 + 1) * (lo1 - lo0 + 1) > 1000 { continue }
                for x in la0...la1 { for y in lo0...lo1 {
                    sqlite3_reset(tile)
                    sqlite3_bind_int(tile, 1, Int32(x)); sqlite3_bind_int(tile, 2, Int32(y))
                    sqlite3_bind_int64(tile, 3, id)
                    sqlite3_step(tile)
                } }
            }
        }
        let iso = ISO8601DateFormatter().string(from: Date())
        for (k, v) in [("data_origin", "© OpenStreetMap contributors; ODbL 1.0"), ("region", "online"),
                       ("bbox_south", "\(Double(la) / 10)"), ("bbox_north", "\(Double(la + 1) / 10)"),
                       ("bbox_west", "\(Double(lo) / 10)"), ("bbox_east", "\(Double(lo + 1) / 10)"),
                       ("updated_utc", iso), ("roads", "\(id)")] {
            sqlite3_reset(meta)
            sqlite3_bind_text(meta, 1, k, -1, transient)
            sqlite3_bind_text(meta, 2, v, -1, transient)
            sqlite3_step(meta)
        }
        try exec("COMMIT; PRAGMA user_version=3;")
    }
}

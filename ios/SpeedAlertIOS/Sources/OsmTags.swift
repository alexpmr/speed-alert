import Foundation

/// OSM tag rules shared with Android (OsmTags.kt) and tools/build_osm_v3.py:
/// only explicit numeric limits, no inference.
enum OsmTags {
    static let highways: Set<String> = ["motorway", "trunk", "primary", "secondary", "tertiary",
        "residential", "unclassified", "living_street", "service", "motorway_link", "trunk_link",
        "primary_link", "secondary_link", "tertiary_link"]
    /// Minor roads are kept only with an explicit limit ("slim" profile, as the regional tiles).
    static let minor: Set<String> = ["residential", "unclassified", "living_street", "service"]
    private static let speed = try! NSRegularExpression(pattern: "^(\\d{1,3})\\s*(km/h|kmh|kph|mph)?$")

    /// "60", "60 km/h", "40 mph" -> km/h; nil for "none", "signals", "DE:urban" or outside 10–160.
    static func kmh(_ text: String?) -> Double? {
        guard let t = text?.lowercased().trimmingCharacters(in: .whitespacesAndNewlines),
              let m = speed.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)),
              let r1 = Range(m.range(at: 1), in: t), let v = Int(t[r1]),
              (10...160).contains(v) else { return nil }
        let mph = Range(m.range(at: 2), in: t).map { t[$0] == "mph" } ?? false
        return mph ? (Double(v) * 1.609344 * 100).rounded() / 100 : Double(v)
    }

    /// Forward and backward limit of a way, or nil when the way should be skipped.
    static func limits(_ tags: [String: String]) -> (fwd: Double?, bwd: Double?)? {
        guard let highway = tags["highway"], highways.contains(highway) else { return nil }
        var fwd: Double?, bwd: Double?
        if tags["maxspeed:conditional"] == nil {
            let shared = kmh(tags["maxspeed"])
            fwd = kmh(tags["maxspeed:forward"]) ?? shared
            bwd = kmh(tags["maxspeed:backward"]) ?? shared
        }
        let limited = fwd != nil || bwd != nil
        let named = !(tags["name"] ?? "").trimmingCharacters(in: .whitespaces).isEmpty ||
                    !(tags["ref"] ?? "").trimmingCharacters(in: .whitespaces).isEmpty
        if !limited && (!named || minor.contains(highway)) { return nil }
        return (fwd, bwd)
    }
}

import Foundation

final class SpeedPreferences: ObservableObject {
    @Published var tolerance: Double { didSet { save("tolerance", tolerance) } }
    @Published var volume: Double { didSet { save("volume", volume) } }
    /// Not saved: silencing lasts until monitoring restarts, so nobody drives on unknowingly muted.
    @Published var mute = false
    @Published var keepAwake: Bool { didSet { save("keepAwake", keepAwake) } }
    @Published var manualEnabled: Bool { didSet { save("manualEnabled", manualEnabled) } }
    @Published var manualLimit: Double { didSet { save("manualLimit", manualLimit) } }
    @Published var sound: AlarmStyle { didSet { save("sound", sound.rawValue) } }

    private let defaults = UserDefaults.standard
    private func save<T>(_ key: String, _ value: T) { defaults.set(value, forKey: key) }
    private static func num(_ d: UserDefaults, _ key: String, _ value: Double) -> Double {
        d.object(forKey: key) == nil ? value : d.double(forKey: key)
    }
    private static func bool(_ d: UserDefaults, _ key: String, _ value: Bool) -> Bool {
        d.object(forKey: key) == nil ? value : d.bool(forKey: key)
    }
    init() {
        let d = UserDefaults.standard
        tolerance = Self.num(d, "tolerance", 0)
        volume = Self.num(d, "volume", 80)
        keepAwake = Self.bool(d, "keepAwake", true)
        manualEnabled = Self.bool(d, "manualEnabled", false)
        manualLimit = min(160, max(10, Self.num(d, "manualLimit", 60)))
        sound = AlarmStyle(rawValue: d.string(forKey: "sound") ?? "") ?? .beeps
        // Pre-1.0.9 keys: radar, site update checks and statistics no longer exist.
        ["mute", "radarRange", "periodicChecks", "statistics", "iosInstallUUID", "iosLastUpdateCheck"]
            .forEach { d.removeObject(forKey: $0) }
    }
}

import Foundation

final class SpeedPreferences: ObservableObject {
    @Published var tolerance: Double { didSet { save("tolerance", tolerance) } }
    @Published var volume: Double { didSet { save("volume", volume) } }
    @Published var mute: Bool { didSet { save("mute", mute) } }
    @Published var keepAwake: Bool { didSet { save("keepAwake", keepAwake) } }
    @Published var periodicChecks: Bool { didSet { save("periodicChecks", periodicChecks) } }
    @Published var statistics: Bool { didSet { save("statistics", statistics) } }
    @Published var radarRange: Double { didSet { save("radarRange", radarRange) } }
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
        mute = Self.bool(d, "mute", false)
        keepAwake = Self.bool(d, "keepAwake", true)
        periodicChecks = Self.bool(d, "periodicChecks", true)
        statistics = Self.bool(d, "statistics", true)
        radarRange = Self.num(d, "radarRange", 300)
        manualEnabled = Self.bool(d, "manualEnabled", false)
        manualLimit = Self.num(d, "manualLimit", 60)
        sound = AlarmStyle(rawValue: d.string(forKey: "sound") ?? "") ?? .beeps
    }
}

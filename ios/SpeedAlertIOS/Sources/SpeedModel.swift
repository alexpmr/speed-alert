import CoreLocation
import Combine
import SwiftUI
import UIKit

/// GPS-first state. Speed and alerts never need the network; only the map cells of
/// OpenStreetMap are downloaded, outside the database bundled with the app.
final class SpeedModel:NSObject,ObservableObject,CLLocationManagerDelegate {
    let preferences=SpeedPreferences()
    @Published var speed:Double?
    @Published var limit:Double?
    @Published var roadName="Via não identificada"
    @Published var source="AGUARDANDO GPS"
    @Published var accuracy:Double?
    @Published var monitoring=false
    @Published var status="Monitoramento parado"
    @Published var alerting=false
    @Published var soundMessage:String?

    private let manager=CLLocationManager()
    private var roads=OfflineRoads()
    private let alarms=AlarmEngine()
    private var timer:Timer?
    private var lastFix:Date?
    private var settingsSubscriber:AnyCancellable?
    private var testingSound=false
    // Map cells: one download run at a time, re-checked when the needed cells change.
    private var tilesFor:String?
    private var tilesAt=Date.distantPast
    private var tilesRunning=false
    private var tilesError:String?

    override init() {
        super.init()
        manager.delegate=self
        manager.activityType = .automotiveNavigation
        manager.desiredAccuracy=kCLLocationAccuracyBestForNavigation
        manager.distanceFilter=kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically=false
        settingsSubscriber=preferences.objectWillChange.sink { [weak self] _ in
            DispatchQueue.main.async { self?.objectWillChange.send() }
        }
    }

    func start() {
        monitoring=true
        preferences.mute=false
        status="Aguardando GPS"
        let auth=manager.authorizationStatus
        if auth == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if auth == .authorizedWhenInUse || auth == .authorizedAlways {
            startLocation()
        } else {
            status="Localização não autorizada"
            monitoring=false
        }
        UIApplication.shared.isIdleTimerDisabled=preferences.keepAwake
        timer?.invalidate()
        timer=Timer.scheduledTimer(withTimeInterval:2,repeats:true) { [weak self] _ in
            self?.invalidateStaleFix()
        }
    }
    private func startLocation() {
        // UIBackgroundModes location MUST be present in Info.plist.
        manager.allowsBackgroundLocationUpdates=true
        manager.showsBackgroundLocationIndicator=true
        manager.startUpdatingLocation()
    }
    func requestAlwaysAccess() {
        manager.requestAlwaysAuthorization()
    }
    func stop() {
        monitoring=false
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates=false
        timer?.invalidate();timer=nil
        speed=nil;limit=nil;accuracy=nil
        alerting=false
        preferences.mute=false
        alarms.stop()
        status="Monitoramento parado"
        UIApplication.shared.isIdleTimerDisabled=false
    }
    func toggleMute() {
        preferences.mute.toggle()
        syncAlarm()
    }
    func refreshSettings() {
        UIApplication.shared.isIdleTimerDisabled=monitoring && preferences.keepAwake
        syncAlarm()
    }
    /// Plays the chosen alarm for 2 s, even when muted, so the driver can check the volume.
    func testSound() {
        testingSound=true
        alarms.play(true,style:preferences.sound,volume:max(preferences.volume,5))
        soundMessage=alarms.lastError.map { "Falha ao tocar: \($0)" }
        DispatchQueue.main.asyncAfter(deadline:.now()+2) { [weak self] in
            self?.testingSound=false
            self?.syncAlarm()
        }
    }
    /// Removes downloaded OpenStreetMap cells; the bundled database stays.
    func clearMaps() {
        OnlineTiles.clear()
        tilesFor=nil
        roads=OfflineRoads()
        objectWillChange.send()
    }
    private func syncAlarm() {
        if testingSound {return}
        alarms.play(alerting && monitoring && !preferences.mute,
                    style:preferences.sound,volume:preferences.volume)
    }
    private func invalidateStaleFix() {
        guard monitoring else{return}
        if let lastFix, Date().timeIntervalSince(lastFix)>6 {
            speed=nil
            status="GPS indisponível"
            alerting=false
            syncAlarm()
        }
    }
    /// Downloads the OpenStreetMap cells around the driver when the bundled database does not
    /// cover the area; reopens the databases when a cell changed.
    private func maintainTiles(_ lat:Double,_ lon:Double) {
        if roads.bundledCovers(lat,lon) {return}
        let key=OnlineTiles.key(lat,lon)
        if tilesRunning || (key==tilesFor && Date().timeIntervalSince(tilesAt)<600) {return}
        tilesFor=key;tilesAt=Date();tilesRunning=true
        Task.detached(priority:.utility) { [weak self] in
            let result=await OnlineTiles.ensure(lat,lon)
            let fresh=result.changed ? OfflineRoads() : nil
            await MainActor.run {
                guard let self else {return}
                self.tilesRunning=false
                self.tilesError=result.error
                // Overpass busy or offline: try again in about a minute, not ten.
                if result.error != nil {self.tilesAt=Date().addingTimeInterval(-540)}
                if let fresh {self.roads=fresh}
            }
        }
    }
    func locationManagerDidChangeAuthorization(_ manager:CLLocationManager) {
        let authorized=manager.authorizationStatus == .authorizedAlways ||
                       manager.authorizationStatus == .authorizedWhenInUse
        if monitoring && authorized {startLocation()}
        else if monitoring && !authorized && manager.authorizationStatus != .notDetermined {
            status="Autorize o GPS nas Configurações"
            alerting=false
            syncAlarm()
        }
    }
    func locationManager(_ manager:CLLocationManager,didUpdateLocations locations:[CLLocation]) {
        guard monitoring,let fix=locations.last else{return}
        guard abs(fix.timestamp.timeIntervalSinceNow)<6,
              fix.horizontalAccuracy>=0 && fix.horizontalAccuracy<=50,
              fix.speed>=0,fix.speedAccuracy<=4 else {
            speed=nil;limit=nil;roadName="Via não identificada"
            accuracy=fix.horizontalAccuracy>=0 ? fix.horizontalAccuracy : nil
            status="GPS impreciso"
            alerting=false
            syncAlarm()
            return
        }
        lastFix=Date()
        let kmh=fix.speed*3.6
        speed=kmh
        accuracy=fix.horizontalAccuracy
        maintainTiles(fix.coordinate.latitude,fix.coordinate.longitude)
        let road=roads.lookup(fix) ?? RoadMatch(source:
            tilesRunning ? "BAIXANDO MAPA DO OPENSTREETMAP…" :
            tilesError != nil ? "FALHA AO BAIXAR DO OPENSTREETMAP — tentando de novo" :
            "SEM DADOS DO OPENSTREETMAP AQUI")
        roadName=road.name
        source=preferences.manualEnabled ? "MANUAL • "+road.source : road.source
        limit=preferences.manualEnabled ? preferences.manualLimit : road.speed
        let exceeding=limit.map {kmh > $0+preferences.tolerance} ?? false
        alerting=exceeding
        status=exceeding ? "Velocidade excedida" :
               limit == nil ? "Limite desconhecido" : "Monitoramento ativo"
        syncAlarm()
    }
    func locationManager(_ manager:CLLocationManager,didFailWithError error:Error) {
        status="Falha GPS: \(error.localizedDescription)"
        alerting=false
        syncAlarm()
    }
}

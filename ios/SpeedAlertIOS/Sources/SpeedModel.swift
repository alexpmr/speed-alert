import CoreLocation
import Combine
import SwiftUI
import UIKit

/// GPS-first state, never requires network access to calculate speed or warnings.
final class SpeedModel:NSObject,ObservableObject,CLLocationManagerDelegate {
    let preferences=SpeedPreferences()
    @Published var speed:Double?
    @Published var limit:Double?
    @Published var roadName="Via não identificada"
    @Published var source="AGUARDANDO GPS"
    @Published var accuracy:Double?
    @Published var radarDistance:Double?
    @Published var radarLimit:Double?
    @Published var monitoring=false
    @Published var status="Monitoramento parado"
    @Published var alerting=false
    @Published var cameraAlert=false

    private let manager=CLLocationManager()
    private let offline=OfflineRoads()
    private let alarms=AlarmEngine()
    private var timer:Timer?
    private var lastFix:Date?
    private var settingsSubscriber:AnyCancellable?

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
        speed=nil;limit=nil;radarDistance=nil
        cameraAlert=false;alerting=false
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
    private func syncAlarm() {
        let sound:AlarmStyle=cameraAlert ? .continuous : preferences.sound
        alarms.play(alerting && monitoring && !preferences.mute,
                    style:sound,volume:preferences.volume)
    }
    private func invalidateStaleFix() {
        guard monitoring else{return}
        if let lastFix, Date().timeIntervalSince(lastFix)>6 {
            speed=nil
            status="GPS indisponível"
            alerting=false;cameraAlert=false
            alarms.stop()
        }
    }
    func locationManagerDidChangeAuthorization(_ manager:CLLocationManager) {
        let authorized=manager.authorizationStatus == .authorizedAlways ||
                       manager.authorizationStatus == .authorizedWhenInUse
        if monitoring && authorized {startLocation()}
        else if monitoring && !authorized && manager.authorizationStatus != .notDetermined {
            status="Autorize o GPS nas Configurações"
            alerting=false
            alarms.stop()
        }
    }
    func locationManager(_ manager:CLLocationManager,didUpdateLocations locations:[CLLocation]) {
        guard monitoring,let fix=locations.last else{return}
        guard abs(fix.timestamp.timeIntervalSinceNow)<6,
              fix.horizontalAccuracy>=0 && fix.horizontalAccuracy<=50,
              fix.speed>=0,fix.speedAccuracy<=4 else {
            speed=nil;limit=nil;roadName="Via não identificada"
            status="GPS impreciso"
            alerting=false;cameraAlert=false
            alarms.stop()
            return
        }
        lastFix=Date()
        speed=fix.speed*3.6
        accuracy=fix.horizontalAccuracy
        let road=offline.lookup(fix,radarRange:preferences.radarRange)
        source=road.source
        roadName=road.name
        limit=preferences.manualEnabled ? preferences.manualLimit : road.speed
        radarDistance=road.cameraMeters
        radarLimit=road.cameraLimit
        let exceeding=limit.map {speed! > $0+preferences.tolerance} ?? false
        cameraAlert=(road.cameraMeters ?? .infinity) <= preferences.radarRange &&
                    (road.cameraLimit.map {speed! > $0+preferences.tolerance} ?? false)
        alerting=exceeding || cameraAlert
        status=cameraAlert ? "Excesso próximo a radar" :
               exceeding ? "Velocidade excedida" :
               limit == nil ? "Limite desconhecido" : "Monitoramento ativo"
        syncAlarm()
    }
    func locationManager(_ manager:CLLocationManager,didFailWithError error:Error) {
        status="Falha GPS: \(error.localizedDescription)"
        alerting=false;cameraAlert=false
        alarms.stop()
    }
}

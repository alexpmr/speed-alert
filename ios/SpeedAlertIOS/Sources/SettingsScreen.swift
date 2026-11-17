import SwiftUI

struct SettingsScreen:View {
    @ObservedObject var model:SpeedModel
    @ObservedObject private var prefs:SpeedPreferences
    @Environment(\.openURL) private var openURL

    init(model:SpeedModel) {
        self.model=model
        self.prefs=model.preferences
    }

    var body:some View {
        Form {
            Section("Monitoramento") {
                Toggle("Manter tela ligada",isOn:$prefs.keepAwake)
                    .onChange(of:prefs.keepAwake) { _ in model.refreshSettings() }
                Toggle("Usar limite manual",isOn:$prefs.manualEnabled)
                if prefs.manualEnabled {
                    Stepper("Limite: \(Int(prefs.manualLimit)) km/h",
                        value:$prefs.manualLimit,in:10...130,step:10)
                }
                HStack {
                    Text("Tolerância")
                    Spacer()
                    Text("\(Int(prefs.tolerance)) km/h")
                }
                Slider(value:$prefs.tolerance,in:0...20,step:1)
            }
            Section("Alertas sonoros") {
                Toggle("Silenciar alarmes",isOn:$prefs.mute)
                    .onChange(of:prefs.mute) { _ in model.refreshSettings() }
                Picker("Tipo de som",selection:$prefs.sound) {
                    ForEach(AlarmStyle.allCases) { style in
                        Text(style.label).tag(style)
                    }
                }.onChange(of:prefs.sound){_ in model.refreshSettings()}
                HStack {
                    Text("Volume relativo")
                    Spacer()
                    Text("\(Int(prefs.volume))%")
                }
                Slider(value:$prefs.volume,in:0...100,step:5)
                    .onChange(of:prefs.volume){_ in model.refreshSettings()}
                Stepper("Distância radar: \(Int(prefs.radarRange)) m",
                    value:$prefs.radarRange,in:100...1000,step:100)
            }
            Section("GPS em segundo plano") {
                Text("Com o monitoramento iniciado, o iOS pode continuar recebendo GPS e reproduzindo alertas enquanto outro aplicativo estiver na tela. O funcionamento depende das permissões de localização e das políticas de energia do iOS.")
                    .font(.footnote)
                Button("Solicitar permissão de localização Sempre") {
                    model.requestAlwaysAccess()
                }
                Button("Interromper monitoramento") {model.stop()}
            }
            Section("Atualizações e privacidade") {
                Toggle("Verificar novas atualizações periodicamente",
                       isOn:$prefs.periodicChecks)
                Toggle("Participar das estatísticas de instalações",
                       isOn:$prefs.statistics)
                Text("O identificador da instalação é pseudônimo. Não são transmitidos velocidade ou trajeto. O país pode ser estimado pelo servidor.")
                    .font(.footnote)
                Button("Consultar projeto e futuras versões iOS") {
                    openURL(URL(string:"https://www.pt2vhf.com/speed")!)
                }
                Text("No iPhone, a instalação de novas versões depende de TestFlight, App Store ou provisionamento Apple. O site não instala um IPA automaticamente.")
                    .font(.footnote)
            }
            Section {
                Text("PT2VHF Speed Alert iOS 1.0.6 — beta técnico")
                Text("Dados de vias: © OpenStreetMap contributors (ODbL 1.0).")
                    .font(.footnote)
            }
        }
        .navigationTitle("Configurações")
        .onDisappear {model.refreshSettings()}
    }
}

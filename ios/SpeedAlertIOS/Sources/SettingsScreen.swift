import SwiftUI

struct SettingsScreen:View {
    @ObservedObject var model:SpeedModel
    @ObservedObject private var prefs:SpeedPreferences
    @Environment(\.openURL) private var openURL
    @State private var maps=OnlineTiles.usage()

    init(model:SpeedModel) {
        self.model=model
        self.prefs=model.preferences
    }
    private var version:String {
        Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? ""
    }

    var body:some View {
        Form {
            Section("Monitoramento") {
                Toggle("Manter tela ligada",isOn:$prefs.keepAwake)
                    .onChange(of:prefs.keepAwake) { _ in model.refreshSettings() }
                Toggle("Usar limite manual",isOn:$prefs.manualEnabled)
                if prefs.manualEnabled {
                    HStack {
                        Text("Limite manual")
                        Spacer()
                        Text("\(Int(prefs.manualLimit)) km/h")
                    }
                    Slider(value:$prefs.manualLimit,in:10...160,step:5)
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
                Button("Testar som (2 s)") {model.testSound()}
                if let message=model.soundMessage {
                    Text(message).font(.footnote).foregroundStyle(.red)
                }
                Text("O volume final segue o volume de mídia do iPhone.").font(.footnote)
            }
            Section("GPS em segundo plano") {
                Text("Com o monitoramento iniciado, o iOS continua recebendo GPS e tocando os alertas enquanto o Waze, o Google Maps ou outro app estiver na tela. Isso depende da permissão de localização e das políticas de energia do iOS.")
                    .font(.footnote)
                Button("Permitir localização \"Sempre\"") {model.requestAlwaysAccess()}
                if model.monitoring {
                    Button("Interromper monitoramento",role:.destructive) {model.stop()}
                } else {
                    Button("Iniciar monitoramento") {model.start()}
                }
            }
            Section("Mapas do OpenStreetMap") {
                Text("Fora do DF, o app baixa sozinho os limites das vias em áreas de cerca de 11 km ao redor de você. Só a área aproximada é enviada, nunca a posição exata. Depois de baixadas, as áreas funcionam sem Internet por 30 dias.")
                    .font(.footnote)
                HStack {
                    Text("Áreas baixadas")
                    Spacer()
                    Text("\(maps.cells) • \(ByteCountFormatter.string(fromByteCount:maps.bytes,countStyle:.file))")
                        .foregroundStyle(.secondary)
                }
                Button("Apagar mapas baixados",role:.destructive) {
                    model.clearMaps()
                    maps=OnlineTiles.usage()
                }
            }
            Section("Privacidade") {
                Text("Localização, velocidade e trajetos ficam só no iPhone. O app não tem anúncios, contas nem estatísticas de uso.")
                    .font(.footnote)
                Button("Política de privacidade") {
                    openURL(URL(string:"https://alexpmr.github.io/speed-alert/privacidade.html")!)
                }
            }
            Section {
                Text("PT2VHF Speed Alert iOS \(version)")
                Text("Dados de vias: © OpenStreetMap contributors (ODbL 1.0).")
                    .font(.footnote)
            }
        }
        .navigationTitle("Configurações")
        .onAppear {maps=OnlineTiles.usage()}
        .onDisappear {model.refreshSettings()}
    }
}

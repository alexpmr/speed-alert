import SwiftUI

@main
struct PT2VHFSpeedAlertApp: App {
    @StateObject private var model = SpeedModel()
    @State private var acceptedThisLaunch = false
    @State private var showSettings = false

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                SpeedDashboard(model: model, showSettings: $showSettings)
                    .navigationDestination(isPresented: $showSettings) {
                        SettingsScreen(model: model)
                    }
            }
            .preferredColorScheme(.dark)
            .interactiveDismissDisabled()
            .alert("Aviso importante", isPresented: Binding(
                get: { !acceptedThisLaunch },
                set: { if !$0 { acceptedThisLaunch = true } }
            )) {
                Button("Não continuar", role: .cancel) {
                    acceptedThisLaunch = true
                    model.stop()
                }
                Button("Continuar") {
                    acceptedThisLaunch = true
                    model.start()
                }
            } message: {
                Text("""
                O PT2VHF Speed Alert é uma ferramenta auxiliar e experimental. A velocidade do GPS e os limites de velocidade do OpenStreetMap podem estar incorretos, desatualizados, incompletos ou indisponíveis. O app e seus desenvolvedores não assumem responsabilidade por multas, acidentes, perdas ou danos decorrentes de seu uso, observados os limites da legislação aplicável.

                Localização, velocidade e trajetos são processados somente no iPhone. Para baixar o mapa, só é enviada a área aproximada (limites de uma área de ~11 km), nunca a posição exata.

                A sinalização oficial e as normas de trânsito prevalecem sempre. Não manuseie o celular enquanto dirige.
                """)
            }
        }
    }
}

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
                O PT2VHF Speed Alert é auxiliar e experimental. Dados GPS, limites de velocidade e radares offline podem ser imprecisos, incompletos ou desatualizados. A sinalização e as regras de trânsito prevalecem. Não manuseie o telefone ao dirigir.

                Ao continuar, o monitoramento é iniciado. A verificação opcional de atualizações e o registro pseudônimo de instalações podem ser desativados em Ajustes. Não são enviados velocidade, trajeto nem coordenadas.
                """)
            }
        }
    }
}

import Foundation

/// Optional connectivity: only for user-initiated/app-open version checks and
/// pseudonymous installation statistics. Core GPS, maps and alarms never depend on it.
enum IOSUpdateClient {
    private static let manifest="https://www.pt2vhf.com/update.php?product=speed&platform=ios&kind=ipa"
    private static let activity="https://www.pt2vhf.com/speed_activity.php"
    private static let installedVersion="1.0.6"

    static func check(shareStatistics:Bool)->String {
        // This synchronous entry is intentionally not used by the main thread.
        return "Consulta pendente"
    }

    static func query(shareStatistics:Bool) async -> String {
        if shareStatistics {
            await sendOptionalActivity()
        }
        guard let url=URL(string:manifest) else{return "URL de atualização inválida"}
        do {
            var req=URLRequest(url:url)
            req.timeoutInterval=12
            req.setValue("PT2VHF-Speed-Alert-iOS","forHTTPHeaderField":"User-Agent")
            let (data,response)=try await URLSession.shared.data(for:req)
            guard let status=response as? HTTPURLResponse else {
                return "Site de atualização indisponível"
            }
            if status.statusCode == 404 {
                return "Ainda não há uma distribuição iOS disponível"
            }
            guard status.statusCode == 200 else {
                return "Site indisponível; GPS permanece offline"
            }
            guard let obj=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any],
                  obj["product"] as? String == "speed",
                  obj["status"] as? String == "available" else {
                return "Nenhuma versão iOS está publicada no site"
            }
            let version=obj["version"] as? String ?? ""
            return version.isEmpty ? "Nenhuma versão iOS está publicada" :
                "Versão iOS \(version) anunciada pelo site. Instalação somente por distribuição Apple."
        } catch {
            return "Sem conexão para consultar versões. Monitoramento offline não afetado."
        }
    }

    private static func sendOptionalActivity() async {
        let defaults=UserDefaults.standard
        let uuid:String
        if let saved=defaults.string(forKey:"iosInstallUUID") {
            uuid=saved
        } else {
            uuid=UUID().uuidString.lowercased()
            defaults.set(uuid,forKey:"iosInstallUUID")
        }
        guard let url=URL(string:activity),
              let bytes=try? JSONSerialization.data(withJSONObject:[
                "product":"speed-alert",
                "install_id":uuid,
                "version":installedVersion,
                "platform":"ios",
                "event":"update_check"
              ]) else{return}
        var req=URLRequest(url:url)
        req.httpMethod="POST"
        req.httpBody=bytes
        req.timeoutInterval=10
        req.setValue("application/json; charset=utf-8",forHTTPHeaderField:"Content-Type")
        _ = try? await URLSession.shared.data(for:req)
    }
}

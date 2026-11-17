# PT2VHF Speed Alert iOS 1.0.6 (beta técnico)

Aplicativo nativo SwiftUI para iPhone (iOS 16+), com as funcionalidades principais do Android.

- Core Location: GPS, velocidade inteira, placa circular e nome da via.
- Limites e radares provenientes de banco OSM v3 offline (DF e entorno).
- Verde abaixo de 95%, amarelo de 95% a 100%, vermelho intermitente acima.
- Seis sons sintetizados, botão Silenciar sem interromper o monitoramento.
- Tolerância padrão de zero, volume relativo 80%, manter tela ligada ativado.
- Capacidade declarada de localização e áudio em segundo plano.
- Sinalização oficial prevalece.

## Build no Mac
1. Instale Xcode 16+ e XcodeGen.
2. Na raiz do repositório: bash ios/scripts/prepare_offline.sh
3. Execute: cd ios && xcodegen generate --spec project.yml
4. Abra PT2VHFSpeedAlert.xcodeproj, escolha Apple Developer Team em Signing & Capabilities.
5. Habilite assinatura para build em dispositivo; o CI desabilita assinatura por padrão.

## Distribuição
**Não existe IPA assinado ou TestFlight publicado nesta etapa.**
Instalação física requer certificado e provisionamento Apple, TestFlight ou App Store.
A compilação do GitHub Actions é apenas para simulador, não para um iPhone real.

## Limitações
GPS em segundo plano exige consentimento e obedece às políticas do iOS; force-quit interrompe.
Dados OSM de velocidade/radar podem estar incorretos e só abrangem a área disponível no SQLite.
A homologação de áudio com Mapas/Waze em aparelhos reais é pendente.
O OTA de instalação direta via site não é suportado para iOS.

© OpenStreetMap contributors, ODbL 1.0.
# PT2VHF Speed Alert iOS 1.0.9

App nativo SwiftUI para iPhone e iPad (iOS 16+), equivalente à versão 1.0.9 do Google Play.

- Velocidade pelo GPS num quadrado verde / amarelo (95–100% do limite) / vermelho piscando (acima).
- Placa com o limite da via e nome da via, de dados OpenStreetMap.
- Base do DF e entorno dentro do app; no resto do mundo, áreas de ~11 km baixadas sob demanda
  do OpenStreetMap (Overpass), válidas por 30 dias e usadas offline depois (`OnlineTiles.swift`).
- Botão "Som ligado / Som DESLIGADO" que mostra o estado atual; o silêncio dura até o
  monitoramento recomeçar. "Testar som (2 s)" em Configurações.
- Indicador de GPS discreto no canto superior direito; limite manual de 10 a 160 km/h.
- Sem radares, sem estatísticas de uso e sem consulta de versões no site (atualizações pela Apple).
- Alertas em segundo plano (localização + áudio), junto com Waze ou Google Maps.

## Testes e build (GitHub Actions)
- `ios-simulator.yml`: gera o projeto, compila para o simulador e roda os testes unitários
  (`SpeedAlertIOS/Tests`: regras de tags OSM iguais às do Android, gravação e leitura das áreas).
- `ios-testflight.yml` (manual): arquiva com assinatura automática e envia ao App Store Connect.
- `ios-ipa.yml` (manual): IPA Ad Hoc para iPhones cadastrados por UDID.

## Publicar no TestFlight, depois da aprovação da conta Apple Developer
1. **App Store Connect → Apps → +  → Novo app**: plataforma iOS, nome "PT2VHF Speed Alert",
   idioma Português (Brasil), ID do pacote `com.pt2vhf.speedalert.ios` (se não aparecer na lista,
   cadastre em developer.apple.com → Identifiers), SKU `pt2vhf-speed-alert-ios`.
2. **Users and Access → Integrations → App Store Connect API → Team Keys → +**: nome "GitHub",
   acesso **Admin**. Baixe o arquivo `AuthKey_XXXX.p8` (só pode ser baixado uma vez) e anote o
   Key ID e o Issuer ID.
3. Team ID: developer.apple.com → Account → Membership details (10 caracteres).
4. GitHub → Settings → Secrets and variables → Actions, crie:
   `IOS_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID` e `ASC_KEY_P8_BASE64`
   (no PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("AuthKey_XXXX.p8"))`).
5. Actions → "Upload iOS build to TestFlight" → Run workflow. Após o processamento da Apple
   (10–30 min), o build aparece em TestFlight.
6. Teste interno (até 100 pessoas da equipe, sem revisão) ou externo (até 10.000, por e-mail ou
   link público, depois de uma revisão rápida da Apple).
7. Para a App Store: preencha a ficha, a privacidade ("Dados não coletados") com a política
   https://alexpmr.github.io/speed-alert/privacidade.html e envie o build para revisão.

## Build no Mac (opcional)
1. Xcode 16+ e XcodeGen.
2. Na raiz do repositório: `bash ios/scripts/prepare_offline.sh`
3. `cd ios && xcodegen generate --spec project.yml`, abra o projeto e escolha o Team em Signing.

O ícone (`Assets.xcassets/AppIcon.appiconset/icon-1024.png`) é gerado no Windows por
`scripts/make_app_icon.ps1`, a partir do mesmo desenho vetorial do Android.

## Limitações
- A maioria dos iPhones entrega GPS a 1 Hz; o app usa a maior taxa disponível.
- GPS em segundo plano obedece às políticas do iOS; fechar o app à força interrompe os alertas.
- Ainda não há "Marcar erro" (relatos ao OpenStreetMap) no iPhone.
- Limites do OSM podem estar incorretos ou faltar. A sinalização oficial prevalece.

© OpenStreetMap contributors, ODbL 1.0.

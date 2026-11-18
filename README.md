# PT2VHF Speed Alert v1.0.6

Aplicativo Android independente (8.0+) com velocidade GNSS e limites offline OSM no DF e entorno.

## Recursos
- Interface adaptativa, versão no cabeçalho, placa circular de limite, velocidade inteira e nome da via abaixo da placa.
- Velocidade verde abaixo de 95%, amarelo entre 95% e 100%, vermelho piscando acima do limite. Sem limite, neutro.
- O monitoramento inicia após aceite do aviso; sem botão de Iniciar.
- Botão Silenciar altera somente áudio, nunca desliga GPS ou avisos visuais.
- Dados OSM v3: vias, limites explícitos, nomes/referências e radares em nós vinculados às vias.
- Alertas independentes de radares; tom contínuo quando radar da mesma way à frente e excesso verificado (experimental).
- Consulta OTA GitHub Releases, download HTTPS com SHA-256 e instalação com confirmação do Android.
- Estatísticas opt-in no site /speed: UUID aleatório por instalação e versão, sem dados de trajetos ou posição.
- Operação de GPS/limites/alertas completamente offline; OTA só com Internet.

## Compilação
JDK 17, Gradle 8.14.3, SDK 36, Python 3 e osmium. O Actions baixa o extrato Centro-Oeste Geofabrik e gera SQLite v3.

## Limitações
Beta ainda sem teste em dispositivo/carro. Bases OSM podem ser incompletas; falsos negativos/positivos de limite ou radar são possíveis, sobretudo em vias paralelas. A sinalização oficial prevalece. Uma assinatura debug variável entre builds pode exigir desinstalar a versão anterior; a assinatura de produção deve ser persistente antes de considerar o OTA transparente. Android Auto e Android Automotive não equivalem a APK instalado numa central Android comum.

© OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright


## Atualizações de interface e áudio da 1.0.4 beta
- Layout da tela principal refeito a partir do mockup entregue pelo usuário: logo PT2VHF, engrenagem, placa circular, cartão de velocidade, rua, radar/limite/GPS e botão Silenciar. O app alterna entre verde (menos de 95% do limite), amarelo (95% até o limite) e vermelho piscando (acima do limite).
- Novo motor AudioTrack em serviço de localização foreground com seis sons: simples, duplo, triplo, sirene, suave e tom contínuo (prioridade para radar).
- Manter tela ligada ativado por padrão nas instalações novas. O GPS/alarme continua com Waze ou Google Maps visíveis, enquanto o serviço estiver ativo; a arbitragem de áudio do Android/central pode variar.
- OTA consulta exclusivamente o manifesto do site `https://www.pt2vhf.com/update.php?product=speed&platform=android&kind=apk`. Baixa pelo link oficial com verificação de SHA-256 e checagem da assinatura Android.
- **Publicação automática de APK debug suspensa** porque certificados variáveis causam conflito com apps já instalados. V1.0.4 será somente artefato interno de CI até definir assinatura estável.
- Layout/capacidade de áudio dependem de homologação prática em celulares e centrais multimídia.

## Linha estável 1.0.5
Versão 1.0 (patch 5) escolhida por ser posterior às betas 1.0.3 e 1.0.4, mantendo compatibilidade com a comparação semântica do OTA. O versionCode é 106.

**Requisito de publicação:** assinatura Android permanente armazenada nos secrets do GitHub Actions (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). O APK debug de CI é **somente para QA**, nunca para instalação estável/OTA. Se secrets estiverem ausentes, a publicação deve falhar explicitamente.

O novo APK com certificado de release também será incompatível com as betas históricas assinadas por chaves debug variáveis: haverá uma migração única por desinstalação, depois da qual a mesma chave deverá ser reutilizada em todas as atualizações.

## 1.0.6 — Android e iOS
- Android: cópia de segurança das configurações em JSON por meio do seletor de arquivos do sistema, para recuperar preferências após migração única da beta com assinatura de depuração incompatível.
- Android: assinatura de release permanente continua obrigatória para atualizar as próximas versões sem desinstalar.
- iOS: implementação SwiftUI independente, com monitoramento GPS, base offline v3, radar e áudio em segundo plano. Código em ios/; compilação para simulador em Actions. IPA para iPhone exige credenciais Apple e TestFlight/App Store ou distribuição autorizada.

## Google Play
- Dois flavors: `github` (APK do site com OTA e estatísticas opcionais) e `play` (AAB para o Google Play, sem OTA, sem `REQUEST_INSTALL_PACKAGES` e sem acesso à Internet, exigências da política do Play).
- compileSdk/targetSdk 36 (exigência do Play desde 31/08/2026), com telas edge-to-edge que respeitam as barras do sistema.
- AAB: workflow manual **Build Google Play bundle (AAB)**. Passo a passo do Console, Segurança dos dados, declaração do serviço em primeiro plano e regra dos 12 testadores em [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md). Política de privacidade: [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md).
- Compilação local: JDK 17+, Gradle 8.14.3, SDK 36 (`gradle :app:bundlePlayRelease` ou `:app:assembleGithubRelease`).

## Base offline OSM
- A base embarcada no Android e no iOS é definida em `data/osm-db.lock` (URL + SHA-256) e restaurada por `scripts/restore_osm_db.sh`.
- O workflow **Rebuild offline OSM database** (manual e mensal) baixa o extrato Geofabrik atual, gera a base com `tools/build_osm_v3.py`, compara com a atual, publica uma pré-release `osm-db-*` e abre um PR atualizando o lock. Depois do merge, gere uma nova versão do app.
- Radares mapeados ao lado da pista (até 25 m) passam a ser associados à via mais próxima.
- Limites errados devem ser corrigidos no OpenStreetMap (tag `maxspeed`); a correção entra no app na próxima regeneração.

## 1.0.7 — relatos de erro, radar e atualizações
- Linha discreta com coordenadas GPS, precisão e direção abaixo da via. Toque para copiar; **Relatar erro** compartilha local, link do mapa, via, limite exibido, versão do app e da base. Nada é enviado pelo próprio app.
- Bipe curto, uma vez por radar, ao entrar na distância de aviso, mesmo dentro do limite. O tom contínuo de excesso perto do radar continua igual, e o bipe respeita o Silenciar.
- Versão Google Play: aviso de atualização do próprio Play (flexível, reinício só com o veículo parado).
- Opções novas em Configurações: "Bipe curto ao se aproximar de radar" e "Mostrar coordenadas GPS na tela".

## 1.0.8 — base por blocos (Centro-Oeste, Sudeste e Sul) e relatos ao OSM
- O app baixa só os blocos de ~110 km ao redor do motorista (o atual e os 8 vizinhos) e apaga os não usados há 30 dias, respeitando um teto de espaço. Também dá para baixar uma região inteira antes de viajar. Detalhes em [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md).
- Os blocos são atualizados sem versão nova do app: basta fazer o merge do PR do workflow **Rebuild offline OSM database**.
- "Marcar erro" e "Relatos de erro": relatos publicados como notas públicas no OpenStreetMap, só com confirmação.
- As bases são carregadas em segundo plano e substituídas a cada atualização do app.
- O APK do site continua levando a base do DF para uso offline imediato.

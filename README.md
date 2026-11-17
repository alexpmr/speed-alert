# PT2VHF Speed Alert v1.0.5

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
JDK 17, Gradle 8.10.2, SDK 35, Python 3 e osmium. O Actions baixa o extrato Centro-Oeste Geofabrik e gera SQLite v3.

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
Versão 1.0 (patch 5) escolhida por ser posterior às betas 1.0.3 e 1.0.4, mantendo compatibilidade com a comparação semântica do OTA. O versionCode é 105.

**Requisito de publicação:** assinatura Android permanente armazenada nos secrets do GitHub Actions (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). O APK debug de CI é **somente para QA**, nunca para instalação estável/OTA. Se secrets estiverem ausentes, a publicação deve falhar explicitamente.

O novo APK com certificado de release também será incompatível com as betas históricas assinadas por chaves debug variáveis: haverá uma migração única por desinstalação, depois da qual a mesma chave deverá ser reutilizada em todas as atualizações.

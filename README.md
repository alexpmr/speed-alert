# PT2VHF Speed Alert v1.0.3-beta.1

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

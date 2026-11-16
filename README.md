# PT2VHF Speed Alert — v1.0.1-beta.1

Aplicativo Android independente para avisar excesso de velocidade, inclusive sem radar.

## Recursos entregues
- Ícone próprio do Speed Alert (velocímetro e sinal de advertência).
- Tela **Configurações**: tolerância, volume, duração e intervalo dos bipes, som contínuo ou bipes, base offline, limite manual, manter tela ligada, aviso de limite desconhecido.
- **Iniciar ligado**: por padrão o serviço de monitoramento é iniciado automaticamente ao abrir o app, após autorização de GPS. Não inicia sozinho ao ligar o telefone.
- GPS e serviço em primeiro plano, inclusive com tela apagada durante a sessão.
- Base OpenStreetMap offline de velocidades explícitas do Distrito Federal e entorno.
- Alerta quando velocidade GPS > limite cadastrado + tolerância; por padrão, 0 km/h.
- Sem permissão de Internet, sem mapas e sem integração a radares.

## Configurações padrão
Tolerância 0 km/h; volume relativo 80%; bipe 250 ms, intervalo 350 ms; offline ligado; monitoramento automático ligado; limite manual desligado.

## Compilação
Gradle 8.10.2, JDK 17, Android SDK 35. O GitHub Actions baixa o extrato Geofabrik Centro-Oeste e gera o banco SQLite incluído no APK.

## Atenção
Versão beta de testes, sem validação em campo. Limites da base OSM podem estar ausentes ou incorretos, e identificação de pistas paralelas é incerta. Sinalização da via prevalece. Builds de debug do GitHub Actions podem ter assinaturas distintas; para instalar esta beta, pode ser necessário desinstalar a anterior (com perda de preferências).

Dados: © OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright

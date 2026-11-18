# Publicação no Google Play — PT2VHF Speed Alert

## Duas variantes (product flavors)
| Flavor | Saída | Distribuição | OTA do site | Internet/estatísticas |
|---|---|---|---|---|
| `github` | APK | pt2vhf.com / GitHub Releases | sim | sim (opt-out) |
| `play` | AAB | Google Play | **não** (política do Play proíbe auto-atualização) | só relatos de erro confirmados pelo usuário (notas do OSM) |

O mesmo `applicationId` é usado nas duas; um aparelho só pode ter uma delas. Quem instalou pelo Play recebe atualizações só pelo Play.

## 1. Chave de upload (uma vez, no seu Windows)
```
powershell -ExecutionPolicy Bypass -File scripts/setup_release_signing.ps1
```
Gera `Documents\PT2VHF\SpeedAlert-Signing\speed-alert-release.jks` e grava os 4 secrets no GitHub. No Play essa chave é a **chave de upload**; o Google guarda a chave de assinatura do app (Play App Signing). Faça backup offline do `.jks` e da senha.

## 2. Gerar o AAB
GitHub → Actions → **Build Google Play bundle (AAB)** → Run workflow (track `none`). Baixe o artefato `PT2VHF-Speed-Alert-Google-Play-AAB`.

O workflow falha se o manifest do flavor `play` contiver `REQUEST_INSTALL_PACKAGES` ou `INTERNET`, ou se o targetSdk não for 36.

## 3. Play Console — configuração do app
- **Criar app:** nome "PT2VHF Speed Alert", idioma pt-BR, App, Gratuito.
- **Política de privacidade:** `https://alexpmr.github.io/speed-alert/privacidade.html`, servida pelo GitHub Pages a partir de `docs/privacidade.html` (Settings → Pages → Deploy from a branch → `main` / `/docs`). O app também abre esse link em Configurações, como o Play exige. Ao mudar `docs/PRIVACY_POLICY.md`, gere de novo o HTML.
- **Acesso ao app:** todo o app está disponível sem login.
- **Anúncios:** não contém anúncios.
- **Classificação de conteúdo:** categoria "Utilitário/Produtividade"; respostas todas "não" → Livre.
- **Público-alvo:** 18+.
- **Segurança dos dados** (a partir da 1.0.8, por causa dos relatos ao OSM):
  - "O app coleta ou compartilha dados?" → **Sim**.
  - **Localização → Localização precisa**: **coletada**, **opcional** (só quando o usuário publica um relato), finalidade **Funcionalidade do app**.
  - **Localização → Localização aproximada**: **coletada**, **obrigatória**, finalidade **Funcionalidade do app**, **processamento efêmero**. O download dos blocos de ~110 km revela ao servidor (GitHub) em que região o aparelho está. Nenhum identificador é enviado.
  - **Mensagens/Outro conteúdo gerado pelo usuário** (texto do relato): **coletado**, opcional, mesma finalidade.
  - **Compartilhamento**: o envio ao OpenStreetMap é feito por ação explícita do usuário, que vê o texto e confirma "Publicar". Isso se enquadra na exceção do Play para transferências iniciadas pelo usuário, então marque "não compartilhado". Se preferir a declaração mais conservadora, marque "compartilhado".
  - Criptografia em trânsito: **Sim** (HTTPS). Exclusão: relatos locais podem ser excluídos no app; notas publicadas seguem o processo da OSMF.
  - Confira as definições atuais no próprio formulário antes de enviar. Na dúvida, declare a mais.
- **Apps governamentais / financeiros / saúde:** não.
- **Categoria:** Mapas e navegação. E-mail de contato obrigatório.
- **Permissões de serviço em primeiro plano** (Conteúdo do app → Permissões de serviço em primeiro plano):
  - Tipo: **Localização**.
  - Descrição sugerida: "O app alerta o motorista quando a velocidade medida pelo GPS excede o limite da via. O serviço de localização em primeiro plano mantém os alertas sonoros ativos com a tela bloqueada ou com um app de navegação (Waze/Google Maps) em primeiro plano. Ele só inicia depois que o usuário aceita o aviso inicial e pode ser parado pelo botão Parar da notificação."
  - Vídeo (YouTube não listado, ~30 s): abrir o app → aceitar o aviso → conceder localização → mostrar a notificação → ir para outro app/bloquear a tela → simular excesso (limite manual baixo, ex.: 10 km/h) e mostrar o alarme tocando.
- **Localização em segundo plano:** não é solicitada (`ACCESS_BACKGROUND_LOCATION` ausente); não há declaração a fazer.

## 4. Ficha da loja
- Ícone 512×512 PNG (32 bits, sem transparência nas bordas é recomendado).
- Gráfico de recursos 1024×500.
- Mínimo de 2 capturas de telefone (ex.: tela principal verde, vermelha em excesso, configurações).
- Descrição curta (até 80 caracteres), por exemplo: "Alerta de velocidade offline com limites de vias e radares do DF e entorno."
- Na descrição completa, diga que a cobertura é DF e entorno, que a base é OSM e que a sinalização oficial prevalece.

## 5. Testes
1. **Teste interno** (opcional, até 100 testadores, sem revisão demorada): suba o AAB, adicione a lista de e-mails e compartilhe o link de opt-in.
2. **Teste fechado (Alpha)**, obrigatório para **contas pessoais criadas após 13/11/2023**: são necessários **12 testadores com opt-in ativo por 14 dias seguidos**. Ter os e-mails cadastrados não basta; cada pessoa precisa abrir o link de opt-in, aceitar e instalar pelo Play.
3. Depois dos 14 dias: Painel → **Solicitar acesso à produção** (questionário sobre o teste).

Contas de organização (CNPJ/D-U-N-S) não têm a regra dos 12 testadores.

## 6. Upload automático (opcional)
Depois que o **primeiro AAB foi enviado manualmente**:
1. Google Cloud: crie uma conta de serviço e uma chave JSON.
2. Play Console → Usuários e permissões → convide o e-mail da conta de serviço com permissão de lançar em trilhas de teste.
3. Grave o JSON no secret `PLAY_SERVICE_ACCOUNT_JSON`.
4. Rode o workflow com track `internal` ou `alpha`. Use status `draft` enquanto o app nunca tiver passado por revisão.

## Base offline por blocos (1.0.8)
- O mapa de limites e radares é dividido em blocos de 1° × 1° (cerca de 110 km) cobrindo Centro-Oeste, Sul e Sudeste.
- O app baixa automaticamente o bloco onde o motorista está e os 8 vizinhos, quando há Internet (ou só no Wi-Fi, se o usuário preferir).
- Blocos sem uso há 30 dias são apagados. Os automáticos respeitam um teto de espaço (padrão 500 MB).
- Em Configurações, "Baixar Centro-Oeste / Sudeste / Sul para viagem" guarda uma região inteira, que não é apagada automaticamente.
- **Hospedagem:** releases `osm-tiles-*` do GitHub. O índice `data/tiles.json` do branch `main` é lido pelo app (via raw.githubusercontent.com). Para trocar de servidor, mude `TileStore.MANIFEST_URL` e o `base_url` gerado.
- **Atualizar as bases:** rode **Rebuild offline OSM database** e faça o merge do PR que ele abrir. Os apps instalados baixam os blocos novos sem precisar de versão nova.
- O AAB não carrega nenhuma base. O APK do site continua levando a base do DF, para funcionar offline desde o primeiro uso.

## 7. Atualizações para os usuários
- O Play atualiza automaticamente conforme a configuração de cada aparelho (normalmente em 1–2 dias, no Wi-Fi).
- O flavor `play` usa a **Play In-App Updates API** (atualização flexível). Ao abrir o app, se houver versão nova, o Play mostra "Atualizar" e baixa em segundo plano, sem parar o monitoramento. O pedido para reiniciar só aparece com o veículo parado (< 5 km/h). Se o usuário recusar, o app pergunta de novo no máximo uma vez a cada 24 h por versão.
- Só funciona em instalações feitas pelo Play (teste interno ou fechado servem para testar). Para ver o aviso, publique uma versão com `versionCode` maior.
- Segurança dos dados: a biblioteca não adiciona permissões, e a comunicação é feita com o app da Play Store. Confira a orientação atual do Google sobre as bibliotecas Play Core antes de enviar o formulário.

## 8. Cada nova versão
Aumente `versionCode` (e `versionName`) em `app/build.gradle.kts`; o Play rejeita versionCode repetido.

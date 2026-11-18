# Publicação no Google Play — PT2VHF Speed Alert

## Duas variantes (product flavors)
| Flavor | Saída | Distribuição | OTA do site | Internet/estatísticas |
|---|---|---|---|---|
| `github` | APK | pt2vhf.com / GitHub Releases | sim | sim (opt-out) |
| `play` | AAB | Google Play | **não** (política do Play proíbe auto-atualização) | **não** (sem permissão INTERNET) |

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
- **Política de privacidade:** publique `docs/PRIVACY_POLICY.md` em uma URL pública (ex.: `https://www.pt2vhf.com/speed/privacidade`) e informe a URL.
- **Acesso ao app:** todo o app está disponível sem login.
- **Anúncios:** não contém anúncios.
- **Classificação de conteúdo:** categoria "Utilitário/Produtividade"; respostas todas "não" → Livre.
- **Público-alvo:** 18+.
- **Segurança dos dados:**
  - "O app coleta ou compartilha algum dos tipos de dados obrigatórios?" → **Não**.
  - Justificativa: a localização é processada somente no aparelho e a variante Play não tem permissão de Internet. Dados processados só no aparelho não contam como coletados.
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

## 7. Atualizações para os usuários
- O Play atualiza automaticamente conforme a configuração de cada aparelho (normalmente em 1–2 dias, no Wi-Fi).
- O flavor `play` usa a **Play In-App Updates API** (atualização flexível). Ao abrir o app, se houver versão nova, o Play mostra "Atualizar" e baixa em segundo plano, sem parar o monitoramento. O pedido para reiniciar só aparece com o veículo parado (< 5 km/h). Se o usuário recusar, o app pergunta de novo no máximo uma vez a cada 24 h por versão.
- Só funciona em instalações feitas pelo Play (teste interno ou fechado servem para testar). Para ver o aviso, publique uma versão com `versionCode` maior.
- Segurança dos dados: a biblioteca não adiciona permissões, e a comunicação é feita com o app da Play Store. Confira a orientação atual do Google sobre as bibliotecas Play Core antes de enviar o formulário.

## 8. Cada nova versão
Aumente `versionCode` (e `versionName`) em `app/build.gradle.kts`; o Play rejeita versionCode repetido.

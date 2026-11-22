# Instalação no iPhone — PT2VHF Speed Alert iOS 1.0.9

O projeto iOS já compila no GitHub Actions para simulador. Um pacote .app de simulador não
pode ser instalado no iPhone. Para instalar num dispositivo físico, é necessário assinar
o aplicativo com uma conta Apple e um perfil de provisionamento compatível.

## Opção A — instalar no próprio iPhone pelo Mac (teste de desenvolvimento)
1. Instale Xcode 16 ou posterior, XcodeGen e conecte o iPhone ao Mac.
2. Faça login em Xcode > Settings > Accounts com sua Conta Apple.
3. Na raiz do repositório, execute: bash ios/scripts/prepare_offline.sh
4. Execute: cd ios && xcodegen generate --spec project.yml
5. Abra ios/PT2VHFSpeedAlert.xcodeproj.
6. No target PT2VHFSpeedAlert > Signing & Capabilities, selecione Automatically manage signing
   e escolha seu Apple Developer Team ou Personal Team.
7. Ajuste a configuração CODE_SIGNING_ALLOWED para YES na compilação em dispositivo.
   Se o Bundle Identifier estiver indisponível na sua equipe, escolha um identificador
   exclusivo para instalação pessoal; na distribuição oficial ele é
   com.pt2vhf.speedalert.ios.
8. Selecione o iPhone como destino e use Product > Run.
9. Autorize a confiança no computador e Developer Mode quando o iOS solicitar.

Esse procedimento testa o app no seu próprio aparelho. Uma Personal Team não
publica TestFlight nem um IPA para instalar em iPhones de terceiros.

## Opção B — gerar IPA Ad Hoc assinado pelo GitHub Actions
Requer adesão ao Apple Developer Program e o UDID de cada aparelho de teste.
O IPA Ad Hoc só pode ser instalado nos iPhones cadastrados no perfil Apple.

1. Em Apple Developer > Certificates, Identifiers & Profiles, crie o App ID explícito
   com o identificador com.pt2vhf.speedalert.ios e registre os UDIDs dos iPhones.
2. Gere um certificado Apple Distribution e exporte a chave privada como .p12
   protegido por senha. Crie também um perfil de provisionamento Ad Hoc que inclua
   os iPhones registrados e o App ID acima.
3. Em GitHub > alexpmr/speed-alert > Settings > Secrets and variables > Actions, cadastre:
   IOS_TEAM_ID (10 caracteres), IOS_DISTRIBUTION_P12_BASE64 (conteúdo do .p12
   convertido para Base64), IOS_DISTRIBUTION_P12_PASSWORD (senha do .p12) e
   IOS_ADHOC_PROFILE_BASE64 (conteúdo Base64 do .mobileprovision).
   Não envie esses dados por chat e não os adicione ao Git.
4. Execute o workflow Build signed iOS IPA for registered iPhones em Actions.
5. Após a validação, baixe o artefato PT2VHF-Speed-Alert-iOS-v1.0.9-AdHoc-SIGNED.
   Ele contém o IPA e o SHA256SUMS.txt. Confirme o SHA-256 antes de distribuir.
6. Instale o IPA por Apple Configurator, Xcode ou outro método autorizado de
   instalação Ad Hoc, somente nos aparelhos presentes no perfil.

Workflow: https://github.com/alexpmr/speed-alert/actions/workflows/ios-ipa.yml

## Opção C — distribuição pública por TestFlight ou App Store
O método recomendado para outras pessoas é arquivar e enviar o app assinado para
App Store Connect por Xcode, configurar TestFlight e divulgar um convite depois
que a build estiver disponível. Não existe link TestFlight confirmado nesta versão.
Um arquivo .ipa não equivale a um APK instalável livremente em qualquer iPhone.

Referências Apple:
https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices/
https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases/

## Manual de uso
1. Abra o aplicativo e leia o aviso legal; escolha Continuar para ativar o monitoramento.
2. Autorize localização precisa. Para usar o áudio com Waze ou Mapas em primeiro
   plano, conceda a localização em segundo plano quando oferecida.
3. A placa redonda indica o limite registrado na base offline, se conhecido;
   o painel central mostra a velocidade atual obtida pelo GPS, sem casas decimais.
4. Verde = abaixo de 95% do limite; amarelo = entre 95% e 100% do limite;
   vermelho piscando = acima do limite. Se não houver limite identificado,
   o indicador será neutro.
5. Em excesso, o alarme sonoro selecionado é reproduzido enquanto houver GPS e
   monitoramento ativo. O botão "Som ligado / Som DESLIGADO" mostra o estado atual;
   o silêncio vale até o monitoramento recomeçar.
6. Em Configurações, ajuste tolerância (padrão 0 km/h), volume (80%), tipo de som,
   manter tela ligada (ativado) e limite manual (10 a 160 km/h). "Testar som" toca 2 s.
7. A base do DF vem no app. Fora dela, o app baixa do OpenStreetMap áreas de ~11 km
   ao redor (precisa de Internet na primeira passagem). Sem Internet, GPS e limites
   continuam funcionando nas regiões que a base
   cartográfica contém. O app não conhece todas as ruas ou velocidades do mundo.
8. O monitoramento em segundo plano depende do iOS, da autorização e do estado
   do aplicativo. Encerrá-lo à força pode impedir os alertas.
9. Não use o app como substituto da sinalização. Os limites podem estar
   incorretos ou desatualizados. Não interaja com a tela enquanto dirige.

© OpenStreetMap contributors — ODbL 1.0.

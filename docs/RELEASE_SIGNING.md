# Assinatura permanente — PT2VHF Speed Alert 1.0

As versões beta anteriores usavam certificados Android de depuração diferentes em cada compilação. Um APK com assinatura diferente não substitui o aplicativo instalado com o mesmo package id.

A release estável precisa de uma chave privada permanente, com backup offline criptografado. Nunca faça commit do arquivo de chave no GitHub e nunca o compartilhe no chat.

## Preparação Windows
1. Instale JDK 17+ e GitHub CLI (gh), execute gh auth login.
2. Execute no PowerShell: powershell -ExecutionPolicy Bypass -File scripts/setup_release_signing.ps1
3. Digite uma senha forte com pelo menos 20 caracteres.
4. O script cria ou reutiliza o arquivo em Documents\PT2VHF\SpeedAlert-Signing\speed-alert-release.jks, fora do repositório.
5. Ele configura os quatro secrets no repositório alexpmr/speed-alert:
   - ANDROID_KEYSTORE_BASE64
   - ANDROID_KEYSTORE_PASSWORD
   - ANDROID_KEY_ALIAS
   - ANDROID_KEY_PASSWORD
6. Publique com: gh workflow run release-speed-v1.yml -R alexpmr/speed-alert

O GitHub Actions não publicará APK debug como release estável. O workflow falhará se a chave estiver ausente.

Ao migrar de uma beta assinada com certificado de depuração para esta primeira versão com assinatura permanente, é provável que seja necessário desinstalar a beta, o que apaga preferências. Após essa migração, todas as próximas versões devem reutilizar exatamente o mesmo arquivo de chave e alias.

A página www.pt2vhf.com/speed e o manifesto OTA só serão atualizados após a existência da release assinada e da verificação do SHA-256.

A liberação técnica não substitui homologação prática de GPS, áudio, limites de vias e radares. A sinalização oficial prevalece.

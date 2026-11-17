# Configurar assinatura permanente da release PT2VHF Speed Alert no GitHub.
# Executar no PowerShell em computador confiavel com JDK 17+ e GitHub CLI (gh).
# Nenhum segredo e salvo no repositorio publico.

$ErrorActionPreference = 'Stop'
$repo = 'alexpmr/speed-alert'
$alias = 'speedalert'
$folder = Join-Path $env:USERPROFILE 'Documents\PT2VHF\SpeedAlert-Signing'
$keyPath = Join-Path $folder 'speed-alert-release.jks'

foreach ($command in @('gh', 'keytool')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Comando necessario nao encontrado: $command"
    }
}
& gh auth status -h github.com
if ($LASTEXITCODE -ne 0) { throw 'Autentique-se primeiro com gh auth login' }

New-Item -ItemType Directory -Path $folder -Force | Out-Null
$secure = Read-Host -Prompt 'Senha da chave (minimo: 20 caracteres)' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $secure).Password
if ($password.Length -lt 20) { throw 'Use pelo menos 20 caracteres.' }

if (-not (Test-Path -LiteralPath $keyPath)) {
    $confirmation = Read-Host -Prompt 'Repita a senha para criar uma nova chave' -AsSecureString
    $confirmed = [System.Net.NetworkCredential]::new('', $confirmation).Password
    if ($password -cne $confirmed) { throw 'Senhas diferentes.' }
    $confirmed = $null
    & keytool -genkeypair -noprompt -keystore $keyPath -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=PT2VHF Speed Alert, O=PT2VHF, C=BR' -storepass $password -keypass $password
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar a chave.' }
} else {
    Write-Host 'Reutilizando keystore existente; a chave nao sera substituida.'
}
& keytool -list -keystore $keyPath -storepass $password -alias $alias
if ($LASTEXITCODE -ne 0) { throw 'Chave, alias ou senha incorreta.' }

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
$base64  | gh secret set ANDROID_KEYSTORE_BASE64 -R $repo
if ($LASTEXITCODE -ne 0) { throw 'Falha ANDROID_KEYSTORE_BASE64' }
$password | gh secret set ANDROID_KEYSTORE_PASSWORD -R $repo
if ($LASTEXITCODE -ne 0) { throw 'Falha ANDROID_KEYSTORE_PASSWORD' }
$alias    | gh secret set ANDROID_KEY_ALIAS -R $repo
if ($LASTEXITCODE -ne 0) { throw 'Falha ANDROID_KEY_ALIAS' }
$password | gh secret set ANDROID_KEY_PASSWORD -R $repo
if ($LASTEXITCODE -ne 0) { throw 'Falha ANDROID_KEY_PASSWORD' }

$base64 = $null
$password = $null
$secure.Dispose()
Write-Host ''
Write-Host 'Segredos configurados. Chave privada permanece no computador.'
Write-Host ('Faça backup offline criptografado: ' + $keyPath)
Write-Host 'Para publicar, execute:'
Write-Host ('gh workflow run release-speed-v1.yml -R ' + $repo)

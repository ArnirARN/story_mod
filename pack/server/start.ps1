# Starts the Story Mod server on Windows. See start.sh for what it does.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$PackUrl = '__PACK_URL__'

$pack = (Invoke-WebRequest -UseBasicParsing $PackUrl).Content
$neo = [regex]::Match($pack, 'neoforge\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $neo) { Write-Host "Не удалось прочитать версию NeoForge из $PackUrl"; exit 1 }
if (-not (Test-Path "libraries/net/neoforged/neoforge/$neo")) {
    Write-Host "Ставлю NeoForge $neo..."
    Invoke-WebRequest -UseBasicParsing "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neo/neoforge-$neo-installer.jar" -OutFile neoforge-installer.jar
    java -jar neoforge-installer.jar --installServer
    Remove-Item neoforge-installer.jar, neoforge-installer.jar.log -ErrorAction SilentlyContinue
}

if (-not ((Test-Path eula.txt) -and (Select-String -Quiet 'eula=true' eula.txt))) {
    Write-Host "Нужно принять лицензию Minecraft: https://aka.ms/MinecraftEULA"
    $answer = Read-Host "Принимаете? (y/n)"
    if ($answer -eq 'y') { Set-Content eula.txt 'eula=true' } else { exit 1 }
}

Write-Host "Обновляю моды..."
java -jar packwiz-installer-bootstrap.jar -g -s server $PackUrl
# The story mod stops the server after it updates itself; start it again in that case.
while ($true) {
    Remove-Item storymod-restart.flag -ErrorAction SilentlyContinue
    & .\run.bat nogui
    if (Test-Path storymod-restart.flag) { Write-Host "Сборка обновилась, перезапускаю сервер..."; continue }
    break
}

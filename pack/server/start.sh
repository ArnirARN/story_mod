#!/usr/bin/env bash
# Starts the Story Mod server. On every start it downloads/updates the server mods from the pack,
# and installs the NeoForge version the pack needs the first time (or when the pack moves to a newer one).
set -e
cd "$(dirname "$0")"
PACK_URL="__PACK_URL__"

NEO=$(curl -fsSL "$PACK_URL" | grep -E '^neoforge *=' | cut -d'"' -f2)
if [ -z "$NEO" ]; then echo "Не удалось прочитать версию NeoForge из $PACK_URL"; exit 1; fi
if [ ! -d "libraries/net/neoforged/neoforge/$NEO" ]; then
  echo "Ставлю NeoForge $NEO..."
  curl -fL -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
  java -jar neoforge-installer.jar --installServer
  rm -f neoforge-installer.jar neoforge-installer.jar.log
fi

if ! grep -q "eula=true" eula.txt 2>/dev/null; then
  echo "Нужно принять лицензию Minecraft: https://aka.ms/MinecraftEULA"
  read -r -p "Принимаете? (y/n) " answer
  if [ "$answer" = "y" ]; then echo "eula=true" > eula.txt; else exit 1; fi
fi

echo "Обновляю моды..."
java -jar packwiz-installer-bootstrap.jar -g -s server "$PACK_URL"
# The story mod stops the server after it updates itself; start it again in that case.
while true; do
  rm -f storymod-restart.flag
  ./run.sh nogui || true
  if [ -f storymod-restart.flag ]; then echo "Сборка обновилась, перезапускаю сервер..."; continue; fi
  break
done

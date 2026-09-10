#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/home/thomas/.local/temurin-25
export PATH="$JAVA_HOME/bin:$PATH"
cd /home/thomas/workspace/TotemNexus
artifacts=test-artifacts/friends-and-source-fix
../TotemCore/gradlew build -x runGameTest --no-daemon --console=plain > "$artifacts/resource-build.log" 2>&1
xvfb-run -a ../TotemCore/gradlew runClientGameTest --no-daemon --console=plain > "$artifacts/client.log" 2>&1
mkdir -p "$artifacts/screenshots"
cp build/run/clientGameTest/screenshots/*nexus-friend-target* "$artifacts/screenshots/"
cp build/run/clientGameTest/screenshots/*nexus-player-source-array-preview* "$artifacts/screenshots/"
cp build/run/clientGameTest/screenshots/*nexus-observer-friend-and-portable-preview* "$artifacts/screenshots/"
../TotemCore/gradlew jar --no-daemon --console=plain > "$artifacts/jar.log" 2>&1
../TotemCore/gradlew -I "$artifacts/production.init.gradle" runRecoveryProductionClientGameTest --no-daemon --console=plain > "$artifacts/production.log" 2>&1
bash "$artifacts/observer-e2e.sh" > "$artifacts/e2e.log" 2>&1

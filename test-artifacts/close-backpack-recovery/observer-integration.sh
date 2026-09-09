#!/usr/bin/env bash
set -euo pipefail
export GRADLE_USER_HOME=/tmp/remnant-doubleclick-observer-gradle
export GITHUB_WORKSPACE=/home/thomas/workspace/TotemVanillaTweaks
export JAVA_HOME=/home/thomas/.local/temurin-25
export PATH="$JAVA_HOME/bin:$PATH"
cd /home/thomas/workspace/TotemVanillaTweaks
props=(
-PtotemCoreJar=/home/thomas/workspace/TotemCore/build/libs/totem-core-0.7.19.jar
-PtotemRemnantJar=/home/thomas/workspace/TotemRemnant/build/libs/totem-remnant-0.2.24.jar
-PtotemAutomataJar=/tmp/remnant-022-observer-deps/TotemAutomata/build/libs/totem-automata-0.1.24.jar
-PtotemNexusJar=/home/thomas/workspace/TotemNexus/build/libs/totem-nexus-0.3.19.jar
-PtotemVillagersJar=/tmp/remnant-022-observer-deps/TotemVillagers/build/libs/totem-villagers-0.1.36.jar
-PtotemLocksmithJar=/tmp/remnant-022-observer-deps/TotemLocksmith/build/libs/totem-locksmith-0.1.10.jar
)
if [[ -d build/owner-present-integration-screenshots ]]; then mv build/owner-present-integration-screenshots "build/owner-present-integration-screenshots-before-recovery-$(date +%s)"; fi
xvfb-run -a ./gradlew "${props[@]}" runIntegrationClientGametest --no-daemon --stacktrace
test "$(find build/owner-present-integration-screenshots -maxdepth 1 -name '*.png' | wc -l)" = 10

#!/usr/bin/env bash
set -euo pipefail
export GRADLE_USER_HOME=/tmp/remnant-doubleclick-observer-gradle
export JAVA_HOME=/home/thomas/.local/temurin-25
export PATH="$JAVA_HOME/bin:$PATH"
export GITHUB_WORKSPACE=/home/thomas/workspace/TotemVanillaTweaks
cd "$GITHUB_WORKSPACE"
if [[ -d build/e2e ]]; then mv build/e2e "build/e2e-before-friends-source-$(date +%s)"; fi
set -euo pipefail
core_version=0.7.19
core_jar="/home/thomas/workspace/TotemCore/build/libs/totem-core-${core_version}.jar"
results="${GITHUB_WORKSPACE}/build/e2e/results"
mkdir -p build/e2e/server build/e2e/target build/e2e/observer "$results"


printf 'eula=true\n' > build/e2e/server/eula.txt
cat > build/e2e/server/server.properties <<'EOF'
server-port=25570
online-mode=false
enforce-secure-profile=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=4
motd=Totem Observer E2E
EOF

# Resolve/compile once before the server join deadline exists. Two
# concurrent Gradle runs contend for Loom's global cache and write the
# same build/classes tree, so prepare immutable launch inputs first
# and then start the Minecraft client JVMs directly in parallel.
client_launch_cache="${GITHUB_WORKSPACE}/build/e2e/gradle-clients"
launch_dir="${GITHUB_WORKSPACE}/build/e2e/launch"
mkdir -p "$launch_dir"
./gradlew -PtotemCoreJar="$core_jar" \
  -PtotemRemnantJar="/home/thomas/workspace/TotemRemnant/build/libs/totem-remnant-0.2.24.jar" \
  -PtotemAutomataJar="/tmp/remnant-022-observer-deps/TotemAutomata/build/libs/totem-automata-0.1.24.jar" \
  -PtotemNexusJar="/home/thomas/workspace/TotemNexus/build/libs/totem-nexus-0.3.19.jar" \
  -PtotemVillagersJar="/tmp/remnant-022-observer-deps/TotemVillagers/build/libs/totem-villagers-0.1.36.jar" \
  -PtotemLocksmithJar="/tmp/remnant-022-observer-deps/TotemLocksmith/build/libs/totem-locksmith-0.1.10.jar" \
  -Pe2eLaunchInputsDir="$launch_dir" \
  --project-cache-dir "$client_launch_cache" \
  prepareE2eClientLaunchInputs \
  --no-daemon --stacktrace

launch_cfg="$launch_dir/launch.cfg"
target_args="$launch_dir/runE2eTarget.args"
observer_args="$launch_dir/runE2eObserver.args"
java_bin="${JAVA_HOME}/bin/java"
for required in "$launch_cfg" "$target_args" "$observer_args" "$java_bin"; do
  if [[ ! -s "$required" ]]; then
    echo "Missing prepared E2E launch input: $required" >&2
    exit 1
  fi
done
if ! grep -Fq 'fabric.defaultModDistributionNamespace=official' "$launch_cfg"; then
  echo 'Prepared E2E launch.cfg is not in the official namespace.' >&2
  exit 1
fi
if grep -Eqi 'org\.relativitymc|modern-yarn' "$launch_cfg" "$target_args" "$observer_args"; then
  echo 'Prepared E2E launch inputs contain stale mapped dependencies.' >&2
  exit 1
fi
if ! cmp -s "$target_args" "$observer_args"; then
  echo 'Target and Observer E2E classpath argfiles differ.' >&2
  exit 1
fi
for client_dir in build/e2e/target build/e2e/observer; do
  printf '%s\n' \
    'onboardAccessibility:false' \
    'skipMultiplayerWarning:true' \
    'tutorialStep:none' > "$client_dir/options.txt"
done

server_pid=''
target_pid=''
observer_pid=''
cleanup() {
  set +e
  for pid in "$observer_pid" "$target_pid" "$server_pid"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  for pid in "$observer_pid" "$target_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then
      wait "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

./gradlew -PtotemCoreJar="$core_jar" \
  -PtotemRemnantJar="/home/thomas/workspace/TotemRemnant/build/libs/totem-remnant-0.2.24.jar" \
  -PtotemAutomataJar="/tmp/remnant-022-observer-deps/TotemAutomata/build/libs/totem-automata-0.1.24.jar" \
  -PtotemNexusJar="/home/thomas/workspace/TotemNexus/build/libs/totem-nexus-0.3.19.jar" \
  -PtotemVillagersJar="/tmp/remnant-022-observer-deps/TotemVillagers/build/libs/totem-villagers-0.1.36.jar" \
  -PtotemLocksmithJar="/tmp/remnant-022-observer-deps/TotemLocksmith/build/libs/totem-locksmith-0.1.10.jar" \
  --project-cache-dir "${GITHUB_WORKSPACE}/build/e2e/gradle-server" \
  runE2eServer --no-daemon --stacktrace > build/e2e/server.log 2>&1 &
server_pid=$!

ready=false
for _ in $(seq 1 180); do
  if (echo > /dev/tcp/127.0.0.1/25570) >/dev/null 2>&1; then
    ready=true
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo 'E2E server exited before opening port 25570.' >&2
    tail -n 200 build/e2e/server.log >&2 || true
    exit 1
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo 'Timed out waiting for E2E server port 25570.' >&2
  tail -n 200 build/e2e/server.log >&2 || true
  exit 1
fi

(
  cd build/e2e/target
  exec xvfb-run -a "$java_bin" @"$target_args" \
    -Dfabric.dli.config="$launch_cfg" \
    -Dfabric.dli.env=client \
    -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient \
    -Dtotem.observer.e2e.enabled=true \
    -Dtotem.observer.e2e.results="$results" \
    -Dtotem.observer.e2e.role=target \
    --sun-misc-unsafe-memory-access=allow \
    --enable-native-access=ALL-UNNAMED \
    -Dfile.encoding=UTF-8 \
    net.fabricmc.devlaunchinjector.Main --username Target
) > build/e2e/target.log 2>&1 &
target_pid=$!

# Start both clients from the same server-ready edge; all expensive
# Gradle/Loom preparation completed before the first client can join.
(
  cd build/e2e/observer
  exec xvfb-run -a "$java_bin" @"$observer_args" \
    -Dfabric.dli.config="$launch_cfg" \
    -Dfabric.dli.env=client \
    -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient \
    -Dtotem.observer.e2e.enabled=true \
    -Dtotem.observer.e2e.results="$results" \
    -Dtotem.observer.e2e.role=observer \
    --sun-misc-unsafe-memory-access=allow \
    --enable-native-access=ALL-UNNAMED \
    -Dfile.encoding=UTF-8 \
    net.fabricmc.devlaunchinjector.Main --username Observer
) > build/e2e/observer.log 2>&1 &
observer_pid=$!

target_ready=false
for _ in $(seq 1 180); do
  if compgen -G "$results/failure-*.txt" >/dev/null; then
    cat "$results"/failure-*.txt >&2 || true
    exit 1
  fi
  if [[ -f "$results/target-world-ready.txt" ]]; then
    target_ready=true
    break
  fi
  for spec in "$target_pid:target" "$observer_pid:observer"; do
    pid="${spec%%:*}"
    name="${spec##*:}"
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "E2E ${name} exited before Target stabilized in gameplay view." >&2
      tail -n 200 "build/e2e/${name}.log" >&2 || true
      exit 1
    fi
  done
  sleep 1
done
if [[ "$target_ready" != true ]]; then
  echo 'Timed out waiting for E2E Target gameplay view to stabilize.' >&2
  tail -n 200 build/e2e/target.log >&2 || true
  tail -n 200 build/e2e/observer.log >&2 || true
  exit 1
fi

complete=false
for _ in $(seq 1 360); do
  if compgen -G "$results/failure-*.txt" >/dev/null; then
    echo 'Observer E2E reported a failure:' >&2
    cat "$results"/failure-*.txt >&2 || true
    exit 1
  fi

  # Includes 105 explicit late-family assertions: 15 semantic families x 7 lifecycle files.
  if [[ -f "$results/server-session-started.txt" \
     && -f "$results/target-world-ready.txt" \
     && -f "$results/target-native-state-enabled.txt" \
     && -f "$results/target-native-no-frame.txt" \
     && -f "$results/observer-native-state-ok.txt" \
     && -f "$results/observer-native-camera-ok.txt" \
     && -f "$results/observer-native-hud-ok.txt" \
     && -f "$results/observer-native-world-settled.txt" \
     && -f "$results/observer-native-world-saved.txt" \
     && -f "$results/observer-native-world.png" \
     && -f "$results/observer-native-hud.png" \
     && -f "$results/observer-ready-for-container.txt" \
     && -f "$results/target-native-container-opened.txt" \
     && -f "$results/target-native-container-state-sent.txt" \
     && -f "$results/observer-native-container-ok.txt" \
     && -f "$results/observer-native-container-saved.txt" \
     && -f "$results/observer-native-container.png" \
     && -f "$results/target-native-container-closed.txt" \
     && -f "$results/target-native-crafting-sender-priority-suppressed.txt" \
     && -f "$results/observer-native-crafting-mirror-priority-suppressed.txt" \
     && -f "$results/observer-ready-for-generic-screen.txt" \
     && -f "$results/target-native-generic-opened.txt" \
     && -f "$results/target-native-generic-no-frame.txt" \
     && -f "$results/observer-native-generic-screen-ok.txt" \
     && -f "$results/observer-native-generic-screen-saved.txt" \
     && -f "$results/observer-native-generic-screen.png" \
     && -f "$results/target-native-generic-closed.txt" \
     && -f "$results/observer-ready-for-merchant.txt" \
     && -f "$results/target-native-merchant-state-sent.txt" \
     && -f "$results/observer-native-merchant-ok.txt" \
     && -f "$results/observer-native-merchant-saved.txt" \
     && -f "$results/observer-native-merchant.png" \
     && -f "$results/target-native-merchant-close-sent.txt" \
     && -f "$results/observer-native-merchant-closed.txt" \
     && -f "$results/observer-ready-for-anvil.txt" \
     && -f "$results/target-native-anvil-state-sent.txt" \
     && -f "$results/observer-native-anvil-ok.txt" \
     && -f "$results/observer-native-anvil-saved.txt" \
     && -f "$results/observer-native-anvil.png" \
     && -f "$results/target-native-anvil-close-sent.txt" \
     && -f "$results/observer-native-anvil-closed.txt" \
     && -f "$results/observer-ready-for-enchanting.txt" \
     && -f "$results/target-native-enchanting-state-sent.txt" \
     && -f "$results/observer-native-enchanting-ok.txt" \
     && -f "$results/observer-native-enchanting-saved.txt" \
     && -f "$results/observer-native-enchanting.png" \
     && -f "$results/target-native-enchanting-close-sent.txt" \
     && -f "$results/observer-native-enchanting-closed.txt" \
     && -f "$results/observer-ready-for-remnant-backpack.txt" \
     && -f "$results/target-native-remnant-backpack-state-sent.txt" \
     && -f "$results/observer-native-remnant-backpack-ok.txt" \
     && -f "$results/observer-native-remnant-backpack-saved.txt" \
     && -f "$results/observer-native-remnant-backpack.png" \
     && -f "$results/target-native-remnant-backpack-close-sent.txt" \
     && -f "$results/observer-native-remnant-backpack-closed.txt" \
     && -f "$results/observer-ready-for-automata-copper-golem.txt" \
     && -f "$results/target-native-automata-copper-golem-state-sent.txt" \
     && -f "$results/observer-native-automata-copper-golem-ok.txt" \
     && -f "$results/observer-native-automata-copper-golem-saved.txt" \
     && -f "$results/observer-native-automata-copper-golem.png" \
     && -f "$results/target-native-automata-copper-golem-close-sent.txt" \
     && -f "$results/observer-native-automata-copper-golem-closed.txt" \
     && -f "$results/observer-ready-for-nexus-compass.txt" \
     && -f "$results/target-native-nexus-compass-state-sent.txt" \
     && -f "$results/observer-native-nexus-compass-ok.txt" \
     && -f "$results/observer-native-nexus-compass-saved.txt" \
     && -f "$results/observer-native-nexus-compass.png" \
     && -f "$results/observer-native-nexus-recovery-compass-initial.txt" \
     && -f "$results/target-native-nexus-recovery-compass-state-sent.txt" \
     && -f "$results/observer-native-nexus-recovery-compass-ok.txt" \
     && -f "$results/observer-native-nexus-recovery-compass-saved.txt" \
     && -f "$results/observer-native-nexus-recovery-compass.png" \
     && -f "$results/observer-native-nexus-recovery-compass-closed.txt" \
     && -f "$results/target-native-nexus-map-state-sent.txt" \
     && -f "$results/observer-native-nexus-map-ok.txt" \
     && -f "$results/observer-native-nexus-map-saved.txt" \
     && -f "$results/observer-native-nexus-map.png" \
     && -f "$results/target-native-nexus-management-state-sent.txt" \
     && -f "$results/observer-native-nexus-management-ok.txt" \
     && -f "$results/observer-native-nexus-management-saved.txt" \
     && -f "$results/observer-native-nexus-management.png" \
     && -f "$results/target-native-nexus-friends-state-sent.txt" \
     && -f "$results/observer-native-nexus-friends-ok.txt" \
     && -f "$results/observer-native-nexus-friends-saved.txt" \
     && -f "$results/observer-native-nexus-friends.png" \
     && -f "$results/target-native-nexus-registration-state-sent.txt" \
     && -f "$results/observer-native-nexus-registration-ok.txt" \
     && -f "$results/observer-native-nexus-registration-saved.txt" \
     && -f "$results/observer-native-nexus-registration.png" \
     && -f "$results/target-native-nexus-close-sent.txt" \
     && -f "$results/observer-native-nexus-closed.txt" \
     && -f "$results/observer-ready-for-villagers-woodcutter.txt" \
     && -f "$results/target-native-villagers-woodcutter-state-sent.txt" \
     && -f "$results/observer-native-villagers-woodcutter-ok.txt" \
     && -f "$results/observer-native-villagers-woodcutter-saved.txt" \
     && -f "$results/observer-native-villagers-woodcutter.png" \
     && -f "$results/target-native-villagers-woodcutter-close-sent.txt" \
     && -f "$results/observer-native-villagers-woodcutter-closed.txt" \
     && -f "$results/observer-ready-for-brewing.txt" \
     && -f "$results/target-native-brewing-state-sent.txt" \
     && -f "$results/observer-native-brewing-ok.txt" \
     && -f "$results/observer-native-brewing-saved.txt" \
     && -f "$results/observer-native-brewing.png" \
     && -f "$results/target-native-brewing-close-sent.txt" \
     && -f "$results/observer-native-brewing-closed.txt" \
     && -f "$results/observer-ready-for-smithing.txt" \
     && -f "$results/target-native-smithing-state-sent.txt" \
     && -f "$results/observer-native-smithing-ok.txt" \
     && -f "$results/observer-native-smithing-saved.txt" \
     && -f "$results/observer-native-smithing.png" \
     && -f "$results/target-native-smithing-close-sent.txt" \
     && -f "$results/observer-native-smithing-closed.txt" \
     && -f "$results/observer-ready-for-stonecutter.txt" \
     && -f "$results/target-native-stonecutter-state-sent.txt" \
     && -f "$results/observer-native-stonecutter-ok.txt" \
     && -f "$results/observer-native-stonecutter-saved.txt" \
     && -f "$results/observer-native-stonecutter.png" \
     && -f "$results/target-native-stonecutter-close-sent.txt" \
     && -f "$results/observer-native-stonecutter-closed.txt" \
     && -f "$results/observer-ready-for-grindstone.txt" \
     && -f "$results/target-native-grindstone-state-sent.txt" \
     && -f "$results/observer-native-grindstone-ok.txt" \
     && -f "$results/observer-native-grindstone-saved.txt" \
     && -f "$results/observer-native-grindstone.png" \
     && -f "$results/target-native-grindstone-close-sent.txt" \
     && -f "$results/observer-native-grindstone-closed.txt" \
     && -f "$results/observer-ready-for-loom.txt" \
     && -f "$results/target-native-loom-state-sent.txt" \
     && -f "$results/observer-native-loom-ok.txt" \
     && -f "$results/observer-native-loom-saved.txt" \
     && -f "$results/observer-native-loom.png" \
     && -f "$results/target-native-loom-close-sent.txt" \
     && -f "$results/observer-native-loom-closed.txt" \
     && -f "$results/observer-ready-for-cartography.txt" \
     && -f "$results/target-native-cartography-state-sent.txt" \
     && -f "$results/observer-native-cartography-ok.txt" \
     && -f "$results/observer-native-cartography-saved.txt" \
     && -f "$results/observer-native-cartography.png" \
     && -f "$results/target-native-cartography-close-sent.txt" \
     && -f "$results/observer-native-cartography-closed.txt" \
     && -f "$results/observer-ready-for-beacon.txt" \
     && -f "$results/target-native-beacon-state-sent.txt" \
     && -f "$results/observer-native-beacon-ok.txt" \
     && -f "$results/observer-native-beacon-saved.txt" \
     && -f "$results/observer-native-beacon.png" \
     && -f "$results/target-native-beacon-close-sent.txt" \
     && -f "$results/observer-native-beacon-closed.txt" \
     && -f "$results/observer-ready-for-sign.txt" \
     && -f "$results/target-native-sign-state-sent.txt" \
     && -f "$results/observer-native-sign-ok.txt" \
     && -f "$results/observer-native-sign-saved.txt" \
     && -f "$results/observer-native-sign.png" \
     && -f "$results/target-native-sign-close-sent.txt" \
     && -f "$results/observer-native-sign-closed.txt" \
     && -f "$results/observer-ready-for-crafter.txt" \
     && -f "$results/target-native-crafter-state-sent.txt" \
     && -f "$results/observer-native-crafter-ok.txt" \
     && -f "$results/observer-native-crafter-saved.txt" \
     && -f "$results/observer-native-crafter.png" \
     && -f "$results/target-native-crafter-close-sent.txt" \
     && -f "$results/observer-native-crafter-closed.txt" \
     && -f "$results/observer-ready-for-nexus-death-node-admin.txt" \
     && -f "$results/target-native-nexus-death-node-admin-state-sent.txt" \
     && -f "$results/observer-native-nexus-death-node-admin-ok.txt" \
     && -f "$results/observer-native-nexus-death-node-admin-saved.txt" \
     && -f "$results/observer-native-nexus-death-node-admin.png" \
     && -f "$results/target-native-nexus-death-node-admin-close-sent.txt" \
     && -f "$results/observer-native-nexus-death-node-admin-closed.txt" \
     && -f "$results/observer-ready-for-locksmith-management.txt" \
     && -f "$results/target-native-locksmith-management-state-sent.txt" \
     && -f "$results/observer-native-locksmith-management-ok.txt" \
     && -f "$results/observer-native-locksmith-management-saved.txt" \
     && -f "$results/observer-native-locksmith-management.png" \
     && -f "$results/target-native-locksmith-management-close-sent.txt" \
     && -f "$results/observer-native-locksmith-management-closed.txt" \
     && -f "$results/observer-ready-for-pause-screen.txt" \
     && -f "$results/target-native-pause-screen-state-sent.txt" \
     && -f "$results/observer-native-pause-screen-ok.txt" \
     && -f "$results/observer-native-pause-screen-saved.txt" \
     && -f "$results/observer-native-pause-screen.png" \
     && -f "$results/target-native-pause-screen-close-sent.txt" \
     && -f "$results/observer-native-pause-screen-closed.txt" \
     && -f "$results/observer-ready-for-advancements.txt" \
     && -f "$results/target-native-advancements-state-sent.txt" \
     && -f "$results/observer-native-advancements-ok.txt" \
     && -f "$results/observer-native-advancements-saved.txt" \
     && -f "$results/observer-native-advancements.png" \
     && -f "$results/target-native-advancements-close-sent.txt" \
     && -f "$results/observer-native-advancements-closed.txt" \
     && -f "$results/observer-ready-for-stats.txt" \
     && -f "$results/target-native-stats-state-sent.txt" \
     && -f "$results/observer-native-stats-ok.txt" \
     && -f "$results/observer-native-stats-saved.txt" \
     && -f "$results/observer-native-stats.png" \
     && -f "$results/target-native-stats-close-sent.txt" \
     && -f "$results/observer-native-stats-closed.txt" \
     && -f "$results/observer-ready-for-horse.txt" \
     && -f "$results/target-native-horse-state-sent.txt" \
     && -f "$results/observer-native-horse-ok.txt" \
     && -f "$results/observer-native-horse-saved.txt" \
     && -f "$results/observer-native-horse.png" \
     && -f "$results/target-native-horse-close-sent.txt" \
     && -f "$results/observer-native-horse-closed.txt" \
     && -f "$results/observer-complete.txt" \
     && -f "$results/target-complete.txt" \
     && -f "$results/server-cleanup-ok.txt" ]]; then
    complete=true
    break
  fi

  for spec in "$server_pid:server" "$target_pid:target" "$observer_pid:observer"; do
    pid="${spec%%:*}"
    name="${spec##*:}"
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      if [[ "$name" == target && -f "$results/target-complete.txt" ]]; then
        continue
      fi
      if [[ "$name" == observer && -f "$results/observer-complete.txt" ]]; then
        continue
      fi
      echo "E2E ${name} process exited before success markers completed." >&2
      tail -n 200 "build/e2e/${name}.log" >&2 || true
      exit 1
    fi
  done
  sleep 1
done

if [[ "$complete" != true ]]; then
  echo 'Timed out waiting for the Observer E2E success markers.' >&2
  find "$results" -maxdepth 1 -type f -print -exec cat {} \; >&2 || true
  echo '--- server log ---' >&2
  tail -n 200 build/e2e/server.log >&2 || true
  echo '--- target log ---' >&2
  tail -n 200 build/e2e/target.log >&2 || true
  echo '--- observer log ---' >&2
  tail -n 200 build/e2e/observer.log >&2 || true
  exit 1
fi

printf '%s\n' \
  'Dedicated server + Target JVM + Observer JVM framebuffer-free Observer E2E passed.' \
  'Verified world/HUD, crafting, unsupported metadata fallback, every registered semantic bridge through Statistics, Stop, and server cleanup.'

#!/usr/bin/env bash
set -euo pipefail
root="$(pwd)"
results="$root/build/access-e2e/results"
if (echo > /dev/tcp/127.0.0.1/25571) >/dev/null 2>&1; then
  echo 'Port 25571 is already occupied; refusing to reuse another server.' >&2
  exit 1
fi
mkdir -p "$results" build/access-e2e/{server,target,observer}
# Only this harness's result files are replaced; world data is retained.
find "$results" -maxdepth 1 -type f -delete
printf 'eula=true\n' > build/access-e2e/server/eula.txt
printf '%s\n' 'server-port=25571' 'online-mode=false' 'enforce-secure-profile=false' 'spawn-protection=0' 'view-distance=2' 'simulation-distance=2' > build/access-e2e/server/server.properties
for role in target observer; do
  printf '%s\n' 'onboardAccessibility:false' 'skipMultiplayerWarning:true' 'tutorialStep:none' > "build/access-e2e/$role/options.txt"
done
printf 'lang:zh_tw\n' >> build/access-e2e/observer/options.txt
if [[ "${ACCESS_E2E_PREPARED:-false}" != true ]]; then
  ../TotemCore/gradlew prepareAccessE2e --no-daemon
fi
pids=()
cleanup() {
  for pid in "${pids[@]}"; do kill -- "-$pid" 2>/dev/null || true; done
  sleep 2
  for pid in "${pids[@]}"; do kill -KILL -- "-$pid" 2>/dev/null || true; done
  for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT
(
  cd build/access-e2e/server
  exec setsid "$JAVA_HOME/bin/java" @"$root/build/access-e2e/server.args" \
    -Dfabric.dli.config="$root/.gradle/loom-cache/launch.cfg" \
    -Dfabric.dli.env=server -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer \
    -Dnexus.access.e2e.results="$results" --enable-native-access=ALL-UNNAMED \
    net.fabricmc.devlaunchinjector.Main nogui
) > build/access-e2e/server.log 2>&1 &
pids+=("$!")
ready=false
for _ in $(seq 1 180); do
  if (echo > /dev/tcp/127.0.0.1/25571) >/dev/null 2>&1; then ready=true; break; fi
  kill -0 "${pids[0]}" 2>/dev/null || { tail -60 build/access-e2e/server.log; exit 1; }
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Server startup timeout'; exit 1; }
for role in target observer; do
  username=AccessTarget
  [[ "$role" != observer ]] || username=AccessObserver
  (
    cd "build/access-e2e/$role"
    exec setsid xvfb-run -a "$JAVA_HOME/bin/java" @"$root/build/access-e2e/client.args" \
      -Dfabric.dli.config="$root/.gradle/loom-cache/launch.cfg" \
      -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient \
      -Dnexus.access.e2e.results="$results" --enable-native-access=ALL-UNNAMED \
      net.fabricmc.devlaunchinjector.Main --username "$username"
  ) > "build/access-e2e/$role.log" 2>&1 &
  pids+=("$!")
done
for _ in $(seq 1 240); do
  if [[ -f "$results/failure.txt" ]]; then cat "$results/failure.txt"; exit 1; fi
  if [[ -f "$results/done" && -s "$results/nexus-access-observer.png" && -s "$results/nexus-access-target.png" ]]; then
    echo 'PASS: offline grant, Observer initial/update/close/reopen/privacy/stop and creative mode restoration'
    exit 0
  fi
  for pid in "${pids[@]}"; do
    kill -0 "$pid" 2>/dev/null || { echo 'E2E process exited before completion'; tail -40 build/access-e2e/{server,target,observer}.log; exit 1; }
  done
  sleep 1
done
echo 'Access E2E timeout'
tail -60 build/access-e2e/{server,target,observer}.log
exit 1

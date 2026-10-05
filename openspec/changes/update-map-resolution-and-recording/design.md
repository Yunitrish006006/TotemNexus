## Context and evidence

Inspected source: TotemNexus 0.3.27, Java 25, Minecraft 26.3, Loader 0.19.5,
Fabric API 0.160.5+26.3; the workspace graph's older version snapshot is not the
implementation target.

- `NexusMapDetailSavedData.record` writes only scale-zero live pages.
- `NexusMapDetailScreenMixin` / `MapDetailTransform` minify those pages at lower
  zooms. They do not generate scale-one, -two or -three data.
- `NexusMapDetailSampling.tick` attempts at most 128 candidates per map per tick,
  under a global 2048-attempt ceiling, in a 65536-position square. One stationary
  sweep is at least 512 ticks (25.6 seconds at 20 TPS), before budget contention.
  This is arithmetic, not measured latency. Circle/coverage/chunk rejection and
  moving centers prevent that figure from guaranteeing completed terrain.
- `NexusMapDetailNetworking` selects at most 20 pages, replaces pending queues
  on each accepted request, and sends whole changed pages under a 32768-byte
  recipient/tick limit. Client requests repeat every 10 ticks. A frequently
  changing early page can repeatedly delay later pages; a regression must
  demonstrate convergence, rather than assuming a larger bandwidth cap fixes it.
- `NexusMapRecordingGameTest` covers storage round-trip and unloaded sampling,
  but does not exercise a moving player's actual recording coverage.

## Goals / Non-Goals

Goals: true resolution selection; faster useful coverage near the travel path;
bounded fair catch-up; measurable recording-versus-delivery latency; unchanged
map ownership, exploration authority and Observer privacy.

Non-goals: unlimited mapping throughput, force-loading cartography chunks,
inventing missing terrain, recovering unknown past terrain from coarse pixels,
changing teleport eligibility, or claiming an FPS improvement without measurement.

## Decisions

### Resolution is independent of GUI scaling

For zoom factor `2^n`, choose `max(0, baseScale - n)`. Screen GUI scale controls
presentation only. For base scale four:

| Zoom | Terrain scale | World blocks per terrain pixel side |
|---|---|---|
| 100% | 4 | 16 |
| 200% | 3 | 8 |
| 400% | 2 | 4 |
| 800% | 1 | 2 |
| 1600% | 0 | 1 |

The existing base map remains the 100% terrain. At higher zoom, matching-resolution
pages cover their true world extent. Missing pixels retain compatible coarser
recorded fallback; finer-than-selected textures are not rendered as a substitute.
Markers, hit testing and cursor anchoring retain the same world transform.

### Incremental derived levels with explicit completeness

Keep recorded finest terrain as source data. Generate scales one through three
incrementally from aligned groups of known source texels, not by GPU texture
minification or averaging encoded palette IDs numerically. Use a deterministic
palette-aware reduction: most frequent map-color ID, ties resolved by lowest ID;
choose the most frequent brightness among samples of that color, same tie rule.
This is a recorded-color LOD policy, not a claim to reproduce vanilla's coarse
height/water resampling exactly.

Compute each level from its entire finest-source footprint (or equivalent exact
histograms), not from the winning colors of its child pixels: majority reduction
is not associative. Test a pattern whose direct footprint majority differs from
the majority of child winners, including deterministic tie and brightness cases.

A derived pixel becomes valid only when its entire source footprint is known.
Unknown inputs must never be treated as black, land or proof of exploration.
Incomplete pixels keep coarser fallback. Recompute only affected ancestors;
unchanged output must not generate a new revision. Account for reduction work
inside the processing budget, not as hidden extra work per fine sample.

Existing historical pages remain frozen, independently validated fallback.
Live matching-resolution pages take precedence only where complete. Keep derived
data rebuildable and bounded, separate from authoritative fine-page retention;
cache eviction must not delete source terrain or rewrite locked maps. If derived
data is persisted, specify a backward-readable codec migration before coding.
Old saves retain all known terrain; missing intermediate data builds gradually.

### World-anchored work and useful-coverage priority

Track deduplicated work by dimension, authorized MapId and world tile, with
within-tile progress/known masks. Movement adds eligible work instead of moving
the coordinates of unfinished work. Prioritize missing cells along the recent
travel corridor, then nearby holes, then refresh of already recorded terrain.
Reserve a fair share for older work and other held maps to prevent starvation.
Multiple holders of the same MapId must contribute work without duplicating it.

Batch route-adjacent missing work into aligned derived-pixel footprints, charging
every source cell against the same budgets and validating its individual drawn/
loaded eligibility. A thin line of fine samples does not complete an 8-by-8
source footprint for 200% detail. Measure completed derived pixels and their
completion latency at every zoom, not just a rising fine-sample count. Batching
must never extend exploration authority to unknown neighbors.

Retain bounded coordinate-only pending work, not chunk references/tickets. Before
sampling, revalidate the map binding, unlocked state, dimension, base-map drawn
coverage and loaded chunk availability. Eligibility comes from legitimate held-
map exploration, never from a client viewport. Pausing/evicting unavailable work
leaves unknown cells unknown and permits requeue on a later eligible visit.
Disconnect, dimension change, map removal and server stop clean up owned work.

Use explicit limits for enqueue discovery, attempted samples, block reads,
derived reductions, queue length and elapsed tick time. The present 2048 global
sample-attempt ceiling is an initial reference, not evidence of a safe CPU time.
Reuse adjacent surface samples within a bounded tick-local cache; never read
mutable world data on an unsafe background thread. Tune limits from measured
server-tick cost and path coverage, not an arbitrary multiplier.

### Delivery and shared-contract stabilization

Repeated requests for the same map/viewport merge revisions without resetting
unsent work to the beginning. Send current-scale coverage before optional finer
work; avoid resending unchanged base pixels. A viewport change retires obsolete
work without erasing completed client terrain. Maintain byte and cache ceilings,
fairness and authorization rechecks immediately before dispatch.

Current request v2 carries center/radius, not an explicit terrain scale. Radius
alone is not an exact zoom contract across window/GUI sizes. Before implementing
scale-aware selection, decide and test a versioned desired-scale request and the
corresponding Observer path. Do not overload radius or silently change an existing
packet's field layout. Inspect `ObserverNexusTerrainRelay.enqueue`, its reflected
`enqueueObserved` signature and provider negotiation. Preserve old compatibility
only where semantics are actually unchanged; otherwise exact-version fail-closed
fallback is required. Any necessary Observer implementation needs updated scope.
No Core, Remnant or DiscordBridge detail-store consumer was found in source search;
their public event, friendship and death-node contracts must remain unchanged.

## Acceptance and measurement

- A synthetic nonuniform pattern must produce independently expected colors at
  every selected resolution, at negative coordinates and adjacent page seams.
  Testing only page visibility or interior solid color cannot distinguish proper
  aggregation from the rejected 0.3.27 minification behavior.
- Drive actual recording on deterministic walking, sprinting, turning and
  boundary-crossing paths; count eligible missing cells, completed samples,
  queue depth and oldest-work age. Compare with 0.3.27 on identical loaded terrain.
  Also compare complete derived-pixel coverage and latency at every zoom; a
  finer sampled route alone is not evidence of improved intermediate zoom.
  Separate base-map painting, detail recording, network arrival and texture update.
- Specify the tested corridor, speed, hold interval and loaded duration. Once new
  work stops, all admitted, still-eligible loaded work must drain within a bound
  derived from admitted workload and configured fair budgets. Do not turn a
  walking test into a teleport that skips exploration or claim arbitrary-flight
  coverage. Unloaded terrain remains deferred, not fabricated or force-loaded.
- Verify single/multiple players and shared MapIds; byte, memory and tick bounds;
  save/reload, derived-cache rebuilding, transfer, SCALE, LOCK and copy-on-write.
- Owner/Observer native-scale screenshots at every zoom and GUI scales 2 and 3;
  production runtime and dedicated three-JVM tests with read-only action rejection,
  authorization revocation and close/disconnect cleanup.

## Risks and implementation gates

### Approved implementation contract (2026-10-05)

The user approved implementation and direct publication after verification.
The implementation keeps SavedData v1 and its authoritative fine-page limits.
Derived LOD is a transient global-per-store LRU of at most 256 pages (4 MiB of
color arrays), with at most 1024 jobs and 32768 source reads / 2 ms scheduling
deadline per server tick. It is reconstructed from each map's own fine records,
including frozen locked-map records. No generated page is serialized.

Owner request v3 adds an explicit scale; response v2 and Observer protocol 5's
existing geometry/zoom semantics remain unchanged. The reflected Observer method
signature remains unchanged. Nexus retains the target's validated v3 request and
uses its scale only when MapId, center and radius exactly match the relay viewport;
otherwise it sends coarse fallback until a matching target request arrives.
There is no radius-to-scale guess, new Observer field or sibling repository edit.
Unsupported v3 clients cannot request the new LOD; existing version negotiation
and coarse fallback are not interpreted as successful fine-detail delivery.

Sampling uses a 2048-attempt / 16384-block-read global tick ceiling, up to 512
attempts per map, a 3 ms scheduling deadline, bounded 8x8 coordinate jobs, and a
tick-local surface cache. Deadlines are checked between work units, not hard
real-time guarantees. Required moving-path and runtime tests must validate these
choices before release.

Per active map, pending tiles, recent-completion entries and deferred cells are
each capped at 1024. When deferred capacity is full, a budget-interrupted sample
can replace older deferred work; unavailable work otherwise waits for a later
eligible visit. No unlimited-area completion guarantee is made under saturation.
The 4 MiB derived color-array limit describes the LRU itself; existing pending
network queues can additionally retain up to 20 page references per recipient.

The storage and delivery decisions above remain subject to verification and
independent review. A full source square can require substantial
work, so no unconditional promise is made to finish an entire visible radius
before it unloads. The performance target is faster measured useful coverage and
bounded eventual completion of eligible admitted work, without server stalls.

The new scenario will replace the conflicting 0.3.27 minification scenario only
with implementation. Independent review, deterministic tests, exact-source CI,
new version/hash recording and authorized Modrinth read-back remain release gates.

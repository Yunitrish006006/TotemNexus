# Persistence compatibility contract

Nexus owns Space Unit, teleport, friend, death-node and distributed-spawn
SavedData, payloads, resources, client UI and Mixins. Extraction preserves all
existing `deadrecall:*` identifiers, SavedData keys, codecs and payload IDs.

Nexus registers the optional Core death-backpack lifecycle adapter; it never
requires Remnant. The first copy remains additive until standalone, bundle,
legacy-world, dimension, multi-player, restart and Dedicated Server validation
have passed.

## Build baseline

The standalone Fabric module scaffold was created and built successfully on
2026-07-23. It has only a TotemCore dependency and deliberately performs no
Nexus gameplay registration, retaining the compatibility bundle as the sole
owner of all preserved `deadrecall:*` persistence and protocol surfaces during
the additive phase.

## Migrated compatibility primitives (not yet registered)

Nexus now owns the stable Space Unit enum values, the full
`deadrecall:space_friends` SavedData schema, and the first map/friend payload
codecs (`request_space_unit_map`, `request_space_unit_friends`, and
`space_unit_friends`). These codecs are intentionally not registered until the
matching server receivers and client screens migrate as the same unit.

The `deadrecall:space_discovery` and `deadrecall:distributed_spawns` schemas
are also now present in Nexus with their existing data versions and codec field
names. They remain inactive until the Space Unit authority layer moves.

The `deadrecall:space_units` record codec, its `data_version=1` container, and
the lodestone/visibility read model are also now in Nexus. Teleport and admin
mutations remain bundle-owned until their server authority and UI flows migrate
together.

The remaining first-wave serverbound Space Unit codecs are now copied as well:
teleport, favorite, calibration, visibility, rename, access, registration
confirmation and friend removal. No receiver has been registered from Nexus.

`NexusPayloadRegistration` and `NexusClientPayloadRegistration` now provide
idempotent cutover boundaries for those codecs. Neither is called by the Nexus
entrypoints; server receivers and client state consumers must move with the
authority/UI unit before either boundary can be activated.

`NexusPayloadHandler` defines the server-authoritative handoff for all copied
Space Unit requests. Receiver registration remains opt-in and has no current
caller, so the compatibility bundle retains live authority.

Death Node admin and registration-preview packets now have the same opt-in
server/client receiver boundaries. No handler or client consumer is supplied by
the entrypoints during the additive phase.

`NexusDeathNodeAdminAuthority` now implements the administrative list, disable
and purge rules against Nexus SavedData. A purge rejects active nodes and
removes all discovery/favorite references only after the node is inactive. The
authority and its receivers remain unregistered until the administrative UI and
permission flow cut over together.

Nexus now has a client-only authoritative payload state and bridge for map,
friends, registration preview and Death Node admin data. The bridge is not
called from the client entrypoint until the matching screens move.

`NexusMapAuthority` now combines the short-lived source session, active/visible
target checks and discovery persistence for favorites. It intentionally does
not yet resend map payloads or activate a receiver; its multi-player GameTest
protects the authorization boundary before UI cutover.

`NexusMapPayloadFactory` now converts server-owned active/visible Space Units
and server-calculated `NexusMapQuote` values into the complete legacy
`space_unit_map` wire shape. It does not calculate teleport costs and has no
networking dependency; the complete quote calculator and resend flow remain
the next authority boundary.

`NexusMapPayloadAuthority.sendCalculated` now completes that server-owned map
projection for both persisted Space Units and online friends. It obtains the
friend relationship and live-player snapshot only from the server, resolves
each quote through the validated interface context, and emits the existing
`space_unit_map` shape. The GameTest suite proves an unrelated online player
cannot appear in the response. This boundary is still opt-in and has no
entrypoint or compatibility-bundle receiver registration.

`NexusMapOpenAuthority` now refreshes that map only from the server-held
interface context after a successful favorite mutation. It re-resolves the
source from SavedData (or the authenticated player anchor) before sending the
replacement payload; it never accepts a source model from the client. The
receiver remains inactive, so this is an additive cutover seam rather than a
bundle behavior change.

`NexusMapClientCutover` is the matching explicit client hook. Once a future
bundle cutover enables both sides, it registers the map payload consumer and
updates an existing matching screen or opens the Nexus screen for a new source.
Neither Nexus entrypoint invokes it during the additive phase.

The factory also accepts server-side online-friend snapshots and emits them
only when `NexusFriendSavedData` authorizes the relationship; cross-dimension
identity is retained in the payload. This remains an inactive read-model path.

Legacy `space_discovery` JSON is now round-trip tested using its actual
`UUIDUtil.CODEC` four-integer UUID representation. The optional default
`data_version=2` may be omitted when re-encoded, which is the established
schema semantics rather than a version downgrade.

`NexusLodestoneAuthority` now owns the persisted mutation rules for rename,
visibility, administrators and allowed players. Its access boundary matches
the legacy model: owners alone may alter administrators; either owner or an
administrator may alter visibility and allowed players; owners cannot be
targeted. Source-item validation, range/loading checks, Discord fan-out and
map resend remain bundle-owned until that complete interaction unit moves.

The GameTest suite now also performs a codec-backed virtual restart of a
lodestone, friendship and distributed-spawn record in a bootstrapped server
environment. It confirms their persisted fields survive encode/decode without
requiring an active Nexus receiver.

`NexusFriendListAuthority` now projects friendships plus incoming/outgoing
invites from Nexus SavedData into the stable `space_unit_friends` payload. It
orders friend, incoming and outgoing states consistently and obtains online
status only from the server player list. The clientbound sender and receiver
remain inactive until the friend UI and source-item gate migrate together.

`NexusTeleportQuoteCalculator` now owns the server-fed route stability, food,
amethyst, preparation, deviation and damage calculations used by the legacy
map quote, including cross-dimension tier/resource gates. It accepts only
resolved server source/target and inventory-resource snapshots. Movement,
cost deduction and safe-landing execution remain the next teleport-session
authority step.

The Death Node admin request/manage/admin payloads and the Space Unit
registration-preview payload are now copied as stable codecs. They remain
inactive until their administrative and registration interfaces move.

## Dedicated Server baseline

On 2026-07-23, the assembled Nexus JAR reached `Done` in a clean dedicated
server directory containing exactly Fabric API and TotemCore. This validates
standalone loading without DeadRecall or Remnant, but not yet the required
teleport, privacy, legacy-world, multi-player, dimension or restart behavior.

The current JAR was revalidated in that same minimal directory after the
expanded authority work and again reached `Done`. The bounded launch then
stopped by timeout after the server saved its worlds.

After the explicit map client-cutover seam was added, the rebuilt Nexus
`0.1.0-SNAPSHOT` JAR was validated again in a fresh Core + Fabric API-only
dedicated-server directory. It reached `Done (0.722s)` and saved the
overworld, Nether and End with no DeadRecall or Remnant artifact present.

## Runtime validation

On 2026-07-23, all 23 Nexus Fabric GameTests passed in the standalone Core +
Fabric API runtime. They cover teleport preparation and cross-dimension quote
gates, visibility and friendship privacy, server-authoritative multi-player
map projection, persisted data restart round-tripping, and legacy SavedData
keys. The companion JUnit suite loads and re-encodes an actual legacy
`space_discovery` JSON document. This completes the additive validation gate;
the compatibility-bundle authority cutover remains a separate step.

## Migrated privacy primitives

Nexus now owns the relationship-bound teleport-session policy and player-target
availability classification, including the existing cancellation message keys.
These pure policies are unit-tested; no teleport session or movement behavior
has been activated from Nexus yet.

## Authority migration boundary

`NexusTeleportAuthority` now records the complete server-only operation surface
of the legacy 2,563-line Space Unit handler: death-node lifecycle, map/friend
operations, teleport mutations, registration and both server ticks. The first
implementation must move this surface as one transaction before any Nexus
receiver is activated.

`NexusDeathBackpackNodeAdapter` now binds that future authority to Core's
optional `DeathBackpackNodeLifecycle` without importing Remnant. It has no
entrypoint registration until a complete authority implementation exists.

The adapter now delegates to the tested `NexusDeathNodeAuthority` slice rather
than the broader unfinished teleport authority. It remains deliberately
unregistered during the additive phase.

Nexus also owns the pure Amethyst catalyst discount quote rules, including the
minimum non-zero paid teleport cost. The rule is unit-tested and is ready for
the future Space Unit map quote migration.

Teleport interface IDs and specialization quote rules are now also copied and
unit-tested. This provides the bounded-cost constants required by the remaining
`space_unit_map` payload migration.

The cut-over safe-landing implementation is now incremental and non-blocking:
it asynchronously prepares one authoritative anchor chunk, scans at most 128
columns per tick, uses only already-loaded chunks for surrounding candidates,
and reads through cached `LevelChunk` instances. This removes the legacy eager
preflight scan and prevents safe-landing block checks from forcing a
`getChunkBlocking` call across the deviation square. Cursor policy is covered
by JUnit and the live loaded-chunk path is covered by Fabric GameTest.

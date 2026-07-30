# Nexus authority migration gates

The legacy `SpaceUnitHandler` remains bundle-owned until one complete authority
implementation can replace it. Move and verify the following units in order:

1. Death-node lifecycle and the four preserved SavedData stores.
2. Friend/map read model and the clientbound payload senders.
3. Lodestone visibility, rename, access and registration-preview authority.
4. Teleport sessions: source/target resolution, cost, safe landing, arrival
   damage, structure wear and cancellation.
5. Item/block interaction hooks, map UI state and both server ticks.
6. GameTests for privacy, multi-player, cross-dimension, legacy worlds and
   restart; then standalone and compatibility-bundle validation.

`NexusTeleportAuthority` is the required implementation surface. The complete
0.1.1 implementation is composed only by `NexusAuthorityBootstrap`, which
registers the Core death-backpack lifecycle adapter, interaction hooks,
distributed-spawn rule, payload codecs/receivers, death-node administration,
server ticks and command after the exact bundle selects this artifact.

Favorite mutation belongs to the map/session unit, not the standalone discovery
store: legacy behavior first validates a short-lived `TeleportInterfaceContext`,
then checks active/visible/discovered target ownership, writes the favorite, and
resends the authoritative map. Moving only `setFavorite` would bypass source
and session validation, so it is intentionally deferred.

Rollback is selecting the prior immutable Nexus pin; persisted `deadrecall`
SavedData keys and payload IDs do not change.

## Current extraction state

- Completed and GameTest-covered: death-node create/owner-only disable,
  inactive-node purge with global discovery/favorite cleanup, administrative
  snapshot/action authority, Core lifecycle adapter delegation, backpack UUID
  binding, friend removal, map session/source validation, favorite persistence,
  and map-payload construction from server-owned visible units and quotes.
- Completed and GameTest-covered: Lodestone rename, managed visibility, and
  owner-only administrator versus manager-controlled allowed-player access.
- Completed and GameTest-covered: friend-list projection for friendships and
  pending invites, including authoritative online-state lookup and sorting.
- Completed and GameTest-covered: server-fed teleport quote calculation,
  including cross-dimension resource and tier gates.
- Completed and GameTest-covered: authoritative map projection for persisted
  units and server-authorized online friends, with each quote resolved against
  the validated server-held interface context.
- Completed and GameTest-covered: favorite mutation refreshes the complete
  authoritative map only after the server accepts the held-interface session,
  source, visibility and discovery checks.
- Completed and tested for compatibility: legacy discovery SavedData JSON
  round-trips with UUID integer-array encoding and default-version semantics.
- Completed and GameTest-covered: codec-backed restart retention for
  lodestone, friendship and distributed-spawn SavedData fields.
- Completed in the 0.1.1 candidate: map construction/resend, friend-list
  presentation, Lodestone management/registration, teleport session execution,
  item/block interactions, distributed spawning, refresh-quote handling,
  full death-node administration and the complete client map/friends/preview/
  administrator screens.

The next implementation must connect this complete map projection and its
post-mutation resends to the client map state consumer. It cannot be cut down
to a receiver-only change: the receiver must return the authoritative
`space_unit_map` after a mutation.

The client state consumer is now available behind `NexusMapClientCutover`; the
remaining work for this slice is an explicit two-sided bundle activation and
its dedicated-server/client integration validation.
Lodestone authority must likewise be wired with source-item capability,
distance/unloaded checks, public-update fan-out and authoritative map resend
as one later cutover unit.
The copied death-node admin receiver remains inactive until its menu and
permission-flow validation can be cut over with this authority.

## Full authority candidate

`NexusSpaceUnitAuthority` is now the complete, inactive port of the legacy
Space Unit server implementation. `NexusGameplayAuthority` adapts it to the
existing `NexusTeleportAuthority` receiver surface without importing a
DeadRecall feature class.  The port owns the preserved SavedData codecs,
death-node lifecycle, map/friend projection, Lodestone mutations, teleport
sessions, interaction callbacks and both server ticks.  Discord publication is
an optional `NexusOptionalIntegrations` callback rather than a direct feature
dependency.

`totem-nexus.mixins.json` now owns the server and client candidate Mixin
composition, while the server and client entrypoints activate the same atomic
bootstrap. A Java 25 standalone build loaded that config and passed all 24
required Fabric GameTests. The remaining bundle work is to pin this immutable
artifact in DeadRecall, gate every matching root registration and Mixin, then
prove legacy-world restart, multi-player and Dedicated Server behavior in the
exact compatibility bundle.

## Safe-landing execution budget

Safe-landing selection now follows the architecture requirement that large
teleport scans use a bounded server session. Starting a teleport no longer
performs an eager landing scan. After preparation, Nexus asynchronously tickets
only the target anchor chunk, checks at most 128 columns per server tick, reads
blocks directly from already-loaded `LevelChunk` instances, and ignores other
unloaded chunks instead of entering `ServerChunkCache#getChunkBlocking`.

The session retains the legacy random-deviation priority and nearest-column
fallback without allowing either path to synchronously generate surrounding
chunks. It uses an existing heightmap only when the surface remains close to
the authoritative target height, revalidates the final target and landing
before cost deduction, and releases its temporary ticket on success,
cancellation, disconnect, timeout or server shutdown.

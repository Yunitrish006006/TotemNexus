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

`NexusTeleportAuthority` is the required implementation surface. Do not invoke
any Nexus payload receiver or Core death-backpack adapter before an authority
implementation has passed the gate that owns its operation.

Favorite mutation belongs to the map/session unit, not the standalone discovery
store: legacy behavior first validates a short-lived `TeleportInterfaceContext`,
then checks active/visible/discovered target ownership, writes the favorite, and
resends the authoritative map. Moving only `setFavorite` would bypass source
and session validation, so it is intentionally deferred.

Rollback before cutover is removing the Nexus module or leaving its entrypoints
inactive; DeadRecall remains the sole live authority throughout this phase.

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
- Still bundle-owned: map construction/resend, friend-list presentation,
  lodestone management/registration, all teleport session execution and item
  interactions.

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

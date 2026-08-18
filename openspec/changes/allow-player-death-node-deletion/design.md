## Context

The existing Death Node administration protocol assumes every caller has
`Permissions.COMMANDS_ADMIN`. Simply removing that command requirement would
expose cross-player queries, teleport and batch deletion. DeadRecall also
bundles TotemRemnant, which independently registers the shared `/deadrecall`
root with an administrator requirement; Brigadier may retain the first root
node's requirement when modules contribute children to the same command.

## Goals / Non-Goals

- Goals: provide a simple owner-only deletion flow, preserve the administrator
  workflow, and enforce every authorization decision on the server.
- Non-goals: allow players to delete another owner's node, expose administrator
  diagnostics or teleport tools, delete death backpacks, or change automatic
  recovery behavior.

## Decisions

- Decision: `/deadrecall deathnodes` and `/deadrecall deathpoints` are available
  to all in-game players. Totem modules keep the shared root unrestricted and
  put administrator requirements on administrative child commands.
- Decision: the server sends an explicit administrator-view capability in the
  snapshot. It is presentation metadata only and never substitutes for a
  fresh server-side permission and ownership check.
- Decision: non-administrator queries are rewritten to the requester's UUID.
  Player mode hides owner filtering, diagnostics, safe teleport and batch
  actions; status, dimension, time and paging remain usable.
- Decision: owner deletion has distinct request/confirm action IDs. The server
  checks ownership both when issuing and consuming a one-use 30-second token,
  then removes the record and every discovery/favorite reference. An active
  point can be removed directly because later death-backpack recovery already
  treats a missing node as an idempotent success.
- Decision: administrator disable-then-purge behavior remains unchanged.

## Risks / Trade-offs

- A forged client could request a foreign UUID or an administrator action. The
  service will reject it after reloading the current record and checking both
  role and owner.
- A player can intentionally remove navigation to an unrecovered backpack.
  The confirmation text will state that the backpack is not moved or deleted
  and that the teleport point cannot be restored through the UI.
- Adjusting the shared command root affects TotemRemnant. Its administrative
  `containers` child retains its own explicit command-admin requirement and is
  covered by authorization tests.

## Migration Plan

No SavedData migration is required. Release compatible TotemNexus and
TotemRemnant versions together in the next exact-version DeadRecall bundle.
Existing Death Node and discovery records remain readable.

## Open Questions

- None. The proposed default is one selected point per confirmation; player
  batch deletion remains out of scope.


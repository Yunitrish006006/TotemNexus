## Context

Nexus registers a Core `DeathRetainedItemPolicy`; Remnant owns extraction,
SavedData staging, and restoration. The current Nexus policy recognizes only
the item carrying the latest successful-teleport owner/token pair. Remnant scans
inventory slot order and stages at most one authorized item.

## Goals / Non-Goals

- Goals: make first-use retention automatic, deterministic, bounded, and
  player-controllable while preserving module independence.
- Non-Goals: retain every interface, scan portable containers, force-load
  chunks, or remove legacy token tags from existing items.

## Decisions

Nexus policy eligibility becomes the same server-owned item resolver used by
teleport initiation: compass, recovery compass, plain book, and filled map with
a valid map ID. Ownership/token metadata remains writable after a successful
teleport for backward compatibility but is not required by death retention.

Remnant constructs a duplicate-free candidate order at death: selected main
hand slot, offhand slot, hotbar slots from zero through eight excluding the
selected slot, then main inventory slots. It invokes the installed Core policy
until the first eligible non-vanishing item is staged. The scan is O(player
inventory size) and occurs only in the existing pre-death path.

## Risks / Trade-offs

- Plain books become eligible even before Nexus is used.
  - Mitigation: retain only one item and let hand/hotbar placement express the
    player's intended priority.
- Legacy tests may expect latest-token selection.
  - Mitigation: replace them with resolver and priority-order tests while
    retaining token serialization compatibility.

## Migration Plan

Deploy Core-compatible Nexus and Remnant versions together. Existing staged
SavedData and token-tagged items remain readable; no SavedData migration is
required.

## Open Questions

- None. The user approved one-item retention and the priority order on
  2026-08-16.

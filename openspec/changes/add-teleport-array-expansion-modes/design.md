## Context

The existing production scanner starts with the 26 positions adjacent to a
lodestone, then follows each reached material's local expansion cube. That
creates useful irregular arrays, but some worlds prefer a symmetric build area.
All existing snapshot, quote, maintenance and visualization systems already
depend on the scanner, so the mode decision belongs at that one authority.

## Goals / Non-Goals

- Goals:
  - retain byte-for-byte-compatible `local` gameplay semantics by default;
  - offer a deterministic, complete lodestone-centred cube mode;
  - keep every scan bounded to distance five and loaded chunks only;
  - update loaded persisted snapshots when an administrator changes the rule;
  - keep diagnostics and visualization derived from the same scan result.
- Non-goals:
  - no client-selected mode;
  - no per-lodestone override;
  - no change to material profile schema or expansion values;
  - no new Observer/protocol fields, Screen controls or framebuffer path.

## Decisions

### One global enum gamerule

Register `deadrecall:teleport_array_expansion_mode` in the server bootstrap with
the stable values `local` and `centered`. `local` is the default so upgraded
worlds preserve their current arrays without administrator action. The game
rule receives title, description and value translations in both maintained
locales.

### Local mode retains the placement graph

The local traversal seeds every non-origin position with Chebyshev distance one.
A reached structural material whose effective local expansion radius is
positive enqueues the cube around that material. The walk continues until no
new positions are reached. Every candidate remains inside distance five of the
lodestone. A dedicated `seen` set prevents repeated work; only loaded positions
enter the authoritative `visited` result.

### Centered mode uses a monotonic fixed point

Let the lodestone be `L` and current centred radius be `R = 1`. Scan the complete
centre-excluded cube whose positions satisfy `distance∞(L, p) <= R`. For each
loaded, structural expansion emitter `p`, compute:

```text
candidate = min(5, distance∞(L, p) + effectiveLocalExpansionRadius(p))
R = max(R, candidate)
```

If `R` increases, scan the newly included positions of the complete cube and
repeat. Monotonic `max` makes the result independent of iteration order and
prevents multiple emitters in one layer from adding their radii. The global cap
guarantees at most 1,330 non-origin candidate positions.

### Shared scan result and mode-aware snapshots

Both algorithms fill the same accumulator and `Result`: totals, material
families, emitter diagnostics, structural positions, replaceable build sites
and loaded visited positions. Therefore snapshot creation, wear/degradation,
repair validation and visualization cannot invent different geometry.

Persisted material totals store a stable mode code (`local = 0`, `centered = 1`)
and separately check whether that key exists. A missing key is stale rather
than being mistaken for the local code. On a gamerule change, a callback
rescans active lodestones whose anchor chunks are already loaded. It never
loads an absent chunk. If an unloaded snapshot's key is absent or its code is
stale, quote material lookup returns no usable structure until the anchor is
loaded and a legitimate access can rescan it.

### Visualization follows normal refresh cadence

The server visualization authority already invokes the production scanner on
each accepted refresh. A gamerule change therefore appears naturally on the
next accepted refresh and existing unchanged-snapshot suppression still works.
No payload format or Observer snapshot changes.

## Risks / Trade-offs

- Centered mode may inspect more loaded positions than local mode. The hard
  11×11×11 envelope and existing one-scan-per-20-ticks visualization throttle
  keep work bounded.
- Switching the gamerule may rescan many loaded active anchors once. This is an
  administrator-triggered operation; unloaded anchors are deliberately skipped.
- Mode-specific snapshots can be stale while their chunks are unloaded. They
  are rejected for live quote use instead of force-loading the world.

## Migration Plan

Existing worlds receive `local`. Existing snapshots without an expansion-mode
total are explicitly treated as stale even though a missing integer decodes as
zero; loaded anchors are refreshed and unloaded anchors remain unloaded until a
legitimate later rescan. No network migration is required.

## Open Questions

None.

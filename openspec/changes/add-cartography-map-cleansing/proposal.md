## Why

Older or unwanted filled maps currently cannot be recycled into the empty map
required by the normal Nexus activation flow. Preserving or importing their
terrain and binding history adds compatibility complexity that is not needed
when the player explicitly wants to start over.

## What Changes

- Add a native cartography-table cleansing operation: one filled map plus one
  water bucket offers one real empty `minecraft:map` as the result.
- Consume the filled-map item only when the server accepts result pickup, and
  replace the water bucket with an empty bucket without item loss or duplication.
- Treat cleansing as item recycling, not in-place SavedData mutation: the empty
  result carries no MapId or Nexus identity, while old SavedData and binding
  records remain untouched for any other copies that may still exist.
- Revalidate the exact inputs on every result take. A stale preview, unsupported
  click path, or unavailable remainder destination fails closed without
  consuming inputs.
- Keep the original empty-map Nexus activation path unchanged. The pending
  filled-map activation compatibility direction is superseded and SHALL NOT be
  enabled.

## Impact

- Affected specs: `nexus-map-cleansing`; pending
  `lodestone-interface-activation` filled-map compatibility is superseded
- Affected code: native cartography menu input/result handling, server-side
  result-take transaction, inventory remainder handling, and focused tests
- Persisted data: no schema change and no deletion of vanilla map SavedData,
  Nexus detail storage, or binding-ledger entries
- Observer/UI: no custom Screen or framebuffer path; the native cartography
  Screen and its existing semantic Observer adapter remain authoritative


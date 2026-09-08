## Why
Recovery compasses cannot select destinations and Nexus maps incorrectly couple their immutable anchor to the teleport source. Rescue needs a bounded, once-per-node grace lifecycle.

## What Changes
- Enable discovered, authorized, distance-ordered compass and recovery-compass destination lists.
- Decouple server-validated map anchor/MapId from loaded, nearby, authorized sources and destinations in actually painted map coverage, without discovery requirements.
- Apply quarter deviation for recovery compass to own active death nodes, retaining safe landing.
- Add persistent one-use recovery grace with exact-node recovery, three-second suffix, sixty-second cap, offensive-action and lifecycle cancellation; preserve vanilla invisibility effects.
- Update production UI, Observer reconstruction, handbook, translations and automated/runtime validation.

## Impact
- Owning module: TotemNexus. Existing Core death lifecycle and Observer API remain unchanged.
- User explicitly authorized this specification, implementation, full verification and final commit in the task request.

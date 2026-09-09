## ADDED Requirements
### Requirement: Live death backpack target
Nexus SHALL resolve a death teleport from its exact bound backpack's current authoritative position and dimension, and revalidate that identity and position before completing movement. Remnant SHALL report movement without owning Nexus effects. Stale death coordinates SHALL NOT replace an unavailable or mismatched backpack.
#### Scenario: Backpack drifts after death
- **WHEN** the bound backpack moves before or during teleport preparation
- **THEN** safe landing targets its updated position, or fails without charge if current location cannot be safely resolved
#### Scenario: Backpack is recovered or replaced
- **WHEN** the bound backpack is recovered, destroyed, or its identity no longer matches
- **THEN** the pending teleport cannot land at the stale death marker

### Requirement: Close reachable recovery landing
Death recovery SHALL prefer safe landing 2–4 blocks from the backpack, at most 6 horizontal blocks and 3 vertical blocks away, with direct visibility or a traversable path of at most 10 blocks to pickup range. The search SHALL remain bounded and SHALL NOT expand to distant fallback landing.
#### Scenario: A corner hides the backpack
- **WHEN** a nearby safe floor has a short walk around a corner to the backpack
- **THEN** it is eligible instead of a distant random position
#### Scenario: Nearby floor is separated by an impassable wall
- **WHEN** no safe reachable landing exists in the bounded neighborhood
- **THEN** the server cancels without consuming payment rather than teleporting far away

### Requirement: Nexus-owned Phasing effect
Nexus SHALL own and register Phasing (虛化), a single visible status effect combining Weakness I, Night Vision, Invisibility and Resistance I after successful teleport to the player's own active death backpack. Remnant and Core SHALL NOT implement or register this effect. Existing unrelated potion effects SHALL retain their normal lifecycle.
#### Scenario: Arrival and exact recovery
- **WHEN** the player arrives and subsequently successfully recovers the exact bound backpack
- **THEN** Phasing remains for 60 ticks after recovery and then disappears together with its own benefits
#### Scenario: Search exceeds one minute
- **WHEN** the valid recovery session lasts longer than 1,200 ticks without a cancellation event
- **THEN** Phasing continues until exact recovery or cancellation
#### Scenario: Unrelated recovery or potion
- **WHEN** another backpack is recovered or an independent potion is already active
- **THEN** the target recovery countdown is unaffected and independent potion state is not erased
#### Scenario: Session cancellation
- **WHEN** the player attacks, launches an offensive projectile, dies, logs out, starts another teleport, or the bound node is invalidated
- **THEN** Nexus ends its Phasing session and associated benefits without leaving persistent unintended protection

## Why

Death retention currently depends on a token written only after a successful
Nexus teleport. A valid compass, recovery compass, plain book, or filled map
therefore drops on death until the player has teleported once, which makes the
feature unpredictable.

## What Changes

- Select one valid teleport-interface item automatically at death without
  requiring a prior successful teleport.
- Use deterministic player-controlled priority: main hand, offhand, remaining
  hotbar slots, then main inventory.
- Preserve the existing one-item limit, persisted staging, respawn restoration,
  and vanishing-item exclusion.
- Retain legacy token metadata for compatibility but remove it from death
  eligibility.

## Impact

- Affected specs: `soulbound-teleport-interface` behavior.
- Affected code: Nexus interface eligibility and Remnant death scan order/tests.
- Security/performance: one bounded inventory scan at death; no tick scan,
  container scan, offline-player scan, or chunk loading.

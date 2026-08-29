## Why

Observer viewers currently receive separately drawn approximations of Nexus screens. Those duplicates drift from the owning map, friends, registration and death-node administration screens.

## What Changes

- Add Nexus-owned, read-only Observer factories/modes for all supported Nexus UI variants.
- Publish them through the TotemCore semantic Observer provider contract.
- Keep viewer authority separate from the target and suppress all mutation requests.
- Require owner-screen Observer support in future Nexus UI work.

## Impact

- Affected specs: `owned-observer-screen` (new capability).
- Affected code: Nexus client screens, provider registration, tests and CI guidance.

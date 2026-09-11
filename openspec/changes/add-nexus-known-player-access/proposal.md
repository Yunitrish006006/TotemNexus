# Nexus previously joined player access selection

## Why
The user requested selecting players who have previously joined, including offline players, when managing Nexus permissions.

## What Changes
- Consume Core’s server-owned directory from actual joins and existing world player data.
- Select/search paginated UUID identities in the access dialog; allow granting and revoking offline roles.
- Preserve owner/administrator and bound-book/range checks.
- Provide a module-owned read-only Observer provider for this production dialog.

## Impact
Core owns the new additive player directory API, lookup, search, pagination and persistence. Nexus consumes it and owns access authorization and UI. Existing map/Observer protocols are unchanged. New optional access-list packets and a dedicated `nexus_access` provider are added. Implementation scope is authorized by the user's request in this session. Commit/publication is not part of this change.

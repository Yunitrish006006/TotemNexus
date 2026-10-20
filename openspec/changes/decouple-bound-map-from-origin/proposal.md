# Independent bound map

## Why
Destroying the origin lodestone currently invalidates already registered maps and ongoing portable-map teleports.

## What Changes
Validate filled-map identity through its existing server binding ledger, independently of origin-node status. Preserve held-item identity, destination permissions, drawn coverage, costs, and safe landing. Non-map interfaces and destination-node lifecycle remain unchanged.

## Approval
User approved the behavior: 完成綁定之後就能獨立使用. This authorizes implementation, not publication.

## Impact
Nexus server item resolution, portable interface sessions, teleport revalidation, and server regression tests. No packet/schema/renderer changes.

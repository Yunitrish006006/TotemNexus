## Why

Death Nodes can currently be removed only through the administrator screen. A
player who no longer wants one of their own death teleport points must ask an
operator to remove it, even though ownership is already persisted by the
server.

## What Changes

- Allow every in-game player to open the Death Node screen while keeping the
  existing cross-player administration view restricted to command admins.
- Give non-administrators a self-service view containing only Death Nodes they
  own, regardless of any forged client query or node UUID.
- Let an owner permanently delete one selected Death Node after a server-issued
  30-second confirmation; the delete may remove an active point directly and
  does not delete or move the associated death backpack.
- Keep safe teleport, diagnostics, owner filtering and batch mutation controls
  administrator-only.
- Move permission checks from the shared `/deadrecall` command root to the
  administrative child commands so TotemRemnant cannot accidentally hide the
  player-accessible Nexus command when both modules are installed.

## Impact

- Affected specs: `player-death-node-management`
- Affected code: Nexus command registration, death-node query/action
  authorization, management payload and client screen, TotemRemnant shared
  command registration, localized documentation and release bundle versions
- Security: all ownership and role decisions remain server-authoritative; the
  client-provided owner filter, node UUID and action are treated as untrusted


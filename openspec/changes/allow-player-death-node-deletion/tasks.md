## 1. Server authority

- [x] 1.1 Make the shared command root player-accessible while retaining child-level administrator checks across Nexus and Remnant.
- [x] 1.2 Scope non-administrator snapshots to the requesting owner's UUID.
- [x] 1.3 Add owner-only request/confirm deletion actions with fresh role, ownership and token validation.
- [x] 1.4 Preserve administrator-only teleport, diagnostics and batch mutation behavior.

## 2. Client experience

- [x] 2.1 Add the server-provided view capability to the bounded snapshot codec.
- [x] 2.2 Present an owner-only screen without administrator controls.
- [x] 2.3 Add localized deletion confirmation and backpack-preservation guidance in English and Traditional Chinese.

## 3. Verification and release

- [x] 3.1 Add unit/GameTests for owner filtering, foreign-node rejection, forged administrator actions and successful reference cleanup.
- [x] 3.2 Verify administrator behavior and the TotemRemnant container command remain permission-gated.
- [x] 3.3 Build and test the affected standalone modules with Java 25.
- [x] 3.4 Bump exact module versions, assemble the next DeadRecall bundle and smoke-test dedicated-server startup.
- [x] 3.5 Update module and DeadRecall documentation, then mark every task complete.

## 0. Approval

- [x] 0.1 Approve the vanilla written-book representation, installed-section
  visibility, explicit two-hand consolidation and Core 0.5.0 release boundary.

## 1. TotemCore manual API

- [x] 1.1 Add immutable manual-section and duplicate-safe ordered registry API
  under `dev.totem.core.api.v1.manual`.
- [x] 1.2 Add canonical marker codec/helpers, deterministic revision and
  bounded written-book assembly with generated cover and contents.
- [x] 1.3 Add player grant, refresh, two-hand consolidation and marked-manual
  login refresh helpers without registering Core gameplay content.
- [x] 1.4 Add `en_us` and `zh_tw` shared manual translations and document the
  API contract.
- [x] 1.5 Unit-test ordering, duplicate rejection, marker recognition, stable
  revision, page bounds and component preservation.
- [x] 1.6 Support nested translatable page arguments so item names follow the
  active client language and resource pack.

## 2. Nexus migration

- [x] 2.1 Register the existing seven Nexus page keys as an ordered Core manual
  section.
- [x] 2.2 Replace direct manual construction/grant with the shared helper while
  preserving plain-book-on-lodestone behavior and stacked-book handling.
- [x] 2.3 Recognize the exact legacy Nexus generated-book signature and migrate
  it only during explicit manual interaction.
- [x] 2.4 Update Nexus localization, README and tests for canonical manual
  content and two-hand consolidation.
- [x] 2.5 Replace hard-coded Nexus item names with translatable item components.

## 3. Remnant tutorial

- [x] 3.1 Register localized sections for backpack basics/upgrades, dyeing,
  dropped-item protection, death-backpack lifecycle and portable-container
  safety.
- [x] 3.2 Add plain-book/recognized-manual interaction on a smithing table and
  leave all unrelated interactions untouched.
- [x] 3.3 Add contextual acquisition text, README documentation and tests for
  standalone Remnant and combined Nexus+Remnant page assembly.
- [x] 3.4 Correct tutorial-blocking content mismatches: the missing death
  backpack creation translation and any claims that do not match runtime
  behavior.
- [x] 3.5 Add the shared "Knowledge Is Power" advancement and award it only
  when a player obtains or already carries a marked Totem manual.
- [x] 3.6 Replace hard-coded Remnant item and recipe-material names with
  translatable item components and verify the rendered book layout.

## 4. Version and compatibility coordination

- [x] 4.1 Publish the additive API as TotemCore 0.5.0 and update Nexus and
  Remnant patch versions and exact Core dependencies.
- [x] 4.2 Update exact Core pins for the active standalone Totem module set so
  all modules remain co-installable.
- [ ] 4.3 Update lockstep manifests/docs only after every standalone artifact
  and assembled compatibility surface passes.

## 5. Verification

- [x] 5.1 Build and unit-test TotemCore with Java 25.
- [x] 5.2 Run Nexus unit tests and required Fabric GameTests, including legacy
  manual migration.
- [x] 5.3 Run Remnant unit tests, required Fabric GameTests and restart probe.
- [x] 5.4 Run a combined module client/server smoke test and verify one manual
  contains both Nexus and Remnant sections.
- [x] 5.5 Verify deterministic JARs, exact dependency pins and no duplicate
  manual authority in the DeadRecall bundle.

## 6. Shared two-page presentation

- [x] 6.1 Move canonical two-page book background, text and navigation from
  TotemRemnant into a TotemCore client Mixin and shared texture.
- [x] 6.2 Add a Core client page-overlay registry and migrate Remnant's crafting
  and smithing recipe graphics to an optional registered overlay.
- [x] 6.3 Verify Core plus Nexus renders the two-page manual without Remnant,
  then verify Core plus Nexus plus Remnant retains recipe overlays.

## 7. Nexus visual chapter

- [x] 7.1 Replace the seven dense Nexus body pages with twelve focused,
  localized pages and vanilla-item diagrams for registration, discovery,
  teleporting, scanning, materials, catalysts and maintenance.
- [x] 7.2 Add hover tooltips to diagram items and resolve copper variants from
  the vanilla item registry for Minecraft 26.2 compatibility.
- [x] 7.3 Capture all seven Traditional Chinese Nexus spreads in the real
  two-page client and verify that all diagrams remain inside their page bounds.

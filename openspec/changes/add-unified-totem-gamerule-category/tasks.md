## 1. Shared category

- [x] 1.1 Add the stable `totem:rules` category API to TotemCore.
- [x] 1.2 Add English and Traditional Chinese category labels.

## 2. Owning modules

- [x] 2.1 Assign every Remnant custom rule to the shared category.
- [x] 2.2 Assign every Locksmith custom rule to the shared category.
- [x] 2.3 Assign every Nexus custom rule to the shared category.
- [x] 2.4 Preserve every existing rule identifier, value type, and default.

## 3. Localization and verification

- [x] 3.1 Add English and Traditional Chinese names and descriptions for all six rules.
- [x] 3.2 Add category and language-key parity unit coverage.
- [x] 3.3 Build TotemCore and run affected module unit/compile verification.
- [x] 3.4 Strictly validate this OpenSpec change.

## 4. Coordinated release preparation

- [x] 4.1 Publish TotemCore 0.7.15 before the owning module releases.
- [x] 4.2 Prepare Locksmith 0.1.7, Remnant 0.2.17, and Nexus 0.3.10 with a
  `totem-core >=0.7.15 <0.8.0` dependency and the pinned Core release commit.
- [x] 4.3 Run clean unit, compile, build, and Server GameTest validation for all
  three owning modules against the released Core 0.7.15 JAR.
- [x] 4.4 Validate release workflows, localized JSON resources, embedded JAR
  metadata, strict OpenSpec, and reproducible artifact hashes.

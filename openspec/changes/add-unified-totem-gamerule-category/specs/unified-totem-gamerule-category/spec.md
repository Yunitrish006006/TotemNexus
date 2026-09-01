## ADDED Requirements

### Requirement: Shared native Totem game-rule category

TotemCore SHALL register one vanilla `GameRuleCategory` with the stable ID
`totem:rules`. Every custom game rule registered by a loaded Totem module SHALL
use that same category instance while remaining owned by its source module.

#### Scenario: Multiple Totem modules are loaded

- **WHEN** Remnant, Locksmith, and Nexus register their custom game rules
- **THEN** Minecraft groups all six rules under one Totem category in its native Game Rules screens

#### Scenario: An optional module is absent

- **WHEN** a Totem module that owns game rules is not installed
- **THEN** its rules are absent without preventing the shared category or other loaded rules from working

### Requirement: Native server-authoritative management

The unified category SHALL reuse Minecraft's native world-creation and in-world
Game Rules screens, current-value synchronization, input widgets, validation,
and game-master permission checks. The implementation SHALL NOT add a copied
rule store or a custom mirror Screen.

#### Scenario: An authorized administrator edits a rule in-world

- **WHEN** the server supplies loaded Totem rules and the administrator changes one through the native Game Rules screen
- **THEN** Minecraft validates and applies the value to the owning rule through the vanilla server-authoritative path

#### Scenario: A player lacks game-master permission

- **WHEN** a player without the required permission opens world options
- **THEN** the vanilla permission behavior prevents unauthorized rule mutation

### Requirement: Stable world and command compatibility

Moving a Totem rule to the shared category SHALL NOT change its registered
identifier, serialized value, default value, or owning module callback.

#### Scenario: Existing world is upgraded

- **WHEN** a world already stores one or more Totem rule values
- **THEN** the same rule identifiers resolve those stored values under the new category without migration

### Requirement: Complete bilingual game-rule text

TotemCore SHALL provide non-empty `en_us` and `zh_tw` labels for the shared
category. Each owning module SHALL provide non-empty `en_us` and `zh_tw` names
and `.description` text for every custom rule; enum rules SHALL also localize
every selectable value.

#### Scenario: Client language is Traditional Chinese

- **WHEN** a player views the native Game Rules screen using `zh_tw`
- **THEN** the Totem category, all loaded rule names, descriptions, and enum choices appear as clear Traditional Chinese text rather than translation keys

#### Scenario: English and Traditional Chinese resources are compared

- **WHEN** automated resource tests enumerate both language files in an owning module
- **THEN** the files expose matching keys and non-empty text for every contributed game rule

## 0.3.26

Map interaction fixes for Minecraft 26.3. Requires Java 25 and TotemCore 0.7.23 or newer within the 0.7 series.

### Fixed

- Actionable buttons on the map receive clicks before map dragging or marker selection.
- Left-click selects a destination, left-drag pans without selecting or toggling a favorite, and right-click toggles a marker's favorite state. Mouse handling now uses Minecraft's current button constants.
- Mouse-wheel zoom keeps the map position under the cursor stable when pan bounds permit it, with only integer-pixel rounding. At a boundary, panning stops at the exact allowed limit.
- Observer views remain read-only: clicking, dragging and scrolling cannot change the viewport or selection or send action packets.

### Validation

- Added regression coverage for actionable buttons, cursor anchoring, exact pan limits and Observer input suppression at GUI scales 2 and 3.
- Local unit tests, server GameTests, map-focused development and production-JAR client tests, and dedicated three-JVM Observer integration passed.

## Totem Nexus 0.3.27

- Fixed newly surveyed regions of expanded maps showing fine terrain only at 1600% zoom. Recorded detail now scales correctly at 200%, 400%, and 800% too.
- Kept detail pages aligned with world coordinates at fractional scales, without independently rounding adjacent page edges.
- Unknown pixels remain transparent over the existing terrain. No invented exploration data, world-format migration, or new terrain packets.
- Applied the same rendering fix to the module-owned read-only Observer map.

Requires Minecraft 26.3, Java 25, Fabric API, and Totem Core >=0.7.23 <0.8.0.

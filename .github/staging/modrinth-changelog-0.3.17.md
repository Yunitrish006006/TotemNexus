## Compass rescue and map routing

- Ordinary and recovery compasses can bind/rebind and teleport using explored, currently authorized destinations, sorted by distance by default. Recovery compasses do not show a map canvas.
- Rescuing your own active Death Node with a recovery compass reduces normal horizontal deviation by 75%, while retaining safe landing checks.
- Successful rescues grant one-use server-tracked invisibility: three seconds after recovering the exact target backpack, capped at 60 seconds from arrival. Attacks, PvP, offensive projectiles, another teleport, invalidation and logout cancel it. Existing invisibility potion effects remain intact.
- Nexus maps can use other authorized, active lodestones inside their currently painted coverage as sources. Map destinations do not require compass discovery. The original anchor, MapId, map center and expansion center remain unchanged.
- Preserves server authority, costs, route reservations, safe landing and native item components. Use TotemVanillaTweaks 0.1.26+ for recovery-compass Observer support.

Requires Minecraft 26.2, Java 25, Fabric API and TotemCore 0.7.18 (below 0.8.0).

Validated with 87 Nexus unit tests, 91 server GameTests, seven client GameTests, dedicated-server smoke, and companion Observer integration/production/three-JVM tests.

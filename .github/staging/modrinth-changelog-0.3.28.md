## Totem Nexus 0.3.28

- Map zoom now uses the matching recorded resolution at each level. On a fully expanded map, 100%, 200%, 400%, 800% and 1600% correspond to 16, 8, 4, 2 and 1 world blocks per terrain pixel respectively.
- Intermediate detail is rebuilt from complete recorded terrain footprints. Unknown terrain keeps the coarser map fallback instead of inventing detail or shrinking the finest texture at every zoom.
- Recording prioritizes route-adjacent gaps using bounded world-anchored work. Pending work does not move when the player moves, and cartography never force-loads chunks.
- Repeated detail requests retain unfinished delivery order, and unchanged base-map pixels are no longer resent unnecessarily.
- Existing fine-detail saves remain readable. Derived detail is a bounded rebuildable cache; copied and locked map records retain their isolation.
- Update Nexus on both the server and clients for the new resolution requests. Missing compatible detail retains the coarse map fallback.
- Requires Minecraft 26.3, Java 25, Fabric Loader 0.19.5+ and Totem Core 0.7.23 (below 0.8.0).

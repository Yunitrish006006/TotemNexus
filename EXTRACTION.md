# Persistence compatibility contract

Nexus owns Space Unit, teleport, friend, death-node and distributed-spawn
SavedData, payloads, resources, client UI and Mixins. Extraction preserves all
existing `deadrecall:*` identifiers, SavedData keys, codecs and payload IDs.

Nexus registers the optional Core death-backpack lifecycle adapter; it never
requires Remnant. The first copy remains additive until standalone, bundle,
legacy-world, dimension, multi-player, restart and Dedicated Server validation
have passed.

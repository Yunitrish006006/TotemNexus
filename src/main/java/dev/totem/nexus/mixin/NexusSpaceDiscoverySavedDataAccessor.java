package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusSpaceDiscoverySavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(NexusSpaceDiscoverySavedData.class)
public interface NexusSpaceDiscoverySavedDataAccessor {
    @Accessor("discoveredByPlayer")
    Map<UUID, Set<UUID>> deadrecall$getDiscoveredByPlayer();

    @Accessor("favoritesByPlayer")
    Map<UUID, Set<UUID>> deadrecall$getFavoritesByPlayer();
}

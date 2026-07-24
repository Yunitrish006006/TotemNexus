package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** Server-side snapshot of an online friend for a map response; never sourced from a client payload. */
public record NexusOnlineFriendTarget(UUID playerId, String name, ResourceKey<Level> dimension, BlockPos displayPos) {
    public NexusOnlineFriendTarget {
        Objects.requireNonNull(playerId, "playerId"); Objects.requireNonNull(dimension, "dimension"); Objects.requireNonNull(displayPos, "displayPos");
        name = name == null || name.isBlank() ? playerId.toString().substring(0, 8) : name;
        displayPos = displayPos.immutable();
    }
}

package dev.totem.nexus.space;

import dev.totem.core.api.v1.death.DeathBackpackNodeLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/** Optional Core lifecycle adapter; it never class-loads a Remnant implementation. */
public final class NexusDeathBackpackNodeAdapter implements DeathBackpackNodeLifecycle {
    private final NexusDeathNodeAuthority authority;

    public NexusDeathBackpackNodeAdapter(NexusDeathNodeAuthority authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    @Override
    public UUID create(ServerPlayer owner, ServerLevel level, BlockPos position) {
        return authority.create(owner, level, position);
    }

    @Override
    public void rollback(ServerPlayer owner, ServerLevel level, UUID nodeId) {
        authority.disable(owner, level, nodeId);
    }

    @Override
    public boolean recover(ServerPlayer recoveringPlayer, UUID nodeId) {
        return authority.recover(recoveringPlayer, nodeId);
    }
}

package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.core.api.DeathBackpackNodeLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Nexus-owned adapter; Remnant never imports Nexus implementation types. */
public final class NexusDeathBackpackNodeAdapter implements DeathBackpackNodeLifecycle {
    @Override
    public UUID create(ServerPlayer owner, ServerLevel level, BlockPos position) {
        return SpaceUnitHandler.createDeathNode(owner, level, position);
    }

    @Override
    public void rollback(ServerPlayer owner, ServerLevel level, UUID nodeId) {
        SpaceUnitHandler.disableDeathNode(owner, level, nodeId);
    }

    @Override
    public boolean recover(ServerPlayer recoveringPlayer, UUID nodeId) {
        return SpaceUnitHandler.disableDeathNode(recoveringPlayer, recoveringPlayer.level(), nodeId);
    }
}

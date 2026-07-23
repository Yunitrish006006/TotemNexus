package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** First authority slice: death-node persistence mutation, inactive until cutover. */
public final class NexusDeathNodeAuthority {
    public void bind(net.minecraft.world.item.ItemStack deathBackpack, UUID nodeId) {
        DeathNodeBackpackBinding.write(deathBackpack, nodeId);
    }

    public UUID create(ServerPlayer player, ServerLevel level, BlockPos position) {
        NexusSpaceUnitSavedData units = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusSpaceUnitRecord unit = units.createDeathUnit(level, position, player);
        discovery.markDiscovered(player.getUUID(), unit.id());
        return unit.id();
    }

    public boolean disable(ServerPlayer player, ServerLevel level, UUID nodeId) {
        return nodeId != null && level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE)
                .disableDeathUnit(player.getUUID(), nodeId, level.getGameTime());
    }
}

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
        NexusSpaceUnitSavedData units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        NexusSpaceDiscoverySavedData discovery = NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        NexusSpaceUnitRecord unit = units.createDeathUnit(level, position, player);
        discovery.markDiscovered(player.getUUID(), unit.id());
        return unit.id();
    }

    public void bind(ServerLevel level, UUID nodeId, UUID backpackEntityId) {
        boolean bound = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage())
                .bindDeathBackpack(nodeId, backpackEntityId, level.getGameTime());
        if (!bound) {
            throw new IllegalStateException("Could not persist death backpack reverse binding");
        }
    }

    public boolean disable(ServerPlayer player, ServerLevel level, UUID nodeId) {
        return nodeId != null && NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage())
                .disableDeathUnit(player.getUUID(), nodeId, level.getGameTime());
    }

    public boolean recover(ServerPlayer player, UUID nodeId) {
        return nodeId != null && NexusSpaceUnitSavedData.loadCanonical(player.level().getServer().overworld().getDataStorage())
                .recoverDeathUnit(nodeId, player.level().getGameTime());
    }
}

package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Complete server-side façade for the extracted Space Unit implementation.
 *
 * <p>The façade is intentionally inert until a future atomic cutover
 * composition registers its interactions, payload receivers and ticks. It
 * keeps that composition from depending on DeadRecall implementation types.
 */
public final class NexusGameplayAuthority implements NexusTeleportAuthority {
    @Override
    public UUID createDeathNode(ServerPlayer player, ServerLevel level, BlockPos deathPos) {
        return NexusSpaceUnitAuthority.createDeathNode(player, level, deathPos);
    }

    @Override
    public void bindDeathNode(ItemStack deathBackpack, UUID unitId) {
        NexusSpaceUnitAuthority.writeDeathNodeBinding(deathBackpack, unitId);
    }

    @Override
    public void disableDeathNodeFromBackpack(ServerPlayer player, ItemStack deathBackpack) {
        NexusSpaceUnitAuthority.disableDeathNodeFromBackpack(player, deathBackpack);
    }

    @Override
    public boolean disableDeathNode(ServerPlayer player, ServerLevel level, UUID unitId) {
        return NexusSpaceUnitAuthority.disableDeathNode(player, level, unitId);
    }

    @Override
    public void sendMap(ServerPlayer player, String sourceType, UUID sourceUnitId) {
        NexusSpaceUnitAuthority.sendSpaceUnitMap(player, sourceType, sourceUnitId);
    }

    @Override
    public void sendFriends(ServerPlayer player) {
        NexusSpaceUnitAuthority.sendFriendList(player);
    }

    @Override
    public void removeFriend(ServerPlayer player, UUID friendId) {
        NexusSpaceUnitAuthority.removeFriend(player, friendId);
    }

    @Override
    public void startTeleport(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId) {
        NexusSpaceUnitAuthority.startTeleport(player, sourceType, sourceUnitId, targetUnitId);
    }

    @Override
    public void setFavorite(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, boolean favorite) {
        NexusSpaceUnitAuthority.setFavorite(player, sourceType, sourceUnitId, targetUnitId, favorite);
    }

    @Override
    public void calibrate(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId) {
        NexusSpaceUnitAuthority.calibrateLodestone(player, sourceType, sourceUnitId, targetUnitId);
    }

    @Override
    public void setVisibility(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, String visibility) {
        NexusSpaceUnitAuthority.setLodestoneVisibility(player, sourceType, sourceUnitId, targetUnitId, visibility);
    }

    @Override
    public void rename(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, String name) {
        NexusSpaceUnitAuthority.setLodestoneName(player, sourceType, sourceUnitId, targetUnitId, name);
    }

    @Override
    public void setAccess(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            String role,
            String playerName,
            boolean enabled) {
        NexusSpaceUnitAuthority.setLodestoneAccess(
                player, sourceType, sourceUnitId, targetUnitId, role, playerName, enabled);
    }

    @Override
    public void confirmRegistration(ServerPlayer player, String dimension, int x, int y, int z) {
        NexusSpaceUnitAuthority.confirmLodestoneRegistration(player, dimension, x, y, z);
    }

    @Override
    public void tickTeleportSessions(MinecraftServer server) {
        NexusSpaceUnitAuthority.tickTeleportSessions(server);
    }

    @Override
    public void tickLodestoneIntegrity(MinecraftServer server) {
        NexusSpaceUnitAuthority.tickLodestoneIntegrity(server);
    }
}

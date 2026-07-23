package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The server-only authority boundary that must move as one unit before Nexus
 * can activate its packet receivers. Implementations own all validation,
 * privacy checks, persistence mutation and player feedback.
 */
public interface NexusTeleportAuthority {
    UUID createDeathNode(ServerPlayer player, ServerLevel level, BlockPos deathPos);
    void bindDeathNode(ItemStack deathBackpack, UUID unitId);
    void disableDeathNodeFromBackpack(ServerPlayer player, ItemStack deathBackpack);
    boolean disableDeathNode(ServerPlayer player, ServerLevel level, UUID unitId);
    void sendMap(ServerPlayer player, String sourceType, UUID sourceUnitId);
    void sendFriends(ServerPlayer player);
    void removeFriend(ServerPlayer player, UUID friendId);
    void startTeleport(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId);
    void setFavorite(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, boolean favorite);
    void calibrate(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId);
    void setVisibility(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, String visibility);
    void rename(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, String name);
    void setAccess(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId,
                   String role, String playerName, boolean enabled);
    void confirmRegistration(ServerPlayer player, String dimension, int x, int y, int z);
    void tickTeleportSessions(MinecraftServer server);
    void tickLodestoneIntegrity(MinecraftServer server);
}

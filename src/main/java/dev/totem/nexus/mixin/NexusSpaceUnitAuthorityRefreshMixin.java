package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusFriendSavedData;
import dev.totem.nexus.space.NexusSpaceUnitSavedData;
import dev.totem.nexus.space.FriendTeleportSessionPolicy;
import dev.totem.nexus.space.NexusSpaceUnitAuthority;
import dev.totem.nexus.space.NexusSpaceUnitStructureRefresh;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mixin(NexusSpaceUnitAuthority.class)
public abstract class NexusSpaceUnitAuthorityRefreshMixin {
    @Accessor("teleportSessions")
    public static Map<UUID, Object> deadrecall$getTeleportSessions() {
        throw new AssertionError();
    }

    @Inject(
            method = "sendSpaceUnitMap(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;)V",
            at = @At("HEAD")
    )
    private static void deadrecall$refreshMapSource(
            ServerPlayer player,
            UUID sourceUnitId,
            CallbackInfo ci
    ) {
        NexusSpaceUnitStructureRefresh.refresh(player.level().getServer(), sourceUnitId);
    }

    @Inject(
            method = "startTeleport(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Ljava/util/UUID;Ljava/util/UUID;)V",
            at = @At("HEAD")
    )
    private static void deadrecall$refreshTeleportRoute(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            CallbackInfo ci
    ) {
        MinecraftServer server = player.level().getServer();
        if (NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE.equals(sourceType)) {
            NexusSpaceUnitStructureRefresh.refresh(server, sourceUnitId);
        }
        NexusSpaceUnitStructureRefresh.refresh(server, targetUnitId);
    }

    @Inject(
            method = "startTeleport(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Ljava/util/UUID;Ljava/util/UUID;)V",
            at = @At("RETURN")
    )
    private static void deadrecall$notifyFriendTeleportStarted(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            CallbackInfo ci
    ) {
        if (targetUnitId == null || targetUnitId.equals(player.getUUID())) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData unitData = server.overworld()
                .getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        if (unitData.get(targetUnitId).isPresent()) {
            return;
        }

        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUnitId);
        if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isRemoved()) {
            return;
        }

        NexusFriendSavedData friendData = server.overworld()
                .getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        if (!friendData.areFriends(player.getUUID(), targetUnitId)) {
            return;
        }

        Object sessionValue = deadrecall$getTeleportSessions().get(player.getUUID());
        if (!(sessionValue instanceof NexusSpaceUnitTeleportSessionAccessor session)
                || !targetUnitId.equals(session.deadrecall$getTargetUnitId())) {
            return;
        }

        targetPlayer.sendSystemMessage(Component.empty()
                .append(Component.translatable("message.deadrecall.space_unit.teleport_start"))
                .append(Component.literal(": "))
                .append(player.getDisplayName())
                .append(Component.literal(" → "))
                .append(targetPlayer.getDisplayName()));
    }

    @Inject(
            method = "removeFriend",
            at = @At("TAIL")
    )
    private static void deadrecall$cancelRemovedFriendTeleports(
            ServerPlayer player,
            UUID friendId,
            CallbackInfo ci
    ) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.level().getServer();
        Iterator<Map.Entry<UUID, Object>> iterator = deadrecall$getTeleportSessions().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Object> entry = iterator.next();
            if (!(entry.getValue() instanceof NexusSpaceUnitTeleportSessionAccessor session)) {
                continue;
            }
            if (!FriendTeleportSessionPolicy.belongsToRelationship(
                    entry.getKey(),
                    session.deadrecall$getTargetUnitId(),
                    playerId,
                    friendId)) {
                continue;
            }

            iterator.remove();
            ServerPlayer requester = server.getPlayerList().getPlayer(entry.getKey());
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable(
                        "message.deadrecall.space_unit.teleport_cancelled.target_friendship"));
            }
        }
    }
}

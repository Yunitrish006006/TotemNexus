package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.Deadrecall;
import com.adaptor.deadrecall.mixin.DeadRecallSpaceDiscoverySavedDataAccessor;
import com.adaptor.deadrecall.mixin.DeadRecallSpaceUnitSavedDataAccessor;
import com.adaptor.deadrecall.network.DeathNodeAdminPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeathNodeAdminService {
    public static final String ACTION_DISABLE = "disable";
    public static final String ACTION_PURGE = "purge";

    private DeathNodeAdminService() {
    }

    public static boolean canManage(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (!canManage(player)) {
            deny(player);
            return;
        }

        MinecraftServer server = player.level().getServer();
        DeadRecallSpaceUnitSavedData data = units(server);
        Map<UUID, SpaceUnitRecord> unitsById = unitMap(data);
        List<SpaceUnitRecord> deathNodes = new ArrayList<>();
        for (SpaceUnitRecord unit : unitsById.values()) {
            if (unit.type() == SpaceUnitType.DEATH) {
                deathNodes.add(unit);
            }
        }

        deathNodes.sort(Comparator
                .comparing((SpaceUnitRecord unit) -> ownerDisplayName(server, unit.owner()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingLong(SpaceUnitRecord::createdGameTime).reversed())
                .thenComparing(SpaceUnitRecord::id));

        boolean truncated = deathNodes.size() > DeathNodeAdminPayload.MAX_ENTRIES;
        int size = Math.min(deathNodes.size(), DeathNodeAdminPayload.MAX_ENTRIES);
        List<DeathNodeAdminPayload.Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            SpaceUnitRecord unit = deathNodes.get(index);
            entries.add(new DeathNodeAdminPayload.Entry(
                    unit.id(),
                    unit.owner(),
                    ownerDisplayName(server, unit.owner()),
                    unit.name(),
                    unit.status().id(),
                    unit.dimension().identifier().toString(),
                    unit.pos().getX(),
                    unit.pos().getY(),
                    unit.pos().getZ(),
                    unit.createdGameTime(),
                    unit.updatedGameTime()
            ));
        }

        ServerPlayNetworking.send(player, new DeathNodeAdminPayload(entries, truncated));
    }

    public static void handleAction(ServerPlayer player, UUID nodeId, String actionId) {
        if (!canManage(player)) {
            deny(player);
            return;
        }
        if (nodeId == null) {
            player.sendSystemMessage(Component.literal("缺少死亡節點 UUID。\n").withStyle(ChatFormatting.RED));
            return;
        }

        MinecraftServer server = player.level().getServer();
        DeadRecallSpaceUnitSavedData data = units(server);
        Map<UUID, SpaceUnitRecord> unitsById = unitMap(data);
        SpaceUnitRecord unit = unitsById.get(nodeId);
        if (unit == null || unit.type() != SpaceUnitType.DEATH) {
            player.sendSystemMessage(Component.literal("找不到指定的死亡節點。\n").withStyle(ChatFormatting.RED));
            sendSnapshot(player);
            return;
        }

        String action = actionId == null ? "" : actionId.trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case ACTION_DISABLE -> disable(player, data, unitsById, unit);
            case ACTION_PURGE -> purge(player, data, unitsById, unit);
            default -> player.sendSystemMessage(Component.literal("不支援的死亡節點管理操作。\n").withStyle(ChatFormatting.RED));
        }
        sendSnapshot(player);
    }

    private static void disable(
            ServerPlayer administrator,
            DeadRecallSpaceUnitSavedData data,
            Map<UUID, SpaceUnitRecord> unitsById,
            SpaceUnitRecord unit) {
        if (unit.status() != SpaceUnitStatus.ACTIVE) {
            administrator.sendSystemMessage(Component.literal("此死亡節點已經停用。\n").withStyle(ChatFormatting.YELLOW));
            return;
        }

        SpaceUnitRecord disabled = unit.withStatus(SpaceUnitStatus.DISABLED, administrator.level().getGameTime());
        unitsById.put(disabled.id(), disabled);
        data.setDirty();
        Deadrecall.LOGGER.info(
                "Administrator {} disabled death node {} owned by {} at {} {}",
                administrator.getName().getString(),
                unit.id(),
                unit.owner(),
                unit.dimension().identifier(),
                unit.pos()
        );
        administrator.sendSystemMessage(Component.literal("已停用死亡節點：" + unit.name()).withStyle(ChatFormatting.GREEN));
    }

    private static void purge(
            ServerPlayer administrator,
            DeadRecallSpaceUnitSavedData data,
            Map<UUID, SpaceUnitRecord> unitsById,
            SpaceUnitRecord unit) {
        if (unit.status() == SpaceUnitStatus.ACTIVE) {
            administrator.sendSystemMessage(Component.literal("ACTIVE 死亡節點必須先停用，才能永久刪除。\n").withStyle(ChatFormatting.RED));
            return;
        }

        unitsById.remove(unit.id());
        data.setDirty();
        removeDiscoveryReferences(administrator.level().getServer(), unit.id());
        Deadrecall.LOGGER.info(
                "Administrator {} permanently purged death node {} owned by {} at {} {}",
                administrator.getName().getString(),
                unit.id(),
                unit.owner(),
                unit.dimension().identifier(),
                unit.pos()
        );
        administrator.sendSystemMessage(Component.literal("已永久刪除死亡節點：" + unit.name()).withStyle(ChatFormatting.GREEN));
    }

    private static void removeDiscoveryReferences(MinecraftServer server, UUID unitId) {
        DeadRecallSpaceDiscoverySavedData data = discovery(server);
        DeadRecallSpaceDiscoverySavedDataAccessor accessor =
                (DeadRecallSpaceDiscoverySavedDataAccessor) (Object) data;
        boolean changed = removeReference(accessor.deadrecall$getDiscoveredByPlayer(), unitId);
        changed = removeReference(accessor.deadrecall$getFavoritesByPlayer(), unitId) || changed;
        if (changed) {
            data.setDirty();
        }
    }

    private static boolean removeReference(Map<UUID, Set<UUID>> referencesByPlayer, UUID unitId) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Set<UUID>>> iterator = referencesByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<UUID>> entry = iterator.next();
            if (entry.getValue().remove(unitId)) {
                changed = true;
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        return changed;
    }

    private static Map<UUID, SpaceUnitRecord> unitMap(DeadRecallSpaceUnitSavedData data) {
        return ((DeadRecallSpaceUnitSavedDataAccessor) (Object) data).deadrecall$getUnitsById();
    }

    private static DeadRecallSpaceUnitSavedData units(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeadRecallSpaceUnitSavedData.TYPE);
    }

    private static DeadRecallSpaceDiscoverySavedData discovery(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeadRecallSpaceDiscoverySavedData.TYPE);
    }

    private static String ownerDisplayName(MinecraftServer server, UUID ownerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
        if (online != null) {
            return online.getName().getString();
        }
        String id = ownerId.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static void deny(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.literal("你沒有管理死亡節點的權限。\n").withStyle(ChatFormatting.RED));
        }
    }
}

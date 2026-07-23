package dev.totem.nexus.space;

import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.ManageDeathNodeAdminPayload;
import dev.totem.nexus.network.NexusDeathNodeAdminHandler;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Nexus-owned administrative authority for persisted death nodes; not live until cutover wiring is enabled. */
public final class NexusDeathNodeAdminAuthority implements NexusDeathNodeAdminHandler {
    public static final String ACTION_DISABLE = "disable";
    public static final String ACTION_PURGE = "purge";

    @Override
    public void requestAdminList(ServerPlayer player, RequestDeathNodeAdminPayload payload) {
        sendSnapshot(player);
    }

    @Override
    public void manageNode(ServerPlayer player, ManageDeathNodeAdminPayload payload) {
        if (!canManage(player)) { deny(player); return; }
        UUID nodeId = payload.nodeId();
        NexusSpaceUnitSavedData units = units(player.level().getServer());
        NexusSpaceUnitRecord node = units.get(nodeId).filter(unit -> unit.type() == SpaceUnitType.DEATH).orElse(null);
        if (node == null) { message(player, "找不到指定的死亡節點。", ChatFormatting.RED); sendSnapshot(player); return; }
        String action = payload.action() == null ? "" : payload.action().trim().toLowerCase(Locale.ROOT);
        if (ACTION_DISABLE.equals(action)) {
            if (node.status() != SpaceUnitStatus.ACTIVE) message(player, "此死亡節點已經停用。", ChatFormatting.YELLOW);
            else {
                units.put(withStatus(node, SpaceUnitStatus.DISABLED, player.level().getGameTime()));
                message(player, "已停用死亡節點：" + node.name(), ChatFormatting.GREEN);
            }
        } else if (ACTION_PURGE.equals(action)) {
            if (node.status() == SpaceUnitStatus.ACTIVE) message(player, "ACTIVE 死亡節點必須先停用，才能永久刪除。", ChatFormatting.RED);
            else if (units.purgeInactiveDeathUnit(nodeId)) {
                discovery(player.level().getServer()).removeUnitReferences(nodeId);
                message(player, "已永久刪除死亡節點：" + node.name(), ChatFormatting.GREEN);
            }
        } else message(player, "不支援的死亡節點管理操作。", ChatFormatting.RED);
        sendSnapshot(player);
    }

    public void sendSnapshot(ServerPlayer player) {
        if (!canManage(player)) { deny(player); return; }
        MinecraftServer server = player.level().getServer();
        List<DeathNodeAdminPayload.Entry> entries = units(server).deathNodes().stream()
                .sorted(Comparator.comparing((NexusSpaceUnitRecord node) -> ownerName(server, node.owner()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.comparingLong(NexusSpaceUnitRecord::createdGameTime).reversed()).thenComparing(NexusSpaceUnitRecord::id))
                .limit(DeathNodeAdminPayload.MAX_ENTRIES)
                .map(node -> new DeathNodeAdminPayload.Entry(node.id(), node.owner(), ownerName(server, node.owner()), node.name(), node.status().id(),
                        node.dimension().identifier().toString(), node.pos().getX(), node.pos().getY(), node.pos().getZ(), node.createdGameTime(), node.updatedGameTime()))
                .toList();
        boolean truncated = units(server).deathNodes().size() > DeathNodeAdminPayload.MAX_ENTRIES;
        ServerPlayNetworking.send(player, new DeathNodeAdminPayload(entries, truncated));
    }

    public static boolean canManage(ServerPlayer player) { return player != null && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN); }
    private static NexusSpaceUnitRecord withStatus(NexusSpaceUnitRecord node, SpaceUnitStatus status, long time) {
        return new NexusSpaceUnitRecord(node.id(), node.type(), node.dimension(), node.pos(), node.owner(), node.name(), node.visibility(), status,
                node.administrators(), node.allowedPlayers(), node.structure(), node.createdGameTime(), time);
    }
    private static NexusSpaceUnitSavedData units(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE); }
    private static NexusSpaceDiscoverySavedData discovery(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE); }
    private static String ownerName(MinecraftServer server, UUID owner) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getName().getString();
        String value = owner.toString(); return value.substring(0, Math.min(8, value.length()));
    }
    private static void deny(ServerPlayer player) { if (player != null) message(player, "你沒有管理死亡節點的權限。", ChatFormatting.RED); }
    private static void message(ServerPlayer player, String text, ChatFormatting color) { player.sendSystemMessage(Component.literal(text).withStyle(color)); }
}

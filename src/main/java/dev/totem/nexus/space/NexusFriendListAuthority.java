package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Server-owned friend-list projection for the stable clientbound payload; receiver activation remains deferred. */
public final class NexusFriendListAuthority {
    private NexusFriendListAuthority() { }
    public static SpaceUnitFriendsPayload build(MinecraftServer server, UUID playerId, NexusFriendSavedData friends) {
        List<SpaceUnitFriendsPayload.Entry> entries = new ArrayList<>();
        for (UUID id : friends.friendsOf(playerId)) entries.add(entry(server, id, "friend"));
        for (UUID id : friends.incomingInviteSources(playerId)) entries.add(entry(server, id, "incoming"));
        for (UUID id : friends.outgoingInviteTargets(playerId)) entries.add(entry(server, id, "outgoing"));
        entries.sort(Comparator.comparingInt((SpaceUnitFriendsPayload.Entry entry) -> statusOrder(entry.status()))
                .thenComparing(entry -> !entry.online()).thenComparing(SpaceUnitFriendsPayload.Entry::name, String.CASE_INSENSITIVE_ORDER));
        return new SpaceUnitFriendsPayload(entries.size() <= SpaceUnitFriendsPayload.MAX_ENTRIES ? entries : entries.subList(0, SpaceUnitFriendsPayload.MAX_ENTRIES));
    }
    private static SpaceUnitFriendsPayload.Entry entry(MinecraftServer server, UUID id, String status) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        String value = id.toString();
        return new SpaceUnitFriendsPayload.Entry(id, online == null ? value.substring(0, Math.min(8, value.length())) : online.getName().getString(), online != null, status);
    }
    private static int statusOrder(String status) { return switch (status) { case "friend" -> 0; case "incoming" -> 1; case "outgoing" -> 2; default -> 3; }; }
}

package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Bounded authorized page. Never contains the user's search input. */
public record AccessPlayersPayload(String sourceType, UUID sourceId, UUID targetId, String role,
                                   int page, int pages, long requestId, List<Entry> players) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 6;
    public record Entry(UUID id, String name, boolean online, boolean member) { }
    public AccessPlayersPayload {
        players = List.copyOf(players);
        if (!Set.of("administrator", "allowed").contains(role) || page < 0 || pages < 1 || page >= pages
                || requestId < 0 || players.size() > PAGE_SIZE || players.stream().anyMatch(p -> p.name().length() > 64)
                || players.stream().map(Entry::id).distinct().count() != players.size())
            throw new IllegalArgumentException("Invalid access player page");
    }
    public static final Type<AccessPlayersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/access_players"));
    public static final StreamCodec<FriendlyByteBuf, AccessPlayersPayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeUtf(p.sourceType,32); b.writeUUID(p.sourceId); b.writeUUID(p.targetId); b.writeUtf(p.role,32);
        b.writeVarInt(p.page); b.writeVarInt(p.pages); b.writeVarLong(p.requestId); b.writeVarInt(p.players.size());
        for (Entry e : p.players) { b.writeUUID(e.id); b.writeUtf(e.name,64); b.writeBoolean(e.online); b.writeBoolean(e.member); }
    }, b -> {
        String source = b.readUtf(32); UUID sourceId = b.readUUID(), targetId = b.readUUID(); String role = b.readUtf(32);
        int page = b.readVarInt(), pages = b.readVarInt(); long requestId = b.readVarLong(); int size = b.readVarInt();
        if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("Oversized access page");
        List<Entry> entries = new ArrayList<>();
        for (int i=0;i<size;i++) entries.add(new Entry(b.readUUID(),b.readUtf(64),b.readBoolean(),b.readBoolean()));
        return new AccessPlayersPayload(source,sourceId,targetId,role,page,pages,requestId,entries);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

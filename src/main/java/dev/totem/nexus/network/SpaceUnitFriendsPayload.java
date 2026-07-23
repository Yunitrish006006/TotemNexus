package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stable {@code deadrecall:space_unit_friends} clientbound wire contract. */
public record SpaceUnitFriendsPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SpaceUnitFriendsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_friends"));
    public static final int MAX_ENTRIES = 128;
    public record Entry(UUID id, String name, boolean online, String status) { }
    public static final StreamCodec<FriendlyByteBuf, SpaceUnitFriendsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> writeEntries(buf, payload.entries()), buf -> new SpaceUnitFriendsPayload(readEntries(buf)));
    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        int size = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) { Entry entry = entries.get(i); buf.writeUUID(entry.id()); buf.writeUtf(entry.name(), 64); buf.writeBoolean(entry.online()); buf.writeUtf(entry.status(), 16); }
    }
    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int size = Math.min(buf.readInt(), MAX_ENTRIES); List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(16)));
        return entries;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

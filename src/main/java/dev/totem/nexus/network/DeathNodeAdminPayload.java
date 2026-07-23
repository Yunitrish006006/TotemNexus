package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stable {@code deadrecall:death_node_admin} clientbound wire contract. */
public record DeathNodeAdminPayload(List<Entry> entries, boolean truncated) implements CustomPacketPayload {
    public static final Type<DeathNodeAdminPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "death_node_admin"));
    public static final int MAX_ENTRIES = 2048;
    public record Entry(UUID id, UUID ownerId, String ownerName, String name, String status, String dimension,
                        int x, int y, int z, long createdGameTime, long updatedGameTime) { }
    public DeathNodeAdminPayload { entries = List.copyOf(entries == null ? List.of() : entries); }
    public static final StreamCodec<FriendlyByteBuf, DeathNodeAdminPayload> CODEC = StreamCodec.of(
            (buf, value) -> { int size = Math.min(value.entries().size(), MAX_ENTRIES); buf.writeInt(size); for (int i = 0; i < size; i++) write(buf, value.entries().get(i)); buf.writeBoolean(value.truncated()); },
            buf -> { int size = Math.max(0, Math.min(buf.readInt(), MAX_ENTRIES)); List<Entry> entries = new ArrayList<>(size); for (int i = 0; i < size; i++) entries.add(read(buf)); return new DeathNodeAdminPayload(entries, buf.readBoolean()); });
    private static void write(FriendlyByteBuf buf, Entry value) { buf.writeUUID(value.id()); buf.writeUUID(value.ownerId()); buf.writeUtf(value.ownerName(), 64); buf.writeUtf(value.name(), 128); buf.writeUtf(value.status(), 32); buf.writeUtf(value.dimension(), 128); buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z()); buf.writeLong(value.createdGameTime()); buf.writeLong(value.updatedGameTime()); }
    private static Entry read(FriendlyByteBuf buf) { return new Entry(buf.readUUID(), buf.readUUID(), buf.readUtf(64), buf.readUtf(128), buf.readUtf(32), buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong(), buf.readLong()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

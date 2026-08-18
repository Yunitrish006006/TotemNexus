package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DeathNodeAdminPayload(
        List<Entry> entries,
        boolean truncated,
        int page,
        int pageSize,
        int totalEntries,
        long serverGameTime,
        boolean administratorView,
        UUID confirmationNodeId,
        UUID confirmationToken,
        String confirmationAction,
        long confirmationExpiresAtMillis) implements CustomPacketPayload {
    public static final Type<DeathNodeAdminPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "death_node_admin"));
    public static final int MAX_ENTRIES = 2048;
    private static final int MAX_DIAGNOSTIC_FLAGS = 8;

    public record Entry(
            UUID id,
            UUID ownerId,
            String ownerName,
            String name,
            String status,
            String dimension,
            int x,
            int y,
            int z,
            long createdGameTime,
            long updatedGameTime,
            List<String> diagnosticFlags) {
        public Entry {
            diagnosticFlags = List.copyOf(diagnosticFlags == null ? List.of() : diagnosticFlags);
        }
    }

    public DeathNodeAdminPayload {
        entries = List.copyOf(entries == null ? List.of() : entries);
        page = Math.max(0, page);
        pageSize = Math.max(1, Math.min(MAX_ENTRIES, pageSize));
        totalEntries = Math.max(0, totalEntries);
        serverGameTime = Math.max(0L, serverGameTime);
        confirmationAction = confirmationAction == null ? "" : confirmationAction;
        confirmationExpiresAtMillis = Math.max(0L, confirmationExpiresAtMillis);
    }

    public DeathNodeAdminPayload(List<Entry> entries, boolean truncated) {
        this(entries, truncated, 0, MAX_ENTRIES, entries == null ? 0 : entries.size(), 0L, false,
                null, null, "", 0L);
    }

    public boolean hasActivePurgeConfirmationFor(UUID nodeId, long nowMillis) {
        return hasActiveConfirmationFor(nodeId, "purge", nowMillis);
    }

    public boolean hasActiveConfirmationFor(UUID nodeId, String action, long nowMillis) {
        return nodeId != null
                && nodeId.equals(this.confirmationNodeId)
                && this.confirmationToken != null
                && (action == null ? "" : action).equals(this.confirmationAction)
                && this.confirmationExpiresAtMillis > nowMillis;
    }

    public static final StreamCodec<FriendlyByteBuf, DeathNodeAdminPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                int size = Math.min(payload.entries().size(), MAX_ENTRIES);
                buf.writeInt(size);
                for (int index = 0; index < size; index++) {
                    writeEntry(buf, payload.entries().get(index));
                }
                buf.writeBoolean(payload.truncated());
                buf.writeInt(payload.page());
                buf.writeInt(payload.pageSize());
                buf.writeInt(payload.totalEntries());
                buf.writeLong(payload.serverGameTime());
                buf.writeBoolean(payload.administratorView());
                buf.writeBoolean(payload.confirmationNodeId() != null && payload.confirmationToken() != null);
                if (payload.confirmationNodeId() != null && payload.confirmationToken() != null) {
                    buf.writeUUID(payload.confirmationNodeId());
                    buf.writeUUID(payload.confirmationToken());
                    buf.writeUtf(payload.confirmationAction(), 32);
                    buf.writeLong(payload.confirmationExpiresAtMillis());
                }
            },
            buf -> {
                int size = Math.max(0, Math.min(buf.readInt(), MAX_ENTRIES));
                List<Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(readEntry(buf));
                }
                boolean truncated = buf.readBoolean();
                int page = buf.readInt();
                int pageSize = buf.readInt();
                int totalEntries = buf.readInt();
                long serverGameTime = buf.readLong();
                boolean administratorView = buf.readBoolean();
                if (!buf.readBoolean()) {
                    return new DeathNodeAdminPayload(
                            entries,
                            truncated,
                            page,
                            pageSize,
                            totalEntries,
                            serverGameTime,
                            administratorView,
                            null,
                            null,
                            "",
                            0L
                    );
                }
                return new DeathNodeAdminPayload(
                        entries,
                        truncated,
                        page,
                        pageSize,
                        totalEntries,
                        serverGameTime,
                        administratorView,
                        buf.readUUID(),
                        buf.readUUID(),
                        buf.readUtf(32),
                        buf.readLong()
                );
            }
    );

    private static void writeEntry(FriendlyByteBuf buf, Entry entry) {
        buf.writeUUID(entry.id());
        buf.writeUUID(entry.ownerId());
        buf.writeUtf(entry.ownerName(), 64);
        buf.writeUtf(entry.name(), 128);
        buf.writeUtf(entry.status(), 32);
        buf.writeUtf(entry.dimension(), 128);
        buf.writeInt(entry.x());
        buf.writeInt(entry.y());
        buf.writeInt(entry.z());
        buf.writeLong(entry.createdGameTime());
        buf.writeLong(entry.updatedGameTime());
        int diagnosticCount = Math.min(entry.diagnosticFlags().size(), MAX_DIAGNOSTIC_FLAGS);
        buf.writeInt(diagnosticCount);
        for (int index = 0; index < diagnosticCount; index++) {
            buf.writeUtf(entry.diagnosticFlags().get(index), 64);
        }
    }

    private static Entry readEntry(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        String name = buf.readUtf(128);
        String status = buf.readUtf(32);
        String dimension = buf.readUtf(128);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        long createdGameTime = buf.readLong();
        long updatedGameTime = buf.readLong();
        int diagnosticCount = Math.max(0, Math.min(buf.readInt(), MAX_DIAGNOSTIC_FLAGS));
        List<String> diagnosticFlags = new ArrayList<>(diagnosticCount);
        for (int index = 0; index < diagnosticCount; index++) {
            diagnosticFlags.add(buf.readUtf(64));
        }
        return new Entry(
                id,
                ownerId,
                ownerName,
                name,
                status,
                dimension,
                x,
                y,
                z,
                createdGameTime,
                updatedGameTime,
                diagnosticFlags
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

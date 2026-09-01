package dev.totem.nexus.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded server-authoritative counted/buildable snapshot for one nearby teleport array. */
public record TeleportArrayVisualizationPayload(
        UUID sourceUnitId,
        String dimension,
        int originX,
        int originY,
        int originZ,
        boolean showArray,
        boolean showBuildSites,
        List<RelativeBlock> blocks) implements CustomPacketPayload {
    public static final int MAX_OFFSET = 5;
    public static final int MAX_BLOCKS = 1_330;
    public static final int MAX_DIMENSION_LENGTH = 128;
    public static final Type<TeleportArrayVisualizationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "teleport_array_visualization"));
    public static final StreamCodec<FriendlyByteBuf, TeleportArrayVisualizationPayload> CODEC = StreamCodec.of(
            TeleportArrayVisualizationPayload::write,
            TeleportArrayVisualizationPayload::read
    );

    public TeleportArrayVisualizationPayload {
        Objects.requireNonNull(sourceUnitId, "sourceUnitId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(blocks, "blocks");
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("Teleport-array dimension is invalid");
        }
        if (!showArray && !showBuildSites) {
            throw new IllegalArgumentException("Teleport-array snapshot has no enabled visualization class");
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Teleport-array preview has too many blocks");
        }
        Set<Integer> unique = new HashSet<>();
        for (RelativeBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            if (block.buildable() && !showBuildSites) {
                throw new IllegalArgumentException("Build-site entry is present while build sites are disabled");
            }
            if (!block.buildable() && !showArray) {
                throw new IllegalArgumentException("Counted entry is present while array display is disabled");
            }
            int coordinateKey = ((block.dx() + MAX_OFFSET) * 11 + block.dy() + MAX_OFFSET) * 11
                    + block.dz() + MAX_OFFSET;
            if (!unique.add(coordinateKey)) {
                throw new IllegalArgumentException("Teleport-array preview contains a duplicate block");
            }
        }
        blocks = List.copyOf(blocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TeleportArrayVisualizationPayload payload) {
        buf.writeUUID(payload.sourceUnitId());
        buf.writeUtf(payload.dimension(), MAX_DIMENSION_LENGTH);
        buf.writeInt(payload.originX());
        buf.writeInt(payload.originY());
        buf.writeInt(payload.originZ());
        buf.writeBoolean(payload.showArray());
        buf.writeBoolean(payload.showBuildSites());
        buf.writeVarInt(payload.blocks().size());
        for (RelativeBlock block : payload.blocks()) {
            buf.writeByte(block.dx());
            buf.writeByte(block.dy());
            buf.writeByte(block.dz());
            int flags = (block.expansionEmitter() ? 1 : 0) | (block.buildable() ? 2 : 0);
            buf.writeByte(flags);
        }
    }

    private static TeleportArrayVisualizationPayload read(FriendlyByteBuf buf) {
        UUID sourceUnitId = buf.readUUID();
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int originX = buf.readInt();
        int originY = buf.readInt();
        int originZ = buf.readInt();
        boolean showArray = buf.readBoolean();
        boolean showBuildSites = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BLOCKS) {
            throw new DecoderException("Teleport-array preview block count is out of range: " + count);
        }
        List<RelativeBlock> blocks = new ArrayList<>(count);
        try {
            for (int index = 0; index < count; index++) {
                int dx = buf.readByte();
                int dy = buf.readByte();
                int dz = buf.readByte();
                int flags = buf.readUnsignedByte();
                if ((flags & ~3) != 0) {
                    throw new IllegalArgumentException("Teleport-array block class flags are invalid");
                }
                blocks.add(new RelativeBlock(dx, dy, dz, (flags & 1) != 0, (flags & 2) != 0));
            }
            return new TeleportArrayVisualizationPayload(
                    sourceUnitId,
                    dimension,
                    originX,
                    originY,
                    originZ,
                    showArray,
                    showBuildSites,
                    blocks
            );
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid teleport-array preview payload", exception);
        }
    }

    public record RelativeBlock(int dx, int dy, int dz, boolean expansionEmitter, boolean buildable) {
        public RelativeBlock {
            if (Math.abs(dx) > MAX_OFFSET || Math.abs(dy) > MAX_OFFSET || Math.abs(dz) > MAX_OFFSET) {
                throw new IllegalArgumentException("Teleport-array block offset is out of range");
            }
            if (dx == 0 && dy == 0 && dz == 0) {
                throw new IllegalArgumentException("Teleport-array block offset cannot be the origin");
            }
            if (expansionEmitter && buildable) {
                throw new IllegalArgumentException("A build site cannot also be an expansion emitter");
            }
        }
    }
}

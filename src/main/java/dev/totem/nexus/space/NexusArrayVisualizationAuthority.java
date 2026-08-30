package dev.totem.nexus.space;

import dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Source-local authorization and bounded snapshot creation for teleport-array previews. */
public final class NexusArrayVisualizationAuthority {
    static final long REQUEST_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_ENABLE_TICK = new HashMap<>();

    private NexusArrayVisualizationAuthority() {
    }

    public static void handle(ServerPlayer player, RequestTeleportArrayVisualizationPayload payload) {
        createPayload(player, payload).ifPresent(result -> ServerPlayNetworking.send(player, result));
    }

    static Optional<TeleportArrayVisualizationPayload> createPayload(
            ServerPlayer player,
            RequestTeleportArrayVisualizationPayload payload) {
        if (!payload.enable()) {
            return Optional.empty();
        }

        long now = player.level().getServer().overworld().getGameTime();
        if (!claimEnable(player.getUUID(), now)) {
            return Optional.empty();
        }
        if (!SpaceUnitType.LODESTONE.id().equals(payload.sourceType())) {
            return Optional.empty();
        }
        if (NexusSpaceUnitAuthority.currentInterfaceContext(player)
                .filter(context -> context.matchesSource(payload.sourceType(), payload.sourceUnitId()))
                .isEmpty()) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord source = new NexusMapSourceAuthority()
                .validateLodestone(player, payload.sourceUnitId())
                .orElse(null);
        if (source == null) {
            return Optional.empty();
        }
        ServerLevel sourceLevel = player.level().getServer().getLevel(source.dimension());
        if (sourceLevel == null || !sourceLevel.isLoaded(source.pos())) {
            return Optional.empty();
        }

        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                sourceLevel,
                source.pos(),
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        List<TeleportArrayVisualizationPayload.RelativeBlock> blocks = relativeBlocks(source.pos(), scan);
        return Optional.of(new TeleportArrayVisualizationPayload(
                source.id(),
                source.dimension().identifier().toString(),
                source.pos().getX(),
                source.pos().getY(),
                source.pos().getZ(),
                TeleportArrayVisualizationPayload.MAX_LIFETIME_TICKS,
                blocks
        ));
    }

    static boolean claimEnable(UUID playerId, long now) {
        Long previous = LAST_ENABLE_TICK.get(playerId);
        if (previous != null && now - previous < REQUEST_INTERVAL_TICKS) {
            return false;
        }
        LAST_ENABLE_TICK.put(playerId, now);
        return true;
    }

    static List<TeleportArrayVisualizationPayload.RelativeBlock> relativeBlocks(
            BlockPos origin,
            TeleportArrayMaterialScan.Result scan) {
        List<TeleportArrayVisualizationPayload.RelativeBlock> blocks = new ArrayList<>(scan.structuralPositions().size());
        for (BlockPos position : scan.structuralPositions()) {
            blocks.add(new TeleportArrayVisualizationPayload.RelativeBlock(
                    position.getX() - origin.getX(),
                    position.getY() - origin.getY(),
                    position.getZ() - origin.getZ(),
                    scan.expansionEmitterPositions().contains(position)
            ));
        }
        blocks.sort(Comparator
                .comparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dy)
                .thenComparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dx)
                .thenComparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dz));
        return List.copyOf(blocks);
    }

    public static void disconnect(UUID playerId) {
        LAST_ENABLE_TICK.remove(playerId);
    }
}

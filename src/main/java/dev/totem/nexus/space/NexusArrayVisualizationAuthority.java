package dev.totem.nexus.space;

import dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationStatusPayload;
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

/** Source-local authorization and bounded dynamic snapshots for teleport-array visualization. */
public final class NexusArrayVisualizationAuthority {
    static final long REQUEST_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_REQUEST_TICK = new HashMap<>();
    private static final Map<UUID, TeleportArrayVisualizationPayload> LAST_SNAPSHOT = new HashMap<>();
    private static final Map<UUID, VisualizationSession> ACTIVE_SESSIONS = new HashMap<>();

    private NexusArrayVisualizationAuthority() {
    }

    public static void handle(ServerPlayer player, RequestTeleportArrayVisualizationPayload payload) {
        Evaluation evaluation = evaluate(player, payload);
        evaluation.snapshot().ifPresent(snapshot -> ServerPlayNetworking.send(player, snapshot));
        evaluation.status().ifPresent(status -> ServerPlayNetworking.send(player, status));
    }

    /** Test-facing production evaluation; a suppressed unchanged refresh returns an empty snapshot. */
    static Optional<TeleportArrayVisualizationPayload> createPayload(
            ServerPlayer player,
            RequestTeleportArrayVisualizationPayload payload) {
        return evaluate(player, payload).snapshot();
    }

    private static Evaluation evaluate(
            ServerPlayer player,
            RequestTeleportArrayVisualizationPayload payload) {
        UUID playerId = player.getUUID();
        if (!payload.enabled()) {
            clearSession(playerId);
            return Evaluation.NONE;
        }

        long now = player.level().getServer().overworld().getGameTime();
        if (!claimRefresh(playerId, now)) {
            return Evaluation.NONE;
        }

        VisualizationSession session = ACTIVE_SESSIONS.get(playerId);
        boolean continuingSameSource = session != null && session.matches(payload.sourceType(), payload.sourceUnitId());
        NexusSpaceUnitRecord source = (continuingSameSource
                ? authorizeSessionRefresh(player, payload)
                : authorizeInitialRequest(player, payload)).orElse(null);
        if (source == null) {
            clearActiveSession(playerId);
            return new Evaluation(Optional.empty(), Optional.of(rejected(payload.sourceUnitId())));
        }
        ServerLevel sourceLevel = player.level().getServer().getLevel(source.dimension());
        if (sourceLevel == null || !sourceLevel.isLoaded(source.pos())) {
            clearActiveSession(playerId);
            return new Evaluation(Optional.empty(), Optional.of(rejected(payload.sourceUnitId())));
        }

        establishSession(playerId, payload.sourceType(), payload.sourceUnitId(),
                payload.showArray(), payload.showBuildSites());

        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                sourceLevel,
                source.pos(),
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        TeleportArrayVisualizationPayload snapshot = new TeleportArrayVisualizationPayload(
                source.id(),
                source.dimension().identifier().toString(),
                source.pos().getX(),
                source.pos().getY(),
                source.pos().getZ(),
                payload.showArray(),
                payload.showBuildSites(),
                relativeBlocks(source.pos(), scan, payload.showArray(), payload.showBuildSites())
        );
        boolean changed = recordSnapshotIfChanged(playerId, snapshot);
        return new Evaluation(
                changed ? Optional.of(snapshot) : Optional.empty(),
                Optional.of(new TeleportArrayVisualizationStatusPayload(
                        source.id(), true, payload.showArray(), payload.showBuildSites()))
        );
    }

    private static Optional<NexusSpaceUnitRecord> authorizeInitialRequest(
            ServerPlayer player,
            RequestTeleportArrayVisualizationPayload payload) {
        if (!SpaceUnitType.LODESTONE.id().equals(payload.sourceType())) {
            return Optional.empty();
        }
        TeleportInterfaceContext context = NexusSpaceUnitAuthority.currentInterfaceContext(player)
                .filter(candidate -> candidate.matchesSource(payload.sourceType(), payload.sourceUnitId()))
                .orElse(null);
        if (context == null || NexusSpaceUnitAuthority.establishInterfaceContext(
                player, context.interactionHand(), payload.sourceType(), payload.sourceUnitId()).isEmpty()) {
            return Optional.empty();
        }
        return new NexusMapSourceAuthority().validateLodestone(player, payload.sourceUnitId());
    }

    /**
     * A successfully enabled preview owns a server-only session, so later
     * refreshes can remain live while the player puts the map away and builds.
     * Source authority is still revalidated in full on every accepted refresh.
     */
    private static Optional<NexusSpaceUnitRecord> authorizeSessionRefresh(
            ServerPlayer player,
            RequestTeleportArrayVisualizationPayload payload) {
        if (!SpaceUnitType.LODESTONE.id().equals(payload.sourceType())) {
            return Optional.empty();
        }
        return new NexusMapSourceAuthority().validateLodestone(player, payload.sourceUnitId());
    }

    private static TeleportArrayVisualizationStatusPayload rejected(UUID sourceUnitId) {
        return new TeleportArrayVisualizationStatusPayload(sourceUnitId, false, false, false);
    }

    static boolean claimRefresh(UUID playerId, long now) {
        Long previous = LAST_REQUEST_TICK.get(playerId);
        if (previous != null && now - previous < REQUEST_INTERVAL_TICKS) {
            return false;
        }
        LAST_REQUEST_TICK.put(playerId, now);
        return true;
    }

    /** Compatibility name retained for existing tests and downstream source callers. */
    static boolean claimEnable(UUID playerId, long now) {
        return claimRefresh(playerId, now);
    }

    static boolean recordSnapshotIfChanged(UUID playerId, TeleportArrayVisualizationPayload snapshot) {
        TeleportArrayVisualizationPayload previous = LAST_SNAPSHOT.put(playerId, snapshot);
        return !snapshot.equals(previous);
    }

    static List<TeleportArrayVisualizationPayload.RelativeBlock> relativeBlocks(
            BlockPos origin,
            TeleportArrayMaterialScan.Result scan,
            boolean showArray,
            boolean showBuildSites) {
        List<TeleportArrayVisualizationPayload.RelativeBlock> blocks = new ArrayList<>();
        if (showArray) {
            for (BlockPos position : scan.structuralPositions()) {
                blocks.add(relative(origin, position, scan.expansionEmitterPositions().contains(position), false));
            }
        }
        if (showBuildSites) {
            for (BlockPos position : scan.buildablePositions()) {
                blocks.add(relative(origin, position, false, true));
            }
        }
        blocks.sort(Comparator
                .comparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dy)
                .thenComparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dx)
                .thenComparingInt(TeleportArrayVisualizationPayload.RelativeBlock::dz));
        return List.copyOf(blocks);
    }

    private static TeleportArrayVisualizationPayload.RelativeBlock relative(
            BlockPos origin,
            BlockPos position,
            boolean expansionEmitter,
            boolean buildable) {
        return new TeleportArrayVisualizationPayload.RelativeBlock(
                position.getX() - origin.getX(),
                position.getY() - origin.getY(),
                position.getZ() - origin.getZ(),
                expansionEmitter,
                buildable
        );
    }

    public static void disconnect(UUID playerId) {
        clearSession(playerId);
    }

    /** Clears all non-persistent visualization state during server shutdown. */
    public static void shutdown() {
        LAST_REQUEST_TICK.clear();
        LAST_SNAPSHOT.clear();
        ACTIVE_SESSIONS.clear();
    }

    static boolean hasSession(UUID playerId) {
        return ACTIVE_SESSIONS.containsKey(playerId);
    }

    static boolean sessionMatches(UUID playerId, String sourceType, UUID sourceUnitId) {
        VisualizationSession session = ACTIVE_SESSIONS.get(playerId);
        return session != null && session.matches(sourceType, sourceUnitId);
    }

    static void establishSession(
            UUID playerId,
            String sourceType,
            UUID sourceUnitId,
            boolean showArray,
            boolean showBuildSites) {
        ACTIVE_SESSIONS.put(playerId,
                new VisualizationSession(sourceType, sourceUnitId, showArray, showBuildSites));
    }

    private static void clearActiveSession(UUID playerId) {
        ACTIVE_SESSIONS.remove(playerId);
        LAST_SNAPSHOT.remove(playerId);
    }

    private static void clearSession(UUID playerId) {
        LAST_REQUEST_TICK.remove(playerId);
        clearActiveSession(playerId);
    }

    private record VisualizationSession(
            String sourceType,
            UUID sourceUnitId,
            boolean showArray,
            boolean showBuildSites) {
        private boolean matches(String candidateType, UUID candidateId) {
            return sourceType.equals(candidateType) && sourceUnitId.equals(candidateId);
        }
    }

    private record Evaluation(
            Optional<TeleportArrayVisualizationPayload> snapshot,
            Optional<TeleportArrayVisualizationStatusPayload> status) {
        private static final Evaluation NONE = new Evaluation(Optional.empty(), Optional.empty());
    }
}

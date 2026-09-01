package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.world.TotemWorldOutlines;
import dev.totem.core.api.v1.client.world.WorldOutlineOcclusion;
import dev.totem.core.api.v1.client.world.WorldOutlineStyle;
import dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationStatusPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded, dynamically refreshed source preview owned by Nexus for the current client session. */
public final class NexusArrayVisualizationClient {
    static final int REFRESH_INTERVAL_TICKS = 20;
    static final int ACK_TIMEOUT_TICKS = 80;
    private static final double DISPLAY_RADIUS = 8.0D;
    private static final WorldOutlineStyle STRUCTURE_STYLE = new WorldOutlineStyle(
            0xFF4FC3F7, 3.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final WorldOutlineStyle EMITTER_STYLE = new WorldOutlineStyle(
            0xFFFFB74D, 4.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final WorldOutlineStyle ORIGIN_STYLE = new WorldOutlineStyle(
            0xFFB388FF, 5.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final WorldOutlineStyle BUILD_SITE_STYLE = new WorldOutlineStyle(
            0xFF66BB6A, 2.0F, WorldOutlineOcclusion.DEPTH_TESTED);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static Selection selection;
    private static TeleportArrayVisualizationPayload current;
    private static int refreshCountdown;
    private static int ticksWithoutAcknowledgement;
    private static UUID lastRenderedSourceId;

    private NexusArrayVisualizationClient() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
        ClientTickEvents.END_CLIENT_TICK.register(NexusArrayVisualizationClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void toggleArray(String sourceType, UUID sourceUnitId) {
        Selection previous = selectionFor(sourceType, sourceUnitId);
        updateSelection(new Selection(sourceType, sourceUnitId, !previous.showArray(), previous.showBuildSites()));
    }

    public static void toggleBuildSites(String sourceType, UUID sourceUnitId) {
        Selection previous = selectionFor(sourceType, sourceUnitId);
        updateSelection(new Selection(sourceType, sourceUnitId, previous.showArray(), !previous.showBuildSites()));
    }

    private static Selection selectionFor(String sourceType, UUID sourceUnitId) {
        if (selection != null && selection.matches(sourceType, sourceUnitId)) {
            return selection;
        }
        return new Selection(sourceType, sourceUnitId, false, false);
    }

    private static void updateSelection(Selection next) {
        if (!next.enabled()) {
            send(new RequestTeleportArrayVisualizationPayload(
                    next.sourceType(), next.sourceUnitId(), false, false));
            clear();
            return;
        }
        selection = next;
        current = null;
        lastRenderedSourceId = null;
        ticksWithoutAcknowledgement = 0;
        refreshCountdown = REFRESH_INTERVAL_TICKS;
        send(next.request());
    }

    public static void accept(TeleportArrayVisualizationPayload payload) {
        Selection active = selection;
        if (active == null || !active.matches(payload.sourceUnitId(), payload.showArray(), payload.showBuildSites())) {
            return;
        }
        current = payload;
        ticksWithoutAcknowledgement = 0;
        lastRenderedSourceId = null;
    }

    public static void acceptStatus(TeleportArrayVisualizationStatusPayload status) {
        Selection active = selection;
        if (active == null || !active.sourceUnitId().equals(status.sourceUnitId())) {
            return;
        }
        if (!status.accepted()) {
            clear();
            return;
        }
        if (!active.matches(status.sourceUnitId(), status.showArray(), status.showBuildSites())) {
            return;
        }
        ticksWithoutAcknowledgement = 0;
    }

    public static boolean isActiveFor(UUID sourceUnitId) {
        return selection != null && selection.sourceUnitId().equals(sourceUnitId) && selection.enabled();
    }

    public static boolean isArrayEnabledFor(UUID sourceUnitId) {
        return selection != null && selection.sourceUnitId().equals(sourceUnitId) && selection.showArray();
    }

    public static boolean isBuildSitesEnabledFor(UUID sourceUnitId) {
        return selection != null && selection.sourceUnitId().equals(sourceUnitId) && selection.showBuildSites();
    }

    static boolean hasSnapshotFor(UUID sourceUnitId) {
        return current != null && current.sourceUnitId().equals(sourceUnitId);
    }

    /** Package-visible deterministic Client GameTest setup; production toggles still own network requests. */
    static void enableForVisualTest(String sourceType, UUID sourceUnitId, boolean showArray, boolean showBuildSites) {
        selection = new Selection(sourceType, sourceUnitId, showArray, showBuildSites);
        current = null;
        refreshCountdown = REFRESH_INTERVAL_TICKS;
        ticksWithoutAcknowledgement = 0;
        lastRenderedSourceId = null;
    }

    public static void clear() {
        selection = null;
        current = null;
        refreshCountdown = 0;
        ticksWithoutAcknowledgement = 0;
        lastRenderedSourceId = null;
    }

    private static void disableAndClear() {
        Selection active = selection;
        if (active != null) {
            send(new RequestTeleportArrayVisualizationPayload(
                    active.sourceType(), active.sourceUnitId(), false, false));
        }
        clear();
    }

    private static void tick(Minecraft minecraft) {
        Selection active = selection;
        if (active == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        TeleportArrayVisualizationPayload snapshot = current;
        if (snapshot != null && !isValidContext(minecraft, snapshot)) {
            disableAndClear();
            return;
        }

        ticksWithoutAcknowledgement++;
        if (ticksWithoutAcknowledgement > ACK_TIMEOUT_TICKS) {
            disableAndClear();
            return;
        }
        if (!advanceRefreshCadence()) {
            return;
        }
        send(active.request());
    }

    static boolean advanceRefreshCadence() {
        if (refreshCountdown > 0) {
            refreshCountdown--;
        }
        if (refreshCountdown > 0) {
            return false;
        }
        refreshCountdown = REFRESH_INTERVAL_TICKS;
        return true;
    }

    private static void render() {
        TeleportArrayVisualizationPayload snapshot = current;
        if (snapshot == null) {
            return;
        }
        if (!isValidContext(Minecraft.getInstance(), snapshot)) {
            disableAndClear();
            return;
        }

        submit(snapshot);
        lastRenderedSourceId = snapshot.sourceUnitId();
    }

    static void submit(TeleportArrayVisualizationPayload payload) {
        BlockPos origin = origin(payload);
        if (payload.showArray()) {
            TotemWorldOutlines.block(origin, ORIGIN_STYLE);
        }
        for (TeleportArrayVisualizationPayload.RelativeBlock block : payload.blocks()) {
            WorldOutlineStyle style = block.buildable()
                    ? BUILD_SITE_STYLE
                    : block.expansionEmitter() ? EMITTER_STYLE : STRUCTURE_STYLE;
            TotemWorldOutlines.block(origin.offset(block.dx(), block.dy(), block.dz()), style);
        }
    }

    static boolean hasRendered(UUID sourceUnitId) {
        return sourceUnitId.equals(lastRenderedSourceId);
    }

    private static boolean isValidContext(Minecraft minecraft, TeleportArrayVisualizationPayload snapshot) {
        if (minecraft.level == null
                || minecraft.player == null
                || !minecraft.level.dimension().identifier().toString().equals(snapshot.dimension())) {
            return false;
        }
        BlockPos origin = origin(snapshot);
        return minecraft.player.position().distanceToSqr(
                origin.getX() + 0.5D,
                origin.getY() + 0.5D,
                origin.getZ() + 0.5D
        ) <= DISPLAY_RADIUS * DISPLAY_RADIUS
                && minecraft.level.isLoaded(origin)
                && minecraft.level.getBlockState(origin).is(Blocks.LODESTONE);
    }

    private static void send(RequestTeleportArrayVisualizationPayload request) {
        if (ClientPlayNetworking.canSend(RequestTeleportArrayVisualizationPayload.TYPE)) {
            ClientPlayNetworking.send(request);
        }
    }

    private static BlockPos origin(TeleportArrayVisualizationPayload payload) {
        return new BlockPos(payload.originX(), payload.originY(), payload.originZ());
    }

    private record Selection(
            String sourceType,
            UUID sourceUnitId,
            boolean showArray,
            boolean showBuildSites) {
        private Selection {
            if (sourceType == null || sourceType.isBlank() || sourceUnitId == null) {
                throw new IllegalArgumentException("Teleport-array visualization source is invalid");
            }
        }

        private boolean enabled() {
            return showArray || showBuildSites;
        }

        private boolean matches(String candidateType, UUID candidateId) {
            return sourceType.equals(candidateType) && sourceUnitId.equals(candidateId);
        }

        private boolean matches(UUID candidateId, boolean candidateArray, boolean candidateBuildSites) {
            return sourceUnitId.equals(candidateId)
                    && showArray == candidateArray
                    && showBuildSites == candidateBuildSites;
        }

        private RequestTeleportArrayVisualizationPayload request() {
            return new RequestTeleportArrayVisualizationPayload(
                    sourceType, sourceUnitId, showArray, showBuildSites);
        }
    }
}

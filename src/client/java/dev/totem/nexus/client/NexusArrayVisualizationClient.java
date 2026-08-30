package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.world.TotemWorldOutlines;
import dev.totem.core.api.v1.client.world.WorldOutlineOcclusion;
import dev.totem.core.api.v1.client.world.WorldOutlineStyle;
import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded, temporary, through-wall teleport-array preview owned by Nexus. */
public final class NexusArrayVisualizationClient {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final double DISPLAY_RADIUS = 16.0D;
    private static final WorldOutlineStyle STRUCTURE_STYLE = new WorldOutlineStyle(
            0xFF4FC3F7, 3.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final WorldOutlineStyle EMITTER_STYLE = new WorldOutlineStyle(
            0xFFFFB74D, 4.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final WorldOutlineStyle ORIGIN_STYLE = new WorldOutlineStyle(
            0xFFB388FF, 5.0F, WorldOutlineOcclusion.THROUGH_WALLS);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static Preview current;
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

    public static void accept(TeleportArrayVisualizationPayload payload) {
        accept(payload, System.nanoTime());
    }

    static void accept(TeleportArrayVisualizationPayload payload, long nowNanos) {
        current = new Preview(payload, nowNanos + payload.lifetimeTicks() * NANOS_PER_TICK);
        lastRenderedSourceId = null;
    }

    public static boolean isActiveFor(UUID sourceUnitId) {
        return isActiveFor(sourceUnitId, System.nanoTime());
    }

    static boolean isActiveFor(UUID sourceUnitId, long nowNanos) {
        Preview preview = current;
        if (preview == null) {
            return false;
        }
        if (nowNanos >= preview.expiresAtNanos()) {
            clear();
            return false;
        }
        return preview.payload().sourceUnitId().equals(sourceUnitId);
    }

    public static void clear() {
        current = null;
        lastRenderedSourceId = null;
    }

    private static void tick(Minecraft minecraft) {
        Preview preview = current;
        if (preview == null) {
            return;
        }
        if (!isValidContext(minecraft, preview, System.nanoTime())) {
            clear();
        }
    }

    private static void render() {
        Preview preview = current;
        if (preview == null) {
            return;
        }
        if (!isValidContext(Minecraft.getInstance(), preview, System.nanoTime())) {
            clear();
            return;
        }

        submit(preview.payload());
        lastRenderedSourceId = preview.payload().sourceUnitId();
    }

    static void submit(TeleportArrayVisualizationPayload payload) {
        BlockPos origin = origin(payload);
        TotemWorldOutlines.block(origin, ORIGIN_STYLE);
        for (TeleportArrayVisualizationPayload.RelativeBlock block : payload.blocks()) {
            TotemWorldOutlines.block(
                    origin.offset(block.dx(), block.dy(), block.dz()),
                    block.expansionEmitter() ? EMITTER_STYLE : STRUCTURE_STYLE
            );
        }
    }

    static boolean hasRendered(UUID sourceUnitId) {
        return sourceUnitId.equals(lastRenderedSourceId);
    }

    private static boolean isValidContext(Minecraft minecraft, Preview preview, long nowNanos) {
        if (nowNanos >= preview.expiresAtNanos()
                || minecraft.level == null
                || minecraft.player == null
                || !minecraft.level.dimension().identifier().toString().equals(preview.payload().dimension())) {
            return false;
        }
        BlockPos origin = origin(preview.payload());
        return minecraft.player.position().distanceToSqr(
                origin.getX() + 0.5D,
                origin.getY() + 0.5D,
                origin.getZ() + 0.5D
        ) <= DISPLAY_RADIUS * DISPLAY_RADIUS
                && minecraft.level.isLoaded(origin)
                && minecraft.level.getBlockState(origin).is(Blocks.LODESTONE);
    }

    private static BlockPos origin(TeleportArrayVisualizationPayload payload) {
        return new BlockPos(payload.originX(), payload.originY(), payload.originZ());
    }

    private record Preview(TeleportArrayVisualizationPayload payload, long expiresAtNanos) {
    }
}

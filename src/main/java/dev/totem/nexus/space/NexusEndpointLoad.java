package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.CompletableFuture;

/** A request-owned loading ticket covering the bounded five-block structure scan. */
final class NexusEndpointLoad implements AutoCloseable {
    private final TicketType TYPE = new TicketType(0, TicketType.FLAG_LOADING);
    private final ServerLevel level;
    private final ChunkPos chunk;
    private final CompletableFuture<?> future;
    private final long deadline;
    private boolean closed;
    private long readyAt = -1;
    NexusEndpointLoad(ServerLevel level, BlockPos anchor, long now) {
        this.level = level;
        chunk = ChunkPos.containing(anchor);
        deadline = now + 200;
        future = level.getChunkSource().addTicketAndLoadWithRadius(TYPE, chunk, 1);
    }
    boolean ready() { return !closed && future.isDone() && !future.isCompletedExceptionally()
            && loaded(level, chunk); }
    boolean failed(long now) {
        if (ready() && readyAt < 0) readyAt = now;
        return future.isCompletedExceptionally() || (!ready() && now >= deadline);
    }
    long landingDeadline(long now) { return now + Math.max(0, deadline - (readyAt < 0 ? now : readyAt)); }
    static boolean structureLoaded(ServerLevel level, BlockPos anchor) {
        return loaded(level, ChunkPos.containing(anchor));
    }
    private static boolean loaded(ServerLevel level, ChunkPos center) {
        for (int x = center.x() - 1; x <= center.x() + 1; x++)
            for (int z = center.z() - 1; z <= center.z() + 1; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        level.getChunkSource().removeTicketWithRadius(TYPE, chunk, 1);
    }
}

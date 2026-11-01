package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Object-scoped native recording hold. Does not freeze every map or authorize import. */
public final class NexusMapTerrainFreeze {
    private static final java.util.concurrent.ConcurrentHashMap<Thread, Object> FULL_INVENTORY =
            new java.util.concurrent.ConcurrentHashMap<>();
    public interface Access {
        boolean totem$swapTerrainFreeze(Object expected, Object replacement);
        boolean totem$hasTerrainFreeze(Object token);
    }
    private NexusMapTerrainFreeze() { }
    /** True only on the server thread that currently owns a full-inventory hold. */
    public static boolean blocksCurrentThreadRecording() {
        return FULL_INVENTORY.containsKey(Thread.currentThread());
    }
    static FullInventoryHold acquireFullInventory(MinecraftServer server) {
        checkThread(server);
        var token = new Object();
        if (FULL_INVENTORY.putIfAbsent(Thread.currentThread(), token) != null)
            throw new IllegalStateException("Native terrain inventory already held");
        return new FullInventoryHold(server, Thread.currentThread(), token);
    }
    static Hold acquire(MinecraftServer server, MapItemSavedData map) {
        checkThread(server);
        var access = (Access) map; var token = new Object();
        if (!access.totem$swapTerrainFreeze(null, token)) throw new IllegalStateException("Native terrain already held");
        return new Hold(server, access, token);
    }
    private static void checkThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Terrain freeze requires server thread");
    }
    static final class Hold implements AutoCloseable {
        private final MinecraftServer server;
        private final Access access;
        private final Object token;
        private boolean closed;
        private Hold(MinecraftServer server, Access access, Object token) {
            this.server = server; this.access = access; this.token = token;
        }
        boolean valid() { checkThread(server); return !closed && access.totem$hasTerrainFreeze(token); }
        @Override public void close() {
            checkThread(server);
            if (closed) return;
            closed = true; access.totem$swapTerrainFreeze(token, null);
        }
    }
    static final class FullInventoryHold implements AutoCloseable {
        private final MinecraftServer server;
        private final Thread thread;
        private final Object token;
        private volatile boolean closed;
        private FullInventoryHold(MinecraftServer server, Thread thread, Object token) {
            this.server = server; this.thread = thread; this.token = token;
        }
        boolean held() { return !closed && FULL_INVENTORY.get(thread) == token; }
        boolean valid() { checkThread(server); return held(); }
        @Override public void close() { checkThread(server); release(); }
        /** Owner establishment may fail on its worker after the server-thread acquisition. */
        void release() {
            if (closed) return;
            closed = true;
            FULL_INVENTORY.remove(thread, token);
        }
    }
}

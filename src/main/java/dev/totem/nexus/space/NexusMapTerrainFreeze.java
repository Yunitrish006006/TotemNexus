package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Object-scoped native recording hold. Does not freeze every map or authorize import. */
public final class NexusMapTerrainFreeze {
    public interface Access {
        boolean totem$swapTerrainFreeze(Object expected, Object replacement);
        boolean totem$hasTerrainFreeze(Object token);
    }
    private NexusMapTerrainFreeze() { }
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
}

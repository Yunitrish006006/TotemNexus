package dev.totem.nexus.api.v1;

import dev.totem.nexus.space.NexusFriendSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.UUID;

/** Stable read-only v1 bridge for modules that honor Nexus mutual friendships. */
public final class NexusFriendshipApi {
    private NexusFriendshipApi() {
    }

    public static boolean areMutualFriends(MinecraftServer server, UUID first, UUID second) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return server.overworld().getDataStorage().computeIfAbsent(NexusFriendSavedData.TYPE)
                .areFriends(first, second);
    }
}

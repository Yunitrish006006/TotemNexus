package dev.totem.nexus.api.v1;

import dev.totem.core.api.v1.social.TotemFriendshipApi;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * @deprecated Totem-wide friendships are owned by TotemCore 0.7+.
 * Use {@link TotemFriendshipApi} directly.
 */
@Deprecated(forRemoval = true)
public final class NexusFriendshipApi {
    private NexusFriendshipApi() { }

    public static boolean areMutualFriends(MinecraftServer server, UUID first, UUID second) {
        return TotemFriendshipApi.areMutualFriends(server, first, second);
    }
}

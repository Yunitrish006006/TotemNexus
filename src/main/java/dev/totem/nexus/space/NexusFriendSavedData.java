package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.totem.core.api.v1.social.TotemFriendshipApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deprecated Nexus compatibility façade over the Core-owned friendship service.
 *
 * <p>This class intentionally persists no friendship data. Existing Nexus map
 * and teleport code still accepts this type for one compatibility cycle while
 * every query/mutation delegates to {@link TotemFriendshipApi}. The historical
 * {@code deadrecall:space_friends} SavedData is now opened exclusively by
 * TotemCore.</p>
 */
@Deprecated(forRemoval = true)
public final class NexusFriendSavedData extends SavedData {
    private static final int ADAPTER_VERSION = 1;
    private static volatile MinecraftServer activeServer;

    public static final Codec<NexusFriendSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("adapter_version", ADAPTER_VERSION).forGetter(ignored -> ADAPTER_VERSION)
    ).apply(instance, ignored -> new NexusFriendSavedData()));

    public static final SavedDataType<NexusFriendSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem-nexus", "friendship_view"),
            NexusFriendSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    public NexusFriendSavedData() { }

    public static void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> activeServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (activeServer == server) activeServer = null;
        });
    }

    public boolean areFriends(UUID first, UUID second) {
        return TotemFriendshipApi.areFriends(server(), first, second);
    }

    public List<UUID> friendsOf(UUID playerId) {
        return TotemFriendshipApi.friendsOf(server(), playerId);
    }

    public List<UUID> outgoingInviteTargets(UUID playerId) {
        return TotemFriendshipApi.outgoingInvites(server(), playerId);
    }

    public List<UUID> incomingInviteSources(UUID playerId) {
        return TotemFriendshipApi.incomingInvites(server(), playerId);
    }

    public FriendActionResult inviteOrAccept(UUID from, UUID to) {
        return FriendActionResult.valueOf(TotemFriendshipApi.inviteOrAccept(server(), from, to).name());
    }

    public boolean removeRelationship(UUID first, UUID second) {
        return TotemFriendshipApi.removeRelationship(server(), first, second);
    }

    private static MinecraftServer server() {
        return Objects.requireNonNull(activeServer,
                "TotemNexus friendship façade used before MinecraftServer lifecycle binding");
    }

    /** Deprecated compatibility enum; new integrations use Core's FriendActionResult. */
    @Deprecated(forRemoval = true)
    public enum FriendActionResult {
        INVITED,
        ACCEPTED,
        PENDING,
        ALREADY_FRIENDS,
        INVALID
    }
}

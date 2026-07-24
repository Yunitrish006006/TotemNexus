package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Compatibility owner for the persisted {@code deadrecall:space_friends} schema. */
public final class NexusFriendSavedData extends SavedData {
    public static final int DATA_VERSION = 1;
    private static final Codec<Friendship> FRIENDSHIP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("first").forGetter(Friendship::first),
            UUIDUtil.CODEC.fieldOf("second").forGetter(Friendship::second)
    ).apply(instance, Friendship::new));
    private static final Codec<PendingInvite> PENDING_INVITE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("from").forGetter(PendingInvite::from),
            UUIDUtil.CODEC.fieldOf("to").forGetter(PendingInvite::to)
    ).apply(instance, PendingInvite::new));
    public static final Codec<NexusFriendSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(NexusFriendSavedData::dataVersion),
            FRIENDSHIP_CODEC.listOf().optionalFieldOf("friendships", List.of()).forGetter(NexusFriendSavedData::friendshipList),
            PENDING_INVITE_CODEC.listOf().optionalFieldOf("pending_invites", List.of()).forGetter(NexusFriendSavedData::pendingInviteList)
    ).apply(instance, NexusFriendSavedData::new));
    public static final SavedDataType<NexusFriendSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_friends"), NexusFriendSavedData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int dataVersion;
    private final Set<Friendship> friendships = new HashSet<>();
    private final Set<PendingInvite> pendingInvites = new HashSet<>();

    public NexusFriendSavedData() { this(DATA_VERSION, List.of(), List.of()); }

    private NexusFriendSavedData(int dataVersion, List<Friendship> friendships, List<PendingInvite> pendingInvites) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        this.friendships.addAll(friendships);
        this.pendingInvites.addAll(pendingInvites);
    }

    public boolean areFriends(UUID first, UUID second) {
        return first != null && second != null && !first.equals(second) && friendships.contains(new Friendship(first, second));
    }

    public List<UUID> friendsOf(UUID playerId) {
        if (playerId == null) return List.of();
        List<UUID> result = new ArrayList<>();
        for (Friendship friendship : friendships) {
            if (friendship.first().equals(playerId)) result.add(friendship.second());
            else if (friendship.second().equals(playerId)) result.add(friendship.first());
        }
        return result;
    }
    public List<UUID> outgoingInviteTargets(UUID playerId) {
        if (playerId == null) return List.of();
        return pendingInvites.stream().filter(invite -> invite.from().equals(playerId)).map(PendingInvite::to).toList();
    }
    public List<UUID> incomingInviteSources(UUID playerId) {
        if (playerId == null) return List.of();
        return pendingInvites.stream().filter(invite -> invite.to().equals(playerId)).map(PendingInvite::from).toList();
    }

    public FriendActionResult inviteOrAccept(UUID from, UUID to) {
        if (from == null || to == null || from.equals(to)) return FriendActionResult.INVALID;
        if (areFriends(from, to)) return FriendActionResult.ALREADY_FRIENDS;
        if (pendingInvites.remove(new PendingInvite(to, from))) {
            pendingInvites.remove(new PendingInvite(from, to));
            friendships.add(new Friendship(from, to));
            setDirty();
            return FriendActionResult.ACCEPTED;
        }
        if (pendingInvites.add(new PendingInvite(from, to))) {
            setDirty();
            return FriendActionResult.INVITED;
        }
        return FriendActionResult.PENDING;
    }

    public boolean removeRelationship(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        boolean removed = friendships.remove(new Friendship(first, second));
        removed |= pendingInvites.remove(new PendingInvite(first, second));
        removed |= pendingInvites.remove(new PendingInvite(second, first));
        if (removed) setDirty();
        return removed;
    }

    private int dataVersion() { return dataVersion; }
    private List<Friendship> friendshipList() { return List.copyOf(friendships); }
    private List<PendingInvite> pendingInviteList() { return List.copyOf(pendingInvites); }

    public enum FriendActionResult { INVITED, ACCEPTED, PENDING, ALREADY_FRIENDS, INVALID }

    private record Friendship(UUID first, UUID second) {
        private Friendship {
            if (first.compareTo(second) > 0) { UUID swap = first; first = second; second = swap; }
        }
    }
    private record PendingInvite(UUID from, UUID to) { }
}

package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DeadRecallFriendSavedData extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<Friendship> FRIENDSHIP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("first").forGetter(Friendship::first),
            UUIDUtil.CODEC.fieldOf("second").forGetter(Friendship::second)
    ).apply(instance, Friendship::new));

    private static final Codec<PendingFriendInvite> PENDING_INVITE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("from").forGetter(PendingFriendInvite::from),
            UUIDUtil.CODEC.fieldOf("to").forGetter(PendingFriendInvite::to)
    ).apply(instance, PendingFriendInvite::new));

    public static final Codec<DeadRecallFriendSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(DeadRecallFriendSavedData::dataVersion),
            FRIENDSHIP_CODEC.listOf().optionalFieldOf("friendships", List.of()).forGetter(DeadRecallFriendSavedData::friendshipList),
            PENDING_INVITE_CODEC.listOf().optionalFieldOf("pending_invites", List.of()).forGetter(DeadRecallFriendSavedData::pendingInviteList)
    ).apply(instance, DeadRecallFriendSavedData::new));

    public static final SavedDataType<DeadRecallFriendSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_friends"),
            DeadRecallFriendSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Set<Friendship> friendships = new HashSet<>();
    private final Set<PendingFriendInvite> pendingInvites = new HashSet<>();

    public DeadRecallFriendSavedData() {
        this(DATA_VERSION, List.of(), List.of());
    }

    private DeadRecallFriendSavedData(int dataVersion, List<Friendship> friendships, List<PendingFriendInvite> pendingInvites) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        this.friendships.addAll(friendships);
        this.pendingInvites.addAll(pendingInvites);
    }

    public boolean areFriends(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        return this.friendships.contains(new Friendship(first, second));
    }

    public List<UUID> friendsOf(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        List<UUID> friends = new java.util.ArrayList<>();
        for (Friendship friendship : this.friendships) {
            if (friendship.first().equals(playerId)) {
                friends.add(friendship.second());
            } else if (friendship.second().equals(playerId)) {
                friends.add(friendship.first());
            }
        }
        return friends;
    }

    public List<UUID> outgoingInviteTargets(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        List<UUID> targets = new java.util.ArrayList<>();
        for (PendingFriendInvite invite : this.pendingInvites) {
            if (invite.from().equals(playerId)) {
                targets.add(invite.to());
            }
        }
        return targets;
    }

    public List<UUID> incomingInviteSources(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        List<UUID> sources = new java.util.ArrayList<>();
        for (PendingFriendInvite invite : this.pendingInvites) {
            if (invite.to().equals(playerId)) {
                sources.add(invite.from());
            }
        }
        return sources;
    }

    public FriendActionResult inviteOrAccept(UUID from, UUID to) {
        if (from == null || to == null || from.equals(to)) {
            return FriendActionResult.INVALID;
        }
        if (areFriends(from, to)) {
            return FriendActionResult.ALREADY_FRIENDS;
        }

        PendingFriendInvite reverse = new PendingFriendInvite(to, from);
        if (this.pendingInvites.remove(reverse)) {
            this.pendingInvites.remove(new PendingFriendInvite(from, to));
            this.friendships.add(new Friendship(from, to));
            setDirty();
            return FriendActionResult.ACCEPTED;
        }

        PendingFriendInvite direct = new PendingFriendInvite(from, to);
        if (this.pendingInvites.add(direct)) {
            setDirty();
            return FriendActionResult.INVITED;
        }
        return FriendActionResult.PENDING;
    }

    public boolean removeRelationship(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }

        boolean removed = this.friendships.remove(new Friendship(first, second));
        removed |= this.pendingInvites.remove(new PendingFriendInvite(first, second));
        removed |= this.pendingInvites.remove(new PendingFriendInvite(second, first));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<Friendship> friendshipList() {
        return List.copyOf(this.friendships);
    }

    private List<PendingFriendInvite> pendingInviteList() {
        return List.copyOf(this.pendingInvites);
    }

    public enum FriendActionResult {
        INVITED,
        ACCEPTED,
        PENDING,
        ALREADY_FRIENDS,
        INVALID
    }

    private record Friendship(UUID first, UUID second) {
        private Friendship {
            if (first.compareTo(second) > 0) {
                UUID swap = first;
                first = second;
                second = swap;
            }
        }
    }

    private record PendingFriendInvite(UUID from, UUID to) {
    }
}

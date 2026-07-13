package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeadRecallSpaceDiscoverySavedData extends SavedData {
    public static final int DATA_VERSION = 2;

    private static final Codec<PlayerDiscovery> PLAYER_DISCOVERY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscovery::player),
            UUIDUtil.CODEC_SET.optionalFieldOf("units", Set.of()).forGetter(PlayerDiscovery::units),
            UUIDUtil.CODEC_SET.optionalFieldOf("favorites", Set.of()).forGetter(PlayerDiscovery::favorites)
    ).apply(instance, PlayerDiscovery::new));

    public static final Codec<DeadRecallSpaceDiscoverySavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(DeadRecallSpaceDiscoverySavedData::dataVersion),
            PLAYER_DISCOVERY_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(DeadRecallSpaceDiscoverySavedData::playerList)
    ).apply(instance, DeadRecallSpaceDiscoverySavedData::new));

    public static final SavedDataType<DeadRecallSpaceDiscoverySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_discovery"),
            DeadRecallSpaceDiscoverySavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<UUID, Set<UUID>> discoveredByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> favoritesByPlayer = new HashMap<>();

    public DeadRecallSpaceDiscoverySavedData() {
        this(DATA_VERSION, List.of());
    }

    private DeadRecallSpaceDiscoverySavedData(int dataVersion, List<PlayerDiscovery> players) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (PlayerDiscovery player : players) {
            this.discoveredByPlayer.put(player.player(), new HashSet<>(player.units()));
            this.favoritesByPlayer.put(player.player(), new HashSet<>(player.favorites()));
        }
    }

    public boolean markDiscovered(UUID playerId, UUID unitId) {
        Set<UUID> units = this.discoveredByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>());
        boolean changed = units.add(unitId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean hasDiscovered(UUID playerId, UUID unitId) {
        return this.discoveredByPlayer.getOrDefault(playerId, Set.of()).contains(unitId);
    }

    public boolean isFavorite(UUID playerId, UUID unitId) {
        return this.favoritesByPlayer.getOrDefault(playerId, Set.of()).contains(unitId);
    }

    public boolean setFavorite(UUID playerId, UUID unitId, boolean favorite) {
        if (favorite && !hasDiscovered(playerId, unitId)) {
            return false;
        }

        boolean changed;
        if (favorite) {
            Set<UUID> favorites = this.favoritesByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>());
            changed = favorites.add(unitId);
        } else {
            Set<UUID> favorites = this.favoritesByPlayer.get(playerId);
            if (favorites == null) {
                return false;
            }
            changed = favorites.remove(unitId);
            if (favorites.isEmpty()) {
                this.favoritesByPlayer.remove(playerId);
            }
        }

        if (changed) {
            setDirty();
        }
        return changed;
    }

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<PlayerDiscovery> playerList() {
        Set<UUID> playerIds = new HashSet<>();
        playerIds.addAll(this.discoveredByPlayer.keySet());
        playerIds.addAll(this.favoritesByPlayer.keySet());

        List<PlayerDiscovery> players = new ArrayList<>(playerIds.size());
        for (UUID playerId : playerIds) {
            players.add(new PlayerDiscovery(
                    playerId,
                    Set.copyOf(this.discoveredByPlayer.getOrDefault(playerId, Set.of())),
                    Set.copyOf(this.favoritesByPlayer.getOrDefault(playerId, Set.of()))
            ));
        }
        return players;
    }

    private record PlayerDiscovery(UUID player, Set<UUID> units, Set<UUID> favorites) {
        private PlayerDiscovery {
            units = Set.copyOf(units == null ? Set.of() : units);
            favorites = Set.copyOf(favorites == null ? Set.of() : favorites);
        }
    }
}

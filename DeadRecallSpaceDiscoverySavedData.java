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
    public static final int DATA_VERSION = 1;

    private static final Codec<PlayerDiscovery> PLAYER_DISCOVERY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscovery::player),
            UUIDUtil.CODEC_SET.optionalFieldOf("units", Set.of()).forGetter(PlayerDiscovery::units)
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

    public DeadRecallSpaceDiscoverySavedData() {
        this(DATA_VERSION, List.of());
    }

    private DeadRecallSpaceDiscoverySavedData(int dataVersion, List<PlayerDiscovery> players) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (PlayerDiscovery player : players) {
            this.discoveredByPlayer.put(player.player(), new HashSet<>(player.units()));
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

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<PlayerDiscovery> playerList() {
        List<PlayerDiscovery> players = new ArrayList<>(this.discoveredByPlayer.size());
        for (Map.Entry<UUID, Set<UUID>> entry : this.discoveredByPlayer.entrySet()) {
            players.add(new PlayerDiscovery(entry.getKey(), Set.copyOf(entry.getValue())));
        }
        return players;
    }

    private record PlayerDiscovery(UUID player, Set<UUID> units) {
        private PlayerDiscovery {
            units = Set.copyOf(units);
        }
    }
}

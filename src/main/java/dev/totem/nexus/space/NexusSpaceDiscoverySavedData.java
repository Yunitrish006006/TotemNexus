package dev.totem.nexus.space;

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

/** Compatibility owner for the persisted {@code deadrecall:space_discovery} schema. */
public final class NexusSpaceDiscoverySavedData extends SavedData {
    public static final int DATA_VERSION = 2;
    private static final Codec<PlayerDiscovery> PLAYER_DISCOVERY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscovery::player),
            UUIDUtil.CODEC_SET.optionalFieldOf("units", Set.of()).forGetter(PlayerDiscovery::units),
            UUIDUtil.CODEC_SET.optionalFieldOf("favorites", Set.of()).forGetter(PlayerDiscovery::favorites)
    ).apply(instance, PlayerDiscovery::new));
    public static final Codec<NexusSpaceDiscoverySavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(NexusSpaceDiscoverySavedData::dataVersion),
            PLAYER_DISCOVERY_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(NexusSpaceDiscoverySavedData::playerList)
    ).apply(instance, NexusSpaceDiscoverySavedData::new));
    public static final SavedDataType<NexusSpaceDiscoverySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_discovery"), NexusSpaceDiscoverySavedData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int dataVersion;
    private final Map<UUID, Set<UUID>> discoveredByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> favoritesByPlayer = new HashMap<>();

    public NexusSpaceDiscoverySavedData() { this(DATA_VERSION, List.of()); }

    private NexusSpaceDiscoverySavedData(int dataVersion, List<PlayerDiscovery> players) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (PlayerDiscovery player : players) {
            discoveredByPlayer.put(player.player(), new HashSet<>(player.units()));
            favoritesByPlayer.put(player.player(), new HashSet<>(player.favorites()));
        }
    }

    public boolean markDiscovered(UUID playerId, UUID unitId) {
        boolean changed = discoveredByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(unitId);
        if (changed) setDirty();
        return changed;
    }
    public boolean hasDiscovered(UUID playerId, UUID unitId) {
        return discoveredByPlayer.getOrDefault(playerId, Set.of()).contains(unitId);
    }
    public boolean isFavorite(UUID playerId, UUID unitId) {
        return favoritesByPlayer.getOrDefault(playerId, Set.of()).contains(unitId);
    }
    public boolean setFavorite(UUID playerId, UUID unitId, boolean favorite) {
        if (favorite && !hasDiscovered(playerId, unitId)) return false;
        boolean changed;
        if (favorite) changed = favoritesByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(unitId);
        else {
            Set<UUID> favorites = favoritesByPlayer.get(playerId);
            if (favorites == null) return false;
            changed = favorites.remove(unitId);
            if (favorites.isEmpty()) favoritesByPlayer.remove(playerId);
        }
        if (changed) setDirty();
        return changed;
    }
    public boolean removeDiscovered(UUID playerId, UUID unitId) {
        boolean changed = remove(discoveredByPlayer, playerId, unitId);
        changed |= remove(favoritesByPlayer, playerId, unitId);
        if (changed) setDirty();
        return changed;
    }
    /** Removes a deleted unit from every player's discovery and favorite lists. */
    public boolean removeUnitReferences(UUID unitId) {
        boolean changed = removeFromAll(discoveredByPlayer, unitId);
        changed |= removeFromAll(favoritesByPlayer, unitId);
        if (changed) setDirty();
        return changed;
    }
    private static boolean remove(Map<UUID, Set<UUID>> values, UUID playerId, UUID unitId) {
        Set<UUID> units = values.get(playerId);
        if (units == null || !units.remove(unitId)) return false;
        if (units.isEmpty()) values.remove(playerId);
        return true;
    }
    private static boolean removeFromAll(Map<UUID, Set<UUID>> values, UUID unitId) {
        boolean changed = false;
        var iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().remove(unitId)) changed = true;
            if (entry.getValue().isEmpty()) iterator.remove();
        }
        return changed;
    }
    private int dataVersion() { return dataVersion; }
    private List<PlayerDiscovery> playerList() {
        Set<UUID> players = new HashSet<>(); players.addAll(discoveredByPlayer.keySet()); players.addAll(favoritesByPlayer.keySet());
        List<PlayerDiscovery> result = new ArrayList<>(players.size());
        for (UUID player : players) result.add(new PlayerDiscovery(player,
                Set.copyOf(discoveredByPlayer.getOrDefault(player, Set.of())), Set.copyOf(favoritesByPlayer.getOrDefault(player, Set.of()))));
        return result;
    }
    private record PlayerDiscovery(UUID player, Set<UUID> units, Set<UUID> favorites) {
        private PlayerDiscovery { units = Set.copyOf(units == null ? Set.of() : units); favorites = Set.copyOf(favorites == null ? Set.of() : favorites); }
    }
}

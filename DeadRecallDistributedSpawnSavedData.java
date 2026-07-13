package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DeadRecallDistributedSpawnSavedData extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<PlayerSpawn> PLAYER_SPAWN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerSpawn::player),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PlayerSpawn::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(PlayerSpawn::pos),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(PlayerSpawn::yaw),
            Codec.LONG.optionalFieldOf("created_game_time", 0L).forGetter(PlayerSpawn::createdGameTime)
    ).apply(instance, PlayerSpawn::new));

    public static final Codec<DeadRecallDistributedSpawnSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(DeadRecallDistributedSpawnSavedData::dataVersion),
            PLAYER_SPAWN_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(DeadRecallDistributedSpawnSavedData::playerList)
    ).apply(instance, DeadRecallDistributedSpawnSavedData::new));

    public static final SavedDataType<DeadRecallDistributedSpawnSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "distributed_spawns"),
            DeadRecallDistributedSpawnSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<UUID, PlayerSpawn> spawnsByPlayer = new HashMap<>();

    public DeadRecallDistributedSpawnSavedData() {
        this(DATA_VERSION, List.of());
    }

    private DeadRecallDistributedSpawnSavedData(int dataVersion, List<PlayerSpawn> players) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (PlayerSpawn spawn : players) {
            this.spawnsByPlayer.put(spawn.player(), spawn);
        }
    }

    public Optional<PlayerSpawn> get(UUID playerId) {
        return Optional.ofNullable(this.spawnsByPlayer.get(playerId));
    }

    public PlayerSpawn put(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, float yaw, long gameTime) {
        PlayerSpawn spawn = new PlayerSpawn(playerId, dimension, pos.immutable(), yaw, gameTime);
        this.spawnsByPlayer.put(playerId, spawn);
        setDirty();
        return spawn;
    }

    public void remove(UUID playerId) {
        if (this.spawnsByPlayer.remove(playerId) != null) {
            setDirty();
        }
    }

    public List<PlayerSpawn> spawns() {
        return List.copyOf(this.spawnsByPlayer.values());
    }

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<PlayerSpawn> playerList() {
        return spawns();
    }

    public record PlayerSpawn(
            UUID player,
            ResourceKey<Level> dimension,
            BlockPos pos,
            float yaw,
            long createdGameTime) {

        public PlayerSpawn {
            pos = pos.immutable();
        }
    }
}

package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

public record SpaceUnitRecord(
        UUID id,
        SpaceUnitType type,
        ResourceKey<Level> dimension,
        BlockPos pos,
        UUID owner,
        String name,
        SpaceUnitVisibility visibility,
        SpaceUnitStatus status,
        Set<UUID> administrators,
        Set<UUID> allowedPlayers,
        SpaceStructureSnapshot structure,
        long createdGameTime,
        long updatedGameTime) {

    private static final Codec<SpaceUnitType> TYPE_CODEC =
            Codec.STRING.xmap(SpaceUnitType::fromId, SpaceUnitType::id);
    private static final Codec<SpaceUnitVisibility> VISIBILITY_CODEC =
            Codec.STRING.xmap(SpaceUnitVisibility::fromId, SpaceUnitVisibility::id);
    private static final Codec<SpaceUnitStatus> STATUS_CODEC =
            Codec.STRING.xmap(SpaceUnitStatus::fromId, SpaceUnitStatus::id);

    public static final Codec<SpaceUnitRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SpaceUnitRecord::id),
            TYPE_CODEC.optionalFieldOf("type", SpaceUnitType.LODESTONE).forGetter(SpaceUnitRecord::type),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SpaceUnitRecord::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(SpaceUnitRecord::pos),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(SpaceUnitRecord::owner),
            Codec.STRING.optionalFieldOf("name", "").forGetter(SpaceUnitRecord::name),
            VISIBILITY_CODEC.optionalFieldOf("visibility", SpaceUnitVisibility.PRIVATE).forGetter(SpaceUnitRecord::visibility),
            STATUS_CODEC.optionalFieldOf("status", SpaceUnitStatus.ACTIVE).forGetter(SpaceUnitRecord::status),
            UUIDUtil.CODEC_SET.optionalFieldOf("administrators", Set.of()).forGetter(SpaceUnitRecord::administrators),
            UUIDUtil.CODEC_SET.optionalFieldOf("allowed_players", Set.of()).forGetter(SpaceUnitRecord::allowedPlayers),
            SpaceStructureSnapshot.CODEC.optionalFieldOf("structure", SpaceStructureSnapshot.EMPTY).forGetter(SpaceUnitRecord::structure),
            Codec.LONG.optionalFieldOf("created_game_time", 0L).forGetter(SpaceUnitRecord::createdGameTime),
            Codec.LONG.optionalFieldOf("updated_game_time", 0L).forGetter(SpaceUnitRecord::updatedGameTime)
    ).apply(instance, SpaceUnitRecord::new));

    public SpaceUnitRecord {
        name = name == null || name.isBlank() ? defaultName(type, pos) : name;
        administrators = Set.copyOf(administrators);
        allowedPlayers = Set.copyOf(allowedPlayers);
        structure = structure == null ? SpaceStructureSnapshot.EMPTY : structure;
    }

    public boolean isLodestoneAnchor() {
        return this.type == SpaceUnitType.LODESTONE;
    }

    public boolean canView(UUID playerId) {
        if (playerId == null || this.visibility == SpaceUnitVisibility.HIDDEN) {
            return false;
        }

        return this.owner.equals(playerId)
                || this.administrators.contains(playerId)
                || this.allowedPlayers.contains(playerId)
                || this.visibility == SpaceUnitVisibility.PUBLIC;
    }

    public SpaceUnitRecord withStructure(SpaceStructureSnapshot nextStructure, long gameTime) {
        return new SpaceUnitRecord(
                this.id,
                this.type,
                this.dimension,
                this.pos,
                this.owner,
                this.name,
                this.visibility,
                this.status,
                this.administrators,
                this.allowedPlayers,
                nextStructure,
                this.createdGameTime,
                gameTime
        );
    }

    private static String defaultName(SpaceUnitType type, BlockPos pos) {
        return switch (type) {
            case DEATH -> "Death Echo " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            case PLAYER -> "Player Anchor";
            case TEMPORARY -> "Temporary Anchor";
            case SYSTEM -> "System Anchor";
            case LODESTONE -> "Lodestone " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        };
    }
}

package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
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
        return canView(playerId, false);
    }

    public boolean canView(UUID playerId, boolean friendsWithOwner) {
        if (playerId == null || this.visibility == SpaceUnitVisibility.HIDDEN) {
            return false;
        }

        return this.owner.equals(playerId)
                || this.administrators.contains(playerId)
                || this.allowedPlayers.contains(playerId)
                || this.visibility == SpaceUnitVisibility.PUBLIC
                || (this.visibility == SpaceUnitVisibility.FRIENDS && friendsWithOwner);
    }

    public boolean canManage(UUID playerId) {
        return playerId != null && (this.owner.equals(playerId) || this.administrators.contains(playerId));
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

    public SpaceUnitRecord withStatus(SpaceUnitStatus nextStatus, long gameTime) {
        return new SpaceUnitRecord(
                this.id,
                this.type,
                this.dimension,
                this.pos,
                this.owner,
                this.name,
                this.visibility,
                nextStatus,
                this.administrators,
                this.allowedPlayers,
                this.structure,
                this.createdGameTime,
                gameTime
        );
    }

    public SpaceUnitRecord withVisibility(SpaceUnitVisibility nextVisibility, long gameTime) {
        return new SpaceUnitRecord(
                this.id,
                this.type,
                this.dimension,
                this.pos,
                this.owner,
                this.name,
                nextVisibility,
                this.status,
                this.administrators,
                this.allowedPlayers,
                this.structure,
                this.createdGameTime,
                gameTime
        );
    }

    public SpaceUnitRecord withName(String nextName, long gameTime) {
        return new SpaceUnitRecord(
                this.id,
                this.type,
                this.dimension,
                this.pos,
                this.owner,
                nextName,
                this.visibility,
                this.status,
                this.administrators,
                this.allowedPlayers,
                this.structure,
                this.createdGameTime,
                gameTime
        );
    }

    public SpaceUnitRecord withAdministrator(UUID playerId, boolean enabled, long gameTime) {
        Set<UUID> nextAdministrators = new HashSet<>(this.administrators);
        if (enabled) {
            nextAdministrators.add(playerId);
        } else {
            nextAdministrators.remove(playerId);
        }
        return new SpaceUnitRecord(
                this.id,
                this.type,
                this.dimension,
                this.pos,
                this.owner,
                this.name,
                this.visibility,
                this.status,
                nextAdministrators,
                this.allowedPlayers,
                this.structure,
                this.createdGameTime,
                gameTime
        );
    }

    public SpaceUnitRecord withAllowedPlayer(UUID playerId, boolean enabled, long gameTime) {
        Set<UUID> nextAllowedPlayers = new HashSet<>(this.allowedPlayers);
        if (enabled) {
            nextAllowedPlayers.add(playerId);
        } else {
            nextAllowedPlayers.remove(playerId);
        }
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
                nextAllowedPlayers,
                this.structure,
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

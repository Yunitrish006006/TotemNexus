package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Immutable record preserving the legacy Space Unit codec field names. */
public record NexusSpaceUnitRecord(UUID id, SpaceUnitType type, ResourceKey<Level> dimension, BlockPos pos, UUID owner,
                                   String name, SpaceUnitVisibility visibility, SpaceUnitStatus status,
                                   Set<UUID> administrators, Set<UUID> allowedPlayers, SpaceStructureSnapshot structure,
                                   long createdGameTime, long updatedGameTime) {
    private static final Codec<SpaceUnitType> TYPE_CODEC = Codec.STRING.xmap(SpaceUnitType::fromId, SpaceUnitType::id);
    private static final Codec<SpaceUnitVisibility> VISIBILITY_CODEC = Codec.STRING.xmap(SpaceUnitVisibility::fromId, SpaceUnitVisibility::id);
    private static final Codec<SpaceUnitStatus> STATUS_CODEC = Codec.STRING.xmap(SpaceUnitStatus::fromId, SpaceUnitStatus::id);
    public static final Codec<NexusSpaceUnitRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(NexusSpaceUnitRecord::id),
            TYPE_CODEC.optionalFieldOf("type", SpaceUnitType.LODESTONE).forGetter(NexusSpaceUnitRecord::type),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(NexusSpaceUnitRecord::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(NexusSpaceUnitRecord::pos),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(NexusSpaceUnitRecord::owner),
            Codec.STRING.optionalFieldOf("name", "").forGetter(NexusSpaceUnitRecord::name),
            VISIBILITY_CODEC.optionalFieldOf("visibility", SpaceUnitVisibility.PRIVATE).forGetter(NexusSpaceUnitRecord::visibility),
            STATUS_CODEC.optionalFieldOf("status", SpaceUnitStatus.ACTIVE).forGetter(NexusSpaceUnitRecord::status),
            UUIDUtil.CODEC_SET.optionalFieldOf("administrators", Set.of()).forGetter(NexusSpaceUnitRecord::administrators),
            UUIDUtil.CODEC_SET.optionalFieldOf("allowed_players", Set.of()).forGetter(NexusSpaceUnitRecord::allowedPlayers),
            SpaceStructureSnapshot.CODEC.optionalFieldOf("structure", SpaceStructureSnapshot.EMPTY).forGetter(NexusSpaceUnitRecord::structure),
            Codec.LONG.optionalFieldOf("created_game_time", 0L).forGetter(NexusSpaceUnitRecord::createdGameTime),
            Codec.LONG.optionalFieldOf("updated_game_time", 0L).forGetter(NexusSpaceUnitRecord::updatedGameTime)
    ).apply(instance, NexusSpaceUnitRecord::new));
    public NexusSpaceUnitRecord {
        pos = pos.immutable(); name = name == null || name.isBlank() ? defaultName(type, pos) : name;
        administrators = Set.copyOf(administrators); allowedPlayers = Set.copyOf(allowedPlayers);
        structure = structure == null ? SpaceStructureSnapshot.EMPTY : structure;
    }
    public boolean isLodestoneAnchor() { return type == SpaceUnitType.LODESTONE; }
    public boolean canView(UUID player, boolean friendsWithOwner) {
        return player != null && visibility != SpaceUnitVisibility.HIDDEN && (owner.equals(player)
                || administrators.contains(player) || allowedPlayers.contains(player) || visibility == SpaceUnitVisibility.PUBLIC
                || (visibility == SpaceUnitVisibility.FRIENDS && friendsWithOwner));
    }
    public boolean canManage(UUID player) { return player != null && (owner.equals(player) || administrators.contains(player)); }
    public NexusSpaceUnitRecord withVisibility(SpaceUnitVisibility next, long time) {
        return new NexusSpaceUnitRecord(id, type, dimension, pos, owner, name, next, status, administrators, allowedPlayers, structure, createdGameTime, time);
    }
    public NexusSpaceUnitRecord withName(String next, long time) {
        return new NexusSpaceUnitRecord(id, type, dimension, pos, owner, next, visibility, status, administrators, allowedPlayers, structure, createdGameTime, time);
    }
    public NexusSpaceUnitRecord withAllowedPlayer(UUID player, boolean enabled, long time) {
        Set<UUID> next = new HashSet<>(allowedPlayers); if (enabled) next.add(player); else next.remove(player);
        return new NexusSpaceUnitRecord(id, type, dimension, pos, owner, name, visibility, status, administrators, next, structure, createdGameTime, time);
    }
    public NexusSpaceUnitRecord withAdministrator(UUID player, boolean enabled, long time) {
        Set<UUID> next = new HashSet<>(administrators); if (enabled) next.add(player); else next.remove(player);
        return new NexusSpaceUnitRecord(id, type, dimension, pos, owner, name, visibility, status, next, allowedPlayers, structure, createdGameTime, time);
    }
    private static String defaultName(SpaceUnitType type, BlockPos pos) { return switch (type) {
        case DEATH -> "Death Echo " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        case PLAYER -> "Player Anchor"; case TEMPORARY -> "Temporary Anchor"; case SYSTEM -> "System Anchor";
        case LODESTONE -> "Lodestone " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }; }
}

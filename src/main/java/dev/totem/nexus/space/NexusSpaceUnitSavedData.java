package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/** Read/write schema owner for {@code deadrecall:space_units}; teleport mutations remain bundle-owned for now. */
public final class NexusSpaceUnitSavedData extends SavedData {
    public static final int DATA_VERSION = 1;
    public static final Codec<NexusSpaceUnitSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(NexusSpaceUnitSavedData::dataVersion),
            NexusSpaceUnitRecord.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(NexusSpaceUnitSavedData::unitList)
    ).apply(instance, NexusSpaceUnitSavedData::new));
    public static final SavedDataType<NexusSpaceUnitSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_units"), NexusSpaceUnitSavedData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final int dataVersion;
    private final Map<UUID, NexusSpaceUnitRecord> unitsById = new HashMap<>();
    private final Map<GlobalPos, UUID> lodestoneUnitsByPosition = new HashMap<>();
    public NexusSpaceUnitSavedData() { this(DATA_VERSION, List.of()); }
    private NexusSpaceUnitSavedData(int dataVersion, List<NexusSpaceUnitRecord> units) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION); for (NexusSpaceUnitRecord unit : units) putInternal(unit);
    }
    public Optional<NexusSpaceUnitRecord> get(UUID id) { return Optional.ofNullable(unitsById.get(id)); }
    public void put(NexusSpaceUnitRecord unit) { putInternal(unit); setDirty(); }
    public NexusSpaceUnitRecord createDeathUnit(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        long time = level.getGameTime();
        NexusSpaceUnitRecord unit = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.DEATH, level.dimension(),
                pos.immutable(), owner.getUUID(), "", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, time, time);
        put(unit);
        return unit;
    }
    public boolean disableDeathUnit(UUID ownerId, UUID unitId, long gameTime) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (unit == null || unit.type() != SpaceUnitType.DEATH || unit.status() != SpaceUnitStatus.ACTIVE || !unit.owner().equals(ownerId)) return false;
        put(new NexusSpaceUnitRecord(unit.id(), unit.type(), unit.dimension(), unit.pos(), unit.owner(), unit.name(),
                unit.visibility(), SpaceUnitStatus.DISABLED, unit.administrators(), unit.allowedPlayers(), unit.structure(),
                unit.createdGameTime(), gameTime));
        return true;
    }
    /** Returns every death node, including disabled nodes needed by administrative recovery. */
    public List<NexusSpaceUnitRecord> deathNodes() {
        return unitsById.values().stream().filter(unit -> unit.type() == SpaceUnitType.DEATH).toList();
    }
    /** Permanently removes a non-active death node after administrative validation. */
    public boolean purgeInactiveDeathUnit(UUID unitId) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (unit == null || unit.type() != SpaceUnitType.DEATH || unit.status() == SpaceUnitStatus.ACTIVE) return false;
        unitsById.remove(unitId);
        setDirty();
        return true;
    }
    public Optional<NexusSpaceUnitRecord> updateLodestoneVisibility(UUID actor, UUID unitId, SpaceUnitVisibility visibility, long time) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (!manageableLodestone(unit, actor) || visibility == null) return Optional.empty();
        NexusSpaceUnitRecord updated = unit.withVisibility(visibility, time); put(updated); return Optional.of(updated);
    }
    public Optional<NexusSpaceUnitRecord> renameLodestone(UUID actor, UUID unitId, String name, long time) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (!manageableLodestone(unit, actor) || name == null || name.isBlank()) return Optional.empty();
        NexusSpaceUnitRecord updated = unit.withName(name, time); put(updated); return Optional.of(updated);
    }
    public Optional<NexusSpaceUnitRecord> updateLodestoneAccess(UUID actor, UUID unitId, UUID target, NexusLodestoneAuthority.AccessRole role, boolean enabled, long time) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (!manageableLodestone(unit, actor) || target == null || target.equals(unit.owner()) || role == null
                || (role == NexusLodestoneAuthority.AccessRole.ADMINISTRATOR && !actor.equals(unit.owner()))) return Optional.empty();
        NexusSpaceUnitRecord updated = role == NexusLodestoneAuthority.AccessRole.ADMINISTRATOR
                ? unit.withAdministrator(target, enabled, time) : unit.withAllowedPlayer(target, enabled, time);
        put(updated); return Optional.of(updated);
    }
    public Optional<NexusSpaceUnitRecord> getLodestone(ResourceKey<Level> dimension, BlockPos pos) {
        UUID id = lodestoneUnitsByPosition.get(GlobalPos.of(dimension, pos.immutable())); return id == null ? Optional.empty() : get(id);
    }
    public List<NexusSpaceUnitRecord> activeLodestones() { return unitsById.values().stream()
            .filter(unit -> unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE).toList(); }
    public boolean disableLodestone(UUID unitId, long gameTime) {
        NexusSpaceUnitRecord unit = unitsById.get(unitId);
        if (unit == null || !unit.isLodestoneAnchor() || unit.status() != SpaceUnitStatus.ACTIVE) return false;
        put(new NexusSpaceUnitRecord(unit.id(), unit.type(), unit.dimension(), unit.pos(), unit.owner(), unit.name(), unit.visibility(), SpaceUnitStatus.DISABLED,
                unit.administrators(), unit.allowedPlayers(), unit.structure(), unit.createdGameTime(), gameTime));
        return true;
    }
    public List<NexusSpaceUnitRecord> visibleDiscoveredUnits(UUID player, NexusSpaceDiscoverySavedData discovery, NexusFriendSavedData friends) {
        List<NexusSpaceUnitRecord> result = new ArrayList<>();
        for (NexusSpaceUnitRecord unit : unitsById.values()) if (unit.status() == SpaceUnitStatus.ACTIVE
                && unit.canView(player, friends != null && friends.areFriends(player, unit.owner())) && discovery.hasDiscovered(player, unit.id())) result.add(unit);
        result.sort(Comparator.comparing((NexusSpaceUnitRecord unit) -> unit.dimension().identifier().toString()).thenComparing(NexusSpaceUnitRecord::name));
        return result;
    }
    private void putInternal(NexusSpaceUnitRecord unit) {
        unitsById.put(unit.id(), unit); if (unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE)
            lodestoneUnitsByPosition.put(GlobalPos.of(unit.dimension(), unit.pos().immutable()), unit.id());
    }
    private static boolean manageableLodestone(NexusSpaceUnitRecord unit, UUID actor) {
        return unit != null && unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE && unit.canManage(actor);
    }
    private int dataVersion() { return dataVersion; }
    private List<NexusSpaceUnitRecord> unitList() { return List.copyOf(unitsById.values()); }
}

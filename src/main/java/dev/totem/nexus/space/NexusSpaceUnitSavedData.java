package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;

public class NexusSpaceUnitSavedData extends SavedData {
    public static final int DATA_VERSION = 2;

    public static final Codec<NexusSpaceUnitSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(NexusSpaceUnitSavedData::dataVersion),
            NexusSpaceUnitRecord.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(NexusSpaceUnitSavedData::unitList)
    ).apply(instance, NexusSpaceUnitSavedData::new));

    public static final SavedDataType<NexusSpaceUnitSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_units"),
            NexusSpaceUnitSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<UUID, NexusSpaceUnitRecord> unitsById = new HashMap<>();
    private final Map<GlobalPos, UUID> lodestoneUnitsByPosition = new HashMap<>();
    private static final TagKey<Block> SPACE_STRUCTURE_BLOCKS = blockTag("space_unit_structure_blocks");
    private static final TagKey<Block> SPACE_STRUCTURE_WORN_BLOCKS = blockTag("space_unit_structure_worn_blocks");
    private static final TagKey<Block> SPACE_STRUCTURE_DEGRADABLE_BLOCKS = blockTag("space_unit_structure_degradable_blocks");

    public NexusSpaceUnitSavedData() {
        this(DATA_VERSION, List.of());
    }

    private NexusSpaceUnitSavedData(int dataVersion, List<NexusSpaceUnitRecord> units) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (NexusSpaceUnitRecord unit : units) {
            this.unitsById.put(unit.id(), unit);
            indexIfLodestone(unit);
        }
    }

    public Optional<NexusSpaceUnitRecord> get(UUID unitId) {
        return Optional.ofNullable(this.unitsById.get(unitId));
    }

    public Optional<NexusSpaceUnitRecord> getLodestone(ResourceKey<Level> dimension, BlockPos pos) {
        GlobalPos globalPos = GlobalPos.of(dimension, pos.immutable());
        UUID unitId = this.lodestoneUnitsByPosition.get(globalPos);
        if (unitId == null) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord unit = this.unitsById.get(unitId);
        if (unit == null || !unit.isLodestoneAnchor() || unit.status() != SpaceUnitStatus.ACTIVE) {
            this.lodestoneUnitsByPosition.remove(globalPos);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(unit);
    }

    public List<NexusSpaceUnitRecord> activeLodestones() {
        List<NexusSpaceUnitRecord> lodestones = new ArrayList<>();
        for (NexusSpaceUnitRecord unit : this.unitsById.values()) {
            if (unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE) {
                lodestones.add(unit);
            }
        }
        return lodestones;
    }

    public List<NexusSpaceUnitRecord> getVisibleDiscoveredUnits(UUID playerId, NexusSpaceDiscoverySavedData discovery) {
        return getVisibleDiscoveredUnits(playerId, discovery, null);
    }

    public List<NexusSpaceUnitRecord> getVisibleDiscoveredUnits(
            UUID playerId,
            NexusSpaceDiscoverySavedData discovery,
            NexusFriendSavedData friends) {
        List<NexusSpaceUnitRecord> visibleUnits = new ArrayList<>();
        for (NexusSpaceUnitRecord unit : this.unitsById.values()) {
            if (unit.status() != SpaceUnitStatus.ACTIVE) {
                continue;
            }
            boolean friendsWithOwner = friends != null && friends.areFriends(playerId, unit.owner());
            if (!unit.canView(playerId, friendsWithOwner)) {
                continue;
            }
            if (!discovery.hasDiscovered(playerId, unit.id())) {
                continue;
            }
            visibleUnits.add(unit);
        }
        visibleUnits.sort(Comparator
                .comparing((NexusSpaceUnitRecord unit) -> unit.dimension().identifier().toString())
                .thenComparing(NexusSpaceUnitRecord::name));
        return visibleUnits;
    }

    /** Compatibility name used by the extracted map authority. */
    public List<NexusSpaceUnitRecord> visibleDiscoveredUnits(
            UUID playerId,
            NexusSpaceDiscoverySavedData discovery,
            NexusFriendSavedData friends) {
        return getVisibleDiscoveredUnits(playerId, discovery, friends);
    }

    public SpaceStructureSnapshot previewLodestoneStructure(ServerLevel level, BlockPos pos) {
        return scanStructure(level, pos);
    }

    public NexusSpaceUnitRecord getOrCreateLodestone(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        UUID existingId = this.lodestoneUnitsByPosition.get(globalPos);
        if (existingId != null) {
            NexusSpaceUnitRecord existing = this.unitsById.get(existingId);
            if (existing != null && existing.isLodestoneAnchor() && existing.status() == SpaceUnitStatus.ACTIVE) {
                SpaceStructureSnapshot snapshot = scanStructure(level, pos);
                NexusSpaceUnitRecord updated = existing.withStructure(snapshot, level.getGameTime());
                this.unitsById.put(updated.id(), updated);
                setDirty();
                return updated;
            }
            this.lodestoneUnitsByPosition.remove(globalPos);
        }

        long gameTime = level.getGameTime();
        NexusSpaceUnitRecord created = new NexusSpaceUnitRecord(
                UUID.randomUUID(),
                SpaceUnitType.LODESTONE,
                level.dimension(),
                pos.immutable(),
                owner.getUUID(),
                "",
                SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE,
                Set.of(),
                Set.of(),
                scanStructure(level, pos),
                gameTime,
                gameTime
        );
        put(created);
        return created;
    }

    public NexusSpaceUnitRecord createDeathUnit(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        long gameTime = level.getGameTime();
        NexusSpaceUnitRecord created = new NexusSpaceUnitRecord(
                UUID.randomUUID(),
                SpaceUnitType.DEATH,
                level.dimension(),
                pos.immutable(),
                owner.getUUID(),
                "",
                SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE,
                Set.of(),
                Set.of(),
                SpaceStructureSnapshot.EMPTY,
                gameTime,
                gameTime
        );
        put(created);
        return created;
    }

    /**
     * Persists the ItemEntity UUID owned by a death node. This reverse link is
     * intentionally SavedData-only so administration diagnostics never search
     * unloaded chunks for entities.
     */
    public boolean bindDeathBackpack(UUID unitId, UUID backpackEntityId, long gameTime) {
        if (unitId == null || backpackEntityId == null) {
            return false;
        }
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || unit.get().type() != SpaceUnitType.DEATH
                || unit.get().status() != SpaceUnitStatus.ACTIVE) {
            return false;
        }
        NexusSpaceUnitRecord bound = unit.get().withBackpackId(backpackEntityId, gameTime);
        this.unitsById.put(bound.id(), bound);
        setDirty();
        return true;
    }

    public boolean disableDeathUnit(UUID ownerId, UUID unitId, long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || unit.get().type() != SpaceUnitType.DEATH
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().owner().equals(ownerId)) {
            return false;
        }

        NexusSpaceUnitRecord disabled = unit.get().withStatus(SpaceUnitStatus.DISABLED, gameTime);
        this.unitsById.put(disabled.id(), disabled);
        setDirty();
        return true;
    }

    /**
     * Completes a death-backpack recovery without requiring the recovering
     * player to be the node owner. A node that was already disabled or removed
     * by an administrator is an idempotent recovery success.
     */
    public boolean recoverDeathUnit(UUID unitId, long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()) {
            return true;
        }
        if (unit.get().type() != SpaceUnitType.DEATH) {
            return false;
        }
        if (unit.get().status() != SpaceUnitStatus.ACTIVE) {
            return true;
        }

        NexusSpaceUnitRecord disabled = unit.get().withStatus(SpaceUnitStatus.DISABLED, gameTime);
        this.unitsById.put(disabled.id(), disabled);
        setDirty();
        return true;
    }

    public boolean disableLodestone(ResourceKey<Level> dimension, BlockPos pos, long gameTime) {
        GlobalPos globalPos = GlobalPos.of(dimension, pos.immutable());
        boolean changed = this.lodestoneUnitsByPosition.remove(globalPos) != null;
        List<UUID> unitIds = new ArrayList<>();
        for (NexusSpaceUnitRecord unit : this.unitsById.values()) {
            if (unit.isLodestoneAnchor()
                    && unit.status() == SpaceUnitStatus.ACTIVE
                    && unit.dimension().equals(dimension)
                    && unit.pos().equals(pos)) {
                unitIds.add(unit.id());
            }
        }

        for (UUID unitId : unitIds) {
            NexusSpaceUnitRecord unit = this.unitsById.get(unitId);
            if (unit != null) {
                this.unitsById.put(unit.id(), unit.withStatus(SpaceUnitStatus.DISABLED, gameTime));
                changed = true;
            }
        }

        if (changed) {
            setDirty();
        }
        return !unitIds.isEmpty();
    }

    /** Disables the selected active Lodestone while retaining its legacy record. */
    public boolean disableLodestone(UUID unitId, long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        return unit.filter(NexusSpaceUnitRecord::isLodestoneAnchor)
                .map(value -> disableLodestone(value.dimension(), value.pos(), gameTime))
                .orElse(false);
    }

    public Optional<NexusSpaceUnitRecord> setLodestoneVisibility(
            UUID unitId,
            UUID playerId,
            SpaceUnitVisibility visibility,
            long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().canManage(playerId)) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord updated = unit.get().withVisibility(visibility, gameTime);
        this.unitsById.put(updated.id(), updated);
        indexIfLodestone(updated);
        setDirty();
        return Optional.of(updated);
    }

    public Optional<NexusSpaceUnitRecord> setLodestoneName(
            UUID unitId,
            UUID playerId,
            String name,
            long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().canManage(playerId)) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord updated = unit.get().withName(name, gameTime);
        this.unitsById.put(updated.id(), updated);
        indexIfLodestone(updated);
        setDirty();
        return Optional.of(updated);
    }

    public Optional<NexusSpaceUnitRecord> setLodestoneAdministrator(
            UUID unitId,
            UUID ownerId,
            UUID targetPlayerId,
            boolean enabled,
            long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().owner().equals(ownerId)
                || unit.get().owner().equals(targetPlayerId)) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord updated = unit.get().withAdministrator(targetPlayerId, enabled, gameTime);
        this.unitsById.put(updated.id(), updated);
        indexIfLodestone(updated);
        setDirty();
        return Optional.of(updated);
    }

    public Optional<NexusSpaceUnitRecord> setLodestoneAllowedPlayer(
            UUID unitId,
            UUID playerId,
            UUID targetPlayerId,
            boolean enabled,
            long gameTime) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().canManage(playerId)
                || unit.get().owner().equals(targetPlayerId)) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord updated = unit.get().withAllowedPlayer(targetPlayerId, enabled, gameTime);
        this.unitsById.put(updated.id(), updated);
        indexIfLodestone(updated);
        setDirty();
        return Optional.of(updated);
    }

    public Optional<NexusSpaceUnitRecord> updateLodestoneVisibility(
            UUID actor, UUID unitId, SpaceUnitVisibility visibility, long gameTime) {
        return setLodestoneVisibility(unitId, actor, visibility, gameTime);
    }

    public Optional<NexusSpaceUnitRecord> renameLodestone(
            UUID actor, UUID unitId, String name, long gameTime) {
        return setLodestoneName(unitId, actor, name, gameTime);
    }

    public Optional<NexusSpaceUnitRecord> updateLodestoneAccess(
            UUID actor,
            UUID unitId,
            UUID targetPlayerId,
            NexusLodestoneAuthority.AccessRole role,
            boolean enabled,
            long gameTime) {
        if (role == null) {
            return Optional.empty();
        }
        return role == NexusLodestoneAuthority.AccessRole.ADMINISTRATOR
                ? setLodestoneAdministrator(unitId, actor, targetPlayerId, enabled, gameTime)
                : setLodestoneAllowedPlayer(unitId, actor, targetPlayerId, enabled, gameTime);
    }

    /** Returns active and inactive death nodes for administrative lifecycle actions. */
    public List<NexusSpaceUnitRecord> deathNodes() {
        return this.unitsById.values().stream()
                .filter(unit -> unit.type() == SpaceUnitType.DEATH)
                .toList();
    }

    /** Removes only an inactive death node; active locations require an explicit disable first. */
    public boolean purgeInactiveDeathUnit(UUID unitId) {
        NexusSpaceUnitRecord unit = this.unitsById.get(unitId);
        if (unit == null || unit.type() != SpaceUnitType.DEATH || unit.status() == SpaceUnitStatus.ACTIVE) {
            return false;
        }
        this.unitsById.remove(unitId);
        setDirty();
        return true;
    }

    public Optional<NexusSpaceUnitRecord> rescanLodestone(ServerLevel level, UUID unitId) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty() || !unit.get().isLodestoneAnchor() || !unit.get().dimension().equals(level.dimension())) {
            return unit;
        }
        if (unit.get().status() != SpaceUnitStatus.ACTIVE) {
            return Optional.empty();
        }

        if (!level.getBlockState(unit.get().pos()).is(Blocks.LODESTONE)) {
            disableLodestone(level.dimension(), unit.get().pos(), level.getGameTime());
            return Optional.empty();
        }

        NexusSpaceUnitRecord updated = unit.get().withStructure(scanStructure(level, unit.get().pos()), level.getGameTime());
        this.unitsById.put(updated.id(), updated);
        indexIfLodestone(updated);
        setDirty();
        return Optional.of(updated);
    }

    public boolean applyLodestoneWear(ServerLevel level, UUID unitId, double chance, RandomSource random) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || chance <= 0.0D
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().dimension().equals(level.dimension())) {
            return false;
        }
        if (!level.getBlockState(unit.get().pos()).is(Blocks.LODESTONE)) {
            disableLodestone(level.dimension(), unit.get().pos(), level.getGameTime());
            return false;
        }

        if (random.nextDouble() >= chance) {
            return false;
        }

        List<BlockPos> candidates = degradableStructureBlocks(level, unit.get().pos());
        if (candidates.isEmpty()) {
            rescanLodestone(level, unitId);
            return false;
        }

        BlockPos pos = candidates.get(random.nextInt(candidates.size()));
        BlockState current = level.getBlockState(pos);
        Optional<BlockState> degraded = NexusSpaceUnitDegradationRules.degradedState(current);
        if (degraded.isEmpty()) {
            return false;
        }

        boolean changed = level.setBlockAndUpdate(pos, degraded.get());
        if (changed) {
            rescanLodestone(level, unitId);
        }
        return changed;
    }

    /** Returns only loaded, currently-scanned worn positions for one active lodestone. */
    public List<BlockPos> wornLodestoneStructureBlocks(ServerLevel level, UUID unitId) {
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().dimension().equals(level.dimension())
                || !level.getBlockState(unit.get().pos()).is(Blocks.LODESTONE)) {
            return List.of();
        }
        return wornStructureBlocks(level, unit.get().pos());
    }

    /** Replaces one verified worn position and immediately refreshes its structure snapshot. */
    public boolean repairLodestoneStructureBlock(
            ServerLevel level,
            UUID unitId,
            BlockPos wornPos,
            BlockState replacement) {
        if (wornPos == null || replacement == null || !level.isLoaded(wornPos)) {
            return false;
        }
        Optional<NexusSpaceUnitRecord> unit = get(unitId);
        if (unit.isEmpty()
                || !unit.get().isLodestoneAnchor()
                || unit.get().status() != SpaceUnitStatus.ACTIVE
                || !unit.get().dimension().equals(level.dimension())
                || !level.getBlockState(unit.get().pos()).is(Blocks.LODESTONE)
                || !wornStructureBlocks(level, unit.get().pos()).contains(wornPos.immutable())) {
            return false;
        }
        if (!level.setBlockAndUpdate(wornPos, replacement)) {
            return false;
        }
        rescanLodestone(level, unitId);
        return true;
    }

    public void put(NexusSpaceUnitRecord unit) {
        this.unitsById.put(unit.id(), unit);
        indexIfLodestone(unit);
        setDirty();
    }

    private void indexIfLodestone(NexusSpaceUnitRecord unit) {
        if (!unit.isLodestoneAnchor()) {
            return;
        }

        GlobalPos globalPos = GlobalPos.of(unit.dimension(), unit.pos());
        if (unit.status() == SpaceUnitStatus.ACTIVE) {
            this.lodestoneUnitsByPosition.put(globalPos, unit.id());
        } else if (unit.id().equals(this.lodestoneUnitsByPosition.get(globalPos))) {
            this.lodestoneUnitsByPosition.remove(globalPos);
        }
    }

    private static SpaceStructureSnapshot scanStructure(ServerLevel level, BlockPos lodestonePos) {
        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                lodestonePos,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        TeleportArrayMaterialAttributes totals = scan.totals();
        int effectiveCapacity = TeleportArrayMaterialStructurePolicy.effectiveCapacity(totals);
        double completeness = TeleportArrayMaterialStructurePolicy.completeness(effectiveCapacity);
        double symmetry = scan.checkedPairs() == 0 ? 0.0D : (double) scan.symmetricPairs() / scan.checkedPairs();
        double wear = scan.rawStructuralBlocks() == 0
                ? 0.0D
                : Math.min(1.0D, (double) scan.wornBlocks() / scan.rawStructuralBlocks());
        int interferencePoints = TeleportArrayMaterialStructurePolicy.interference(
                scan.familyCounts().size(), totals.interferenceResistance());
        int stabilityPoints = TeleportArrayMaterialStructurePolicy.stability(
                completeness, symmetry, totals.stability(), interferencePoints);
        double resonance = stabilityPoints / 100.0D;
        resonance *= 1.0D - (wear * 0.35D);
        int tier = TeleportArrayMaterialStructurePolicy.tier(effectiveCapacity);
        Map<String, Integer> materialTotals = new LinkedHashMap<>();
        materialTotals.put("raw_structural_blocks", scan.rawStructuralBlocks());
        materialTotals.put("effective_structure_capacity", effectiveCapacity);
        materialTotals.put("maximum_reached_distance", scan.maximumReachedDistance());
        materialTotals.put("profile_revision", (int) Math.min(Integer.MAX_VALUE, TeleportArrayMaterialProfiles.revision()));
        materialTotals.put("expansion_mode", NexusTeleportArrayExpansionRules.mode(level).snapshotCode());
        materialTotals.put("stability", totals.stability());
        materialTotals.put("arrival_accuracy", totals.arrivalAccuracy());
        materialTotals.put("target_lock", totals.targetLock());
        materialTotals.put("arrival_safety", totals.arrivalSafety());
        materialTotals.put("wear_resistance", totals.wearResistance());
        materialTotals.put("maintenance_efficiency", totals.maintenanceEfficiency());
        materialTotals.put("interference_resistance", totals.interferenceResistance());
        materialTotals.put("food_efficiency", totals.foodEfficiency());
        materialTotals.put("phase_speed", totals.phaseSpeed());
        materialTotals.put("cooldown_recovery", totals.cooldownRecovery());
        materialTotals.put("route_load_capacity", totals.routeLoadCapacity());
        materialTotals.put("cross_dimension_catalyst_units", totals.crossDimensionCatalystUnits());
        return new SpaceStructureSnapshot(
                completeness,
                symmetry,
                resonance,
                interferencePoints / 100.0D,
                stabilityPoints / 100.0D,
                wear,
                tier,
                Math.max(0, totals.crossDimensionCatalystUnits()),
                new SpaceStructureSnapshot.MaterialState(
                        SpaceStructureSnapshot.MaterialState.CURRENT_SCHEMA_VERSION,
                        materialTotals,
                        scan.familyCounts(),
                        materialFamilyContributions(scan.familyContributions()),
                        totals.dimensionAffinity(),
                        scan.localExpansionPathCounts(),
                        false)
        );
    }

    private static Map<String, Map<String, Integer>> materialFamilyContributions(
            Map<String, TeleportArrayMaterialAttributes> contributions) {
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        contributions.forEach((family, attributes) -> values.put(family, attributes.scalarValues()));
        return Map.copyOf(values);
    }

    static boolean isStructureBlock(BlockState state) {
        return state.is(SPACE_STRUCTURE_BLOCKS);
    }

    private static List<BlockPos> degradableStructureBlocks(ServerLevel level, BlockPos lodestonePos) {
        List<BlockPos> candidates = new ArrayList<>();
        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                lodestonePos,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        for (BlockPos scanPos : scan.structuralPositions()) {
            if (isDegradableStructureBlock(level.getBlockState(scanPos))) {
                candidates.add(scanPos.immutable());
            }
        }
        return candidates;
    }

    private static List<BlockPos> wornStructureBlocks(ServerLevel level, BlockPos lodestonePos) {
        List<BlockPos> candidates = new ArrayList<>();
        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                lodestonePos,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        for (BlockPos scanPos : scan.structuralPositions()) {
            if (isWornStructureBlock(level.getBlockState(scanPos))) {
                candidates.add(scanPos.immutable());
            }
        }
        candidates.sort(Comparator.<BlockPos>comparingInt(position -> position.getY())
                .thenComparingInt(position -> position.getX())
                .thenComparingInt(position -> position.getZ()));
        return List.copyOf(candidates);
    }

    public static boolean isWornStructureBlock(BlockState state) {
        return state.is(SPACE_STRUCTURE_WORN_BLOCKS);
    }

    private static boolean isDegradableStructureBlock(BlockState state) {
        return state.is(SPACE_STRUCTURE_DEGRADABLE_BLOCKS) && NexusSpaceUnitDegradationRules.degradedState(state).isPresent();
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("deadrecall", path));
    }

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<NexusSpaceUnitRecord> unitList() {
        return new ArrayList<>(this.unitsById.values());
    }
}

package com.adaptor.deadrecall.space;

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

public class DeadRecallSpaceUnitSavedData extends SavedData {
    public static final int DATA_VERSION = 1;

    public static final Codec<DeadRecallSpaceUnitSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(DeadRecallSpaceUnitSavedData::dataVersion),
            SpaceUnitRecord.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(DeadRecallSpaceUnitSavedData::unitList)
    ).apply(instance, DeadRecallSpaceUnitSavedData::new));

    public static final SavedDataType<DeadRecallSpaceUnitSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "space_units"),
            DeadRecallSpaceUnitSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<UUID, SpaceUnitRecord> unitsById = new HashMap<>();
    private final Map<GlobalPos, UUID> lodestoneUnitsByPosition = new HashMap<>();

    public DeadRecallSpaceUnitSavedData() {
        this(DATA_VERSION, List.of());
    }

    private DeadRecallSpaceUnitSavedData(int dataVersion, List<SpaceUnitRecord> units) {
        this.dataVersion = Math.max(dataVersion, DATA_VERSION);
        for (SpaceUnitRecord unit : units) {
            this.unitsById.put(unit.id(), unit);
            indexIfLodestone(unit);
        }
    }

    public Optional<SpaceUnitRecord> get(UUID unitId) {
        return Optional.ofNullable(this.unitsById.get(unitId));
    }

    public Optional<SpaceUnitRecord> getLodestone(ResourceKey<Level> dimension, BlockPos pos) {
        UUID unitId = this.lodestoneUnitsByPosition.get(GlobalPos.of(dimension, pos));
        return unitId == null ? Optional.empty() : get(unitId);
    }

    public List<SpaceUnitRecord> getVisibleDiscoveredUnits(UUID playerId, DeadRecallSpaceDiscoverySavedData discovery) {
        List<SpaceUnitRecord> visibleUnits = new ArrayList<>();
        for (SpaceUnitRecord unit : this.unitsById.values()) {
            if (unit.status() != SpaceUnitStatus.ACTIVE) {
                continue;
            }
            if (!unit.canView(playerId)) {
                continue;
            }
            if (!discovery.hasDiscovered(playerId, unit.id())) {
                continue;
            }
            visibleUnits.add(unit);
        }
        visibleUnits.sort(Comparator
                .comparing((SpaceUnitRecord unit) -> unit.dimension().identifier().toString())
                .thenComparing(SpaceUnitRecord::name));
        return visibleUnits;
    }

    public SpaceUnitRecord getOrCreateLodestone(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        UUID existingId = this.lodestoneUnitsByPosition.get(globalPos);
        if (existingId != null) {
            SpaceUnitRecord existing = this.unitsById.get(existingId);
            if (existing != null) {
                SpaceStructureSnapshot snapshot = scanStructure(level, pos);
                SpaceUnitRecord updated = existing.withStructure(snapshot, level.getGameTime());
                this.unitsById.put(updated.id(), updated);
                setDirty();
                return updated;
            }
            this.lodestoneUnitsByPosition.remove(globalPos);
        }

        long gameTime = level.getGameTime();
        SpaceUnitRecord created = new SpaceUnitRecord(
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

    public SpaceUnitRecord createDeathUnit(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        long gameTime = level.getGameTime();
        SpaceUnitRecord created = new SpaceUnitRecord(
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

    private void put(SpaceUnitRecord unit) {
        this.unitsById.put(unit.id(), unit);
        indexIfLodestone(unit);
        setDirty();
    }

    private void indexIfLodestone(SpaceUnitRecord unit) {
        if (unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE) {
            this.lodestoneUnitsByPosition.put(GlobalPos.of(unit.dimension(), unit.pos()), unit.id());
        }
    }

    private static SpaceStructureSnapshot scanStructure(ServerLevel level, BlockPos lodestonePos) {
        int structuralBlocks = 0;
        int symmetricPairs = 0;
        int checkedPairs = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    BlockPos scanPos = lodestonePos.offset(dx, dy, dz);
                    if (isStructureBlock(level.getBlockState(scanPos))) {
                        structuralBlocks++;
                    }

                    if (dx > 0 || (dx == 0 && dz > 0)) {
                        checkedPairs++;
                        BlockPos mirrorPos = lodestonePos.offset(-dx, dy, -dz);
                        if (level.getBlockState(scanPos).is(level.getBlockState(mirrorPos).getBlock())) {
                            symmetricPairs++;
                        }
                    }
                }
            }
        }

        double completeness = Math.min(1.0D, structuralBlocks / 24.0D);
        double symmetry = checkedPairs == 0 ? 0.0D : (double) symmetricPairs / checkedPairs;
        double resonance = Math.min(1.0D, (completeness * 0.7D) + (symmetry * 0.3D));
        int tier = structuralBlocks >= 24 ? 2 : structuralBlocks >= 8 ? 1 : 0;
        return new SpaceStructureSnapshot(completeness, symmetry, resonance, 0.0D, 1.0D, 0.0D, tier);
    }

    private static boolean isStructureBlock(BlockState state) {
        Block block = state.getBlock();
        return state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.DEEPSLATE_BRICKS)
                || state.is(Blocks.DEEPSLATE_TILES)
                || state.is(Blocks.POLISHED_DEEPSLATE)
                || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || state.is(Blocks.CHISELED_DEEPSLATE)
                || state.is(Blocks.AMETHYST_BLOCK)
                || Blocks.COPPER_BLOCK.asList().contains(block)
                || Blocks.CUT_COPPER.asList().contains(block)
                || Blocks.CHISELED_COPPER.asList().contains(block);
    }

    private int dataVersion() {
        return this.dataVersion;
    }

    private List<SpaceUnitRecord> unitList() {
        return new ArrayList<>(this.unitsById.values());
    }
}

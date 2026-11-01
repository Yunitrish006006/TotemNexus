package dev.totem.nexus.space;

import dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;
import java.util.UUID;

/** Server-owned creation and cartography lifecycle for Nexus-bound vanilla maps. */
public final class NexusMapLifecycleAuthority {
    private NexusMapLifecycleAuthority() { }

    static Optional<ItemStack> createExactCenteredMap(
            ServerLevel level,
            BlockPos anchorPos,
            UUID unitId,
            ItemStack componentSource) {
        if (level == null || anchorPos == null || unitId == null || componentSource == null
                || !componentSource.is(Items.MAP) || !level.isLoaded(anchorPos)
                || !level.getBlockState(anchorPos).is(Blocks.LODESTONE)) return Optional.empty();

        var storage = level.getServer().overworld().getDataStorage();
        NexusSpaceUnitRecord unit = NexusSpaceUnitSavedData.loadCanonical(storage).get(unitId)
                .filter(value -> value.isLodestoneAnchor()
                        && value.status() == SpaceUnitStatus.ACTIVE
                        && value.dimension().equals(level.dimension())
                        && value.pos().equals(anchorPos))
                .orElse(null);
        if (unit == null) return Optional.empty();

        MapItemSavedData mapData = exactData(
                anchorPos.getX(), anchorPos.getZ(), (byte) 0, false, level.dimension());
        NexusMapRecordSavedData records;
        try {
            records = storage.computeIfAbsent(NexusMapRecordSavedData.TYPE);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        NexusMapBindingSavedData legacy = NexusMapBindingSavedData.loadCanonical(storage);
        MapId mapId = null;
        for (int attempt = 0; attempt < 1024; attempt++) {
            if (mapAllocatorExhausted(level)) break;
            MapId candidate = nextMapId(level);
            if (level.getMapData(candidate) != null || records.get(candidate).isPresent()
                    || legacy.get(candidate).isPresent()) {
                consumeExpectedMapId(level, candidate);
                continue;
            }
            consumeExpectedMapId(level, candidate);
            NexusMapRecordSavedData.BootstrapResult baseline = records.bootstrap(candidate, mapData);
            if (baseline.result() != NexusMapRecordSavedData.RegisterResult.CONFLICT) {
                mapId = candidate;
                break;
            }
        }
        if (mapId == null) return Optional.empty();

        ItemStack result = new ItemStack(Items.FILLED_MAP);
        result.applyComponents(componentSource.getComponentsPatch());
        result.set(DataComponents.MAP_ID, mapId);
        result.remove(DataComponents.MAP_DECORATIONS);
        NexusInterfaceBinding.clearIdentityMetadata(result);
        level.setMapData(mapId, mapData);
        return Optional.of(result);
    }

    static Optional<ItemStack> createBoundMap(
            ServerLevel level,
            BlockPos anchorPos,
            UUID unitId,
            ItemStack componentSource) {
        if (level == null || anchorPos == null || unitId == null || componentSource == null
                || !componentSource.is(Items.MAP) || !level.isLoaded(anchorPos)
                || !level.getBlockState(anchorPos).is(Blocks.LODESTONE)) return Optional.empty();

        var storage = level.getServer().overworld().getDataStorage();
        NexusSpaceUnitRecord unit = NexusSpaceUnitSavedData.loadCanonical(storage).get(unitId)
                .filter(value -> value.isLodestoneAnchor()
                        && value.status() == SpaceUnitStatus.ACTIVE
                        && value.dimension().equals(level.dimension())
                        && value.pos().equals(anchorPos))
                .orElse(null);
        if (unit == null) return Optional.empty();

        NexusMapBindingSavedData bindings = NexusMapBindingSavedData.loadCanonical(storage);
        if (!bindings.acceptsMutation()) return Optional.empty();
        var terrain = NexusMapDetailSavedData.available(level).orElse(null);
        if (terrain == null || !terrain.acceptsMutation()) return Optional.empty();

        MapItemSavedData mapData = exactData(
                anchorPos.getX(), anchorPos.getZ(), (byte) 0, false, level.dimension());
        MapId mapId = level.getFreeMapId();
        ItemStack result = new ItemStack(Items.FILLED_MAP);
        result.applyComponents(componentSource.getComponentsPatch());
        result.set(DataComponents.MAP_ID, mapId);
        result.remove(DataComponents.MAP_DECORATIONS);
        if (!NexusInterfaceBinding.write(result, level, anchorPos, unitId)) return Optional.empty();

        if (!bindings.bind(mapId, unitId, GlobalPos.of(level.dimension(), anchorPos.immutable()), mapData)) {
            return Optional.empty();
        }
        level.setMapData(mapId, mapData);
        return Optional.of(result);
    }

    /** Recipe-level no-force-load gate used before a server crafting result exists. */
    public static ScaleEligibility expansionAnchorEligibility(ItemStack stack, ServerLevel level) {
        return postProcessEligibility(stack, level, MapPostProcessing.SCALE);
    }

    /** Shared pre-consumption classification for SCALE and LOCK menu results. */
    public static ScaleEligibility postProcessEligibility(
            ItemStack stack,
            ServerLevel level,
            MapPostProcessing processing) {
        MapIdRecordResolution mapIdResolution = resolveMapIdRecord(stack, level);
        if (mapIdResolution.registered()) {
            MapIdValidatedMap map = mapIdResolution.validated();
            if (map == null || processing == null) return ScaleEligibility.DENIED;
            var terrain = NexusMapDetailSavedData.available(level).orElse(null);
            if (terrain == null || !terrain.acceptsMutation() || !terrain.canDerive(map.mapId().id())) {
                return ScaleEligibility.DENIED;
            }
            if (processing == MapPostProcessing.SCALE) {
                return map.data().locked || map.entry().extent(map.data()).expanded().isEmpty()
                        ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
            }
            if (processing == MapPostProcessing.LOCK) {
                return map.data().locked ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
            }
            return ScaleEligibility.DENIED;
        }

        RegistryResolution resolution = resolveRegistryBinding(stack, level);
        if (!resolution.registered()) return ScaleEligibility.NOT_NEXUS;
        ValidatedMap validated = resolution.validated();
        if (validated == null || processing == null || !NexusMapBindingSavedData.loadCanonical(
                level.getServer().overworld().getDataStorage()).acceptsMutation()) return ScaleEligibility.DENIED;
        var terrain = NexusMapDetailSavedData.available(level).orElse(null);
        if (terrain == null || !terrain.acceptsMutation()) return ScaleEligibility.DENIED;
        if (processing == MapPostProcessing.SCALE) {
            return validated.data().locked || validated.entry().extent(validated.data()).expanded().isEmpty()
                    || !terrain.canDerive(validated.mapId().id(), validated.entry())
                    ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
        }
        if (processing == MapPostProcessing.LOCK) {
            return validated.data().locked || !terrain.canDerive(validated.mapId().id(), validated.entry())
                    ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
        }
        return ScaleEligibility.DENIED;
    }

    /**
     * Replaces vanilla post-processing only for a server-recognized Nexus map.
     * SCALE keeps the exact persisted anchor center; LOCK keeps vanilla pixels
     * and decorations. A denied late/racy SCALE leaves the old MapId untouched.
     */
    public static PostProcessResult postProcess(
            ItemStack stack,
            ServerLevel level,
            MapPostProcessing processing) {
        if (processing == null) return PostProcessResult.NOT_NEXUS;
        MapIdRecordResolution mapIdResolution = resolveMapIdRecord(stack, level);
        if (mapIdResolution.registered()) {
            return mapIdResolution.validated() == null
                    ? PostProcessResult.DENIED
                    : postProcessMapId(stack, level, processing, mapIdResolution.validated());
        }

        RegistryResolution resolution = resolveRegistryBinding(stack, level);
        if (!resolution.registered()) return PostProcessResult.NOT_NEXUS;
        ValidatedMap validated = resolution.validated();
        if (validated == null) return PostProcessResult.DENIED;
        NexusMapBindingSavedData bindings = NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        if (!bindings.acceptsMutation()) return PostProcessResult.DENIED;
        var terrain = NexusMapDetailSavedData.available(level).orElse(null);
        if (terrain == null || !terrain.acceptsMutation()) return PostProcessResult.DENIED;

        MapItemSavedData resultData;
        if (processing == MapPostProcessing.SCALE) {
            if (validated.data().locked || validated.entry().extent(validated.data()).expanded().isEmpty()
                    || !terrain.canDerive(validated.mapId().id(), validated.entry())) {
                return PostProcessResult.DENIED;
            }
            resultData = exactData(
                    validated.entry().centerX(),
                    validated.entry().centerZ(),
                    validated.entry().extent(validated.data()).expanded().orElseThrow().vanillaScale(),
                    false,
                    validated.data().dimension);
            // Native pixels retain their true scale-four coordinates beyond vanilla's limit.
            // Extended exploration lives in map-owned detail pages, never a stretched authorization pixel.
            if (validated.data().scale == MapItemSavedData.MAX_SCALE) {
                System.arraycopy(validated.data().colors, 0, resultData.colors, 0, 16384);
            } else {
                // Preserve the source's known footprint in the central quarter of the expanded map.
                for (int z=0;z<64;z++) for(int x=0;x<64;x++) {
                    int[] counts=new int[256]; int selected=0;
                    for(int dz=0;dz<2;dz++) for(int dx=0;dx<2;dx++) {
                        int color=validated.data().colors[x*2+dx+(z*2+dz)*128]&255;
                        if(color/4!=0 && ++counts[color]>counts[selected]) selected=color;
                    }
                    if(selected!=0) resultData.setColor(32+x,32+z,(byte)selected);
                }
            }
        } else if (processing == MapPostProcessing.LOCK) {
            if (!terrain.canDerive(validated.mapId().id(), validated.entry())) return PostProcessResult.DENIED;
            if (validated.data().locked) return PostProcessResult.DENIED;
            resultData = validated.data().locked();
        } else {
            return PostProcessResult.NOT_NEXUS;
        }

        var index = level.getServer().getDataStorage()
                .computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
        int last = ((dev.totem.nexus.mixin.NexusMapIndexAccessor) index).totem$lastMapId();
        if (last > Integer.MAX_VALUE - (validated.entry().detailAncestors().size() + 2))
            return PostProcessResult.DENIED;
        MapId candidate = new MapId(last + 1);
        int nextScale = validated.entry().extent(validated.data()).scale() + (processing == MapPostProcessing.SCALE ? 1 : 0);
        var prepared = bindings.prepareDerivation(validated.mapId(), validated.data(), candidate, resultData, nextScale);
        if (prepared.isEmpty() || level.getMapData(candidate) != null) return PostProcessResult.DENIED;
        // No expected refusal remains beyond allocation. Server-thread execution prevents interleaving.
        MapId resultMapId = level.getFreeMapId();
        if (!candidate.equals(resultMapId)) throw new IllegalStateException("Map allocator changed during commit");
        bindings.commitDerivation(prepared.get());
        terrain.derive(level, validated.mapId(), validated.data(),
                validated.entry(), resultMapId);
        level.setMapData(resultMapId, resultData);
        stack.set(DataComponents.MAP_ID, resultMapId);
        return PostProcessResult.PROCESSED;
    }

    private static PostProcessResult postProcessMapId(
            ItemStack stack,
            ServerLevel level,
            MapPostProcessing processing,
            MapIdValidatedMap map) {
        var terrain = NexusMapDetailSavedData.available(level).orElse(null);
        if (terrain == null || !terrain.acceptsMutation() || !terrain.canDerive(map.mapId().id())) {
            return PostProcessResult.DENIED;
        }

        NexusMapExtent extent = map.entry().extent(map.data());
        int targetLogicalScale = extent.scale();
        MapItemSavedData resultData;
        if (processing == MapPostProcessing.SCALE) {
            NexusMapExtent expanded = extent.expanded().orElse(null);
            if (map.data().locked || expanded == null) return PostProcessResult.DENIED;
            targetLogicalScale = expanded.scale();
            resultData = exactData(map.entry().centerX(), map.entry().centerZ(), expanded.vanillaScale(),
                    false, map.entry().dimension());
            copyScaledPixels(map.data(), resultData);
        } else if (processing == MapPostProcessing.LOCK) {
            if (map.data().locked) return PostProcessResult.DENIED;
            resultData = map.data().locked();
        } else {
            return PostProcessResult.NOT_NEXUS;
        }

        MapId resultMapId = null;
        for (int attempt = 0; attempt < 1024; attempt++) {
            if (mapAllocatorExhausted(level)) break;
            MapId candidate = nextMapId(level);
            if (level.getMapData(candidate) != null || map.records().get(candidate).isPresent()) {
                consumeExpectedMapId(level, candidate);
                continue;
            }
            consumeExpectedMapId(level, candidate);
            resultMapId = candidate;
            break;
        }
        if (resultMapId == null) return PostProcessResult.DENIED;
        var prepared = map.records().prepareDerivation(map.mapId(), map.data(), resultMapId,
                resultData, targetLogicalScale);
        if (prepared.isEmpty()) return PostProcessResult.DENIED;
        if (!map.records().commitDerivation(prepared.get())) {
            throw new IllegalStateException("Prepared Nexus MapId derivation changed during commit");
        }
        terrain.derive(level, map.mapId(), map.data(), null, resultMapId);
        level.setMapData(resultMapId, resultData);
        stack.set(DataComponents.MAP_ID, resultMapId);
        return PostProcessResult.PROCESSED;
    }

    private static boolean mapAllocatorExhausted(ServerLevel level) {
        var index = level.getServer().getDataStorage()
                .computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
        return mapAllocatorExhausted(
                ((dev.totem.nexus.mixin.NexusMapIndexAccessor) index).totem$lastMapId());
    }

    static boolean mapAllocatorExhausted(int lastMapId) {
        return lastMapId == Integer.MAX_VALUE;
    }

    private static MapId nextMapId(ServerLevel level) {
        var index = level.getServer().getDataStorage()
                .computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
        int last = ((dev.totem.nexus.mixin.NexusMapIndexAccessor) index).totem$lastMapId();
        return new MapId(last + 1);
    }

    private static void consumeExpectedMapId(ServerLevel level, MapId expected) {
        MapId allocated = level.getFreeMapId();
        if (!expected.equals(allocated)) {
            throw new IllegalStateException("Map allocator changed during Nexus MapId allocation");
        }
    }

    private static void copyScaledPixels(MapItemSavedData source, MapItemSavedData target) {
        if (source.scale == MapItemSavedData.MAX_SCALE) {
            System.arraycopy(source.colors, 0, target.colors, 0, source.colors.length);
            return;
        }
        for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) {
            int[] counts = new int[256];
            int selected = 0;
            for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
                int color = source.colors[x * 2 + dx + (z * 2 + dz) * 128] & 255;
                if (color / 4 != 0 && ++counts[color] > counts[selected]) selected = color;
            }
            if (selected != 0) target.setColor(32 + x, 32 + z, (byte) selected);
        }
    }

    static MapItemSavedData exactData(
            int centerX,
            int centerZ,
            byte scale,
            boolean locked,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return NexusMapItemSavedDataInvoker.totem$createExact(
                centerX, centerZ, scale, false, false, locked, dimension);
    }

    private static RegistryResolution resolveRegistryBinding(ItemStack stack, ServerLevel level) {
        if (stack == null || level == null || !stack.is(Items.FILLED_MAP)) return RegistryResolution.NOT_NEXUS;
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) return RegistryResolution.NOT_NEXUS;

        var storage = level.getServer().overworld().getDataStorage();
        NexusMapBindingSavedData.Entry entry = NexusMapBindingSavedData.loadCanonical(storage)
                .get(mapId).orElse(null);
        if (entry == null) return NexusInterfaceBinding.hasIdentityMetadata(stack)
                ? RegistryResolution.INVALID_NEXUS : RegistryResolution.NOT_NEXUS;
        if (!NexusInterfaceBinding.hasIdentityMetadata(stack)) return RegistryResolution.NOT_NEXUS;
        UUID claimedUnitId = NexusInterfaceBinding.read(stack);
        MapItemSavedData mapData = level.getMapData(mapId);
        if (claimedUnitId == null || mapData == null
                || !entry.unitId().equals(claimedUnitId)
                || !entry.matchesMapData(mapData)) return RegistryResolution.INVALID_NEXUS;
        return new RegistryResolution(true, new ValidatedMap(mapId, mapData, entry));
    }

    private static MapIdRecordResolution resolveMapIdRecord(ItemStack stack, ServerLevel level) {
        if (stack == null || level == null || !stack.is(Items.FILLED_MAP)) {
            return MapIdRecordResolution.NOT_NEXUS;
        }
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) return MapIdRecordResolution.NOT_NEXUS;
        var storage = level.getServer().overworld().getDataStorage();
        NexusMapRecordSavedData records = storage.get(NexusMapRecordSavedData.TYPE);
        if (records == null || records.get(mapId).isEmpty()) return MapIdRecordResolution.NOT_NEXUS;
        MapItemSavedData mapData = level.getMapData(mapId);
        NexusMapRecordSavedData.Entry entry = records.resolve(mapId, mapData).orElse(null);
        return entry == null
                ? MapIdRecordResolution.INVALID_NEXUS
                : new MapIdRecordResolution(true, new MapIdValidatedMap(mapId, mapData, entry, records));
    }

    public enum ScaleEligibility { NOT_NEXUS, ALLOWED, DENIED }

    public enum PostProcessResult { NOT_NEXUS, PROCESSED, DENIED }

    private record ValidatedMap(
            MapId mapId,
            MapItemSavedData data,
            NexusMapBindingSavedData.Entry entry) { }

    private record RegistryResolution(boolean registered, ValidatedMap validated) {
        private static final RegistryResolution NOT_NEXUS = new RegistryResolution(false, null);
        private static final RegistryResolution INVALID_NEXUS = new RegistryResolution(true, null);
    }

    private record MapIdValidatedMap(
            MapId mapId,
            MapItemSavedData data,
            NexusMapRecordSavedData.Entry entry,
            NexusMapRecordSavedData records) { }

    private record MapIdRecordResolution(boolean registered, MapIdValidatedMap validated) {
        private static final MapIdRecordResolution NOT_NEXUS = new MapIdRecordResolution(false, null);
        private static final MapIdRecordResolution INVALID_NEXUS = new MapIdRecordResolution(true, null);
    }
}

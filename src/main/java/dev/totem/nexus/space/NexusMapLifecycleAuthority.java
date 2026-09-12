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

        MapItemSavedData mapData = exactData(
                anchorPos.getX(), anchorPos.getZ(), (byte) 0, false, level.dimension());
        MapId mapId = level.getFreeMapId();
        ItemStack result = new ItemStack(Items.FILLED_MAP);
        result.applyComponents(componentSource.getComponentsPatch());
        result.set(DataComponents.MAP_ID, mapId);
        result.remove(DataComponents.MAP_DECORATIONS);
        if (!NexusInterfaceBinding.write(result, level, anchorPos, unitId)) return Optional.empty();

        NexusMapBindingSavedData bindings = NexusMapBindingSavedData.loadCanonical(storage);
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
        RegistryResolution resolution = resolveRegistryBinding(stack, level);
        if (!resolution.registered()) return ScaleEligibility.NOT_NEXUS;
        ValidatedMap validated = resolution.validated();
        if (validated == null || processing == null) return ScaleEligibility.DENIED;
        if (processing == MapPostProcessing.SCALE) {
            return validated.data().locked || validated.data().scale >= MapItemSavedData.MAX_SCALE
                    || !anchorAvailable(level, validated.entry(), validated.unit())
                    ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
        }
        if (processing == MapPostProcessing.LOCK) {
            return validated.data().locked ? ScaleEligibility.DENIED : ScaleEligibility.ALLOWED;
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
        RegistryResolution resolution = resolveRegistryBinding(stack, level);
        if (!resolution.registered()) return PostProcessResult.NOT_NEXUS;
        ValidatedMap validated = resolution.validated();
        if (validated == null) return PostProcessResult.DENIED;

        MapItemSavedData resultData;
        if (processing == MapPostProcessing.SCALE) {
            if (validated.data().locked || validated.data().scale >= MapItemSavedData.MAX_SCALE
                    || !anchorAvailable(level, validated.entry(), validated.unit())) {
                return PostProcessResult.DENIED;
            }
            resultData = exactData(
                    validated.entry().centerX(),
                    validated.entry().centerZ(),
                    (byte) (validated.data().scale + 1),
                    false,
                    validated.entry().anchor().dimension());
            // Preserve the source's known footprint in the central quarter of the expanded map.
            for (int z=0;z<64;z++) for(int x=0;x<64;x++) {
                int[] counts=new int[256]; int selected=0;
                for(int dz=0;dz<2;dz++) for(int dx=0;dx<2;dx++) {
                    int color=validated.data().colors[x*2+dx+(z*2+dz)*128]&255;
                    if(color/4!=0 && ++counts[color]>counts[selected]) selected=color;
                }
                if(selected!=0) resultData.setColor(32+x,32+z,(byte)selected);
            }
        } else if (processing == MapPostProcessing.LOCK) {
            if (validated.data().locked) return PostProcessResult.DENIED;
            resultData = validated.data().locked();
        } else {
            return PostProcessResult.NOT_NEXUS;
        }

        MapId resultMapId = level.getFreeMapId();
        NexusMapBindingSavedData bindings = NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        if (!bindings.derive(validated.mapId(), validated.data(), resultMapId, resultData)) {
            return PostProcessResult.DENIED;
        }
        NexusMapDetailSavedData.get(level).derive(level, validated.mapId(), validated.data(),
                validated.entry(), resultMapId);
        level.setMapData(resultMapId, resultData);
        stack.set(DataComponents.MAP_ID, resultMapId);
        return PostProcessResult.PROCESSED;
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
        if (entry == null) return RegistryResolution.NOT_NEXUS;
        UUID claimedUnitId = NexusInterfaceBinding.read(stack);
        MapItemSavedData mapData = level.getMapData(mapId);
        if (claimedUnitId == null || mapData == null
                || !entry.unitId().equals(claimedUnitId)
                || !entry.anchor().dimension().equals(mapData.dimension)
                || entry.centerX() != mapData.centerX
                || entry.centerZ() != mapData.centerZ) return RegistryResolution.INVALID_NEXUS;
        NexusSpaceUnitRecord unit = NexusSpaceUnitSavedData.loadCanonical(storage)
                .get(entry.unitId()).filter(entry::matchesUnit).orElse(null);
        return unit == null
                ? RegistryResolution.INVALID_NEXUS
                : new RegistryResolution(true, new ValidatedMap(mapId, mapData, entry, unit));
    }

    private static boolean anchorAvailable(
            ServerLevel currentLevel,
            NexusMapBindingSavedData.Entry entry,
            NexusSpaceUnitRecord unit) {
        if (!entry.matchesUnit(unit)) return false;
        ServerLevel anchorLevel = currentLevel.getServer().getLevel(entry.anchor().dimension());
        BlockPos anchorPos = entry.anchor().pos();
        return anchorLevel != null
                && anchorLevel.isLoaded(anchorPos)
                && anchorLevel.getBlockState(anchorPos).is(Blocks.LODESTONE);
    }

    public enum ScaleEligibility { NOT_NEXUS, ALLOWED, DENIED }

    public enum PostProcessResult { NOT_NEXUS, PROCESSED, DENIED }

    private record ValidatedMap(
            MapId mapId,
            MapItemSavedData data,
            NexusMapBindingSavedData.Entry entry,
            NexusSpaceUnitRecord unit) { }

    private record RegistryResolution(boolean registered, ValidatedMap validated) {
        private static final RegistryResolution NOT_NEXUS = new RegistryResolution(false, null);
        private static final RegistryResolution INVALID_NEXUS = new RegistryResolution(true, null);
    }
}

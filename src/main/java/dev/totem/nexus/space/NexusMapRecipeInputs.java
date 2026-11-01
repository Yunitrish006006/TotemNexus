package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;

import java.util.List;
import java.util.Optional;

/** Pure checks for native paper recipes and the exact map represented by a preview. */
public final class NexusMapRecipeInputs {
    private NexusMapRecipeInputs() { }

    public static Optional<ItemStack> workbenchSource(List<ItemStack> grid, MapPostProcessing processing) {
        if (processing != MapPostProcessing.SCALE || grid.size() != 9) return Optional.empty();
        for (int i = 0; i < 9; i++) {
            if (i == 4 ? !isSourceMap(grid.get(i)) : !grid.get(i).is(Items.PAPER)) return Optional.empty();
        }
        return Optional.of(grid.get(4));
    }

    public static Optional<ItemStack> cartographySource(ItemStack map, ItemStack addition,
                                                       MapPostProcessing processing) {
        if (!isSourceMap(map)) return Optional.empty();
        boolean matches = processing == MapPostProcessing.SCALE && addition.is(Items.PAPER)
                || processing == MapPostProcessing.LOCK && addition.is(Items.GLASS_PANE);
        return matches ? Optional.of(map) : Optional.empty();
    }

    /** Returns the non-mutating native cartography preview for an exact cleansing pair. */
    public static ItemStack cleansingPreview(ItemStack map, ItemStack addition) {
        if (!isCleansingPair(map, addition)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.MAP);
    }

    public static boolean isCleansingPair(ItemStack map, ItemStack addition) {
        return map != null && addition != null
                && map.getCount() == 1 && addition.getCount() == 1
                && isSourceMap(map) && addition.is(Items.WATER_BUCKET);
    }

    public static boolean isCleanEmptyMap(ItemStack result) {
        return result != null && result.getCount() == 1
                && ItemStack.isSameItemSameComponents(new ItemStack(Items.MAP), result);
    }

    public static boolean matchesCleansingTake(ItemStack map, ItemStack addition, ItemStack result) {
        return isCleansingPair(map, addition) && isCleanEmptyMap(result);
    }

    public static boolean matchesResult(ItemStack source, ItemStack result, MapPostProcessing processing) {
        if (!isSourceMap(source) || result.getCount() != 1 || processing == null
                || result.get(DataComponents.MAP_POST_PROCESSING) != processing) return false;
        ItemStack expected = source.copyWithCount(1);
        expected.set(DataComponents.MAP_POST_PROCESSING, processing);
        return ItemStack.isSameItemSameComponents(expected, result);
    }

    private static boolean isSourceMap(ItemStack map) {
        return map.is(Items.FILLED_MAP) && map.has(DataComponents.MAP_ID)
                && !map.has(DataComponents.MAP_POST_PROCESSING);
    }
}
